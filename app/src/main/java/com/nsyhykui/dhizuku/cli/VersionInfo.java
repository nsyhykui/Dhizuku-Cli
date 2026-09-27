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
import android.content.pm.PackageManager;

public class VersionInfo {

    private static String sCached = null;

    public static String getServerVersion(Context ctx) {
        if (sCached != null) return sCached;
        try {
            String v = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName;
            if (v == null || v.isEmpty()) v = "unknown";
            sCached = v;
            return v;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
