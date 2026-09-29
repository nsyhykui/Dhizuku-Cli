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

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

public class PmGrantHandler {

    public static final int MODE_GRANT  = 1;
    public static final int MODE_REVOKE = 2;
    public static final int MODE_RESET  = 3;

    private final Context context;
    private final DhizukuDpm dpmHelper;

    public PmGrantHandler(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.dpmHelper = dpmHelper;
    }

    public String handle(String[] args, int mode) {
        if (args.length < 2) {
            return "Failed: usage: pm " + modeName(mode)
                    + " <package> <permission>";
        }

        String pkg = args[0];
        String perm = args[1];

        if (!isPackageInstalled(pkg)) {
            return "Failed: package not installed";
        }

        int state;
        String stateName;
        switch (mode) {
            case MODE_GRANT:
                state = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
                stateName = "granted";
                break;
            case MODE_REVOKE:
                state = DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
                stateName = "denied";
                break;
            default:
                state = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
                stateName = "default";
                break;
        }

        try {
            DevicePolicyManager dpm = dpmHelper.get();
            if (dpm == null) return "Failed: dpm null";

            ComponentName admin = dpmHelper.admin();
            dpm.setPermissionGrantState(admin, pkg, perm, state);

            /* 成功后实时更新缓存 */
            ScanCache.get(context).updatePermissionState(pkg, perm, stateName);

            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static String modeName(int mode) {
        if (mode == MODE_GRANT) return "grant";
        if (mode == MODE_REVOKE) return "revoke";
        return "reset";
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
