# dhizuku-cli

Dhizuku Device Owner command-line interface.

Android server app that exposes Device Owner commands over TCP,
with TOTP authentication and AES-GCM encryption.

---

## English

### About

dhizuku-cli is the Android server app for the dhizuku-cli project.

It runs on your device, holds Dhizuku authorization, and listens
on a local TCP port. When a client connects, it decrypts the
message, verifies the TOTP code, checks the client UID against
an authorization list, then executes Device Owner commands via
Dhizuku.

Clients for other platforms live in separate repositories:

- Python client: https://github.com/nsyhykui/dhizuku_cli_python
  (also on PyPI as dhizuku-cli)
- C client: https://github.com/nsyhykui/dhizuku_cli_c

### Features

- Fills the Device Owner command-line gap (no other public DO CLI exists)
- TOTP + AES-GCM: messages are encrypted, the key never travels over the network
- UID authorization: first-time clients trigger an on-screen prompt
- Overlay dialog works in the background (HarmonyOS blocks Activity-based prompts)
- Local and LAN listening modes
- Foreground service with battery optimization exemption

### Requirements

- Android 9+
- Dhizuku installed and activated as Device Owner
- Overlay permission (Display over other apps) for the authorization dialog

### Installation

All releases are on the Releases page.

1. Download and install the latest APK.
2. Open the app. It will ask for overlay permission on first launch.
3. Grant it in system settings.

### App Configuration

1. Grant overlay permission when prompted
2. Tap Check Dhizuku to confirm activation
3. Set port (default 12345)
4. Choose bind address:
   - Localhost: only local clients can connect
   - LAN: devices on the same network can connect
5. A key is generated automatically; tap Random to regenerate
6. Tap Start TCP Service; a persistent notification will appear

### Authorization

When a client connects for the first time, the app shows an
overlay dialog on the current screen:

- UID and package name of the client
- Allow / Deny buttons

If allowed, the UID is added to a permanent whitelist and future
commands from that client run without prompting.

To manage authorized clients, tap Manage Authorizations on the
main screen. You can revoke or re-grant any UID from the list.

### Security

- The TOTP key is the only credential. Keep it safe.
- All messages are encrypted with AES-GCM (key derived from the
  TOTP key via HKDF-SHA256).
- The TOTP code itself is encrypted, so packet sniffing cannot
  capture and replay it.
- Do not enable LAN mode on untrusted networks.

### Risk Notice

This tool borrows Device Owner privileges via Dhizuku and can
perform system-level operations.

- All commands run as Device Owner
- Misuse may cause abnormal system behavior or require a factory reset
- Use only on devices you own and control
- The author is not liable for any loss caused by this tool

### Changelog

#### v2.0.0

- Added AES-GCM encryption (all messages encrypted)
- Added UID authorization with overlay dialog
- Added authorization management UI (grant / revoke)
- Split Python and C clients into separate repositories
- Code refactored into modules (DhizukuDpm, Totp, AesGcm, AuthManager, etc.)

#### v1.1.0

- Added suspend / resume / block_uninstall / unblock_uninstall
- Added English / Chinese i18n
- Added command argument validation

#### v1.0.0

- First release
- Commands: ping, lock_now, hide, unhide

---

## 简体中文

### 简介

dhizuku-cli 是 dhizuku-cli 项目的 Android 服务端 App。

它运行在你的设备上，持有 Dhizuku 授权，在本机监听 TCP 端口。
客户端连接后，它会解密消息、校验 TOTP、检查 UID 是否在授权
列表中，然后通过 Dhizuku 执行 Device Owner 命令。

其他平台的客户端在独立仓库中：

- Python 客户端：https://github.com/nsyhykui/dhizuku_cli_python
  （PyPI 上叫 dhizuku-cli）
- C 客户端：https://github.com/nsyhykui/dhizuku_cli_c

### 特性

- 填补 Device Owner 的命令行空白（目前没有其他公开的 DO CLI）
- TOTP + AES-GCM：消息全程加密，密钥不上网
- UID 授权：首次连接的客户端会触发屏幕授权弹窗
- 悬浮窗授权：后台也能弹窗（华为禁止 Activity 方式的后台弹窗）
- 本机和局域网两种监听模式
- 前台服务 + 电池优化豁免

### 前提条件

- Android 9+
- 已安装 Dhizuku 并激活为 Device Owner
- 悬浮窗权限（显示在其他应用上层）——用于显示授权对话框

### 安装

所有发布版本见 Releases 页面。

1. 下载并安装最新版 APK。
2. 打开 App，首次启动会请求悬浮窗权限。
3. 在系统设置里授予该权限。

### App 侧配置

1. 首次启动授予悬浮窗权限
2. 点检测 Dhizuku，确认已激活
3. 设置端口（默认 12345）
4. 选择监听地址：
   - 仅本机：只允许本机客户端连接
   - 局域网：允许同网络设备连接
5. 密钥自动生成，可点随机重新生成
6. 点启动 TCP 服务，状态栏出现常驻通知即成功

### 授权

客户端首次连接时，App 会在当前屏幕上显示一个悬浮窗：

- 客户端的 UID 和包名
- 允许 / 拒绝 按钮

允许后，UID 加入永久白名单，之后该客户端的命令直接执行，
不再弹窗。

要管理已授权的客户端，在主界面点授权管理。列表中每个 UID
都可以撤销或重新授权。

### 安全说明

- TOTP 密钥是唯一的认证凭据，请妥善保管。
- 所有消息都用 AES-GCM 加密（密钥由 TOTP 密钥通过
  HKDF-SHA256 派生）。
- TOTP 验证码本身也被加密，抓包无法直接捕获并重放。
- 不要在不可信网络上开启局域网模式。

### 风险声明

本工具通过 Dhizuku 借用 Device Owner 权限，可以执行系统级
操作。

- 所有命令以 Device Owner 身份执行
- 误用可能导致系统行为异常，甚至需要恢复出厂设置
- 仅在自己拥有并完全控制的设备上使用
- 作者不对因使用本工具导致的任何损失负责

### 更新日志

#### v2.0.0

- 新增 AES-GCM 加密（所有消息加密）
- 新增 UID 授权，带悬浮窗弹窗
- 新增授权管理界面（授权 / 撤销）
- Python 与 C 客户端拆分为独立仓库
- 代码拆分为多个模块（DhizukuDpm、Totp、AesGcm、AuthManager 等）

#### v1.1.0

- 新增 suspend / resume / block_uninstall / unblock_uninstall
- 新增中英文双语
- 新增命令参数校验

#### v1.0.0

- 首个版本
- 命令：ping、lock_now、hide、unhide

---

## License

GPL-3.0. See LICENSE for details.

Copyright (C) 2026 nsyhykui
