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

import java.util.List;

public class ListHandler {

    private final Context context;
    private final ScanRefresher refresher;
    private final PermissionScanner scanner;

    public ListHandler(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.refresher = new ScanRefresher(context, dpmHelper);
        this.scanner = new PermissionScanner(context, dpmHelper);
    }

    public String handle(String[] args) {
        if (args.length == 0) {
            return "Failed: usage: list <hidden|suspended|blocked>";
        }

        String sub = args[0];

        if (sub.equals("hidden")) return listState("hidden");
        if (sub.equals("suspended")) return listState("suspended");
        if (sub.equals("blocked")) return listState("blocked");

        return "Unknown";
    }

    private String listState(String kind) {
        String warning = refresher.ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        String[] errors = scanner.getLastErrors();
        String apiError = null;
        if (kind.equals("hidden")) apiError = errors[0];
        else if (kind.equals("suspended")) apiError = errors[1];
        else apiError = errors[2];

        if (apiError != null && !apiError.isEmpty()) {
            return "Failed: " + kind + " query unsupported on this device: " + apiError;
        }

        ScanResult r = ScanCache.get(context).loadAll();

        List<String> list;
        if (kind.equals("hidden")) list = r.hid;
        else if (kind.equals("suspended")) list = r.suspend;
        else list = r.block;

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");
        boolean first = true;

        for (String pkg : list) {
            if (!first) sb.append("\n");
            first = false;
            sb.append("package:").append(pkg);
        }
        return sb.toString();
    }
}
