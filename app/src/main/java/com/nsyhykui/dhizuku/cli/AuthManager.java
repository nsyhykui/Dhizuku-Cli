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
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AuthManager {

    public static final int ALLOWED = 1;
    public static final int DENIED = 0;
    public static final int PENDING = -1;
    public static final int TIMEOUT = -2;

    public interface PromptListener {
        void onPrompt(int uid);
    }

    private static AuthManager instance;

    private final PrefsHelper prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> allowedUids;
    private final Set<Integer> seenUids;
    private final ConcurrentHashMap<Integer, Integer> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CountDownLatch> latches = new ConcurrentHashMap<>();
    private PromptListener promptListener;

    private AuthManager(Context ctx) {
        this.prefs = new PrefsHelper(ctx);
        this.allowedUids = new HashSet<>(prefs.getAllowedUids());
        this.seenUids = new HashSet<>(prefs.getSeenUids());
    }

    public static synchronized AuthManager get(Context ctx) {
        if (instance == null) instance = new AuthManager(ctx.getApplicationContext());
        return instance;
    }

    public void setPromptListener(PromptListener l) {
        this.promptListener = l;
    }

    public synchronized Set<Integer> getAllowedUids() {
        return new HashSet<>(allowedUids);
    }

    public synchronized Set<Integer> getSeenUids() {
        return new HashSet<>(seenUids);
    }

    /**
     * 检查 UID 授权状态。
     * - 已授权 → ALLOWED
     * - 新 UID → 触发弹窗并返回 PENDING
     * - 上次拒绝 → DENIED（返回一次后清除）
     * - 正在等待 → PENDING
     */
    public synchronized int check(int uid) {
        if (allowedUids.contains(uid)) return ALLOWED;

        Integer state = states.get(uid);
        if (state != null) {
            if (state == DENIED) {
                states.remove(uid);
                return DENIED;
            }
            if (state == PENDING) {
                return PENDING;
            }
        }

        if (!seenUids.contains(uid)) {
            seenUids.add(uid);
            prefs.setSeenUids(seenUids);
        }

        states.put(uid, PENDING);
        if (!latches.containsKey(uid)) {
            latches.put(uid, new CountDownLatch(1));
        }
        final int u = uid;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (promptListener != null) promptListener.onPrompt(u);
            }
        });
        return PENDING;
    }

    /**
     * 阻塞等待用户决定，最多 timeoutMs 毫秒。
     * 返回 ALLOWED / DENIED / TIMEOUT
     */
    public int awaitDecision(int uid, long timeoutMs) {
        CountDownLatch latch = latches.get(uid);
        if (latch == null) {
            // 已经处理过，直接返回当前状态
            if (allowedUids.contains(uid)) return ALLOWED;
            return DENIED;
        }
        try {
            boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!ok) {
                synchronized (this) {
                    latches.remove(uid);
                    states.remove(uid);
                }
                return TIMEOUT;
            }
        } catch (InterruptedException e) {
            return TIMEOUT;
        }
        return allowedUids.contains(uid) ? ALLOWED : DENIED;
    }

    public synchronized void decide(int uid, boolean allow) {
        states.remove(uid);
        if (allow) {
            allowedUids.add(uid);
            prefs.setAllowedUids(allowedUids);
        }
        CountDownLatch latch = latches.remove(uid);
        if (latch != null) latch.countDown();
    }

    public synchronized void grant(int uid) {
        allowedUids.add(uid);
        prefs.setAllowedUids(allowedUids);
        states.remove(uid);
        CountDownLatch latch = latches.remove(uid);
        if (latch != null) latch.countDown();
    }

    public synchronized void revoke(int uid) {
        allowedUids.remove(uid);
        prefs.setAllowedUids(allowedUids);
        states.remove(uid);
        latches.remove(uid);
    }

    public synchronized void clearPending() {
        states.clear();
        for (CountDownLatch latch : latches.values()) {
            latch.countDown();
        }
        latches.clear();
    }
}