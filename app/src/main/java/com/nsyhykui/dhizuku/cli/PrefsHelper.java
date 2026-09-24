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

import java.util.HashSet;
import java.util.Set;

public class PrefsHelper {

    private static final String PREFS = "do_server_prefs";
    private static final String KEY_PORT = "port";
    private static final String KEY_AUTH = "auth_key";
    private static final String KEY_BIND = "bind_addr";
    private static final String KEY_ALLOWED = "allowed_uids";
    private static final String KEY_SEEN = "seen_uids";

    public static final int DEFAULT_PORT = 12345;
    public static final String DEFAULT_BIND = "127.0.0.1";

    private final SharedPreferences sp;

    public PrefsHelper(Context ctx) {
        this.sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getPort() { return sp.getInt(KEY_PORT, DEFAULT_PORT); }
    public void setPort(int port) { sp.edit().putInt(KEY_PORT, port).apply(); }

    public String getKey() { return sp.getString(KEY_AUTH, ""); }
    public void setKey(String key) { sp.edit().putString(KEY_AUTH, key).apply(); }

    public String getBind() { return sp.getString(KEY_BIND, DEFAULT_BIND); }
    public void setBind(String addr) { sp.edit().putString(KEY_BIND, addr).apply(); }

    public Set<Integer> getAllowedUids() {
        return parseUids(sp.getString(KEY_ALLOWED, ""));
    }

    public void setAllowedUids(Set<Integer> uids) {
        sp.edit().putString(KEY_ALLOWED, joinUids(uids)).apply();
    }

    public Set<Integer> getSeenUids() {
        return parseUids(sp.getString(KEY_SEEN, ""));
    }

    public void setSeenUids(Set<Integer> uids) {
        sp.edit().putString(KEY_SEEN, joinUids(uids)).apply();
    }

    private Set<Integer> parseUids(String s) {
        Set<Integer> out = new HashSet<>();
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split(",")) {
            try { out.add(Integer.parseInt(p.trim())); } catch (Exception ignored) {}
        }
        return out;
    }

    private String joinUids(Set<Integer> uids) {
        StringBuilder sb = new StringBuilder();
        for (Integer u : uids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(u);
        }
        return sb.toString();
    }
}