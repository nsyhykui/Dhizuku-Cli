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
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

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

        if (!isRuntimePermission(perm)) {
            return "Failed: not a runtime permission: " + perm;
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
            boolean result = dpm.setPermissionGrantState(admin, pkg, perm, state);
            if (!result) {
                return "Failed: setPermissionGrantState returned false";
            }

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
            int flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
                      | PackageManager.MATCH_DISABLED_COMPONENTS;
            context.getPackageManager().getPackageInfo(pkg, flags);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 只允许运行时权限（dangerous）。
     * 非运行时权限调用 setPermissionGrantState 会返回 false，
     * 且在部分系统（如华为）上会卡约 20 秒。提前拒绝。
     */
    private boolean isRuntimePermission(String perm) {
        try {
            PermissionInfo info = context.getPackageManager()
                    .getPermissionInfo(perm, 0);
            return (info.protectionLevel & PermissionInfo.PROTECTION_DANGEROUS) != 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
