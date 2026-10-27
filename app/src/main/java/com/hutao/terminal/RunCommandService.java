package com.hutao.terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Lets other apps (Tasker, scripts) run a shell command in the background, modelled on Termux's RUN_COMMAND.
 * Guarded twice: callers need the dangerous com.hutao.terminal.permission.RUN_COMMAND permission (granted by
 * the user), and the user must enable "allow other apps" in the menu.
 *
 * Extras: "command" (String, required, run with sh -c), "linux" (boolean, run inside Alpine),
 * "workdir" (String), "pending_intent" (PendingIntent receiving "stdout", "stderr", "exit_code", "error").
 */
public class RunCommandService extends Service {
    static final String ACTION = "com.hutao.terminal.RUN_COMMAND";
    static final String PREF_ALLOW = "allowExternalApps";
    private static final String TAG = "HutaoRunCommand";
    private static final String CHANNEL = "run_command";
    private static final int MAX_OUTPUT = 100 * 1024; // keeps the result Intent well under Binder's 1 MB limit

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Callers on Android 8+ must use startForegroundService, which requires this within 5 s.
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Lệnh từ app khác", NotificationManager.IMPORTANCE_LOW));
        startForeground(2, new Notification.Builder(this, CHANNEL)
                .setContentTitle("Hutao đang chạy lệnh từ app khác")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .build());

        if (intent == null || !ACTION.equals(intent.getAction())) {
            finish(startId);
            return START_NOT_STICKY;
        }
        PendingIntent reply = intent.getParcelableExtra("pending_intent");
        if (!getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean(PREF_ALLOW, false)) {
            sendResult(reply, null, null, null, "Hutao: bật \"Cho app khác chạy lệnh\" trong menu ⋮ trước");
            finish(startId);
            return START_NOT_STICKY;
        }
        String command = intent.getStringExtra("command");
        if (command == null || command.isEmpty()) {
            sendResult(reply, null, null, null, "thiếu extra \"command\"");
            finish(startId);
            return START_NOT_STICKY;
        }
        String workdir = intent.getStringExtra("workdir");
        if (workdir != null && !workdir.isEmpty()) command = "cd " + Linux.quote(workdir) + " && " + command;
        boolean linux = intent.getBooleanExtra("linux", false);
        String script = command;
        new Thread(() -> {
            run(script, linux, reply);
            finish(startId);
        }).start();
        return START_NOT_STICKY;
    }

    private void run(String command, boolean linux, PendingIntent reply) {
        if (linux && !Linux.isInstalled(this)) {
            sendResult(reply, null, null, null, "chưa cài Linux: bấm nút Linux trong Hutao");
            return;
        }
        Map<String, String> env = MainActivity.shellEnv(this);
        List<String> argv = linux ? new ArrayList<>(Arrays.asList(Linux.command(this)))
                : new ArrayList<>(Arrays.asList("/system/bin/sh"));
        argv.add("-c");
        argv.add(command);
        ProcessBuilder pb = new ProcessBuilder(argv).directory(new File(env.get("HOME")));
        pb.environment().clear();
        pb.environment().putAll(env);
        try {
            Process p = pb.start();
            p.getOutputStream().close();
            // Drain stderr on its own thread so a full pipe can't block the process.
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            Thread errReader = new Thread(() -> readCapped(p.getErrorStream(), err));
            errReader.start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            readCapped(p.getInputStream(), out);
            int exit = p.waitFor();
            errReader.join();
            sendResult(reply, out.toString("UTF-8"), err.toString("UTF-8"), exit, null);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "run failed", e);
            sendResult(reply, null, null, null, e.toString());
        }
    }

    /** Keeps the first MAX_OUTPUT bytes but always reads to EOF so the process never blocks on a full pipe. */
    static void readCapped(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[8192];
        try (InputStream s = in) {
            for (int n; (n = s.read(buf)) > 0; ) {
                int room = MAX_OUTPUT - out.size();
                if (room > 0) out.write(buf, 0, Math.min(n, room));
            }
        } catch (IOException ignored) {
            // process exited and closed the pipe
        }
    }

    private void sendResult(PendingIntent reply, String stdout, String stderr, Integer exit, String error) {
        if (error != null) Log.w(TAG, error);
        if (reply == null) return;
        Intent data = new Intent();
        if (stdout != null) data.putExtra("stdout", stdout);
        if (stderr != null) data.putExtra("stderr", stderr);
        if (exit != null) data.putExtra("exit_code", (int) exit);
        if (error != null) data.putExtra("error", error);
        try {
            reply.send(this, 0, data);
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "caller's PendingIntent was cancelled");
        }
    }

    private void finish(int startId) {
        stopSelf(startId); // only stops once the most recent command is done
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
