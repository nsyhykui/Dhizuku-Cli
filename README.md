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
- pm-style commands: list packages, query and manage app permissions
- Status queries: hidden apps, suspended apps, uninstall-blocked apps
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

### Commands

Operation commands:

| Command | Description |
|---------|-------------|
| ping | Test connection |
| lock_now | Lock the screen |
| hide / unhide | Hide / unhide an app |
| suspend / resume | Suspend / resume an app |
| block_uninstall / unblock_uninstall | Block / allow uninstall |

Query commands:

| Command | Description |
|---------|-------------|
| list hidden | List hidden apps |
| list suspended | List suspended apps |
| list blocked | List apps with uninstall blocked |
| pm list packages [options] | List packages (same as pm list packages) |
| pm list permissions <perm> | List apps with this permission |
| pm list permissions --package <pkg> | List all permissions of an app |
| pm list permissions <perm> --package <pkg> | Query one app's permission state |
| cache update | Rescan all apps and update cache |
| status | Show server running status (client-side) |

Permission management commands:

| Command | Description |
|---------|-------------|
| pm grant <pkg> <perm> | Grant a runtime permission |
| pm revoke <pkg> <perm> | Revoke a runtime permission |
| pm reset <pkg> <perm> | Reset a permission to default |

pm list packages supports the same options as Android's pm list packages:
-f -d -e -s -3 -i -u -U --uid, plus a package name filter. The only
exception is --user, which is not supported.

Missing a command you need? Open an issue:
https://github.com/nsyhykui/Dhizuku-Cli/issues

### Scan Cache Rules

The app caches app scan results (permissions, hidden / suspended /
uninstall-blocked states) in a local database. Query commands read
from this cache.

Three cache modes:

- Always: rescan on every query
- Auto: rescan when cache expires (TTL)
- Manual: only rescan when triggered manually

Configure them under "Update app scan cache rules" in the app:

- Cache mode: Always / Auto / Manual
- TTL: time-to-live for Auto mode (seconds / minutes / hours / days)
- Last update: timestamp of the most recent scan
- Update cache now: trigger a manual rescan

When hide / unhide / suspend / resume / block_uninstall /
unblock_uninstall / pm grant / revoke / reset succeed, the cache is
updated immediately. You do not need to run cache update manually.

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

#### v3.0.1

- Disabled allowBackup to prevent the TOTP key from being included in system backups

#### v3.0.0

- Breaking change: command structure and output protocol changed
- Added pm-style commands (pm list packages / pm list permissions / pm grant / pm revoke / pm reset)
- Added list hidden / list suspended / list blocked
- Added cache update
- Cache is now updated immediately after hide / suspend / block_uninstall / pm grant / revoke / reset
- Data commands no longer prefix output with Success
- Server version is read dynamically from the APK
- Removed: status hid / status suspend / status block_uninstall / status permission xxx
  (replaced by list hidden / list suspended / list blocked / pm list permissions xxx / cache update)

#### v2.1.0

- Added status commands (superseded by v3.0.0)
- Added scan cache with Always / Auto / Manual modes
- Added app-side "Update app scan cache rules" settings
- Server version is now read dynamically from APK
- Fixed status hid not listing hidden apps

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

### Acknowledgements

This project depends on the following open-source projects:

