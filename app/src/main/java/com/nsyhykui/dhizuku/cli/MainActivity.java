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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends Activity implements CheckCallback, LogCallback {

    private static final String PREFS = "do_server_prefs";
    private static final String KEY_PORT = "port";
    private static final String KEY_AUTH = "auth_key";
    private static final String KEY_BIND = "bind_addr";
    private static final int DEFAULT_PORT = 12345;
    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final int KEY_LEN = 32;

    private static final String[] BIND_LABELS = {
            "仅本机 (127.0.0.1)",
            "局域网 (0.0.0.0)"
    };
    private static final String[] BIND_VALUES = {
            "127.0.0.1",
            "0.0.0.0"
    };

    private TextView tvStatus;
    private TextView tvLog;
    private TextView tvLocalIp;
    private EditText etPort;
    private EditText etKey;
    private Spinner spBind;
    private DhizukuChecker checker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        Button btnCheck = new Button(this);
        btnCheck.setText("检测 Dhizuku");

        // ---- 端口行 ----
        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setPadding(0, 20, 0, 0);

        TextView tvPortLabel = new TextView(this);
        tvPortLabel.setText("端口：");
        tvPortLabel.setTextSize(15f);
        tvPortLabel.setPadding(0, 20, 20, 0);

        etPort = new EditText(this);
        etPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        etPort.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int savedPort = sp.getInt(KEY_PORT, DEFAULT_PORT);
        etPort.setText(String.valueOf(savedPort));

        portRow.addView(tvPortLabel);
        portRow.addView(etPort);

        // ---- 监听地址行 ----
        LinearLayout bindRow = new LinearLayout(this);
        bindRow.setOrientation(LinearLayout.HORIZONTAL);
        bindRow.setPadding(0, 10, 0, 0);

        TextView tvBindLabel = new TextView(this);
        tvBindLabel.setText("监听：");
        tvBindLabel.setTextSize(15f);
        tvBindLabel.setPadding(0, 20, 20, 0);

        spBind = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, BIND_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBind.setAdapter(adapter);

        String savedBind = sp.getString(KEY_BIND, "127.0.0.1");
        for (int i = 0; i < BIND_VALUES.length; i++) {
            if (BIND_VALUES[i].equals(savedBind)) {
                spBind.setSelection(i);
                break;
            }
        }

        bindRow.addView(tvBindLabel);
        bindRow.addView(spBind);

        // ---- 本机局域网 IP 显示 ----
        tvLocalIp = new TextView(this);
        tvLocalIp.setTextSize(13f);
        tvLocalIp.setPadding(0, 10, 0, 10);
        tvLocalIp.setText("本机局域网 IP: " + getLocalIpAddress());

        // ---- 密钥行 ----
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setPadding(0, 10, 0, 0);

        TextView tvKeyLabel = new TextView(this);
        tvKeyLabel.setText("密钥：");
        tvKeyLabel.setTextSize(15f);
        tvKeyLabel.setPadding(0, 20, 20, 0);

        etKey = new EditText(this);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT);
        etKey.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String savedKey = sp.getString(KEY_AUTH, "");
        if (savedKey.isEmpty()) {
            savedKey = generateKey();
            sp.edit().putString(KEY_AUTH, savedKey).apply();
        }
        etKey.setText(savedKey);

        Button btnRandom = new Button(this);
        btnRandom.setText("随机");
        btnRandom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newKey = generateKey();
                etKey.setText(newKey);
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putString(KEY_AUTH, newKey).apply();
                onLog("密钥已更新");
            }
        });

        keyRow.addView(tvKeyLabel);
        keyRow.addView(etKey);
        keyRow.addView(btnRandom);

        Button btnStart = new Button(this);
        btnStart.setText("启动 TCP 服务");

        Button btnStop = new Button(this);
        btnStop.setText("停止 TCP 服务");

        tvStatus = new TextView(this);
        tvStatus.setTextSize(15f);
        tvStatus.setText("准备就绪");

        tvLog = new TextView(this);
        tvLog.setTextSize(12f);

        layout.addView(btnCheck);
        layout.addView(portRow);
        layout.addView(bindRow);
        layout.addView(tvLocalIp);
        layout.addView(keyRow);
        layout.addView(btnStart);
        layout.addView(btnStop);
        layout.addView(tvStatus);
        layout.addView(tvLog);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        checker = new DhizukuChecker(this, this);

        btnCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checker.check();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 保存密钥
                String key = etKey.getText().toString().trim();
                if (key.length() < 8) {
                    onLog("密钥太短，至少 8 位");
                    return;
                }
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putString(KEY_AUTH, key).apply();

                // 解析端口
                int port;
                try {
                    port = Integer.parseInt(etPort.getText().toString().trim());
                } catch (Exception e) {
                    onLog("端口格式错误");
                    return;
                }
                if (port < 1 || port > 65535) {
                    onLog("端口必须在 1 ~ 65535 之间");
                    return;
                }

                // 解析监听地址
                int sel = spBind.getSelectedItemPosition();
                if (sel < 0 || sel >= BIND_VALUES.length) sel = 0;
                String bindAddr = BIND_VALUES[sel];

                if (bindAddr.equals("0.0.0.0")) {
                    onLog("警告：局域网模式下，同网络设备可访问。请确保密钥安全");
                }

                checkPortAndStart(port, bindAddr);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(MainActivity.this, DoForegroundService.class);
                stopService(it);
                onLog("前台服务已停止");
                onStatus("服务已停止");
            }
        });
    }

    private String generateKey() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(KEY_LEN);
        for (int i = 0; i < KEY_LEN; i++) {
            sb.append(KEY_CHARS.charAt(r.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 遍历网络接口，返回第一个非回环的 IPv4 地址。
     */
    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ni : interfaces) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "(未获取到)";
    }

    private void checkPortAndStart(final int port, final String bindAddr) {
        onStatus("正在检查端口 " + port + " ...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = tryBind(port, bindAddr);
                final String finalError = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalError == null) {
                            onLog("端口 " + port + " 可用");
                            startForegroundServer(port, bindAddr);
                        } else {
                            onLog("端口检查失败: " + finalError);
                            onStatus("端口不可用");
                        }
                    }
                });
            }
        }).start();
    }

    private String tryBind(int port, String bindAddr) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port, 1, java.net.InetAddress.getByName(bindAddr));
            return null;
        } catch (java.net.BindException e) {
            String msg = e.getMessage();
            if (msg == null) msg = "";
            if (msg.contains("Permission denied")) {
                return "权限不足，无法绑定 " + bindAddr + ":" + port;
            }
            if (msg.contains("Address already in use")) {
                return "端口 " + port + " 已被占用，请换一个";
            }
            return "绑定失败: " + msg;
        } catch (SecurityException e) {
            return "被系统策略拒绝: " + e.getMessage();
        } catch (Exception e) {
            return "未知错误: " + e.toString();
        } finally {
            if (ss != null) {
                try { ss.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void startForegroundServer(int port, String bindAddr) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putInt(KEY_PORT, port)
                .putString(KEY_BIND, bindAddr)
                .apply();

        requestIgnoreBatteryOptimization();

        Intent it = new Intent(MainActivity.this, DoForegroundService.class);
        it.putExtra("port", port);
        it.putExtra("bind_addr", bindAddr);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(it);
        } else {
            startService(it);
        }
        onLog("前台服务已启动，监听 " + bindAddr + ":" + port);
        onStatus("服务运行中");
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm =
                    (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent it = new Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                it.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(it);
            }
        }
    }

    @Override
    public void onStatus(String text) {
        tvStatus.setText(text);
    }

    @Override
    public void onAppend(String text) {
        tvStatus.append(text);
    }

    @Override
    public void onLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvLog.append(msg);
                tvLog.append("\n");
            }
        });
    }
}