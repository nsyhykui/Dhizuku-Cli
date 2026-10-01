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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 管理"更新应用扫描缓存规则"区块的所有交互。
 * 也负责保存主界面的全部设置。
 */
public class ScanUiController {

    private final Activity activity;
    private final UiBuilder ui;
    private final ScanSettings settings;
    private final PrefsHelper prefs;
    private final DhizukuDpm dpmHelper;
    private final LogCallback log;

    public ScanUiController(Activity activity, UiBuilder ui,
                            ScanSettings settings, DhizukuDpm dpmHelper,
                            LogCallback log) {
        this.activity = activity;
        this.ui = ui;
        this.settings = settings;
        this.prefs = new PrefsHelper(activity);
        this.dpmHelper = dpmHelper;
        this.log = log;
    }

    public void init() {
        loadTtlToUi();
        loadThreadsToUi();
        refreshModeButton();
        refreshLastUpdate();

        ui.spTtlUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveTtlFromUi();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ui.btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveClicked();
            }
        });
    }

    public void onResume() {
        refreshLastUpdate();
    }

    /* ================= 保存全部设置 ================= */

    private void onSaveClicked() {
        /* 端口 */
        int port;
        try {
            port = Integer.parseInt(ui.etPort.getText().toString().trim());
        } catch (Exception e) {
            log.onLog(activity.getString(R.string.log_port_format_error));
            return;
        }
        if (port < 1 || port > 65535) {
            log.onLog(activity.getString(R.string.log_port_range));
            return;
        }

        /* 密钥 */
        String key = ui.etKey.getText().toString().trim();
        if (key.length() < 8) {
            log.onLog(activity.getString(R.string.log_key_too_short));
            return;
        }

        /* 绑定地址 */
        int sel = ui.spBind.getSelectedItemPosition();
        if (sel < 0 || sel >= UiBuilder.BIND_VALUES.length) sel = 0;
        String bindAddr = UiBuilder.BIND_VALUES[sel];

        /* TTL 和线程数 */
        double ttlSeconds = parseTtlSeconds();
        if (ttlSeconds < 0) return;
        int threads = parseThreads();
        if (threads < 0) return;

        /* 写入 */
        prefs.setPort(port);
        prefs.setKey(key);
        prefs.setBind(bindAddr);
        settings.setTtlSeconds(ttlSeconds);
        settings.setThreads(threads);

        log.onLog(activity.getString(R.string.scan_settings_saved));
    }

    /* ================= 模式 ================= */

    public void onModeClicked() {
        final String[] modes = {
                ScanSettings.MODE_ALWAYS,
                ScanSettings.MODE_AUTO,
                ScanSettings.MODE_MANUAL
        };
        String[] labels = {
                activity.getString(R.string.scan_mode_always),
                activity.getString(R.string.scan_mode_auto),
                activity.getString(R.string.scan_mode_manual)
        };

        new AlertDialog.Builder(activity)
                .setTitle(R.string.scan_mode_label)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        saveTtlFromUi();
                        saveThreadsFromUi();
                        settings.setMode(modes[which]);
                        refreshModeButton();
                        refreshLastUpdate();
                    }
                })
                .show();
    }

    private void refreshModeButton() {
        String mode = settings.getMode();
        ui.btnMode.setText(modeText(mode));
        ui.ttlRow.setVisibility(
                ScanSettings.MODE_AUTO.equals(mode) ? View.VISIBLE : View.GONE);
    }

    private String modeText(String mode) {
        if (ScanSettings.MODE_ALWAYS.equals(mode)) {
            return activity.getString(R.string.scan_mode_always);
        }
        if (ScanSettings.MODE_MANUAL.equals(mode)) {
            return activity.getString(R.string.scan_mode_manual);
        }
        return activity.getString(R.string.scan_mode_auto);
    }

    /* ================= TTL ================= */

    private void loadTtlToUi() {
        double seconds = settings.getTtlSeconds();
        double value;
        int unitIdx;
        if (seconds < 60) { value = seconds; unitIdx = 0; }
        else if (seconds < 3600) { value = seconds / 60.0; unitIdx = 1; }
        else if (seconds < 86400) { value = seconds / 3600.0; unitIdx = 2; }
        else { value = seconds / 86400.0; unitIdx = 3; }

        ui.etTtl.setText(formatDouble(value));
        ui.spTtlUnit.setSelection(unitIdx);
    }

    /** 从 UI 解析 TTL，返回秒数；失败返回 -1 且不写盘 */
    private double parseTtlSeconds() {
        double value;
        try {
            value = Double.parseDouble(ui.etTtl.getText().toString().trim());
        } catch (Exception e) {
            return -1;
        }
        if (value < 0) value = 0;

        int unitIdx = ui.spTtlUnit.getSelectedItemPosition();
        switch (unitIdx) {
            case 0: return value;
            case 1: return value * 60.0;
            case 2: return value * 3600.0;
            default: return value * 86400.0;
        }
    }

    private void saveTtlFromUi() {
        double seconds = parseTtlSeconds();
        if (seconds < 0) return;
        settings.setTtlSeconds(seconds);
    }

    /* ================= 线程数 ================= */

    private void loadThreadsToUi() {
        ui.etThreads.setText(String.valueOf(settings.getThreads()));
    }

    /** 从 UI 解析线程数；失败返回 -1 */
    private int parseThreads() {
        try {
            return Integer.parseInt(ui.etThreads.getText().toString().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private void saveThreadsFromUi() {
        int n = parseThreads();
        if (n < 0) return;
        settings.setThreads(n);
    }

    private String formatDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /* ================= 上次更新 ================= */

    public void refreshLastUpdate() {
        String mode = settings.getMode();
        if (ScanSettings.MODE_ALWAYS.equals(mode)) {
            ui.tvLastUpdate.setText(activity.getString(R.string.scan_last_update_label)
                    + " " + activity.getString(R.string.scan_last_update_na));
            return;
        }

        long ts = ScanCache.get(activity).getLastScanTime();
        if (ts == 0) {
            ui.tvLastUpdate.setText(activity.getString(R.string.scan_last_update_label)
                    + " " + activity.getString(R.string.scan_last_update_never));
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            String s = fmt.format(new Date(ts));
            ui.tvLastUpdate.setText(
                    activity.getString(R.string.scan_last_update_label) + " " + s);
        }
    }

    /* ================= 立即更新 ================= */

    public void onUpdateCacheClicked() {
        saveTtlFromUi();
        saveThreadsFromUi();
        ui.btnUpdateCache.setEnabled(false);
        ui.btnUpdateCache.setText(R.string.scan_updating);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PermissionScanner scanner = new PermissionScanner(activity, dpmHelper);
                    final int count = scanner.scanAndCache();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            log.onLog(activity.getString(R.string.scan_done, count));
                            ui.btnUpdateCache.setEnabled(true);
                            ui.btnUpdateCache.setText(R.string.scan_update_button);
                            refreshLastUpdate();
                        }
                    });
                } catch (final Throwable t) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            log.onLog(activity.getString(
                                    R.string.scan_failed, t.getMessage()));
                            ui.btnUpdateCache.setEnabled(true);
                            ui.btnUpdateCache.setText(R.string.scan_update_button);
                        }
                    });
                }
            }
        }).start();
    }
}
