package com.chispa.control;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/** Acciones sobre la tablet. Cada una devuelve un JSON con ok true/false. */
public class Control {
    public static final String CHISPA_URL = "https://supranovasss.github.io/Chispa/";
    private final Context ctx;
    private final SharedPreferences sp;

    public Control(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("control", Context.MODE_PRIVATE);
    }

    private JSONObject err(String m) {
        try { return new JSONObject().put("ok", false).put("err", m); } catch (Exception e) { return new JSONObject(); }
    }

    private int num(Map<String, String> q, String k, int d) {
        try { return Integer.parseInt(q.get(k)); } catch (Exception e) { return d; }
    }

    private boolean on(Map<String, String> q, String k) {
        String v = q.get(k);
        return v != null && (v.equals("1") || v.equals("true") || v.equals("on") || v.equals("si"));
    }

    public JSONObject handle(String path, Map<String, String> q) {
        try {
            switch (path) {
                case "/ping": return ping();
                case "/status": return status();
                case "/brightness": return brightness(q);
                case "/brightness/restore": return restoreBrightness();
                case "/auto": return autoBrightness(on(q, "on"));
                case "/volume": return volume(q);
                case "/awake": return awake(on(q, "on"));
                case "/dnd": return dnd(on(q, "on"));
                case "/open": return open(q.get("url"));
                case "/fetch": return fetch(q.get("url"));
                case "/deck": return deck(q);
                case "/update/check": return Updater.check(ctx);
                case "/update/install": return Updater.install(ctx);
                default: return err("ruta desconocida");
            }
        } catch (SecurityException e) {
            return err("sin permiso");
        } catch (Exception e) {
            return err(String.valueOf(e.getMessage()));
        }
    }

    /** Consulta un sitio de aviones por la página (las webs de aviones no dejan que el navegador las lea directo). Solo sitios permitidos. */
    private static final String[] FETCH_OK = {"api.adsb.lol", "api.airplanes.live", "opendata.adsb.fi", "api.adsbdb.com", "decapi.me", "earthquake.usgs.gov", "api.wheretheiss.at", "calendar.google.com", "outlook.live.com", "outlook.office365.com"};

    private JSONObject fetch(String u) throws Exception {
        if (u == null || u.isEmpty()) return err("falta la direccion");
        URL url = new URL(u);
        if (!"https".equals(url.getProtocol())) return err("solo https");
        boolean allowed = false;
        for (String h : FETCH_OK) if (h.equalsIgnoreCase(url.getHost())) allowed = true;
        if (!allowed) return err("sitio no permitido");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Chispa/1.0 (+https://github.com/Supranovasss/Chispa)");
        c.setRequestProperty("Accept", "*/*");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = "";
        if (in != null) {
            try (InputStream is = in; ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192];
                int n, total = 0;
                while ((n = is.read(b)) > 0 && total < 3000000) { bo.write(b, 0, n); total += n; }
                body = bo.toString("UTF-8");
            }
        }
        return new JSONObject().put("ok", true).put("status", code).put("body", body);
    }

