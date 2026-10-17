package com.chispa.control;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Busca la versión nueva en GitHub y la instala (Android pide una confirmación). */
public class Updater {
    static final String API = "https://api.github.com/repos/Supranovasss/Chispa/releases/tags/control-latest";
    static final String APK = "https://github.com/Supranovasss/Chispa/releases/download/control-latest/ChispaControl.apk";

    static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        }
    }

    public static JSONObject check(Context ctx) throws Exception {
        JSONObject r = new JSONObject(get(API));
        String notes = r.optString("body", "");
        int code = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("code:(\\d+)").matcher(notes);
        if (m.find()) code = Integer.parseInt(m.group(1));
        return new JSONObject().put("ok", true).put("current", BuildConfig.VERSION_CODE).put("latest", code).put("newer", code > BuildConfig.VERSION_CODE)
                .put("name", r.optString("name", ""));
    }

    /** Descarga e instala en segundo plano; el resultado llega a UpdateReceiver. */
    public static JSONObject install(final Context ctx) throws Exception {
        if (!ctx.getPackageManager().canRequestPackageInstalls()) {
            return new JSONObject().put("ok", false).put("err", "falta el permiso Instalar apps desconocidas");
        }
        final JSONObject c = check(ctx);
        if (!c.optBoolean("newer")) return new JSONObject().put("ok", true).put("started", false).put("msg", "ya tienes la última versión");
        new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(APK).openConnection();
                h.setInstanceFollowRedirects(true); h.setConnectTimeout(10000); h.setReadTimeout(30000);
                PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
                int id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
                PackageInstaller.Session s = pi.openSession(id);
                try (InputStream in = h.getInputStream(); OutputStream os = s.openWrite("app", 0, -1)) {
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                    s.fsync(os);
                }
                Intent i = new Intent(ctx, UpdateReceiver.class).setAction("com.chispa.control.INSTALL_RESULT");
                PendingIntent p = PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                s.commit(p.getIntentSender());
            } catch (Exception ignored) { }
        }, "chispa-update").start();
        return new JSONObject().put("ok", true).put("started", true);
    }
}
