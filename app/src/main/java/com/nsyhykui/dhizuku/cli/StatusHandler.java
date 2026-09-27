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

public class StatusHandler {

    private final Context context;
    private final ScanSettings settings;
    private final PermissionScanner scanner;

    public StatusHandler(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.settings = new ScanSettings(context);
        this.scanner = new PermissionScanner(context, dpmHelper);
    }

    public String handle(String[] args) {
        if (args.length == 0) {
            return "Failed: usage: status <hid|suspend|block_uninstall|permission>";
        }

        String sub = args[0];

        if (sub.equals("hid")) return listState("hid");
        if (sub.equals("suspend")) return listState("suspend");
        if (sub.equals("block_uninstall")) return listState("block_uninstall");
        if (sub.equals("permission")) return handlePermission(args, 1);

        return "Unknown";
    }

    /* ================= 状态列表 ================= */

    private String listState(String kind) {
        String warning = ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        /* 检查该 API 是否在扫描时抛过错 */
        String[] errors = scanner.getLastErrors();
        String apiError = null;
        if (kind.equals("hid")) apiError = errors[0];
        else if (kind.equals("suspend")) apiError = errors[1];
        else apiError = errors[2];

        if (apiError != null && !apiError.isEmpty()) {
            return "Failed: " + kind + " query unsupported on this device: " + apiError;
        }

        ScanResult r = ScanCache.get(context).loadAll();

        List<String> list;
        if (kind.equals("hid")) list = r.hid;
        else if (kind.equals("suspend")) list = r.suspend;
        else list = r.block;

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");
        sb.append("Success");
        for (String pkg : list) {
            sb.append("\n").append(pkg);
        }
        return sb.toString();
    }

    /* ================= permission ================= */

    private String handlePermission(String[] args, int start) {
        if (start >= args.length) {
            return "Failed: usage: status permission <update|<perm>|--package <pkg>...>";
        }

        int pkgIdx = -1;
        for (int i = start; i < args.length; i++) {
            if (args[i].equals("--package")) {
                pkgIdx = i;
                break;
            }
        }

        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();

        if (pkgIdx < 0) {
            for (int i = start; i < args.length; i++) before.add(args[i]);
        } else {
            for (int i = start; i < pkgIdx; i++) before.add(args[i]);
            for (int i = pkgIdx + 1; i < args.length; i++) after.add(args[i]);
        }

        if (pkgIdx < 0 && before.size() == 1 && before.get(0).equals("update")) {
            return doUpdate();
        }

        if (!before.isEmpty() && after.isEmpty()) {
            return listByPermission(before.get(0));
        }
        if (before.isEmpty() && !after.isEmpty()) {
            return listByPackages(after);
        }
        if (!before.isEmpty() && !after.isEmpty()) {
            return queryOne(before.get(0), after.get(0));
        }

        return "Failed: bad arguments";
    }

    private String doUpdate() {
        try {
            int count = scanner.scanAndCache();
            return "Success scanned " + count + " apps";
        } catch (Throwable t) {
            return "Failed: scan failed: " + t.getMessage();
        }
    }

    private String listByPermission(String perm) {
        String warning = ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        ScanResult r = ScanCache.get(context).loadAll();

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");
        sb.append("Success");
        for (Map.Entry<String, Map<String, String>> e : r.permissions.entrySet()) {
            String state = e.getValue().get(perm);
            if (state != null) {
                sb.append("\n").append(e.getKey()).append(": ").append(state);
            }
        }
        return sb.toString();
    }

    private String listByPackages(List<String> pkgs) {
        String warning = ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        ScanResult r = ScanCache.get(context).loadAll();

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");
        sb.append("Success");

        for (String pkg : pkgs) {
            sb.append("\n").append(pkg);
            Map<String, String> perms = r.permissions.get(pkg);
            if (perms == null) {
                sb.append("\n  (no permissions)");
                continue;
            }
            for (Map.Entry<String, String> e : perms.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        return sb.toString();
    }

    private String queryOne(String perm, String pkg) {
        String warning = ensureFresh();
        if (warning != null && warning.startsWith("Failed:")) return warning;

        ScanResult r = ScanCache.get(context).loadAll();

        StringBuilder sb = new StringBuilder();
        if (warning != null) sb.append(warning).append("\n");

        Map<String, String> perms = r.permissions.get(pkg);
        String state = perms == null ? null : perms.get(perm);

        if (state == null) {
            sb.append("Success\n").append(pkg).append(": not declared");
        } else {
            sb.append("Success\n").append(pkg).append(": ").append(state);
        }
        return sb.toString();
    }

    /* ================= 缓存刷新 ================= */

    private String ensureFresh() {
        String mode = settings.getMode();
        ScanCache cache = ScanCache.get(context);
        long last = cache.getLastScanTime();

        if (ScanSettings.MODE_MANUAL.equals(mode) && last == 0) {
            return "Failed: no cache, run 'status permission update' first";
        }

        if (!settings.shouldRescan(last)) return null;

        String warning = null;
        if (settings.shouldWarnOnRescan()) {
            warning = "Warning: cache expired, rescanning all apps...";
        }

        try {
            if (ScanSettings.MODE_ALWAYS.equals(mode)) {
                scanner.scanToResult();   /* always 只扫不写盘 */
            } else {
                scanner.scanAndCache();
            }
        } catch (Throwable t) {
            return "Failed: scan failed: " + t.getMessage();
        }

        return warning;
    }
}