- [Dhizuku-API](https://github.com/iamr0s/Dhizuku-API) (MIT) — provides the Device Owner API
- [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) (Apache 2.0) — bypasses Android hidden API restrictions
- [AndroidX Core](https://developer.android.com/jetpack/androidx/releases/core) (Apache 2.0) — provides BundleCompat and other compat utilities

Reference implementations:

- [OwnDroid](https://github.com/BinTianqi/OwnDroid) (GPL-3.0) — the DevicePolicyManager binder wrapping approach is based on this project

Development tools:

- [CodeAssist](https://github.com/tyron12233/CodeAssist) (GPL-3.0) — IDE used for on-device development

Other:

- [Dhizuku](https://github.com/iamr0s/Dhizuku) (GPL-3.0) — the host environment this project serves

Thanks to all authors and community contributors.

### Note

The code of this project is primarily AI-assisted; the author is responsible for design, debugging and testing.

This project is for learning and personal research only. The author makes no warranty regarding the completeness, security or fitness of the code.

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
- pm 风格命令：列应用、查询和管理应用权限
- 状态查询：隐藏应用、挂起应用、阻止卸载
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

### 命令列表

操作类命令：

| 命令 | 说明 |
|------|------|
| ping | 测试连接 |
| lock_now | 立即锁屏 |
| hide / unhide | 隐藏 / 取消隐藏应用 |
| suspend / resume | 挂起 / 恢复应用 |
| block_uninstall / unblock_uninstall | 阻止 / 允许卸载 |

查询类命令：

| 命令 | 说明 |
|------|------|
| list hidden | 列出被隐藏的应用 |
| list suspended | 列出被挂起的应用 |
| list blocked | 列出阻止卸载的应用 |
| pm list packages [参数] | 列出应用（同 pm list packages） |
| pm list permissions <权限> | 列出拥有该权限的应用 |
| pm list permissions --package <包名> | 列出该应用的所有权限 |
| pm list permissions <权限> --package <包名> | 查询某应用某权限状态 |
| cache update | 重新扫描所有应用并更新缓存 |
| status | 显示服务端运行状态（客户端本地处理） |

权限管理命令：

| 命令 | 说明 |
|------|------|
| pm grant <包名> <权限> | 授予运行时权限 |
| pm revoke <包名> <权限> | 拒绝运行时权限 |
| pm reset <包名> <权限> | 恢复权限到默认状态 |

pm list packages 的参数和 Android 自带的 pm list packages 一致：
-f -d -e -s -3 -i -u -U --uid，另加包名过滤。唯一不支持的是 --user。

缺少你需要的功能？欢迎提 Issue：
https://github.com/nsyhykui/Dhizuku-Cli/issues

### 扫描缓存规则

App 把应用扫描结果（权限、隐藏 / 挂起 / 阻止卸载状态）缓存在
本地数据库，供查询类命令读取。

三种缓存模式：

- 每次：每次查询都重新扫描
- 自动：缓存过期（TTL）时重新扫描
- 手动：只有手动触发才重新扫描

在主界面的"更新应用扫描缓存规则"里配置：

- 更新缓存模式：每次 / 自动 / 手动
- TTL：自动模式的缓存有效期（秒 / 分钟 / 小时 / 天）
- 上次更新：最近一次扫描的时间
- 立即更新缓存：手动触发一次扫描

hide / unhide / suspend / resume / block_uninstall /
unblock_uninstall / pm grant / revoke / reset 成功后，缓存会立即
更新，不需要手动跑 cache update。

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

#### v3.0.1

- 关闭 allowBackup，避免 TOTP 密钥被系统备份

#### v3.0.0

- 破坏性更新：命令结构和输出协议都变了
- 新增 pm 风格命令（pm list packages / pm list permissions / pm grant / pm revoke / pm reset）
- 新增 list hidden / list suspended / list blocked
- 新增 cache update
- hide / suspend / block_uninstall / pm grant / revoke / reset 成功后立即更新缓存
- 有数据的命令不再带 Success 前缀
- 服务端版本号从 APK 动态读取
- 删除：status hid / status suspend / status block_uninstall / status permission xxx
  （分别由 list hidden / list suspended / list blocked / pm list permissions xxx / cache update 替代）

#### v2.1.0

- 新增 status 命令（v3.0.0 中被替代）
- 新增扫描缓存，支持 每次 / 自动 / 手动 三种模式
- 新增 App 侧"更新应用扫描缓存规则"设置
- 服务端版本号改为动态读取 APK
- 修复 status hid 不显示被隐藏应用的问题

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

### 致谢

本项目依赖以下开源项目：

- [Dhizuku-API](https://github.com/iamr0s/Dhizuku-API)（MIT）—— 提供 Device Owner 权限调用接口
- [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)（Apache 2.0）—— 绕过 Android 隐藏 API 限制
- [AndroidX Core](https://developer.android.com/jetpack/androidx/releases/core)（Apache 2.0）—— 提供 BundleCompat 等兼容性工具

参考了以下项目的实现：

- [OwnDroid](https://github.com/BinTianqi/OwnDroid)（GPL-3.0）—— DevicePolicyManager 的 binder 包装方案参考自此项目

使用以下项目进行开发：

- [CodeAssist](https://github.com/tyron12233/CodeAssist)（GPL-3.0）—— 本项目在手机端开发所用的 IDE

其他：

- [Dhizuku](https://github.com/iamr0s/Dhizuku)（GPL-3.0）—— 本项目所服务的宿主环境

感谢以上项目的作者和社区贡献者。

### 说明

本项目代码由 AI 辅助生成，作者负责设计、调试与测试。

项目仅供学习与个人研究使用，作者不对代码的完整性、安全性、适用性作任何担保。

---

## License

GPL-3.0. See LICENSE for details.

Copyright (C) 2026 nsyhykui
