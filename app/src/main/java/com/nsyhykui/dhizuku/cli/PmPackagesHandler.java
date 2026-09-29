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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.List;

public class PmPackagesHandler {

    private final Context context;

    public PmPackagesHandler(Context ctx) {
        this.context = ctx.getApplicationContext();
    }

    private static class PkgOpts {
        boolean showSourceDir = false;
        boolean showInstaller = false;
        boolean showUid = false;
        boolean matchUninstalled = false;
        int matchFlags = 0;
        int enabledFlags = 0;
        String filter = null;
    }

    public String handle(String[] args) {
        PkgOpts o = new PkgOpts();

        for (String arg : args) {
            String err = parseArg(arg, o);
            if (err != null) return err;
        }

        PackageManager pm = context.getPackageManager();
        int flags = PackageManager.MATCH_DISABLED_COMPONENTS;
        if (o.matchUninstalled) {
            flags |= PackageManager.MATCH_UNINSTALLED_PACKAGES;
        }

        List<PackageInfo> list;
        try {
            list = pm.getInstalledPackages(flags);
        } catch (Throwable t) {
            return "Failed: " + t.getMessage();
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (PackageInfo pi : list) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null) continue;

            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (o.matchFlags == 1 && !isSystem) continue;
            if (o.matchFlags == 2 && isSystem) continue;

            boolean enabled = ai.enabled;
            if (o.enabledFlags == 1 && !enabled) continue;
            if (o.enabledFlags == 2 && enabled) continue;

            if (o.filter != null && !pi.packageName.contains(o.filter)) continue;

            if (!first) sb.append("\n");
            first = false;

            sb.append("package:");
            if (o.showSourceDir) {
                sb.append(ai.sourceDir).append('=');
            }
            sb.append(pi.packageName);
            if (o.showInstaller) {
                String installer;
                try {
                    installer = pm.getInstallerPackageName(pi.packageName);
                } catch (Throwable t) {
                    installer = null;
                }
                sb.append(" installer=");
                sb.append(installer == null ? "null" : installer);
            }
            if (o.showUid) {
                sb.append(" uid:").append(ai.uid);
            }
        }

        return sb.toString();
    }

    private String parseArg(String arg, PkgOpts o) {
        if (arg.equals("--uid")) {
            o.showUid = true;
            return null;
        }
        if (arg.equals("--user")) {
            return "Failed: --user not supported";
        }
        if (arg.startsWith("--")) {
            return "Failed: unknown option: " + arg;
        }

        if (arg.startsWith("-") && arg.length() > 1) {
            for (int i = 1; i < arg.length(); i++) {
                char c = arg.charAt(i);
                String err = parseFlag(c, o);
                if (err != null) return err;
            }
            return null;
        }

        if (o.filter != null) {
            return "Failed: only one filter allowed";
        }
        o.filter = arg;
        return null;
    }

    private String parseFlag(char c, PkgOpts o) {
        switch (c) {
            case 'f': o.showSourceDir = true; return null;
            case 'i': o.showInstaller = true; return null;
            case 'u': o.matchUninstalled = true; return null;
            case 'U': o.showUid = true; return null;
            case 'l': return null;
            case 's':
                if (o.matchFlags != 0)
                    return "Failed: -s and -3 are mutually exclusive";
                o.matchFlags = 1;
                return null;
            case '3':
                if (o.matchFlags != 0)
                    return "Failed: -s and -3 are mutually exclusive";
                o.matchFlags = 2;
                return null;
            case 'd':
                if (o.enabledFlags != 0)
                    return "Failed: -d and -e are mutually exclusive";
                o.enabledFlags = 2;
                return null;
            case 'e':
                if (o.enabledFlags != 0)
                    return "Failed: -d and -e are mutually exclusive";
                o.enabledFlags = 1;
                return null;
            default:
                return "Failed: unknown option: -" + c;
        }
    }
}
