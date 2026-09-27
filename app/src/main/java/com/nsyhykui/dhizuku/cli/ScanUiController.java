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
 */
public class ScanUiController {

    private final Activity activity;
    private final UiBuilder ui;
    private final ScanSettings settings;
    private final DhizukuDpm dpmHelper;
    private final LogCallback log;

    public ScanUiController(Activity activity, UiBuilder ui,
                            ScanSettings settings, DhizukuDpm dpmHelper,
                            LogCallback log) {
        this.activity = activity;
        this.ui = ui;
        this.settings = settings;
        this.dpmHelper = dpmHelper;
        this.log = log;
    }

    /**
     * onCreate 里调用一次：恢复设置到 UI，绑定监听。
     */
    public void init() {
        loadTtlToUi();
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
    }

    /**
     * onResume 里调用：刷新"上次更新"。
     */
    public void onResume() {
        refreshLastUpdate();
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

    private void saveTtlFromUi() {
        double value;
        try {
            value = Double.parseDouble(ui.etTtl.getText().toString().trim());
        } catch (Exception e) {
            return;
        }
        if (value < 0) value = 0;

        int unitIdx = ui.spTtlUnit.getSelectedItemPosition();
        double seconds;
        switch (unitIdx) {
            case 0: seconds = value; break;
            case 1: seconds = value * 60.0; break;
            case 2: seconds = value * 3600.0; break;
            default: seconds = value * 86400.0; break;
        }
        settings.setTtlSeconds(seconds);
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
