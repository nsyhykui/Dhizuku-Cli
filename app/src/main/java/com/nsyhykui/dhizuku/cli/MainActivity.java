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
import java.util.List;

public class MainActivity extends Activity implements CheckCallback, LogCallback {

    private static final String PREFS = "do_server_prefs";
    private static final String KEY_PORT = "port";
    private static final String KEY_AUTH = "auth_key";
    private static final String KEY_BIND = "bind_addr";
    private static final int DEFAULT_PORT = 12345;
    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final int KEY_LEN = 32;

    private static final String[] BIND_VALUES = {
            "127.0.0.1",
            "0.0.0.0"
    };

    private TextView tvStatus;
    private TextView tvLog;
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
        btnCheck.setText(R.string.btn_check);

        // 端口行
        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setPadding(0, 20, 0, 0);

        TextView tvPortLabel = new TextView(this);
        tvPortLabel.setText(R.string.label_port);
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

        // 监听行
        LinearLayout bindRow = new LinearLayout(this);
        bindRow.setOrientation(LinearLayout.HORIZONTAL);
        bindRow.setPadding(0, 10, 0, 0);

        TextView tvBindLabel = new TextView(this);
        tvBindLabel.setText(R.string.label_bind);
        tvBindLabel.setTextSize(15f);
        tvBindLabel.setPadding(0, 20, 20, 0);

        spBind = new Spinner(this);
        String[] bindLabels = {
                getString(R.string.bind_localhost),
                getString(R.string.bind_lan)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, bindLabels);
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

        // 本机 IP
        TextView tvLocalIp = new TextView(this);
        tvLocalIp.setTextSize(13f);
        tvLocalIp.setPadding(0, 10, 0, 10);
        tvLocalIp.setText(getString(R.string.label_local_ip, getLocalIpAddress()));

        // 密钥行
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setPadding(0, 10, 0, 0);

        TextView tvKeyLabel = new TextView(this);
        tvKeyLabel.setText(R.string.label_key);
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
        btnRandom.setText(R.string.btn_random);
        btnRandom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newKey = generateKey();
                etKey.setText(newKey);
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putString(KEY_AUTH, newKey).apply();
                onLog(getString(R.string.log_key_updated));
            }
        });

        keyRow.addView(tvKeyLabel);
        keyRow.addView(etKey);
        keyRow.addView(btnRandom);

        Button btnStart = new Button(this);
        btnStart.setText(R.string.btn_start);

        Button btnStop = new Button(this);
        btnStop.setText(R.string.btn_stop);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(15f);
        tvStatus.setText(R.string.status_ready);

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
                String key = etKey.getText().toString().trim();
                if (key.length() < 8) {
                    onLog(getString(R.string.log_key_too_short));
                    return;
                }
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putString(KEY_AUTH, key).apply();

                int port;
                try {
                    port = Integer.parseInt(etPort.getText().toString().trim());
                } catch (Exception e) {
                    onLog(getString(R.string.log_port_format_error));
                    return;
                }
                if (port < 1 || port > 65535) {
                    onLog(getString(R.string.log_port_range));
                    return;
                }

                int sel = spBind.getSelectedItemPosition();
                if (sel < 0 || sel >= BIND_VALUES.length) sel = 0;
                String bindAddr = BIND_VALUES[sel];

                if (bindAddr.equals("0.0.0.0")) {
                    onLog(getString(R.string.log_lan_warning));
                }

                checkPortAndStart(port, bindAddr);
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(MainActivity.this, DoForegroundService.class);
                stopService(it);
                onLog(getString(R.string.log_service_stopped));
                onStatus(getString(R.string.status_stopped));
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
        return "(unknown)";
    }

    private void checkPortAndStart(final int port, final String bindAddr) {
        onStatus(getString(R.string.status_checking_port, port));

        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = tryBind(port, bindAddr);
                final String finalError = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finalError == null) {
                            onLog(getString(R.string.log_port_available, port));
                            startForegroundServer(port, bindAddr);
                        } else {
                            onLog(getString(R.string.log_port_check_failed, finalError));
                            onStatus(getString(R.string.status_port_unavailable));
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
                return "Permission denied: " + bindAddr + ":" + port;
            }
            if (msg.contains("Address already in use")) {
                return "Address already in use: " + port;
            }
            return msg;
        } catch (Exception e) {
            return e.toString();
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
        onLog(getString(R.string.log_service_started, bindAddr, port));
        onStatus(getString(R.string.status_running));
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