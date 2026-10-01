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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PermissionScanner {

    private static final int MIN_PARALLEL = 16;

    private final Context context;
    private final DhizukuDpm dpmHelper;
    private final ScanSettings settings;

    public PermissionScanner(Context ctx, DhizukuDpm dpmHelper) {
        this.context = ctx.getApplicationContext();
        this.dpmHelper = dpmHelper;
        this.settings = new ScanSettings(context);
    }

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
        List<PackageInfo> packages =
                context.getPackageManager().getInstalledPackages(installFlags());

        int threads = settings.getThreads();
        if (threads <= 1 || packages.size() < MIN_PARALLEL) {
            ScanResult r = new ScanResult();
            for (PackageInfo pi : packages) {
                PackageScanTask.scan(dpm, admin, pi, r);
            }
            return r;
        }
        return scanParallel(dpm, admin, packages, threads);
    }

    private ScanResult scanParallel(DevicePolicyManager dpm, ComponentName admin,
                                    List<PackageInfo> packages, int threads) throws Exception {
        int count = Math.min(threads, packages.size());
        int chunkSize = (packages.size() + count - 1) / count;

        List<List<PackageInfo>> chunks = new ArrayList<>();
        for (int i = 0; i < packages.size(); i += chunkSize) {
            chunks.add(packages.subList(i, Math.min(i + chunkSize, packages.size())));
        }

        ExecutorService pool = Executors.newFixedThreadPool(chunks.size());
        List<Future<ScanResult>> futures = new ArrayList<>();
        for (final List<PackageInfo> chunk : chunks) {
            futures.add(pool.submit(() -> {
                ScanResult part = new ScanResult();
                for (PackageInfo pi : chunk) {
                    PackageScanTask.scan(dpm, admin, pi, part);
                }
                return part;
            }));
        }
        pool.shutdown();

        ScanResult merged = new ScanResult();
        for (Future<ScanResult> f : futures) merge(merged, f.get());
        return merged;
    }

    private void merge(ScanResult dst, ScanResult src) {
        dst.permissions.putAll(src.permissions);
        dst.hid.addAll(src.hid);
        dst.suspend.addAll(src.suspend);
        dst.block.addAll(src.block);
        dst.appCount += src.appCount;
        if (dst.hidError == null) dst.hidError = src.hidError;
        if (dst.susError == null) dst.susError = src.susError;
        if (dst.blockError == null) dst.blockError = src.blockError;
    }

    public int scanAndCache() throws Exception {
        ScanResult r = scanToResult();
        ScanCache.get(context).writeAll(r);
        return r.appCount;
    }

    public String[] getLastErrors() {
        return ScanCache.get(context).loadErrors();
    }
}
