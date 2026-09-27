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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionScanner {

    private static final String S_GRANTED = "granted";
    private static final String S_DENIED  = "denied";
    private static final String S_DEFAULT = "default";

    private final Context context;
    private final DhizukuDpm dpmHelper;

    public PermissionScanner(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.dpmHelper = dpmHelper;
    }

    /**
     * 扫描所有已安装应用。
     * 关键：必须加 MATCH_DISABLED_COMPONENTS 和 MATCH_UNINSTALLED_PACKAGES，
     * 否则被 hide 的应用不在列表里，isApplicationHidden 根本不会被调。
     */
    private static int installFlags() {
        int flags = PackageManager.GET_PERMISSIONS;
        if (Build.VERSION.SDK_INT >= 24) {
            flags |= PackageManager.MATCH_DISABLED_COMPONENTS;
            flags |= PackageManager.MATCH_UNINSTALLED_PACKAGES;
        }
        return flags;
    }

    public ScanResult scanToResult() throws Exception {
        DevicePolicyManager dpm = dpmHelper.get();
        if (dpm == null) throw new IllegalStateException("dpm null");

        ComponentName admin = dpmHelper.admin();
        PackageManager pm = context.getPackageManager();

        ScanResult r = new ScanResult();

        List<PackageInfo> packages = pm.getInstalledPackages(installFlags());

        for (PackageInfo pi : packages) {
            String pkg = pi.packageName;
            if (pkg == null) continue;
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
                if (r.hidError == null) {
                    r.hidError = t.getClass().getSimpleName() + ": " + t.getMessage();
                }
            }

            try {
                if (dpm.isPackageSuspended(admin, pkg)) r.suspend.add(pkg);
            } catch (Throwable t) {
                if (r.susError == null) {
                    r.susError = t.getClass().getSimpleName() + ": " + t.getMessage();
                }
            }

            try {
                if (dpm.isUninstallBlocked(admin, pkg)) r.block.add(pkg);
            } catch (Throwable t) {
                if (r.blockError == null) {
                    r.blockError = t.getClass().getSimpleName() + ": " + t.getMessage();
                }
            }
        }

        return r;
    }

    public int scanAndCache() throws Exception {
        ScanResult r = scanToResult();
        ScanCache.get(context).writeAll(r);
        return r.appCount;
    }

    public String[] getLastErrors() {
        return ScanCache.get(context).loadErrors();
    }

    private String queryPermState(DevicePolicyManager dpm,
                                  ComponentName admin,
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
