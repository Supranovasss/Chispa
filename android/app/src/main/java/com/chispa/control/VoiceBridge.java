package com.chispa.control;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** Reconocimiento de voz de Android para la página (el WebView no trae SpeechRecognition). */
public class VoiceBridge {
    private final Activity act;
    private final WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SpeechRecognizer sr;
    private boolean active = false;
    private boolean quiet = true;
    private boolean muted = false;
    private final boolean[] mutedByUs = new boolean[3];
    private static final int[] STREAMS = {AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_RING};
    private final Runnable unmute = this::doUnmute;

    public VoiceBridge(Activity a, WebView w) {
        act = a;
        web = w;
    }

    @JavascriptInterface
    public boolean available() {
        try { return SpeechRecognizer.isRecognitionAvailable(act); } catch (Exception e) { return false; }
    }

    @JavascriptInterface
    public void setQuiet(boolean q) { quiet = q; }

    @JavascriptInterface
    public void start(final String lang, final boolean partial) { ui.post(() -> doStart(lang, partial)); }

    @JavascriptInterface
    public void stop() {
        ui.post(() -> { try { if (sr != null) sr.stopListening(); } catch (Exception ignored) { } });
    }

    @JavascriptInterface
    public void abort() { ui.post(this::doAbort); }

    private void emit(String type, JSONObject data) {
        final String js = "window.__cvEv&&window.__cvEv(" + JSONObject.quote(type) + "," + data.toString() + ")";
        ui.post(() -> { try { web.evaluateJavascript(js, null); } catch (Exception ignored) { } });
    }

    private void emitError(String code) {
        try { emit("error", new JSONObject().put("error", code)); } catch (Exception ignored) { }
    }

    private void doStart(String lang, boolean partial) {
        try {
            ui.removeCallbacks(unmute);
            if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                act.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ChispaActivity.REQ);
                emitError("not-allowed");
                emit("end", new JSONObject());
                return;
            }
            if (!SpeechRecognizer.isRecognitionAvailable(act)) {
                emitError("service-not-allowed");
                emit("end", new JSONObject());
                return;
            }
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(act);
                sr.setRecognitionListener(listener);
            } else if (active) {
                sr.cancel();
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang == null || lang.isEmpty() ? "es-CO" : lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partial);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, act.getPackageName());
            i.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L);
            i.putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L);
            muteBeeps();
            active = true;
            sr.startListening(i);
        } catch (Exception e) {
            active = false;
            emitError("service-not-allowed");
            emit("end", new JSONObject());
        }
    }

    private void doAbort() {
        active = false;
        try { if (sr != null) sr.cancel(); } catch (Exception ignored) { }
        ui.postDelayed(unmute, 2500);
    }

    public void destroy() {
        active = false;
        ui.removeCallbacks(unmute);
        doUnmute();
        try { if (sr != null) { sr.cancel(); sr.destroy(); } } catch (Exception ignored) { }
        sr = null;
    }

    /** Silencia los pitidos de "te escucho" mientras Chispa escucha (solo notificaciones y sistema, no la música). */
    private void muteBeeps() {
        if (!quiet || muted) return;
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            for (int k = 0; k < STREAMS.length; k++) {
                mutedByUs[k] = false;
                try {
                    if (!am.isStreamMute(STREAMS[k])) {
                        am.adjustStreamVolume(STREAMS[k], AudioManager.ADJUST_MUTE, 0);
                        mutedByUs[k] = true;
                    }
                } catch (Exception ignored) { }
            }
            muted = true;
        } catch (Exception ignored) { }
    }

    private void doUnmute() {
        if (!muted) return;
        muted = false;
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            for (int k = 0; k < STREAMS.length; k++) {
                if (mutedByUs[k]) {
                    try { am.adjustStreamVolume(STREAMS[k], AudioManager.ADJUST_UNMUTE, 0); } catch (Exception ignored) { }
                    mutedByUs[k] = false;
                }
            }
        } catch (Exception ignored) { }
    }

    private void finish() {
        active = false;
        emit("end", new JSONObject());
        ui.removeCallbacks(unmute);
        ui.postDelayed(unmute, 2500);
    }

    private JSONObject result(ArrayList<String> list, boolean fin) throws Exception {
        JSONArray a = new JSONArray();
        if (list != null) for (String s : list) a.put(s);
        return new JSONObject().put("alts", a).put("final", fin);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle p) { if (active) emit("start", new JSONObject()); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float v) { }
        @Override public void onBufferReceived(byte[] b) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int t, Bundle b) { }

        @Override public void onPartialResults(Bundle b) {
            if (!active) return;
            try {
                ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (l != null && !l.isEmpty()) emit("result", result(l, false));
            } catch (Exception ignored) { }
        }

        @Override public void onResults(Bundle b) {
            if (!active) return;
            try {
                ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (l != null && !l.isEmpty()) emit("result", result(l, true));
            } catch (Exception ignored) { }
            finish();
        }

        @Override public void onError(int e) {
            if (!active && e == SpeechRecognizer.ERROR_CLIENT) return;
            String code;
            switch (e) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: code = "no-speech"; break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_SERVER: code = "network"; break;
                case SpeechRecognizer.ERROR_AUDIO: code = "audio-capture"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: code = "not-allowed"; break;
                case 12:
                case 13: code = "language-not-supported"; break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                case SpeechRecognizer.ERROR_CLIENT: code = "aborted"; break;
                default: code = "service-not-allowed";
            }
            boolean was = active;
            if (was && !"aborted".equals(code)) emitError(code);
            finish();
        }
    };
}
