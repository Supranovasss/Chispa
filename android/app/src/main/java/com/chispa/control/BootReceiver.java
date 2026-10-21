package com.chispa.control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try { c.startForegroundService(new Intent(c, ControlService.class)); } catch (Exception ignored) { }
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) && Settings.canDrawOverlays(c)) {
            try {
                Intent v = new Intent(c, ChispaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(v);
            } catch (Exception ignored) { }
        }
    }
}
