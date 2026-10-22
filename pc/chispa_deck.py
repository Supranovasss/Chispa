#!/usr/bin/env python3
"""Chispa Deck - recibe las órdenes de los botones de Chispa y las ejecuta en este computador.

Solo usa la librería estándar de Python. Pensado para Windows (atajos de teclado, apagar, bloquear);
en otros sistemas funcionan abrir programas y OBS.
Escucha en la red de tu casa. Cada orden debe traer el código secreto que se muestra al iniciar.
"""
import base64
import ctypes
import hashlib
import hmac
import http.server
import json
import os
import secrets
import socket
import subprocess
import sys
import threading

VERSION = "1.0"
PORT = 8777
WIN = os.name == "nt"
CFG_PATH = os.path.join(os.path.expanduser("~"), "ChispaDeck.json")
CFG = {"token": "", "obs": {"host": "127.0.0.1", "port": 4455, "password": ""}, "allow_cmd": False}


def load_cfg():
    global CFG
    try:
        with open(CFG_PATH, "r", encoding="utf-8") as f:
            CFG.update(json.load(f))
    except Exception:
        pass
    if not CFG.get("token"):
        CFG["token"] = secrets.token_hex(4).upper()
        save_cfg()


def save_cfg():
    try:
        with open(CFG_PATH, "w", encoding="utf-8") as f:
            json.dump(CFG, f, indent=2)
    except Exception as e:
        print("No pude guardar la configuración:", e)


# ---------------------------------------------------------------- teclado (Windows)
VK = {"ctrl": 0x11, "shift": 0x10, "alt": 0x12, "win": 0x5B, "enter": 0x0D, "esc": 0x1B, "tab": 0x09, "space": 0x20,
      "backspace": 0x08, "delete": 0x2E, "insert": 0x2D, "home": 0x24, "end": 0x23, "pgup": 0x21, "pgdn": 0x22,
      "left": 0x25, "up": 0x26, "right": 0x27, "down": 0x28, "printscreen": 0x2C,
      "playpause": 0xB3, "next": 0xB0, "prev": 0xB1, "stop": 0xB2, "volup": 0xAF, "voldown": 0xAE, "mute": 0xAD,
      "plus": 0xBB, "minus": 0xBD, "comma": 0xBC, "period": 0xBE}
EXT = {"left", "up", "right", "down", "home", "end", "pgup", "pgdn", "delete", "insert",
       "playpause", "next", "prev", "stop", "volup", "voldown", "mute"}


def vk_of(name):
    n = name.strip().lower()
    if n in VK:
        return VK[n], n in EXT
    if len(n) == 1 and n.isalnum():
        return ord(n.upper()), False
    if n.startswith("f") and n[1:].isdigit() and 1 <= int(n[1:]) <= 24:
        return 0x70 + int(n[1:]) - 1, False
    raise ValueError("tecla desconocida: " + name)


def press_keys(combo):
    if not WIN:
        raise RuntimeError("los atajos de teclado solo funcionan en Windows")
    parts = [p for p in combo.lower().replace(" ", "").split("+") if p]
    if not parts or len(parts) > 5:
        raise ValueError("atajo vacío o demasiado largo")
    codes = [vk_of(p) for p in parts]
    u = ctypes.windll.user32
    for vk, ext in codes:
        u.keybd_event(vk, 0, 1 if ext else 0, 0)
    for vk, ext in reversed(codes):
        u.keybd_event(vk, 0, (1 if ext else 0) | 2, 0)


# ---------------------------------------------------------------- OBS (obs-websocket v5, sin librerías)
GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def ws_send(s, text):
    data = text.encode("utf-8")
    hdr = bytearray([0x81])
    n = len(data)
    if n < 126:
        hdr.append(0x80 | n)
    elif n < 65536:
        hdr.append(0x80 | 126)
        hdr += n.to_bytes(2, "big")
    else:
        hdr.append(0x80 | 127)
        hdr += n.to_bytes(8, "big")
    mask = os.urandom(4)
    hdr += mask
    s.sendall(bytes(hdr) + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))


