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
import android.os.IBinder;
import android.os.IInterface;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuBinderWrapper;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Field;

public class DhizukuDpm {

    private final Context context;
    private DevicePolicyManager cached = null;

    public DhizukuDpm(Context context) {
        this.context = context.getApplicationContext();
    }

    public DevicePolicyManager get() throws Exception {
        if (cached != null) return cached;

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
        Object old = field.get(manager);
        if (old == null) throw new IllegalStateException("mService null");

        if (!(old instanceof DhizukuBinderWrapper)) {
            IBinder oldBinder = ((IInterface) old).asBinder();
            IBinder newBinder = Dhizuku.binderWrapper(oldBinder);
            IDevicePolicyManager newIf =
                    IDevicePolicyManager.Stub.asInterface(newBinder);
            field.set(manager, newIf);
        }

        cached = manager;
        return cached;
    }

    public ComponentName admin() {
        return Dhizuku.getOwnerComponent();
    }
}