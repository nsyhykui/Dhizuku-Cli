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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PmPermissionsHandler {

    private final Context context;
    private final ScanRefresher refresher;

    public PmPermissionsHandler(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.refresher = new ScanRefresher(context, dpmHelper);
    }

    public String handle(String[] args) {
        if (args.length == 0) {
            return "Failed: usage: pm list permissions <perm> [--package <pkg>]";
        }

        int pkgIdx = -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--package")) {
                pkgIdx = i;
                break;
            }
        }

        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();

        if (pkgIdx < 0) {
            for (String a : args) before.add(a);
        } else {
            for (int i = 0; i < pkgIdx; i++) before.add(args[i]);
            for (int i = pkgIdx + 1; i < args.length; i++) after.add(args[i]);
        }

        if (!before.isEmpty() && after.isEmpty()) {
            return byPermission(before.get(0));
        }
        if (before.isEmpty() && !after.isEmpty()) {
            return byPackages(after);
        }
        if (!before.isEmpty() && !after.isEmpty()) {
            return queryOne(before.get(0), after.get(0));
        }

        return "Failed: bad arguments";
    }

    private String byPermission(String perm) {
        String warning = refresher.ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        ScanResult r = ScanCache.get(context).loadAll();

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");
        boolean first = true;

        for (Map.Entry<String, Map<String, String>> e : r.permissions.entrySet()) {
            String state = e.getValue().get(perm);
            if (state != null) {
                if (!first) sb.append("\n");
                first = false;
                sb.append(e.getKey()).append(": ").append(state);
            }
        }
        return sb.toString();
    }

    private String byPackages(List<String> pkgs) {
        String warning = refresher.ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        ScanResult r = ScanCache.get(context).loadAll();

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");
        boolean first = true;

        for (String pkg : pkgs) {
            if (!first) sb.append("\n");
            first = false;
            sb.append(pkg);

            Map<String, String> perms = r.permissions.get(pkg);
            if (perms == null) {
                sb.append("\n\t(no permissions)");
                continue;
            }
            for (Map.Entry<String, String> e : perms.entrySet()) {
                sb.append("\n\t").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        return sb.toString();
    }

    private String queryOne(String perm, String pkg) {
        String warning = refresher.ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        ScanResult r = ScanCache.get(context).loadAll();

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");

        Map<String, String> perms = r.permissions.get(pkg);
        String state = perms == null ? null : perms.get(perm);

        if (state == null) {
            sb.append(pkg).append(": not declared");
        } else {
            sb.append(pkg).append(": ").append(state);
        }
        return sb.toString();
    }
}
