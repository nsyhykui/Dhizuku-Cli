# dhizuku-cli

一个为Dhizuku设计的命令行应用

## 简介

Dhizuku CLI 是一个通过 TCP 执行 Dhizuku Device Owner 命令的工具。

项目由两部分组成：

- **服务端**：Android App，持有 Dhizuku 授权，在本机监听 TCP 端口
- **客户端 dcli**：命令行工具，通过 TCP 向服务端发送命令

服务端收到命令后，以 Device Owner 身份调用系统 API 执行操作。

## 特性

- 填补 Device Owner 的命令行空白——目前没有其他公开的 DO 命令行工具
- 通过 TOTP 认证，密钥从不上网传输
- 不依赖 Root，只需 Dhizuku 已激活为 Device Owner
- 支持本机和局域网两种连接模式

## 支持平台

- 服务端：所有 Android 架构
- 客户端(二进制文件)：Android(aarch64)
- 客户端(Python)：所有支持Python的平台

## 前提条件

- 已安装Dhizuku并正确激活
- 已安装至少一个终端应用

## 安装

所有发布版本见 [Releases](https://github.com/nsyhykui/dhizuku-cli/releases)。

### 服务端（Android App）

从 [Releases](https://github.com/nsyhykui/dhizuku-cli/releases) 下载最新版 APK，安装后打开。

### 客户端（dcli）

从 [Releases](https://github.com/nsyhykui/dhizuku-cli/releases) 下载对应平台的客户端。

- **二进制版**：下载对应平台的 `dcli` 文件，赋执行权限后即可使用。
  ```bash
  chmod +x dcli
  ./dcli help
  ```

- **Python 版**：下载 dcli.py，需要 Python 3.6+。
  ```bash
  python3 dcli.py help
  ```

## 使用

### App 侧配置

1. 请手动为应用设置后台运行
2. 打开 App，点「检测 Dhizuku」确认已激活
3. 设置端口（默认 12345）
4. 选择监听地址：
   - **仅本机**：只允许本机客户端连接
   - **局域网**：允许同网络设备连接
5. 密钥已自动生成，可点「随机」重新生成
6. 点「启动 TCP 服务」，状态栏出现常驻通知即成功

### 客户端配置

把 App 上显示的密钥写到客户端配置：

```bash
echo "<App 里的密钥>" > ~/.dcli_key
```

如果要用局域网连接，把服务端 IP 也写进去：

```bash
echo "192.168.1.100" > ~/.dcli_host
```

或者每次命令行指定：

```bash
dcli --host 192.168.1.100 ping
```

### 命令示例

```bash
dcli ping                    # 测试连接
dcli lock_now                # 锁屏
dcli hide com.example.app    # 隐藏应用
dcli unhide com.example.app  # 取消隐藏
```

### 连接优先级

客户端解析服务端地址的顺序：

1. --host / -H 参数
2. 环境变量 DCLI_HOST
3. 文件 ~/.dcli_host
4. 默认 127.0.0.1


## 命令列表

| 命令 | 参数 | 说明 |
|------|------|------|
| `ping` | 无 | 测试客户端与服务端是否连通 |
| `lock_now` | 无 | 立即锁定屏幕 |
| `hide` | `<包名>` | 隐藏指定应用（应用仍存在，但用户不可见） |
| `unhide` | `<包名>` | 恢复被隐藏的应用 |

### 命令详情

#### ping

测试连接。

```bash
dcli ping
```

返回 Success 表示服务端正常响应。

#### lock_now

立即锁屏，等效于按电源键。

```bash
dcli lock_now
```

#### hide

隐藏指定应用。被隐藏的应用不会出现在桌面和应用列表中，但数据保留。

```bash
dcli hide com.example.app
```

#### unhide

恢复被 hide 隐藏的应用。

```bash
dcli unhide com.example.app
```

## 协议

客户端与服务端通过 TCP 通信，端口由 App 端配置（默认 12345）。

## 安全说明

### 密钥

- TOTP 密钥是唯一的认证凭据，**泄露即等于设备被控**
- 密钥仅在 App 界面和客户端本地存储，不经过网络传输
- 不要截图或分享密钥，不要写进公开脚本
- 怀疑泄露时，在 App 里点「随机」重新生成，并更新客户端配置

### 连接模式

- 默认「仅本机」模式，只有本机进程能连接
- 「局域网」模式下，同一网络内的任何设备都能尝试连接
- **不要在公共 WiFi 或不可信网络下开启局域网模式**
- 不用时及时停止服务

### 命令

- 所有命令均以 Device Owner 身份执行，权限极高
- 执行前确认目标包名正确，特别是 `hide` 类操作
- 建议先用 `ping` 验证连接，再执行实际命令

### 设备

- 仅在自己拥有并控制的设备上使用
- 不要在他人设备上安装或运行
- 定期检查 App 内的已授权状态和密钥

### 其他

- 为了安全起见，此应用不会封装危险命令

## 风险声明

本工具通过 Dhizuku 借用 Device Owner 权限，可以执行系统级操作。请在使用前充分理解以下风险。

### 高危操作

- 所有命令均以 Device Owner 身份执行，权限等同于设备管理员
- 误用可能导致应用无法访问、系统行为异常、甚至需要恢复出厂设置
- 隐藏系统关键应用可能导致系统无法正常启动

### 数据安全

- 本工具不提供数据备份或恢复功能
- 操作导致的数据丢失无法通过本工具找回
- 使用前请自行备份重要数据

### 兼容性

- 仅在部分 Android 版本和机型上测试
- HarmonyOS 等定制系统可能存在未知限制
- 系统更新可能导致工具失效

### 责任

- 本工具仅供学习与个人研究使用
- 请仅在自己拥有并完全控制的设备上使用
- 使用本工具产生的任何直接或间接后果，由使用者自行承担
- 作者不对因使用本工具导致的任何损失负责

## 致谢

本项目依赖以下开源项目：

- [Dhizuku-API](https://github.com/iamr0s/Dhizuku-API)（MIT）——提供 Device Owner 权限调用接口
- [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)（Apache 2.0）——绕过 Android 隐藏 API 限制
- [AndroidX Core](https://developer.android.com/jetpack/androidx/releases/core)（Apache 2.0）——提供 `BundleCompat` 等兼容性工具

参考了以下项目的实现：

- [OwnDroid](https://github.com/BinTianqi/OwnDroid)（GPL-3.0）——DevicePolicyManager 的 binder 包装方案参考自此项目

使用以下项目进行开发：

- [CodeAssist](https://github.com/tyron12233/CodeAssist)（GPL-3.0）——本项目在手机端开发所用的 IDE

感谢以上项目的作者和社区贡献者。

## 说明

本项目代码由 AI 辅助生成，作者负责设计、调试与测试。

项目仅供学习与个人研究使用，作者不对代码的完整性、安全性、适用性作任何担保。

## License

本项目采用 [GPL-3.0](LICENSE) 许可证发布。

Copyright (C) 2026 nsyhykui
