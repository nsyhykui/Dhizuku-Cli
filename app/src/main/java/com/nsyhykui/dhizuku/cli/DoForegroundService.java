/*
 * dcli - 通过 TCP 调用 Dhizuku 的 DO 命令工具
 * Copyright (C) 2026 nsyhykui
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.nsyhykui.dhizuku.cli;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

public class DoForegroundService extends Service {

    private static final String CHANNEL_ID = "do_server_channel";
    private static final int NOTIFY_ID = 1;
    private static final int NOTIFY_PERM_ID = 2;
    private static final String PREFS = "do_server_prefs";
    private static final String KEY_PORT = "port";
    private static final String KEY_BIND = "bind_addr";
    private static final int DEFAULT_PORT = 12345;
    private static final String DEFAULT_BIND = "127.0.0.1";

    private DoServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        AuthManager.get(this).setPromptListener(new AuthManager.PromptListener() {
            @Override
            public void onPrompt(final int uid) {
                String pkg = uidToPackage(uid);

                if (AuthOverlay.hasPermission(DoForegroundService.this)) {
                    AuthOverlay.show(getApplicationContext(), uid, pkg);
                } else {
                    AuthOverlay.requestPermission(getApplicationContext());
                    notifyPermissionNeeded();
                    AuthManager.get(DoForegroundService.this).decide(uid, false);
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int port = DEFAULT_PORT;
        String bindAddr = DEFAULT_BIND;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        if (intent != null && intent.hasExtra("port")) {
            port = intent.getIntExtra("port", DEFAULT_PORT);
            bindAddr = intent.getStringExtra("bind_addr");
            if (bindAddr == null) bindAddr = DEFAULT_BIND;
            sp.edit().putInt(KEY_PORT, port).putString(KEY_BIND, bindAddr).apply();
        } else {
            port = sp.getInt(KEY_PORT, DEFAULT_PORT);
            bindAddr = sp.getString(KEY_BIND, DEFAULT_BIND);
        }

        startForeground(NOTIFY_ID, buildNotification(bindAddr, port));

        if (server == null) {
            LogCallback silent = new LogCallback() {
                @Override public void onLog(String msg) { }
                @Override public void onStatus(String msg) { }
            };
            server = new DoServer(silent, port, bindAddr, getApplicationContext());
            server.start();
        }

        return START_STICKY;
    }

    private void notifyPermissionNeeded() {
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b = new Notification.Builder(this, CHANNEL_ID);
            } else {
                b = new Notification.Builder(this);
            }
            b.setContentTitle(getString(R.string.overlay_needed_title))
             .setContentText(getString(R.string.overlay_needed_msg))
             .setSmallIcon(android.R.drawable.stat_sys_warning)
             .setAutoCancel(true);

            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFY_PERM_ID, b.build());
        } catch (Exception ignored) {}
    }

    private String uidToPackage(int uid) {
        try {
            String[] pkgs = getPackageManager().getPackagesForUid(uid);
            if (pkgs == null || pkgs.length == 0) return getString(R.string.uid_unknown);
            if (pkgs.length == 1) return pkgs[0];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pkgs.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(pkgs[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            return getString(R.string.uid_unknown);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "DO Server",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String bindAddr, int port) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("DO Server 运行中")
                .setContentText("监听 " + bindAddr + ":" + port)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            server = null;
        }
        AuthOverlay.dismiss(getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}