def _recvn(s, n):
    buf = b""
    while len(buf) < n:
        c = s.recv(n - len(buf))
        if not c:
            raise ConnectionError("OBS cerró la conexión")
        buf += c
    return buf


def ws_recv(s):
    msg = b""
    while True:
        b0, b1 = _recvn(s, 2)
        op = b0 & 0x0F
        n = b1 & 0x7F
        if n == 126:
            n = int.from_bytes(_recvn(s, 2), "big")
        elif n == 127:
            n = int.from_bytes(_recvn(s, 8), "big")
        mask = _recvn(s, 4) if b1 & 0x80 else None
        data = _recvn(s, n) if n else b""
        if mask:
            data = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
        if op == 8:
            raise ConnectionError("OBS cerró la conexión")
        if op in (9, 10):
            continue
        msg += data
        if b0 & 0x80:
            return msg.decode("utf-8")


def obs_call(rtype, rdata=None):
    o = CFG.get("obs", {})
    s = socket.create_connection((o.get("host", "127.0.0.1"), int(o.get("port", 4455))), timeout=4)
    try:
        key = base64.b64encode(os.urandom(16)).decode()
        s.sendall(("GET / HTTP/1.1\r\nHost: %s:%s\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                   "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: obswebsocket.json\r\n\r\n"
                   % (o.get("host", "127.0.0.1"), o.get("port", 4455), key)).encode())
        head = b""
        while not head.endswith(b"\r\n\r\n"):  # byte a byte: lo que sigue ya es el primer mensaje de OBS
            c = s.recv(1)
            if not c:
                raise ConnectionError("OBS no respondió")
            head += c
        if b" 101 " not in head.split(b"\r\n")[0] + b" ":
            raise ConnectionError("OBS rechazó la conexión")
        hello = json.loads(ws_recv(s))
        ident = {"rpcVersion": 1, "eventSubscriptions": 0}
        auth = hello.get("d", {}).get("authentication")
        if auth:
            pw = o.get("password", "")
            secret = base64.b64encode(hashlib.sha256((pw + auth["salt"]).encode()).digest()).decode()
            ident["authentication"] = base64.b64encode(hashlib.sha256((secret + auth["challenge"]).encode()).digest()).decode()
        ws_send(s, json.dumps({"op": 1, "d": ident}))
        r = json.loads(ws_recv(s))
        if r.get("op") != 2:
            raise PermissionError("OBS: contraseña incorrecta")
        rid = secrets.token_hex(4)
        ws_send(s, json.dumps({"op": 6, "d": {"requestType": rtype, "requestId": rid, "requestData": rdata or {}}}))
        while True:
            m = json.loads(ws_recv(s))
            if m.get("op") == 7 and m["d"].get("requestId") == rid:
                st = m["d"].get("requestStatus", {})
                if not st.get("result"):
                    raise RuntimeError("OBS: " + str(st.get("comment") or st.get("code")))
                return m["d"].get("responseData") or {}
    finally:
        try:
            s.close()
        except Exception:
            pass


def do_obs(a):
    op = a.get("op", "")
    name = str(a.get("name", ""))[:120]
    if op == "scene":
        obs_call("SetCurrentProgramScene", {"sceneName": name})
    elif op == "mute":
        obs_call("ToggleInputMute", {"inputName": name})
    elif op == "record":
        obs_call("ToggleRecord")
    elif op == "stream":
        obs_call("ToggleStream")
    elif op == "replay":
        obs_call("SaveReplayBuffer")
    else:
        raise ValueError("acción de OBS desconocida")


