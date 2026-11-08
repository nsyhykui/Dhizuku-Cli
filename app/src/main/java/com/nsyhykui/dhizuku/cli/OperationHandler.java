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

public class OperationHandler {

    private final Context context;
    private final DhizukuDpm dpmHelper;

    public OperationHandler(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.dpmHelper = dpmHelper;
    }

    public String handle(String cmd, String arg) {
        if (cmd.equals("lock_now")) return doLockNow();
        
        if (cmd.equals("hide")) return doHide(arg, true);
        if (cmd.equals("unhide")) return doHide(arg, false);
        if (cmd.equals("suspend")) return doSuspend(arg, true);
        if (cmd.equals("resume")) return doSuspend(arg, false);
        if (cmd.equals("block_uninstall")) return doBlockUninstall(arg, true);
        if (cmd.equals("unblock_uninstall")) return doBlockUninstall(arg, false);
        
        if (cmd.equals("camera_off")) return doCamera(false);
        if (cmd.equals("camera_on")) return doCamera(true);
        if (cmd.equals("screencap_off")) return doScreenCapture(false);
        if (cmd.equals("screencap_on")) return doScreenCapture(true);
        if (cmd.equals("screen_timeout")) return doScreenTimeout(arg);

        return "Unknown";
    }

    private String doLockNow() {
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            if (dpm == null) return "Failed: dpm null";
            dpm.lockNow();
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doHide(String pkg, boolean hidden) {
        if (!isPackageInstalled(pkg)) return "Failed: package not installed";
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            boolean result = dpm.setApplicationHidden(admin, pkg, hidden);
            if (!result) return "Failed: setApplicationHidden returned false";

            ScanCache cache = ScanCache.get(context);
            if (hidden) cache.addHidden(pkg);
            else cache.removeHidden(pkg);

            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doSuspend(String pkg, boolean suspended) {
        if (!isPackageInstalled(pkg)) return "Failed: package not installed";
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            String[] failed = dpm.setPackagesSuspended(admin, new String[]{pkg}, suspended);
            if (failed != null && failed.length > 0) return "Failed: cannot suspend " + failed[0];

            ScanCache cache = ScanCache.get(context);
            if (suspended) cache.addSuspended(pkg);
            else cache.removeSuspended(pkg);

            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doBlockUninstall(String pkg, boolean blocked) {
        if (!isPackageInstalled(pkg)) return "Failed: package not installed";
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            dpm.setUninstallBlocked(admin, pkg, blocked);

            ScanCache cache = ScanCache.get(context);
            if (blocked) cache.addBlocked(pkg);
            else cache.removeBlocked(pkg);

            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doCamera(boolean enabled) {
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            dpm.setCameraDisabled(admin, !enabled);
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doScreenCapture(boolean enabled) {
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            dpm.setScreenCaptureDisabled(admin, !enabled);
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doScreenTimeout(String secondsStr) {
        long seconds;
        try {
            seconds = Long.parseLong(secondsStr);
        } catch (Exception e) {
            return "Failed: invalid seconds";
        }
        
        // 拦截负数，允许 0（0 代表清除限制）
        if (seconds < 0) {
            return "Failed: seconds must be >= 0";
        }
        
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            dpm.setMaximumTimeToLock(admin, seconds * 1000L);
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
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
}
