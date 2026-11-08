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
import android.util.Base64;

import java.nio.charset.StandardCharsets;

public class CommandHandler {

    private static final String PREFS = "do_server_prefs";
    private static final String KEY_AUTH = "auth_key";
    private static final long AUTH_TIMEOUT_MS = 60_000;

    private final Context context;
    private final PmHandler pmHandler;
    private final ListHandler listHandler;
    private final CacheHandler cacheHandler;
    private final OperationHandler operationHandler;

    private AesGcm aes = null;
    private String cachedKey = null;

    public CommandHandler(Context context) {
        this.context = context.getApplicationContext();
        DhizukuDpm dpmHelper = new DhizukuDpm(context);
        this.pmHandler = new PmHandler(context, dpmHelper);
        this.listHandler = new ListHandler(context, dpmHelper);
        this.cacheHandler = new CacheHandler(context, dpmHelper);
        this.operationHandler = new OperationHandler(context, dpmHelper);
    }

    public String process(String b64, int realUid, String remoteIp, int remotePort) {
        String plaintext;
        try {
            byte[] raw = Base64.decode(b64, Base64.DEFAULT);
            plaintext = new String(getAes().decrypt(raw), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "crypto: denied";
        }

        String[] parts = plaintext.trim().split("\\s+", 5);
        if (parts.length < 5) return "Failed: bad message format";

        int uid;
        try {
            uid = Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return "Failed: bad uid";
        }

        String claimedIp = parts[1];
        String claimedPortStr = parts[2];
        String totp = parts[3];
        String rest = parts[4];

        int claimedPort;
        try {
            claimedPort = Integer.parseInt(claimedPortStr);
        } catch (Exception e) {
            return "Failed: bad port";
        }

        if (!claimedIp.equals(remoteIp) || claimedPort != remotePort) {
            return "Failed: source mismatch";
        }

        if (!Totp.verify(getKey(), totp)) return "totp: denied";

        if (rest.startsWith("#$%")) {
            if (rest.equals("#$%version")) return VersionInfo.getServerVersion(context);
            return "Unknown";
        }

        int state = AuthManager.get(context).check(uid);
        if (state == AuthManager.PENDING) {
            state = AuthManager.get(context).awaitDecision(uid, AUTH_TIMEOUT_MS);
        }
        if (state == AuthManager.TIMEOUT) return "timeout";
        if (state != AuthManager.ALLOWED) return "uid: denied";

        String[] cmdParts = rest.split("\\s+");
        String cmd = cmdParts[0];
        String[] args = new String[cmdParts.length - 1];
        System.arraycopy(cmdParts, 1, args, 0, args.length);

        if (cmd.equals("pm")) return pmHandler.handle(args);
        if (cmd.equals("list")) return listHandler.handle(args);
        if (cmd.equals("cache")) return cacheHandler.handle(args);

        String arg = args.length > 0 ? args[0] : "";

        // 参数校验
        if (cmd.equals("ping") || cmd.equals("lock_now") || 
            cmd.equals("camera_off") || cmd.equals("camera_on") ||
            cmd.equals("screencap_off") || cmd.equals("screencap_on")) {
            if (args.length > 0) return "Failed: unexpected argument: " + arg;
        }
        if (cmd.equals("hide") || cmd.equals("unhide") ||
            cmd.equals("suspend") || cmd.equals("resume") ||
            cmd.equals("block_uninstall") || cmd.equals("unblock_uninstall")) {
            if (args.length == 0) return "Failed: missing package";
        }
        if (cmd.equals("screen_timeout")) {
            if (args.length == 0) return "Failed: missing seconds";
        }

        if (cmd.equals("ping")) return "Success";

        // 路由到 OperationHandler
        return operationHandler.handle(cmd, arg);
    }

    private AesGcm getAes() {
        String key = getKey();
        if (aes == null || !key.equals(cachedKey)) {
            aes = new AesGcm(key);
            cachedKey = key;
        }
        return aes;
    }

    private String getKey() {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_AUTH, "");
    }
}
