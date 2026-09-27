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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanResult {
    public final Map<String, Map<String, String>> permissions = new HashMap<>();
    public final List<String> hid = new ArrayList<>();
    public final List<String> suspend = new ArrayList<>();
    public final List<String> block = new ArrayList<>();
    public int appCount = 0;

    /* 各 API 第一次失败时的错误信息（为 null 表示没失败过） */
    public String hidError = null;
    public String susError = null;
    public String blockError = null;
}
