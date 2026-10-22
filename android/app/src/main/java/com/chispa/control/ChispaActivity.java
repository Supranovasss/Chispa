package com.chispa.control;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;

/** Chispa como app propia: pantalla completa con WebView, sin Chrome. */
public class ChispaActivity extends Activity {
    public static final int REQ = 77;
    private WebView web;
    private FrameLayout root;
    private View custom;
    private WebChromeClient.CustomViewCallback customCb;
    private VoiceBridge voice;
    private Control control;
    private long lastBack = 0;
    private FrameLayout overlay;
    private WebView ow;
    private PermissionRequest pendingPerm;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeo;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try { startForegroundService(new Intent(this, ControlService.class)); } catch (Exception ignored) { }
        control = new Control(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        immersive();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " ChispaApp/1");

        voice = new VoiceBridge(this, web);
        web.addJavascriptInterface(voice, "ChispaVoice");
        web.addJavascriptInterface(new AppBridge(), "ChispaApp");

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u.toString().startsWith(Control.CHISPA_URL)) return false;
                String h = u.getHost() == null ? "" : u.getHost();
                if (h.equals("twitch.tv") || h.endsWith(".twitch.tv")) return false; // inicio de sesión de Twitch dentro de la app
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadData("<html><body style='background:#0D1722;color:#FFC78F;font-family:sans-serif;text-align:center;padding-top:30vh'>"
                            + "<h2>Sin conexión</h2><p>Reintentando en unos segundos…</p></body></html>", "text/html", "utf-8");
                    v.postDelayed(() -> v.loadUrl(Control.CHISPA_URL), 8000);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest r) {
                runOnUiThread(() -> grantWeb(r));
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, true);
                } else {
                    pendingGeo = cb;
                    pendingGeoOrigin = origin;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ);
                }
            }
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (custom != null) { cb.onCustomViewHidden(); return; }
                custom = v;
                customCb = cb;
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
            }
            @Override public void onHideCustomView() { hideCustom(); }
        });

        setHz(true);
        askPerms();
        web.loadUrl(Control.CHISPA_URL);
    }

    private void hideCustom() {
        if (custom == null) return;
        root.removeView(custom);
        custom = null;
        web.setVisibility(View.VISIBLE);
        try { if (customCb != null) customCb.onCustomViewHidden(); } catch (Exception ignored) { }
        customCb = null;
    }

    private void grantWeb(PermissionRequest r) {
        ArrayList<String> ok = new ArrayList<>();
        ArrayList<String> need = new ArrayList<>();
        for (String res : r.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) ok.add(res); else need.add(Manifest.permission.CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ok.add(res); else need.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        if (!need.isEmpty()) {
            pendingPerm = r;
            requestPermissions(need.toArray(new String[0]), REQ);
            return;
        }
        if (ok.isEmpty()) r.deny(); else r.grant(ok.toArray(new String[0]));
    }

    private void askPerms() {
        ArrayList<String> p = new ArrayList<>();
        String[] all = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        for (String x : all) if (checkSelfPermission(x) != PackageManager.PERMISSION_GRANTED) p.add(x);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (pendingPerm != null) {
            PermissionRequest r = pendingPerm;
            pendingPerm = null;
            try { grantWeb(r); } catch (Exception e) { try { r.deny(); } catch (Exception ignored) { } }
        }
        if (pendingGeo != null) {
            boolean ok = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            pendingGeo.invoke(pendingGeoOrigin, ok, ok);
            pendingGeo = null;
        }
    }

    /** Pide la frecuencia más alta de la pantalla (120 Hz) o 60 Hz para ahorrar batería. Android puede ignorarlo. */
    private void setHz(boolean high) {
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            android.view.Display.Mode cur = d.getMode(), best = cur;
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
                boolean better = high ? m.getRefreshRate() > best.getRefreshRate() : Math.abs(m.getRefreshRate() - 60f) < Math.abs(best.getRefreshRate() - 60f);
                if (better) best = m;
            }
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            lp.preferredRefreshRate = best.getRefreshRate();
            getWindow().setAttributes(lp);
        } catch (Exception ignored) { }
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) immersive();
    }

    @Override protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setHz(true);
        web.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
        // Se queda activa en segundo plano (alarmas, avisos); no se pausa el WebView.
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); }

    /** Muestra una página de tu red de casa (por ejemplo Macro Deck) encima de Chispa. */
    private void showOverlay(final String url) {
        if (url == null || !url.matches("^https?://(10\\.\\d{1,3}|192\\.168|172\\.(1[6-9]|2\\d|3[01]))\\.\\d{1,3}(\\.\\d{1,3})?(:\\d{1,5})?(/\\S*)?$")) {
            Toast.makeText(this, "Solo puedo abrir direcciones de tu red de casa (192.168.x.x)", Toast.LENGTH_LONG).show();
            return;
        }
        if (overlay == null) {
            overlay = new FrameLayout(this);
            overlay.setBackgroundColor(Color.BLACK);
            ow = new WebView(this);
            WebSettings w = ow.getSettings();
            w.setJavaScriptEnabled(true);
            w.setDomStorageEnabled(true);
            ow.setWebViewClient(new WebViewClient() {
                @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                    if (r.isForMainFrame()) v.loadData("<html><body style='background:#0D1722;color:#FFC78F;font-family:sans-serif;text-align:center;padding-top:25vh'><h2>No pude abrir la dirección</h2><p>Revisa que el PC esté encendido, con el programa abierto y en el mismo wifi.</p></body></html>", "text/html", "utf-8");
                }
            });
            overlay.addView(ow, new FrameLayout.LayoutParams(-1, -1));
            android.widget.Button x = new android.widget.Button(this);
            x.setText("✕ Cerrar");
            x.setOnClickListener(v -> hideOverlay());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP | android.view.Gravity.END);
            lp.setMargins(0, 24, 24, 0);
            overlay.addView(x, lp);
            root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        }
        ow.loadUrl(url);
        overlay.setVisibility(View.VISIBLE);
    }

    private void hideOverlay() {
        if (overlay == null) return;
        overlay.setVisibility(View.GONE);
        try { ow.loadUrl("about:blank"); } catch (Exception ignored) { }
    }

    @Override public void onBackPressed() {
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) { hideOverlay(); return; }
        if (custom != null) { hideCustom(); return; }
        long n = System.currentTimeMillis();
        if (n - lastBack < 2000) { finish(); return; }
        lastBack = n;
        Toast.makeText(this, "Toca atrás otra vez para salir", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { voice.destroy(); } catch (Exception ignored) { }
        try { root.removeAllViews(); web.destroy(); } catch (Exception ignored) { }
    }

    /** Puente para la página: control de la tablet sin pasar por la red. */
    public class AppBridge {
        @JavascriptInterface public String version() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface public void callAsync(final String id, final String path) {
            new Thread(() -> {
                String out;
                try {
                    String p = path == null ? "/ping" : path;
                    java.util.Map<String, String> q = new java.util.HashMap<>();
                    int k = p.indexOf('?');
                    String route = k < 0 ? p : p.substring(0, k);
                    if (k >= 0) {
                        for (String kv : p.substring(k + 1).split("&")) {
                            int e = kv.indexOf('=');
                            if (e > 0) q.put(Uri.decode(kv.substring(0, e)), Uri.decode(kv.substring(e + 1).replace("+", " ")));
                        }
                    }
                    out = control.handle(route, q).toString();
                } catch (Exception e) {
                    out = "{\"ok\":false,\"err\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
                }
                final String js = "window.__ctl&&window.__ctl(" + org.json.JSONObject.quote(id) + "," + org.json.JSONObject.quote(out) + ")";
                runOnUiThread(() -> { try { web.evaluateJavascript(js, null); } catch (Exception ignored) { } });
            }).start();
        }

        @JavascriptInterface public void keepAwake(final boolean on) {
            runOnUiThread(() -> { if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
        }

        /** Suelta la pantalla: Android la apaga y la bloquea con su propio tiempo. */
        @JavascriptInterface public void screenOff() {
            keepAwake(false);
            new Thread(() -> { try { control.handle("/awake", new java.util.HashMap<String, String>()); } catch (Exception ignored) { } }).start();
        }

        @JavascriptInterface public void hz(final boolean high) { runOnUiThread(() -> setHz(high)); }

        @JavascriptInterface public void openWeb(final String url) { runOnUiThread(() -> showOverlay(url)); }

        @JavascriptInterface public void closeWeb() { runOnUiThread(ChispaActivity.this::hideOverlay); }

        @JavascriptInterface public void exit() { runOnUiThread(ChispaActivity.this::finish); }
    }
}
