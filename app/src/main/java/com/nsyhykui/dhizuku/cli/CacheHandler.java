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

public class CacheHandler {

    private final PermissionScanner scanner;

    public CacheHandler(Context ctx, DhizukuDpm dpmHelper) {
        this.scanner = new PermissionScanner(ctx, dpmHelper);
    }

    public String handle(String[] args) {
        if (args.length == 0) {
            return "Failed: usage: cache update";
        }

        String sub = args[0];
        if (sub.equals("update")) return doUpdate();

        return "Unknown";
    }

    private String doUpdate() {
        try {
            int count = scanner.scanAndCache();
            return "scanned " + count + " apps";
        } catch (Throwable t) {
            return "Failed: scan failed: " + t.getMessage();
        }
    }
}
