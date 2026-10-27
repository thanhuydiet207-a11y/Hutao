package com.hutao.terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/** Foreground service so Android does not kill running shells when the app is in background. */
public class TermService extends Service {
    // Sessions live as long as the process; the service only keeps the process alive.
    public static final List<TerminalSession> SESSIONS = new ArrayList<>();

    private static final String CHANNEL = "sessions";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel(CHANNEL, "Terminal sessions", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, b.setContentTitle("Hutao")
                .setContentText(SESSIONS.size() + " session(s) running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(open)
                .setOngoing(true)
                .build());
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        for (TerminalSession s : SESSIONS) s.finishIfRunning();
        SESSIONS.clear();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
