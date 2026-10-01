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
import android.content.pm.PackageInfo;

import java.util.HashMap;
import java.util.Map;

final class PackageScanTask {

    private static final String S_GRANTED = "granted";
    private static final String S_DENIED  = "denied";
    private static final String S_DEFAULT = "default";

    private PackageScanTask() {}

    static void scan(DevicePolicyManager dpm, ComponentName admin,
                     PackageInfo pi, ScanResult r) {
        String pkg = pi.packageName;
        if (pkg == null) return;
        r.appCount++;

        if (pi.requestedPermissions != null) {
            Map<String, String> perms = new HashMap<>();
            for (String perm : pi.requestedPermissions) {
                String state = queryPermState(dpm, admin, pkg, perm);
                if (state != null) perms.put(perm, state);
            }
            if (!perms.isEmpty()) r.permissions.put(pkg, perms);
        }

        try {
            if (dpm.isApplicationHidden(admin, pkg)) r.hid.add(pkg);
        } catch (Throwable t) {
            if (r.hidError == null)
                r.hidError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }

        try {
            if (dpm.isPackageSuspended(admin, pkg)) r.suspend.add(pkg);
        } catch (Throwable t) {
            if (r.susError == null)
                r.susError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }

        try {
            if (dpm.isUninstallBlocked(admin, pkg)) r.block.add(pkg);
        } catch (Throwable t) {
            if (r.blockError == null)
                r.blockError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static String queryPermState(DevicePolicyManager dpm, ComponentName admin,
                                         String pkg, String perm) {
        try {
            int state = dpm.getPermissionGrantState(admin, pkg, perm);
            if (state == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) return S_GRANTED;
            if (state == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED)  return S_DENIED;
            if (state == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) return S_DEFAULT;
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
