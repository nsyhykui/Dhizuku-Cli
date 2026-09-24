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
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class UidManagerActivity extends Activity {

    private AuthManager auth;
    private LinearLayout listLayout;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = AuthManager.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50, 50, 50, 50);

        tvEmpty = new TextView(this);
        tvEmpty.setTextSize(15f);
        tvEmpty.setPadding(0, 20, 0, 20);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        root.addView(tvEmpty);
        root.addView(listLayout);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        listLayout.removeAllViews();

        Set<Integer> seen = auth.getSeenUids();
        Set<Integer> allowed = auth.getAllowedUids();

        if (seen.isEmpty()) {
            tvEmpty.setText(R.string.uid_empty);
            return;
        }
        tvEmpty.setText("");

        List<Integer> sorted = new ArrayList<>(seen);
        java.util.Collections.sort(sorted);

        for (Integer uid : sorted) {
            listLayout.addView(buildRow(uid, allowed.contains(uid)));
        }
    }

    private View buildRow(final int uid, boolean granted) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 20, 0, 20);

        TextView tv = new TextView(this);
        tv.setTextSize(14f);
        tv.setText(getString(R.string.uid_row, uid, uidToPackage(uid)));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btn = new Button(this);
        if (granted) {
            btn.setText(R.string.uid_revoke);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    auth.revoke(uid);
                    refresh();
                }
            });
        } else {
            btn.setText(R.string.uid_grant);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    auth.grant(uid);
                    refresh();
                }
            });
        }

        row.addView(tv);
        row.addView(btn);
        return row;
    }

    private String uidToPackage(int uid) {
        try {
            String[] pkgs = getPackageManager().getPackagesForUid(uid);
            if (pkgs == null || pkgs.length == 0) return getString(R.string.uid_unknown);
            if (pkgs.length == 1) return pkgs[0];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pkgs.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(pkgs[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            return getString(R.string.uid_unknown);
        }
    }
}