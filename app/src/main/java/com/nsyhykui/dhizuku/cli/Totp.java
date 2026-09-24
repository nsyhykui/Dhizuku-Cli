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

import java.nio.ByteBuffer;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Totp {

    private static final long TIME_STEP = 30L;

    public static boolean verify(String secret, String code) {
        if (code == null || code.length() != 6) return false;
        if (secret == null || secret.isEmpty()) return false;

        long t = System.currentTimeMillis() / 1000L / TIME_STEP;

        String c1 = generate(secret, t - 1);
        String c2 = generate(secret, t);
        String c3 = generate(secret, t + 1);

        if (c1 == null || c2 == null || c3 == null) return false;

        return code.equals(c1) || code.equals(c2) || code.equals(c3);
    }

    public static String generate(String key, long t) {
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