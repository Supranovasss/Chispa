package com.chispa.control;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Pantalla principal: estado del servicio, permisos con tarjetas y pruebas rápidas. */
public class MainActivity extends Activity {
    // Paleta de Chispa
    private static final int BG = Color.parseColor("#0D1722");
    private static final int PANEL = Color.parseColor("#13233A");
    private static final int LINE = Color.parseColor("#25405C");
    private static final int INK = Color.parseColor("#F6ECDC");
    private static final int MUTED = Color.parseColor("#9FB3C8");
    private static final int GLOW = Color.parseColor("#FFC78F");
    private static final int OK = Color.parseColor("#8BE0B5");
    private static final int DARK_INK = Color.parseColor("#2A1620");

    private Control control;
    private TextView svcChip, progressTxt, resultTxt;
    private View progressFill;
    private FrameLayout progressTrack;
    private final List<Perm> perms = new ArrayList<>();

    /** Un permiso: tarjeta con icono, explicación y estado. */
    private static class Perm {
        String icon, title, why, action;
        TextView pill;
        View card;
        Perm(String i, String t, String w, String a) { icon = i; title = t; why = w; action = a; }
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private void go(String action) {
        try {
            Intent i = new Intent(action);
            if (!action.equals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
        }
    }

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        control = new Control(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(28), dp(22), dp(36));
        // En pantallas anchas (tablet) el contenido se centra con un ancho cómodo
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(Math.min(dp(640), getResources().getDisplayMetrics().widthPixels), -2);
        wp.gravity = Gravity.CENTER_HORIZONTAL;
        wrap.addView(col, wp);
        sv.addView(wrap, new ScrollView.LayoutParams(-1, -2));

        // Encabezado
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("⚡", 26, DARK_INK, false);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{GLOW, Color.parseColor("#FF9A76")});
        lg.setCornerRadius(dp(18));
        logo.setBackground(lg);
        head.addView(logo, lp(dp(56), dp(56), 0, 0, 16, 0));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.addView(text("Chispa Control", 24, INK, true));
        TextView ver = text("Versión " + BuildConfig.VERSION_NAME, 14, MUTED, false);
        ht.addView(ver, lp(-2, -2, 0, 5, 0, 0));
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head);

        col.addView(text("Deja que Chispa ajuste el brillo, el volumen y otras cosas de esta tablet. Concede cada permiso una sola vez.", 15, MUTED, false), lp(-1, -2, 0, 18, 0, 0));

        // Estado del servicio
        svcChip = text("", 14, INK, true);
        svcChip.setPadding(dp(14), dp(10), dp(14), dp(10));
        col.addView(svcChip, lp(-2, -2, 0, 16, 0, 0));

