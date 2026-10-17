package com.chispa.control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servidor local (solo 127.0.0.1:8765) que recibe órdenes de Chispa desde Chrome. */
public class ControlService extends Service {
    public static final int PORT = 8765;
    private static final String CH = "chispa_control";
    private volatile boolean running;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private Control control;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, "Chispa Control", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CH).setSmallIcon(R.drawable.ic)
                .setContentTitle("Chispa Control activo").setContentText("Listo para recibir órdenes de Chispa").setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
        control = new Control(this);
        if (!running) startServer();
        return START_STICKY;
    }

    private void startServer() {
        running = true;
        new Thread(() -> {
            try {
                server = new ServerSocket(PORT, 20, InetAddress.getByName("127.0.0.1"));
                server.setReuseAddress(true);
                while (running) {
                    final Socket s = server.accept();
                    pool.execute(() -> serve(s));
                }
            } catch (Exception e) {
                running = false;
            }
        }, "chispa-http").start();
    }

    private static boolean originOk(String o) {
        if (o == null || o.isEmpty()) return true;
        return o.equals("https://supranovasss.github.io") || o.startsWith("http://localhost") || o.startsWith("http://127.0.0.1") || o.equals("null");
    }

    private void serve(Socket s) {
        try {
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) { s.close(); return; }
            String[] p = line.split(" ");
            String method = p[0], target = p.length > 1 ? p[1] : "/";
            String origin = null, h;
            while ((h = in.readLine()) != null && !h.isEmpty()) {
                if (h.toLowerCase().startsWith("origin:")) origin = h.substring(7).trim();
            }
            boolean ok = originOk(origin);
            StringBuilder head = new StringBuilder();
            String body = "";
            int code = 200;
            if (!ok) { code = 403; body = "{\"ok\":false,\"err\":\"origen no permitido\"}"; }
            else if (method.equals("OPTIONS")) { code = 204; }
            else {
                String path = target, qs = "";
                int qi = target.indexOf('?');
                if (qi >= 0) { path = target.substring(0, qi); qs = target.substring(qi + 1); }
                Map<String, String> q = new HashMap<>();
                for (String kv : qs.split("&")) {
                    if (kv.isEmpty()) continue;
                    int e = kv.indexOf('=');
                    String k = e < 0 ? kv : kv.substring(0, e), v = e < 0 ? "" : kv.substring(e + 1);
                    q.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
                }
                JSONObject r = control.handle(path, q);
                body = r.toString();
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            head.append("HTTP/1.1 ").append(code).append(code == 200 ? " OK" : code == 204 ? " No Content" : " Forbidden").append("\r\n");
            head.append("Content-Type: application/json; charset=utf-8\r\n");
            if (ok) {
                head.append("Access-Control-Allow-Origin: ").append(origin == null || origin.isEmpty() ? "*" : origin).append("\r\n");
                head.append("Access-Control-Allow-Private-Network: true\r\n");
                head.append("Access-Control-Allow-Methods: GET, OPTIONS\r\n");
                head.append("Access-Control-Allow-Headers: *\r\n");
                head.append("Access-Control-Max-Age: 600\r\n");
                head.append("Vary: Origin\r\n");
            }
            head.append("Cache-Control: no-store\r\n");
            head.append("Content-Length: ").append(b.length).append("\r\nConnection: close\r\n\r\n");
            OutputStream out = s.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            out.write(b);
            out.flush();
            s.close();
        } catch (Exception e) {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    @Override public void onDestroy() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        pool.shutdownNow();
        super.onDestroy();
    }
}
