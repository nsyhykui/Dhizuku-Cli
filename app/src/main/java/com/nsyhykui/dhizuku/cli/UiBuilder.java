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
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public class UiBuilder {

    public static final String[] BIND_VALUES = {"127.0.0.1", "0.0.0.0"};

    public final Button btnCheck;
    public final Button btnStart;
    public final Button btnStop;
    public final Button btnRandom;
    public final Button btnUidManager;
    public final EditText etPort;
    public final EditText etKey;
    public final Spinner spBind;
    public final TextView tvStatus;
    public final TextView tvLog;

    public UiBuilder(Activity a,
                     View.OnClickListener startListener,
                     View.OnClickListener stopListener,
                     View.OnClickListener checkListener,
                     View.OnClickListener randomListener,
                     View.OnClickListener uidManagerListener) {
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        btnCheck = new Button(a);
        btnCheck.setText(R.string.btn_check);
        btnCheck.setOnClickListener(checkListener);

        btnUidManager = new Button(a);
        btnUidManager.setText(R.string.btn_uid_manager);
        btnUidManager.setOnClickListener(uidManagerListener);

        // 端口行
        LinearLayout portRow = new LinearLayout(a);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setPadding(0, 20, 0, 0);
        TextView tvPortLabel = new TextView(a);
        tvPortLabel.setText(R.string.label_port);
        tvPortLabel.setTextSize(15f);
        tvPortLabel.setPadding(0, 20, 20, 0);
        etPort = new EditText(a);
        etPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        etPort.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        portRow.addView(tvPortLabel);
        portRow.addView(etPort);

        // 监听行
        LinearLayout bindRow = new LinearLayout(a);
        bindRow.setOrientation(LinearLayout.HORIZONTAL);
        bindRow.setPadding(0, 10, 0, 0);
        TextView tvBindLabel = new TextView(a);
        tvBindLabel.setText(R.string.label_bind);
        tvBindLabel.setTextSize(15f);
        tvBindLabel.setPadding(0, 20, 20, 0);
        spBind = new Spinner(a);
        String[] labels = {
                a.getString(R.string.bind_localhost),
                a.getString(R.string.bind_lan)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                a, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBind.setAdapter(adapter);
        bindRow.addView(tvBindLabel);
        bindRow.addView(spBind);

        // 本机 IP
        TextView tvLocalIp = new TextView(a);
        tvLocalIp.setTextSize(13f);
        tvLocalIp.setPadding(0, 10, 0, 10);
        tvLocalIp.setText(a.getString(R.string.label_local_ip, IpUtils.getLocalIp()));

        // 密钥行
        LinearLayout keyRow = new LinearLayout(a);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setPadding(0, 10, 0, 0);
        TextView tvKeyLabel = new TextView(a);
        tvKeyLabel.setText(R.string.label_key);
        tvKeyLabel.setTextSize(15f);
        tvKeyLabel.setPadding(0, 20, 20, 0);
        etKey = new EditText(a);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT);
        etKey.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnRandom = new Button(a);
        btnRandom.setText(R.string.btn_random);
        btnRandom.setOnClickListener(randomListener);
        keyRow.addView(tvKeyLabel);
        keyRow.addView(etKey);
        keyRow.addView(btnRandom);

        btnStart = new Button(a);
        btnStart.setText(R.string.btn_start);
        btnStart.setOnClickListener(startListener);

        btnStop = new Button(a);
        btnStop.setText(R.string.btn_stop);
        btnStop.setOnClickListener(stopListener);

        tvStatus = new TextView(a);
        tvStatus.setTextSize(15f);
        tvStatus.setText(R.string.status_ready);

        tvLog = new TextView(a);
        tvLog.setTextSize(12f);

        layout.addView(btnCheck);
        layout.addView(btnUidManager);
        layout.addView(portRow);
        layout.addView(bindRow);
        layout.addView(tvLocalIp);
        layout.addView(keyRow);
        layout.addView(btnStart);
        layout.addView(btnStop);
        layout.addView(tvStatus);
        layout.addView(tvLog);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(layout);
        a.setContentView(scroll);
    }
}