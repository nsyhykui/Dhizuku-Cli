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
import android.content.SharedPreferences;

public class ScanSettings {

    private static final String PREFS = "do_server_prefs";
    private static final String KEY_MODE = "cache_mode";
    private static final String KEY_TTL = "cache_ttl_seconds";

    public static final String MODE_ALWAYS = "always";
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";

    public static final double DEFAULT_TTL_SECONDS = 300.0;

    private final SharedPreferences sp;

    public ScanSettings(Context ctx) {
        this.sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getMode() {
        return sp.getString(KEY_MODE, MODE_AUTO);
    }

    public void setMode(String mode) {
        if (!MODE_ALWAYS.equals(mode) && !MODE_AUTO.equals(mode)
                && !MODE_MANUAL.equals(mode)) mode = MODE_AUTO;
        sp.edit().putString(KEY_MODE, mode).apply();
    }

    public double getTtlSeconds() {
        String v = sp.getString(KEY_TTL, null);
        if (v == null) return DEFAULT_TTL_SECONDS;
        try { return Double.parseDouble(v); }
        catch (Exception e) { return DEFAULT_TTL_SECONDS; }
    }

    public void setTtlSeconds(double seconds) {
        sp.edit().putString(KEY_TTL, String.valueOf(seconds)).apply();
    }

    public boolean shouldRescan(long lastScanTime) {
        String mode = getMode();
        if (MODE_ALWAYS.equals(mode)) return true;
        if (MODE_MANUAL.equals(mode)) return false;
        if (lastScanTime == 0) return true;
        long ttlMs = (long)(getTtlSeconds() * 1000.0);
        return System.currentTimeMillis() - lastScanTime > ttlMs;
    }

    public boolean shouldWarnOnRescan() {
        return MODE_AUTO.equals(getMode());
    }
}
