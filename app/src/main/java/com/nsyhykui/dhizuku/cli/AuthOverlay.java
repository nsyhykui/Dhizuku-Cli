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

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AuthOverlay {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static View currentView = null;

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    /** 跳转到系统设置，请求悬浮窗权限 */
    public static void requestPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent it = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(it);
            } catch (Exception ignored) {}
        }
    }

    public static void show(final Context context, final int uid, final String pkgName) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    showInternal(context, uid, pkgName);
                } catch (Throwable t) {
                    AuthManager.get(context).decide(uid, false);
                }
            }
        });
    }

    private static void showInternal(Context context, final int uid, String pkgName) {
        final WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            AuthManager.get(context).decide(uid, false);
            return;
        }

        if (currentView != null) {
            try { wm.removeView(currentView); } catch (Exception ignored) {}
            currentView = null;
        }

        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int)(24 * density);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF202124);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText(R.string.auth_title);
        title.setTextSize(18f);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, pad / 2);

        TextView msg = new TextView(context);
        msg.setText(context.getString(R.string.auth_message, uid, pkgName));
        msg.setTextSize(14f);
        msg.setTextColor(0xFFCCCCCC);
        msg.setPadding(0, 0, 0, pad);

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        Button btnDeny = new Button(context);
        btnDeny.setText(R.string.auth_deny);

        Button btnAllow = new Button(context);
        btnAllow.setText(R.string.auth_allow);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.leftMargin = pad / 2;
        btnAllow.setLayoutParams(btnLp);

        btnRow.addView(btnDeny);
        btnRow.addView(btnAllow);

        root.addView(title);
        root.addView(msg);
        root.addView(btnRow);

        final Runnable dismiss = new Runnable() {
            @Override
            public void run() {
                if (currentView != null) {
                    try { wm.removeView(currentView); } catch (Exception ignored) {}
                    currentView = null;
                }
            }
        };

        btnAllow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AuthManager.get(context).decide(uid, true);
                dismiss.run();
            }
        });
        btnDeny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AuthManager.get(context).decide(uid, false);
                dismiss.run();
            }
        });

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        try {
            wm.addView(root, lp);
            currentView = root;
        } catch (Throwable t) {
            AuthManager.get(context).decide(uid, false);
        }
    }

    /** 主动关闭悬浮窗（如服务停止时） */
    public static void dismiss(Context context) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (currentView == null) return;
                try {
                    WindowManager wm = (WindowManager)
                            context.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) wm.removeView(currentView);
                } catch (Exception ignored) {}
                currentView = null;
            }
        });
    }
}