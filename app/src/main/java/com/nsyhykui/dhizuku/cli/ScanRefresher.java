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

/**
 * 按缓存模式决定是否重扫。
 * 返回 Warning 行（可为 null），或 "Failed: ..." 表示不能继续。
 */
public class ScanRefresher {

    private final Context context;
    private final ScanSettings settings;
    private final PermissionScanner scanner;

    public ScanRefresher(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.settings = new ScanSettings(context);
        this.scanner = new PermissionScanner(context, dpmHelper);
    }

    /**
     * 检查缓存新鲜度，必要时重扫。
     * 返回 null 表示可用（无警告），返回 Warning 字符串表示可用但有警告，
     * 返回 "Failed: ..." 表示不能继续。
     */
    public String ensureFresh() {
        String mode = settings.getMode();
        ScanCache cache = ScanCache.get(context);
        long last = cache.getLastScanTime();

        if (ScanSettings.MODE_MANUAL.equals(mode) && last == 0) {
            return "Failed: no cache, run 'cache update' first";
        }

        if (!settings.shouldRescan(last)) return null;

        String warning = null;
        if (settings.shouldWarnOnRescan()) {
            warning = "Warning: cache expired, rescanning all apps...";
        }

        try {
            if (ScanSettings.MODE_ALWAYS.equals(mode)) {
                scanner.scanToResult();
            } else {
                scanner.scanAndCache();
            }
        } catch (Throwable t) {
            return "Failed: scan failed: " + t.getMessage();
        }

        return warning;
    }
}
