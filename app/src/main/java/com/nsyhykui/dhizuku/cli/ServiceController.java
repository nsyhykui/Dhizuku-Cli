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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

public class ServiceController {

    private final Activity activity;

    public ServiceController(Activity activity) {
        this.activity = activity;
    }

    public void start(int port, String bindAddr) {
        requestIgnoreBatteryOptimization();
        Intent it = new Intent(activity, DoForegroundService.class);
        it.putExtra("port", port);
        it.putExtra("bind_addr", bindAddr);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(it);
        } else {
            activity.startService(it);
        }
    }

    public void stop() {
        Intent it = new Intent(activity, DoForegroundService.class);
        activity.stopService(it);
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                it.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(it);
            }
        }
    }
}