# ---------------------------------------------------------------- acciones
def do_open(a):
    target = str(a.get("target", "")).strip()
    if not target or len(target) > 500:
        raise ValueError("falta qué abrir")
    if WIN:
        args = str(a.get("args", "")).strip()
        if args:
            os.startfile(target, arguments=args)  # noqa: S606 (Python 3.10+)
        else:
            os.startfile(target)  # noqa: S606
    elif sys.platform == "darwin":
        subprocess.Popen(["open", target])
    else:
        subprocess.Popen(["xdg-open", target])


def do_power(a):
    op = a.get("op", "")
    delay = max(0, min(int(a.get("delay", 30)), 600))
    if not WIN:
        raise RuntimeError("apagar y bloquear desde Chispa solo funciona en Windows")
    if op == "shutdown":
        subprocess.Popen(["shutdown", "/s", "/t", str(delay)])
    elif op == "restart":
        subprocess.Popen(["shutdown", "/r", "/t", str(delay)])
    elif op == "abort":
        subprocess.Popen(["shutdown", "/a"])
    elif op == "lock":
        ctypes.windll.user32.LockWorkStation()
    elif op == "sleep":
        subprocess.Popen(["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"])
    else:
        raise ValueError("acción de energía desconocida")


def run_action(a):
    t = a.get("t")
    if t == "open":
        do_open(a)
    elif t == "keys":
        press_keys(str(a.get("keys", "")))
    elif t == "obs":
        do_obs(a)
    elif t == "power":
        do_power(a)
    elif t == "cmd":
        if not CFG.get("allow_cmd"):
            raise PermissionError("los comandos libres están apagados en este computador (allow_cmd)")
        subprocess.Popen(str(a.get("cmd", ""))[:500], shell=True)
    else:
        raise ValueError("tipo de botón desconocido")


class H(http.server.BaseHTTPRequestHandler):
    server_version = "ChispaDeck/" + VERSION

    def log_message(self, *a):
        pass

    def _send(self, code, obj):
        b = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def _auth(self):
        tok = self.headers.get("X-Token", "")
        if hmac.compare_digest(tok.encode(), CFG["token"].encode()):
            return True
        self._send(401, {"ok": False, "err": "código incorrecto"})
        return False

    def do_GET(self):
        if not self._auth():
            return
        try:
            if self.path == "/ping":
                self._send(200, {"ok": True, "v": VERSION, "name": socket.gethostname(), "win": WIN})
            elif self.path == "/obs/scenes":
                r = obs_call("GetSceneList")
                self._send(200, {"ok": True, "scenes": [s.get("sceneName") for s in r.get("scenes", [])][::-1]})
            else:
                self._send(404, {"ok": False, "err": "no existe"})
        except Exception as e:
            self._send(200, {"ok": False, "err": str(e)})

    def do_POST(self):
        if not self._auth():
            return
        try:
            n = min(int(self.headers.get("Content-Length", "0")), 20000)
            body = json.loads(self.rfile.read(n) or b"{}")
            if self.path != "/run":
                self._send(404, {"ok": False, "err": "no existe"})
                return
            run_action(body.get("a", {}))
            self._send(200, {"ok": True})
        except Exception as e:
            self._send(200, {"ok": False, "err": str(e)})


def lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def main():
    port = PORT
    if "--port" in sys.argv:
        port = int(sys.argv[sys.argv.index("--port") + 1])
    load_cfg()
    srv = http.server.ThreadingHTTPServer(("0.0.0.0", port), H)
    print("=" * 52)
    print(" Chispa Deck %s funcionando" % VERSION)
    print("=" * 52)
    print(" En Chispa, en el Deck, escribe:")
    print("   Dirección del computador:  %s" % lan_ip())
    print("   Puerto:                    %d" % port)
    print("   Código:                    %s" % CFG["token"])
    print()
    print(" Deja esta ventana abierta. Para cerrar el programa, cierra la ventana.")
    print(" Si Windows pregunta por el firewall, permite 'redes privadas'.")
    print("=" * 52)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("Error:", e)
        try:
            input("Pulsa Enter para salir...")
        except Exception:
            pass
