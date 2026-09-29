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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

public class ScanCache extends SQLiteOpenHelper {

    private static final String DB_NAME = "scan_cache.db";
    private static final int DB_VERSION = 1;

    private static final String T_META = "meta";
    private static final String T_PERM = "permissions";
    private static final String T_HID = "hid_apps";
    private static final String T_SUS = "suspend_apps";
    private static final String T_BLK = "block_uninstall_apps";

    private static ScanCache instance;

    public static synchronized ScanCache get(Context ctx) {
        if (instance == null) instance = new ScanCache(ctx.getApplicationContext());
        return instance;
    }

    private ScanCache(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_META + " (key TEXT PRIMARY KEY, value TEXT)");
        db.execSQL("CREATE TABLE " + T_PERM + " ("
                + "package TEXT NOT NULL, permission TEXT NOT NULL, "
                + "state TEXT NOT NULL, PRIMARY KEY (package, permission))");
        db.execSQL("CREATE TABLE " + T_HID + " (package TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE " + T_SUS + " (package TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE " + T_BLK + " (package TEXT PRIMARY KEY)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + T_META);
        db.execSQL("DROP TABLE IF EXISTS " + T_PERM);
        db.execSQL("DROP TABLE IF EXISTS " + T_HID);
        db.execSQL("DROP TABLE IF EXISTS " + T_SUS);
        db.execSQL("DROP TABLE IF EXISTS " + T_BLK);
        onCreate(db);
    }

    /* ================= meta ================= */

    private void setMeta(SQLiteDatabase db, String key, String value) {
        db.execSQL("INSERT OR REPLACE INTO " + T_META
                + " (key, value) VALUES (?, ?)", new Object[]{key, value});
    }

    private String getMeta(String key) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT value FROM " + T_META + " WHERE key = ?",
                new String[]{key});
        try {
            if (c.moveToFirst()) return c.getString(0);
            return null;
        } finally {
            c.close();
        }
    }

    public long getLastScanTime() {
        String v = getMeta("last_scan_time");
        if (v == null) return 0;
        try { return Long.parseLong(v); }
        catch (Exception e) { return 0; }
    }

    public int countApps() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COUNT(DISTINCT package) FROM " + T_PERM, null);
        try {
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            c.close();
        }
    }

    /* ================= 写入 ================= */

    public void writeAll(ScanResult r) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + T_PERM);
            db.execSQL("DELETE FROM " + T_HID);
            db.execSQL("DELETE FROM " + T_SUS);
            db.execSQL("DELETE FROM " + T_BLK);

            for (Map.Entry<String, Map<String, String>> e
                    : r.permissions.entrySet()) {
                String pkg = e.getKey();
                for (Map.Entry<String, String> pe : e.getValue().entrySet()) {
                    db.execSQL("INSERT OR REPLACE INTO " + T_PERM
                                    + " (package, permission, state) VALUES (?, ?, ?)",
                            new Object[]{pkg, pe.getKey(), pe.getValue()});
                }
            }
            for (String pkg : r.hid) {
                db.execSQL("INSERT OR REPLACE INTO " + T_HID
                        + " (package) VALUES (?)", new Object[]{pkg});
            }
            for (String pkg : r.suspend) {
                db.execSQL("INSERT OR REPLACE INTO " + T_SUS
                        + " (package) VALUES (?)", new Object[]{pkg});
            }
            for (String pkg : r.block) {
                db.execSQL("INSERT OR REPLACE INTO " + T_BLK
                        + " (package) VALUES (?)", new Object[]{pkg});
            }

            setMeta(db, "last_scan_time", String.valueOf(System.currentTimeMillis()));
            setMeta(db, "hid_error", r.hidError == null ? "" : r.hidError);
            setMeta(db, "sus_error", r.susError == null ? "" : r.susError);
            setMeta(db, "block_error", r.blockError == null ? "" : r.blockError);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /* ================= 读取 ================= */

    public ScanResult loadAll() {
        ScanResult r = new ScanResult();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;

        try {
            c = db.rawQuery("SELECT package, permission, state FROM " + T_PERM, null);
            while (c.moveToNext()) {
                String pkg = c.getString(0);
                Map<String, String> m = r.permissions.get(pkg);
                if (m == null) {
                    m = new HashMap<>();
                    r.permissions.put(pkg, m);
                }
                m.put(c.getString(1), c.getString(2));
            }
            c.close();

            c = db.rawQuery("SELECT package FROM " + T_HID, null);
            while (c.moveToNext()) r.hid.add(c.getString(0));
            c.close();

            c = db.rawQuery("SELECT package FROM " + T_SUS, null);
            while (c.moveToNext()) r.suspend.add(c.getString(0));
            c.close();

            c = db.rawQuery("SELECT package FROM " + T_BLK, null);
            while (c.moveToNext()) r.block.add(c.getString(0));
            c.close();
        } finally {
            if (c != null) c.close();
        }

        r.appCount = r.permissions.size();
        return r;
    }

    public String[] loadErrors() {
        String h = getMeta("hid_error");
        String s = getMeta("sus_error");
        String b = getMeta("block_error");
        return new String[]{
                h == null ? "" : h,
                s == null ? "" : s,
                b == null ? "" : b
        };
    }

    /* ================= 单条实时更新 ================= */

    private boolean cacheExists() {
        return getLastScanTime() != 0;
    }

    /**
     * 更新某应用某权限的状态。
     * 缓存不存在时不写。
     * 表里没这条记录时也不插入。
     */
    public void updatePermissionState(String pkg, String perm, String state) {
        if (!cacheExists()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + T_PERM + " SET state = ? "
                        + "WHERE package = ? AND permission = ?",
                new Object[]{state, pkg, perm});
    }

    public void addHidden(String pkg) {
        if (!cacheExists()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO " + T_HID
                + " (package) VALUES (?)", new Object[]{pkg});
    }

    public void removeHidden(String pkg) {
        if (!cacheExists()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + T_HID + " WHERE package = ?",
                new Object[]{pkg});
    }

    public void addSuspended(String pkg) {
        if (!cacheExists()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO " + T_SUS
                + " (package) VALUES (?)", new Object[]{pkg});
    }

    public void removeSuspended(String pkg) {
        if (!cacheExists()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + T_SUS + " WHERE package = ?",
                new Object[]{pkg});
    }

    public void addBlocked(String pkg) {
        if (!cacheExists()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO " + T_BLK
                + " (package) VALUES (?)", new Object[]{pkg});
    }

    public void removeBlocked(String pkg) {
        if (!cacheExists()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + T_BLK + " WHERE package = ?",
                new Object[]{pkg});
    }
}
