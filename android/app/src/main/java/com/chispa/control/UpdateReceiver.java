package com.chispa.control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class UpdateReceiver extends BroadcastReceiver {
    @SuppressWarnings("deprecation")
    @Override public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (st != PackageInstaller.STATUS_PENDING_USER_ACTION) return;
        Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirm == null) return;
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(confirm); } catch (Exception ignored) { }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("chispa_update", "Actualizaciones", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent pi = PendingIntent.getActivity(c, 7, confirm, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "chispa_update").setSmallIcon(R.drawable.ic)
                .setContentTitle("Chispa Control: actualización lista").setContentText("Toca para instalar").setContentIntent(pi).setAutoCancel(true).build();
        nm.notify(2, n);
    }
}
