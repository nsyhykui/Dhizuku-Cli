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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcm {

    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] INFO =
            "dhizuku-cli-v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec aesKey;

    public AesGcm(String sharedSecret) {
        byte[] key = hkdfSha256(
                sharedSecret.getBytes(StandardCharsets.UTF_8),
                new byte[32],
                INFO,
                32);
        this.aesKey = new SecretKeySpec(key, "AES");
    }

    public byte[] encrypt(byte[] plaintext) throws Exception {
        byte[] nonce = new byte[NONCE_LEN];
        new SecureRandom().nextBytes(nonce);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
        byte[] ct = c.doFinal(plaintext);

        byte[] out = new byte[NONCE_LEN + ct.length];
        System.arraycopy(nonce, 0, out, 0, NONCE_LEN);
        System.arraycopy(ct, 0, out, NONCE_LEN, ct.length);
        return out;
    }

    public byte[] decrypt(byte[] input) throws Exception {
        if (input.length < NONCE_LEN + 16) {
            throw new IllegalArgumentException("too short");
        }
        byte[] nonce = new byte[NONCE_LEN];
        System.arraycopy(input, 0, nonce, 0, NONCE_LEN);

        byte[] ct = new byte[input.length - NONCE_LEN];
        System.arraycopy(input, NONCE_LEN, ct, 0, ct.length);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, nonce));
        return c.doFinal(ct);
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);

            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] okm = new byte[length];
            byte[] t = new byte[0];
            int pos = 0;

            for (int i = 1; pos < length; i++) {
                mac.update(t);
                mac.update(info);
                mac.update((byte) i);
                t = mac.doFinal();
                int n = Math.min(t.length, length - pos);
                System.arraycopy(t, 0, okm, pos, n);
                pos += n;
            }
            return okm;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}