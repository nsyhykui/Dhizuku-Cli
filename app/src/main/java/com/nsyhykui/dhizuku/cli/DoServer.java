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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class DoServer {

    private final String bindAddr;
    private final int port;
    private final LogCallback callback;
    private final CommandHandler handler;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;

    public DoServer(LogCallback callback, int port, String bindAddr, Context context) {
        this.callback = callback;
        this.port = port;
        this.bindAddr = bindAddr;
        this.handler = new CommandHandler(context, port);
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public String getBindAddr() {
        return bindAddr;
    }

    public void start() {
        if (running) {
            callback.onLog("服务已在运行");
            return;
        }

        running = true;
        callback.onStatus("服务启动中...");

        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port, 10,
                            InetAddress.getByName(bindAddr));
                    callback.onStatus("监听 " + bindAddr + ":" + port);
                    callback.onLog("服务已启动");

                    while (running) {
                        try {
                            Socket client = serverSocket.accept();
                            callback.onLog("客户端接入: " + client.getRemoteSocketAddress());
                            handleClient(client);
                        } catch (Exception e) {
                            if (running) {
                                callback.onLog("连接异常: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    callback.onLog("服务启动失败: " + e.getMessage());
                    callback.onStatus("服务启动失败");
                    running = false;
                }
            }
        });
        serverThread.start();
    }

    private void handleClient(final Socket client) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), "UTF-8"));
                    OutputStream out = client.getOutputStream();

                    String line = reader.readLine();
                    callback.onLog("收到: " + line);

                    String response = handler.process(line);

                    out.write(response.getBytes("UTF-8"));
                    out.write('\n');
                    out.flush();
                    client.close();

                } catch (Exception e) {
                    callback.onLog("处理异常: " + e.getMessage());
                    try {
                        client.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }).start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
        serverSocket = null;
        serverThread = null;
        callback.onStatus("服务已停止");
        callback.onLog("服务已停止");
    }
}