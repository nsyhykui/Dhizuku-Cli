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
import android.os.Bundle;
import android.view.View;

import java.net.InetAddress;
import java.net.ServerSocket;

public class MainActivity extends Activity implements CheckCallback, LogCallback {

    private UiBuilder ui;
    private PrefsHelper prefs;
    private ServiceController svc;
    private DhizukuChecker checker;
    private ScanUiController scanUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new PrefsHelper(this);
        svc = new ServiceController(this);
        checker = new DhizukuChecker(this, this);

        ui = new UiBuilder(this,
                new View.OnClickListener() {
                    @Override public void onClick(View v) { onStartClicked(); }
                },
                new View.OnClickListener() {
                    @Override public void onClick(View v) { onStopClicked(); }
                },
                new View.OnClickListener() {
                    @Override public void onClick(View v) { checker.check(); }
                },
                new View.OnClickListener() {
                    @Override public void onClick(View v) { onRandomClicked(); }
                },
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, UidManagerActivity.class));
                    }
                },
                new View.OnClickListener() {
                    @Override public void onClick(View v) { scanUi.onModeClicked(); }
                },
                new View.OnClickListener() {
                    @Override public void onClick(View v) { scanUi.onUpdateCacheClicked(); }
                });

        scanUi = new ScanUiController(this, ui,
                new ScanSettings(this), new DhizukuDpm(this), this);

        restoreUiFromPrefs();
        scanUi.init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanUi.onResume();
    }

    private void restoreUiFromPrefs() {
        ui.etPort.setText(String.valueOf(prefs.getPort()));

        String key = prefs.getKey();
        if (key.isEmpty()) {
            key = KeyUtils.generate();
            prefs.setKey(key);
        }
        ui.etKey.setText(key);

        for (int i = 0; i < UiBuilder.BIND_VALUES.length; i++) {
            if (UiBuilder.BIND_VALUES[i].equals(prefs.getBind())) {
                ui.spBind.setSelection(i);
                break;
            }
        }
    }

    /* ================= 服务启停 ================= */

    private void onStartClicked() {
        String key = ui.etKey.getText().toString().trim();
        if (key.length() < 8) {
            onLog(getString(R.string.log_key_too_short));
            return;
        }
        prefs.setKey(key);

        int port;
        try {
            port = Integer.parseInt(ui.etPort.getText().toString().trim());
        } catch (Exception e) {
            onLog(getString(R.string.log_port_format_error));
            return;
        }
        if (port < 1 || port > 65535) {
            onLog(getString(R.string.log_port_range));
            return;
        }

        int sel = ui.spBind.getSelectedItemPosition();
        if (sel < 0 || sel >= UiBuilder.BIND_VALUES.length) sel = 0;
        String bindAddr = UiBuilder.BIND_VALUES[sel];

        if (bindAddr.equals("0.0.0.0")) {
            onLog(getString(R.string.log_lan_warning));
        }

        checkPortAndStart(port, bindAddr);
    }

    private void onStopClicked() {
        svc.stop();
        onLog(getString(R.string.log_service_stopped));
        onStatus(getString(R.string.status_stopped));
    }

    private void onRandomClicked() {
        String key = KeyUtils.generate();
        ui.etKey.setText(key);
        prefs.setKey(key);
        onLog(getString(R.string.log_key_updated));
    }

    private void checkPortAndStart(final int port, final String bindAddr) {
        onStatus(getString(R.string.status_checking_port, port));
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = tryBind(port, bindAddr);
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (err == null) {
                            onLog(getString(R.string.log_port_available, port));
                            prefs.setPort(port);
                            prefs.setBind(bindAddr);
                            svc.start(port, bindAddr);
                            onLog(getString(R.string.log_service_started, bindAddr, port));
                            onStatus(getString(R.string.status_running));
                        } else {
                            onLog(getString(R.string.log_port_check_failed, err));
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
            ss = new ServerSocket(port, 1, InetAddress.getByName(bindAddr));
            return null;
        } catch (Exception e) {
            return e.toString();
        } finally {
            if (ss != null) try { ss.close(); } catch (Exception ignored) {}
        }
    }

    /* ================= 回调 ================= */

    @Override public void onStatus(String text) { ui.tvStatus.setText(text); }
    @Override public void onAppend(String text) { ui.tvStatus.append(text); }

    @Override
    public void onLog(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ui.tvLog.append(msg);
                ui.tvLog.append("\n");
            }
        });
    }
}
