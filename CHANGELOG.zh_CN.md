# 更新日志

本文件记录本项目所有重要变更。

## v3.1.0

- 并行扫描，线程数可配置（1-8，默认 4）
- 新增"保存全部设置"按钮（端口、密钥、绑定、TTL、线程数）
- cache update 在多核设备上速度显著提升

## v3.0.3

- unhide / resume / unblock_uninstall 在包不存在时返回"未安装"，不再误报 Success

## v3.0.2

- 修复：对被隐藏的应用执行 hide / suspend / block_uninstall 时误报"未安装"
- 修复：pm grant / revoke / reset 在失败时误报 Success，非运行时权限可能卡约 20 秒
- pm grant / revoke / reset 现在会提前拒绝非运行时权限

## v3.0.1

- 关闭 allowBackup，避免 TOTP 密钥被系统备份

## v3.0.0

- 破坏性更新：命令结构和输出协议都变了
- 新增 pm 风格命令（pm list packages / pm list permissions / pm grant / pm revoke / pm reset）
- 新增 list hidden / list suspended / list blocked
- 新增 cache update
- hide / suspend / block_uninstall / pm grant / revoke / reset 成功后立即更新缓存
- 有数据的命令不再带 Success 前缀
- 服务端版本号从 APK 动态读取
- 删除：status hid / status suspend / status block_uninstall / status permission xxx
  （分别由 list hidden / list suspended / list blocked / pm list permissions xxx / cache update 替代）

## v2.1.0

- 新增 status 命令（v3.0.0 中被替代）
- 新增扫描缓存，支持 每次 / 自动 / 手动 三种模式
- 新增 App 侧"更新应用扫描缓存规则"设置
- 服务端版本号改为动态读取 APK
- 修复 status hid 不显示被隐藏应用的问题

## v2.0.0

- 新增 AES-GCM 加密（所有消息加密）
- 新增 UID 授权，带悬浮窗弹窗
- 新增授权管理界面（授权 / 撤销）
- Python 与 C 客户端拆分为独立仓库
- 代码拆分为多个模块（DhizukuDpm、Totp、AesGcm、AuthManager 等）

## v1.1.0

- 新增 suspend / resume / block_uninstall / unblock_uninstall
- 新增中英文双语
- 新增命令参数校验

## v1.0.0

- 首个版本
- 命令：ping、lock_now、hide、unhide
