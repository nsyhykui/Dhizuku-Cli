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
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;

public class DhizukuChecker {

    private final Context context;
    private final CheckCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DhizukuChecker(Context context, CheckCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void check() {
        StringBuilder sb = new StringBuilder();

        try {
            context.getPackageManager().getPackageInfo("com.rosan.dhizuku", 0);
            sb.append("✅ Dhizuku 已安装\n");
        } catch (PackageManager.NameNotFoundException e) {
            sb.append("❌ 未安装 Dhizuku 应用\n");
            postStatus(sb.toString());
            return;
        }

        boolean initOk;
        try {
            initOk = Dhizuku.init(context);
        } catch (Throwable t) {
            sb.append("❌ 初始化异常: ");
            sb.append(t.getMessage());
            sb.append("\n");
            postStatus(sb.toString());
            return;
        }

        if (!initOk) {
            sb.append("❌ 初始化失败，请确认 Dhizuku 已激活为 Device Owner\n");
            postStatus(sb.toString());
            return;
        }
        sb.append("✅ 初始化成功\n");

        boolean granted;
        try {
            granted = Dhizuku.isPermissionGranted();
        } catch (Throwable t) {
            sb.append("❌ 检查权限异常: ");
            sb.append(t.getMessage());
            sb.append("\n");
            postStatus(sb.toString());
            return;
        }

        if (granted) {
            sb.append("✅ 已获得 Dhizuku 权限\n");
            postStatus(sb.toString());
            return;
        }

        sb.append("⚠️ 权限未授予，正在请求...\n");
        postStatus(sb.toString());

        try {
            Dhizuku.requestPermission(new DhizukuRequestPermissionListener() {
                @Override
                public void onRequestPermission(int grantResult) throws RemoteException {
                    final int result = grantResult;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (result == PackageManager.PERMISSION_GRANTED) {
                                callback.onAppend("✅ 权限已授予\n");
                            } else {
                                callback.onAppend("❌ 权限被拒绝\n");
                            }
                        }
                    });
                }
            });
        } catch (Throwable t) {
            callback.onAppend("❌ 请求权限异常: ");
            callback.onAppend(t.getMessage());
            callback.onAppend("\n");
        }
    }

    private void postStatus(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onStatus(text);
            }
        });
    }
}