        // Botón principal: iniciar Chispa en Chrome
        LinearLayout start = new LinearLayout(this);
        start.setOrientation(LinearLayout.HORIZONTAL);
        start.setGravity(Gravity.CENTER_VERTICAL);
        start.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{GLOW, Color.parseColor("#FF9A76")});
        sg.setCornerRadius(dp(24));
        start.setBackground(sg);
        start.setClickable(true);
        TextView sIc = text("⚡", 28, DARK_INK, false);
        start.addView(sIc, lp(-2, -2, 0, 0, 16, 0));
        LinearLayout sTx = new LinearLayout(this);
        sTx.setOrientation(LinearLayout.VERTICAL);
        sTx.addView(text("Iniciar Chispa", 20, DARK_INK, true));
        sTx.addView(text("Abre Google Chrome con Chispa lista para hablar", 13, Color.parseColor("#5A3A38"), false), lp(-2, -2, 0, 4, 0, 0));
        start.addView(sTx, new LinearLayout.LayoutParams(0, -2, 1f));
        start.addView(text("›", 28, DARK_INK, true));
        start.setOnClickListener(v -> run(() -> control.handle("/open", q())));
        col.addView(start, lp(-1, -2, 0, 20, 0, 0));

        // Progreso de permisos
        LinearLayout ph = new LinearLayout(this);
        ph.setOrientation(LinearLayout.HORIZONTAL);
        ph.setGravity(Gravity.BOTTOM);
        ph.addView(text("Permisos", 18, INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        progressTxt = text("", 14, MUTED, true);
        ph.addView(progressTxt);
        col.addView(ph, lp(-1, -2, 0, 28, 0, 0));
        progressTrack = new FrameLayout(this);
        progressTrack.setBackground(shape(LINE, 4, 0));
        progressFill = new View(this);
        progressFill.setBackground(shape(OK, 4, 0));
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        col.addView(progressTrack, lp(-1, dp(6), 0, 10, 0, 6));

        perms.add(new Perm("☀️", "Brillo de la pantalla", "Permite bajar o subir el brillo real al dormir o en el modo reloj.", Settings.ACTION_MANAGE_WRITE_SETTINGS));
        perms.add(new Perm("🔕", "No molestar", "Para activar el silencio cuando se lo pidas a Chispa.", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        perms.add(new Perm("🪟", "Abrir sobre otras apps", "Deja que Chispa se abra sola al encender la tablet.", Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        perms.add(new Perm("🔋", "Sin ahorro de batería", "Evita que Android duerma el servicio y Chispa deje de responder.", Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS));
        perms.add(new Perm("⬇️", "Instalar actualizaciones", "Así la app se actualiza sola con cada comando nuevo.", Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
        for (final Perm p : perms) col.addView(permCard(p), lp(-1, -2, 0, 10, 0, 0));

        // Pruebas rápidas
        col.addView(text("Pruebas rápidas", 18, INK, true), lp(-1, -2, 0, 30, 0, 0));
        LinearLayout tests = new LinearLayout(this);
        tests.setOrientation(LinearLayout.HORIZONTAL);
        tests.addView(testTile("🌙", "Brillo al 15 %", v -> run(() -> control.handle("/brightness", q("level", "15", "save", "1")))), new LinearLayout.LayoutParams(0, -2, 1f));
        tests.addView(testTile("☀️", "Restaurar brillo", v -> run(() -> control.handle("/brightness/restore", q()))), lp(0, -2, 10, 0, 0, 0, 1f));
        tests.addView(testTile("🔄", "Reiniciar servicio", v -> { startSvc(); refresh(); resultTxt.setTextColor(OK); resultTxt.setText("✓ Servicio reiniciado"); }), lp(0, -2, 10, 0, 0, 0, 1f));
        col.addView(tests, lp(-1, -2, 0, 12, 0, 0));
        resultTxt = text("", 13, MUTED, false);
        resultTxt.setLineSpacing(0, 1.2f);
        col.addView(resultTxt, lp(-1, -2, 2, 12, 0, 0));

        // Actualización
        LinearLayout up = new LinearLayout(this);
        up.setOrientation(LinearLayout.HORIZONTAL);
        up.setGravity(Gravity.CENTER_VERTICAL);
        up.setPadding(dp(18), dp(16), dp(18), dp(16));
        up.setBackground(shape(PANEL, 20, LINE));
        LinearLayout ut = new LinearLayout(this);
        ut.setOrientation(LinearLayout.VERTICAL);
        ut.addView(text("Actualización", 17, INK, true));
        ut.addView(text("Busca una versión nueva de esta app e instálala.", 13, MUTED, false), lp(-2, -2, 0, 4, 0, 0));
        up.addView(ut, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView ub = pillButton("Buscar", GLOW, DARK_INK);
        ub.setOnClickListener(v -> run(() -> control.handle("/update/install", q())));
        up.addView(ub);
        col.addView(up, lp(-1, -2, 0, 22, 0, 0));

        TextView srv = text("", 12, MUTED, false);
        srv.setText("Servicio local en 127.0.0.1:" + ControlService.PORT + " · solo esta tablet puede usarlo");
        srv.setGravity(Gravity.CENTER);
        col.addView(srv, lp(-1, -2, 0, 22, 0, 0));

        setContentView(sv);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        startSvc();
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private TextView pillButton(String label, int fill, int ink) {
        TextView b = text(label, 14, ink, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(11), dp(18), dp(11));
        b.setBackground(shape(fill, 22, 0));
        b.setClickable(true);
        return b;
    }

    private View permCard(final Perm p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setClickable(true);
        TextView ic = text(p.icon, 22, INK, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(shape(Color.parseColor("#1B3350"), 14, 0));
        card.addView(ic, lp(dp(46), dp(46), 0, 0, 14, 0));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(p.title, 16, INK, true));
        TextView why = text(p.why, 13, MUTED, false);
        why.setLineSpacing(0, 1.15f);
        tx.addView(why, lp(-2, -2, 0, 4, 0, 0));
        card.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        p.pill = text("", 13, INK, true);
        p.pill.setGravity(Gravity.CENTER);
        p.pill.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.addView(p.pill, lp(-2, -2, 12, 0, 0, 0));
        card.setOnClickListener(v -> go(p.action));
        p.card = card;
        return card;
    }

    private View testTile(String icon, String label, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(16), dp(8), dp(14));
        t.setBackground(shape(PANEL, 20, LINE));
        t.setClickable(true);
        t.addView(text(icon, 24, INK, false));
        TextView lb = text(label, 13, INK, true);
        lb.setGravity(Gravity.CENTER);
        t.addView(lb, lp(-2, -2, 0, 8, 0, 0));
        t.setOnClickListener(l);
        return t;
    }

    private static java.util.Map<String, String> q(String... kv) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private interface Job { JSONObject call(); }

    private void run(final Job j) {
        resultTxt.setText("Un momento…");
        new Thread(() -> {
            JSONObject r;
            try { r = j.call(); } catch (Exception e) { r = new JSONObject(); }
            final JSONObject res = r;
            runOnUiThread(() -> {
                boolean ok = res.optBoolean("ok", false);
                String err = res.optString("err", "");
                String msg = ok ? "✓ Listo" : (err.isEmpty() ? "✗ No se pudo" : "✗ " + err);
                if (ok && res.has("msg")) msg += " · " + res.optString("msg");
                resultTxt.setTextColor(ok ? OK : GLOW);
                resultTxt.setText(msg);
            });
        }).start();
    }

    private void startSvc() {
        try { startForegroundService(new Intent(this, ControlService.class)); } catch (Exception ignored) { }
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void pill(TextView t, boolean done) {
        if (done) {
            t.setText("✓ Listo"); t.setTextColor(OK);
            t.setBackground(shape(Color.parseColor("#1E3F37"), 18, 0));
        } else {
            t.setText("Permitir"); t.setTextColor(DARK_INK);
            t.setBackground(shape(GLOW, 18, 0));
        }
    }

    private void refresh() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean[] st = {
                control.canWrite(), control.canDnd(), Settings.canDrawOverlays(this),
                pm.isIgnoringBatteryOptimizations(getPackageName()), getPackageManager().canRequestPackageInstalls()
        };
        int n = 0;
        for (int i = 0; i < perms.size(); i++) {
            Perm p = perms.get(i); pill(p.pill, st[i]); if (st[i]) n++;
            p.card.setBackground(shape(PANEL, 20, st[i] ? Color.parseColor("#2C5A4C") : LINE));
        }
        progressTxt.setText(n + " de " + perms.size());
        final int done = n;
        progressTrack.post(() -> {
            FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
            fp.width = progressTrack.getWidth() * done / perms.size();
            progressFill.setLayoutParams(fp);
        });
        new Thread(() -> {
            boolean up;
            try (java.net.Socket so = new java.net.Socket()) {
                so.connect(new java.net.InetSocketAddress("127.0.0.1", ControlService.PORT), 800);
                up = true;
            } catch (Exception e) { up = false; }
            final boolean alive = up;
            runOnUiThread(() -> {
                svcChip.setText(alive ? "●  Servicio activo" : "●  Servicio detenido · se reinicia al abrir");
                svcChip.setTextColor(alive ? OK : GLOW);
                svcChip.setBackground(shape(alive ? Color.parseColor("#17302B") : Color.parseColor("#3A2A22"), 20, 0));
            });
        }).start();
    }
}
