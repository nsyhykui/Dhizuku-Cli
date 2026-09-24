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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

public class CommandHandler {

    private static final String PREFS = "do_server_prefs";
    private static final String KEY_AUTH = "auth_key";

    private static final long AUTH_TIMEOUT_MS = 60_000;

    private final Context context;
    private final DhizukuDpm dpmHelper;

    private AesGcm aes = null;
    private String cachedKey = null;

    public CommandHandler(Context context) {
        this.context = context.getApplicationContext();
        this.dpmHelper = new DhizukuDpm(context);
    }

    public String process(String b64, int realUid, String remoteIp, int remotePort) {
        // 1. 解密
        String plaintext;
        try {
            byte[] raw = Base64.decode(b64, Base64.DEFAULT);
            plaintext = new String(getAes().decrypt(raw), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "Denied";
        }

        // 2. 解析：UID IP PORT TOTP CMD [ARG]
        String[] parts = plaintext.trim().split("\\s+", 6);
        if (parts.length < 5) return "Failed";

        // 3. 取 UID（优先真实 UID，反查失败时用声明的 UID）
        int uid;
        if (realUid >= 0) {
            uid = realUid;
        } else {
            try {
                uid = Integer.parseInt(parts[0]);
            } catch (Exception e) {
                return "Failed: bad uid";
            }
        }

        // 4. UID 授权（阻塞等待用户响应）
        int state = AuthManager.get(context).check(uid);
        if (state == AuthManager.PENDING) {
            state = AuthManager.get(context).awaitDecision(uid, AUTH_TIMEOUT_MS);
        }
        if (state != AuthManager.ALLOWED) {
            return "Denied";
        }

        // 5. 剩余字段
        String claimedIp = parts[1];
        String claimedPortStr = parts[2];
        String totp = parts[3];
        String cmd = parts[4];
        String arg = parts.length > 5 ? parts[5].trim() : "";

        // 6. 源 IP / 端口校验
        int claimedPort;
        try {
            claimedPort = Integer.parseInt(claimedPortStr);
        } catch (Exception e) {
            return "Failed: bad port";
        }

        if (!claimedIp.equals(remoteIp) || claimedPort != remotePort) {
            return "Failed: source mismatch";
        }

        // 7. TOTP
        if (!Totp.verify(getKey(), totp)) return "Denied";

        // 8. 参数校验
        if (cmd.equals("ping") || cmd.equals("lock_now")) {
            if (!arg.isEmpty()) return "Failed: unexpected argument: " + arg;
        }
        if (cmd.equals("hide") || cmd.equals("unhide") ||
            cmd.equals("suspend") || cmd.equals("resume") ||
            cmd.equals("block_uninstall") || cmd.equals("unblock_uninstall")) {
            if (arg.isEmpty()) return "Failed: missing package";
        }

        // 9. 分发
        if (cmd.equals("ping")) return "Success";
        if (cmd.equals("lock_now")) return doLockNow();
        if (cmd.equals("hide")) return doHide(arg, true);
        if (cmd.equals("unhide")) return doHide(arg, false);
        if (cmd.equals("suspend")) return doSuspend(arg, true);
        if (cmd.equals("resume")) return doSuspend(arg, false);
        if (cmd.equals("block_uninstall")) return doBlockUninstall(arg, true);
        if (cmd.equals("unblock_uninstall")) return doBlockUninstall(arg, false);

        return "Unknown";
    }

    /* ================= 命令实现 ================= */

    private String doLockNow() {
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            if (dpm == null) return "Failed: dpm null";
            dpm.lockNow();
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doHide(String pkg, boolean hidden) {
        if (hidden && !isPackageInstalled(pkg)) {
            return "Failed: package not installed";
        }
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            boolean result = dpm.setApplicationHidden(admin, pkg, hidden);
            return result ? "Success" : "Failed: setApplicationHidden returned false";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doSuspend(String pkg, boolean suspended) {
        if (suspended && !isPackageInstalled(pkg)) {
            return "Failed: package not installed";
        }
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            String[] failed = dpm.setPackagesSuspended(admin, new String[]{pkg}, suspended);
            if (failed != null && failed.length > 0) {
                return "Failed: cannot suspend " + failed[0];
            }
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doBlockUninstall(String pkg, boolean blocked) {
        if (blocked && !isPackageInstalled(pkg)) {
            return "Failed: package not installed";
        }
        try {
            DevicePolicyManager dpm = dpmHelper.get();
            ComponentName admin = dpmHelper.admin();
            dpm.setUninstallBlocked(admin, pkg, blocked);
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /* ================= 密钥 / 加密 ================= */

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