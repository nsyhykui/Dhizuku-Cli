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

public class PmHandler {

    private final PmPackagesHandler packages;
    private final PmPermissionsHandler permissions;
    private final PmGrantHandler grant;

    public PmHandler(Context ctx, DhizukuDpm dpmHelper) {
        this.packages = new PmPackagesHandler(ctx);
        this.permissions = new PmPermissionsHandler(ctx, dpmHelper);
        this.grant = new PmGrantHandler(ctx, dpmHelper);
    }

    public String handle(String[] args) {
        if (args.length == 0) {
            return "Failed: usage: pm <list|grant|revoke|reset>";
        }

        String sub = args[0];
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        if (sub.equals("list"))   return handleList(rest);
        if (sub.equals("grant"))  return grant.handle(rest, PmGrantHandler.MODE_GRANT);
        if (sub.equals("revoke")) return grant.handle(rest, PmGrantHandler.MODE_REVOKE);
        if (sub.equals("reset"))  return grant.handle(rest, PmGrantHandler.MODE_RESET);

        return "Unknown";
    }

    private String handleList(String[] args) {
        if (args.length == 0) {
            return "Failed: usage: pm list <packages|permissions>";
        }

        String sub = args[0];
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        if (sub.equals("packages"))    return packages.handle(rest);
        if (sub.equals("permissions")) return permissions.handle(rest);

        return "Unknown";
    }
}
