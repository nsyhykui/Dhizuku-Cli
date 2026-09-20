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
import android.app.admin.IDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.IInterface;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuBinderWrapper;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class CommandHandler {

    private static final String PREFS = "do_server_prefs";
    private static final String KEY_AUTH = "auth_key";
    private static final long TIME_STEP = 30L;

    private final Context context;
    private final int port;

    private DevicePolicyManager cachedDpm = null;

    public CommandHandler(Context context, int port) {
        this.context = context.getApplicationContext();
        this.port = port;
    }

    public String process(String line) {
        if (line == null || line.trim().isEmpty()) return "Failed";

        // <包名> <TOTP> <命令> [参数]
        String[] parts = line.trim().split("\\s+", 4);
        if (parts.length < 3) return "Failed";

        String pkg = parts[0];
        String code = parts[1];
        String cmd = parts[2];
        String arg = parts.length > 3 ? parts[3].trim() : "";

        if (!verifyTotp(code)) return "Denied";

        // 无参数命令：不允许携带参数
        if (cmd.equals("ping") || cmd.equals("lock_now")) {
            if (!arg.isEmpty()) {
                return "Failed: unexpected argument: " + arg;
            }
        }

        // 需要参数的命令：必须携带参数
        if (cmd.equals("hide") || cmd.equals("unhide") ||
            cmd.equals("suspend") || cmd.equals("resume") ||
            cmd.equals("block_uninstall") || cmd.equals("unblock_uninstall")) {
            if (arg.isEmpty()) {
                return "Failed: missing package";
            }
        }

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
            DevicePolicyManager dpm = getDhizukuDpm();
            if (dpm == null) return "Failed: dpm null";
            dpm.lockNow();
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doHide(String pkg, boolean hidden) {
        if (pkg.isEmpty()) return "Failed: missing package";

        if (hidden && !isPackageInstalled(pkg)) {
            return "Failed: package not installed";
        }

        try {
            DevicePolicyManager dpm = getDhizukuDpm();
            if (dpm == null) return "Failed: dpm null";

            ComponentName admin = Dhizuku.getOwnerComponent();
            boolean result = dpm.setApplicationHidden(admin, pkg, hidden);

            if (!result) {
                return "Failed: setApplicationHidden returned false";
            }
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doSuspend(String pkg, boolean suspended) {
        if (pkg.isEmpty()) return "Failed: missing package";

        if (suspended && !isPackageInstalled(pkg)) {
            return "Failed: package not installed";
        }

        try {
            DevicePolicyManager dpm = getDhizukuDpm();
            if (dpm == null) return "Failed: dpm null";

            ComponentName admin = Dhizuku.getOwnerComponent();
            String[] packages = new String[]{pkg};
            String[] failed = dpm.setPackagesSuspended(admin, packages, suspended);

            if (failed != null && failed.length > 0) {
                return "Failed: cannot suspend " + failed[0];
            }
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private String doBlockUninstall(String pkg, boolean blocked) {
        if (pkg.isEmpty()) return "Failed: missing package";

        if (blocked && !isPackageInstalled(pkg)) {
            return "Failed: package not installed";
        }

        try {
            DevicePolicyManager dpm = getDhizukuDpm();
            if (dpm == null) return "Failed: dpm null";

            ComponentName admin = Dhizuku.getOwnerComponent();
            dpm.setUninstallBlocked(admin, pkg, blocked);
            return "Success";
        } catch (Throwable t) {
            return "Failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /* ================= 工具 ================= */

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

    /* ================= Dhizuku DPM 包装 ================= */

    private DevicePolicyManager getDhizukuDpm() throws Exception {
        if (cachedDpm != null) return cachedDpm;

        HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/app/admin/DevicePolicyManager;");
        HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/app/admin/IDevicePolicyManager;");

        if (!Dhizuku.init(context)) {
            throw new IllegalStateException("Dhizuku init failed");
        }

        Context ownerCtx = context.createPackageContext(
                Dhizuku.getOwnerComponent().getPackageName(),
                Context.CONTEXT_IGNORE_SECURITY);
        DevicePolicyManager manager =
                ownerCtx.getSystemService(DevicePolicyManager.class);
        if (manager == null) throw new IllegalStateException("dpm null");

        Field field = manager.getClass().getDeclaredField("mService");
        field.setAccessible(true);
        Object oldInterface = field.get(manager);
        if (oldInterface == null) {
            throw new IllegalStateException("mService null");
        }

        if (!(oldInterface instanceof DhizukuBinderWrapper)) {
            IBinder oldBinder = ((IInterface) oldInterface).asBinder();
            IBinder newBinder = Dhizuku.binderWrapper(oldBinder);
            IDevicePolicyManager newInterface =
                    IDevicePolicyManager.Stub.asInterface(newBinder);
            field.set(manager, newInterface);
        }

        cachedDpm = manager;
        return cachedDpm;
    }

    /* ================= TOTP ================= */

    private boolean verifyTotp(String code) {
        if (code == null || code.length() != 6) return false;

        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = sp.getString(KEY_AUTH, "");
        if (key.isEmpty()) return false;

        long t = System.currentTimeMillis() / 1000L / TIME_STEP;

        String c1 = totp(key, t - 1);
        String c2 = totp(key, t);
        String c3 = totp(key, t + 1);

        if (c1 == null || c2 == null || c3 == null) return false;

        return code.equals(c1) || code.equals(c2) || code.equals(c3);
    }

    private String totp(String key, long t) {
        try {
            byte[] msg = ByteBuffer.allocate(8).putLong(t).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int offset = h[19] & 0x0f;
            int code = ((h[offset] & 0x7f) << 24) |
                       ((h[offset + 1] & 0xff) << 16) |
                       ((h[offset + 2] & 0xff) << 8) |
                       ((h[offset + 3] & 0xff));
            return String.format(Locale.US, "%06d", code % 1000000);
        } catch (Exception e) {
            return null;
        }
    }
}