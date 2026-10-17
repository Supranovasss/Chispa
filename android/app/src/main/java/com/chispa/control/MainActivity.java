package com.chispa.control;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private TextView status;
    private Control control;

    private Button btn(LinearLayout box, String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t); b.setAllCaps(false); b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = 12; box.addView(b, lp);
        return b;
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
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 40, 40, 60);
        sv.addView(box);
        TextView t = new TextView(this);
        t.setText("Chispa Control"); t.setTextSize(26); box.addView(t);
        TextView sub = new TextView(this);
        sub.setText("Deja que Chispa controle el brillo, el volumen y otros ajustes de esta tablet. Concede cada permiso una sola vez.");
        box.addView(sub);
        status = new TextView(this); status.setTextSize(16); status.setPadding(0, 24, 0, 8); box.addView(status);

        btn(box, "1. Permitir modificar ajustes del sistema (brillo)", v -> go(Settings.ACTION_MANAGE_WRITE_SETTINGS));
        btn(box, "2. Permitir No molestar", v -> go(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        btn(box, "3. Permitir mostrarse sobre otras apps (abrir Chispa al encender)", v -> go(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        btn(box, "4. Sin ahorro de batería para esta app", v -> go(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS));
        btn(box, "5. Permitir instalar actualizaciones", v -> go(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
        btn(box, "Reiniciar servicio", v -> { startSvc(); refresh(); });
        btn(box, "Probar: brillo al 15 %", v -> { run(() -> control.handle("/brightness", q("level", "15", "save", "1"))); });
        btn(box, "Probar: restaurar brillo", v -> { run(() -> control.handle("/brightness/restore", q())); });
        btn(box, "Abrir Chispa", v -> run(() -> control.handle("/open", q())));
        btn(box, "Buscar e instalar actualización", v -> run(() -> {
            JSONObject r = control.handle("/update/install", q());
            return r;
        }));
        setContentView(sv);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        startSvc();
    }

    private static java.util.Map<String, String> q(String... kv) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private interface Job { JSONObject call(); }

    private void run(final Job j) {
        new Thread(() -> {
            JSONObject r;
            try { r = j.call(); } catch (Exception e) { r = new JSONObject(); }
            final String m = r.toString();
            runOnUiThread(() -> { status.setText(status.getText() + "\n→ " + m); });
        }).start();
    }

    private void startSvc() {
        try { startForegroundService(new Intent(this, ControlService.class)); } catch (Exception ignored) { }
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        StringBuilder sb = new StringBuilder();
        sb.append("Versión ").append(BuildConfig.VERSION_NAME).append("\n");
        sb.append(control.canWrite() ? "✓" : "✗").append(" Ajustes del sistema (brillo)\n");
        sb.append(control.canDnd() ? "✓" : "✗").append(" No molestar\n");
        sb.append(Settings.canDrawOverlays(this) ? "✓" : "✗").append(" Sobre otras apps\n");
        sb.append(pm.isIgnoringBatteryOptimizations(getPackageName()) ? "✓" : "✗").append(" Sin ahorro de batería\n");
        sb.append(getPackageManager().canRequestPackageInstalls() ? "✓" : "✗").append(" Instalar actualizaciones\n");
        sb.append("Servicio local: 127.0.0.1:").append(ControlService.PORT);
        status.setText(sb.toString());
    }
}