    /** Envía una orden al programa Chispa Deck del computador (solo direcciones de la red de casa). */
    private JSONObject deck(Map<String, String> q) throws Exception {
        String host = q.get("host"), port = q.get("port"), token = q.get("token"), path = q.get("path"), body = q.get("body");
        if (host == null || !host.matches("(10\\.\\d{1,3}|192\\.168|172\\.(1[6-9]|2\\d|3[01]))\\.\\d{1,3}(\\.\\d{1,3})?")) return err("la direccion del computador debe ser de tu red (192.168.x.x)");
        int p = 8777;
        try { p = Integer.parseInt(port); } catch (Exception ignored) { }
        if (path == null || !path.startsWith("/")) return err("falta la ruta");
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + host + ":" + p + path).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(9000);
        c.setRequestProperty("X-Token", token == null ? "" : token);
        if (body != null && !body.isEmpty()) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String out = "";
        if (in != null) {
            try (InputStream is = in; ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] b = new byte[4096];
                int n, total = 0;
                while ((n = is.read(b)) > 0 && total < 100000) { bo.write(b, 0, n); total += n; }
                out = bo.toString("UTF-8");
            }
        }
        return new JSONObject().put("ok", true).put("status", code).put("body", out);
    }

    private JSONObject ping() throws Exception {
        JSONArray caps = new JSONArray();
        for (String c : new String[]{"brightness", "auto", "volume", "awake", "dnd", "open", "update", "fetch", "deck"}) caps.put(c);
        return new JSONObject().put("ok", true).put("v", BuildConfig.VERSION_NAME).put("code", BuildConfig.VERSION_CODE).put("caps", caps);
    }

    public boolean canWrite() { return Settings.System.canWrite(ctx); }
    public boolean canDnd() { return ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted(); }

    private JSONObject status() throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        int b = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128);
        int mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int vmax = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        Intent bat = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1, charging = 0;
        if (bat != null) {
            int l = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = s > 0 ? Math.round(l * 100f / s) : -1;
            int st = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL) ? 1 : 0;
        }
        return new JSONObject().put("ok", true)
                .put("brightness", Math.round(b * 100f / 255f)).put("auto", mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                .put("volume", Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / vmax))
                .put("battery", level).put("charging", charging == 1)
                .put("canWrite", canWrite()).put("canDnd", canDnd())
                .put("savedBrightness", sp.getInt("saved_b", -1) >= 0);
    }

    private JSONObject brightness(Map<String, String> q) throws Exception {
        if (!canWrite()) return err("falta el permiso Modificar ajustes del sistema");
        ContentResolver cr = ctx.getContentResolver();
        int pct = num(q, "level", -1);
        if (pct < 0) {
            int delta = num(q, "delta", 0);
            int cur = Math.round(Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) * 100f / 255f);
            pct = cur + delta;
        }
        pct = Math.max(0, Math.min(100, pct));
        if (on(q, "save") && sp.getInt("saved_b", -1) < 0) {
            sp.edit().putInt("saved_b", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
                    .putInt("saved_m", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)).apply();
        }
        int raw = Math.max(1, Math.round(pct * 255f / 100f));
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, raw);
        return new JSONObject().put("ok", true).put("brightness", pct);
    }

    private JSONObject restoreBrightness() throws Exception {
        if (!canWrite()) return err("falta el permiso Modificar ajustes del sistema");
        int b = sp.getInt("saved_b", -1);
        if (b < 0) return new JSONObject().put("ok", true).put("restored", false);
        ContentResolver cr = ctx.getContentResolver();
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, b);
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, sp.getInt("saved_m", 0));
        sp.edit().remove("saved_b").remove("saved_m").apply();
        return new JSONObject().put("ok", true).put("restored", true).put("brightness", Math.round(b * 100f / 255f));
    }

    private JSONObject autoBrightness(boolean on) throws Exception {
        if (!canWrite()) return err("falta el permiso Modificar ajustes del sistema");
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                on ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        return new JSONObject().put("ok", true).put("auto", on);
    }

    private JSONObject volume(Map<String, String> q) throws Exception {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        String s = q.get("stream") == null ? "music" : q.get("stream");
        int st = s.equals("alarm") ? AudioManager.STREAM_ALARM : s.equals("ring") ? AudioManager.STREAM_RING
                : s.equals("notification") ? AudioManager.STREAM_NOTIFICATION : AudioManager.STREAM_MUSIC;
        int max = Math.max(1, am.getStreamMaxVolume(st));
        int pct = num(q, "level", -1);
        if (pct < 0) pct = Math.round(am.getStreamVolume(st) * 100f / max) + num(q, "delta", 0);
        pct = Math.max(0, Math.min(100, pct));
        am.setStreamVolume(st, Math.round(pct * max / 100f), 0);
        return new JSONObject().put("ok", true).put("volume", pct);
    }

    private JSONObject awake(boolean on) throws Exception {
        if (!canWrite()) return err("falta el permiso Modificar ajustes del sistema");
        ContentResolver cr = ctx.getContentResolver();
        if (on) {
            if (sp.getInt("saved_t", -1) < 0) sp.edit().putInt("saved_t", Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 120000)).apply();
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, Integer.MAX_VALUE);
        } else {
            if (sp.getInt("saved_t", -1) < 0) return new JSONObject().put("ok", true).put("awake", false);
            int t = sp.getInt("saved_t", 120000);
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, t);
            sp.edit().remove("saved_t").apply();
        }
        return new JSONObject().put("ok", true).put("awake", on);
    }

    private JSONObject dnd(boolean on) throws Exception {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) return err("falta el permiso No molestar");
        nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY : NotificationManager.INTERRUPTION_FILTER_ALL);
        return new JSONObject().put("ok", true).put("dnd", on);
    }

    private JSONObject open(String url) throws Exception {
        Intent i = new Intent(ctx, ChispaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ctx.startActivity(i);
        return new JSONObject().put("ok", true);
    }
}
