# 命令列表

## 操作类命令

| 命令 | 说明 |
|------|------|
| ping | 测试连接 |
| lock_now | 立即锁屏 |
| hide / unhide | 隐藏 / 取消隐藏应用 |
| suspend / resume | 挂起 / 恢复应用 |
| block_uninstall / unblock_uninstall | 阻止 / 允许卸载 |

## 查询类命令

| 命令 | 说明 |
|------|------|
| list hidden | 列出被隐藏的应用 |
| list suspended | 列出被挂起的应用 |
| list blocked | 列出阻止卸载的应用 |
| pm list packages [参数] | 列出应用（同 pm list packages） |
| pm list permissions <权限> | 列出拥有该权限的应用 |
| pm list permissions --package <包名> | 列出该应用的所有权限 |
| pm list permissions <权限> --package <包名> | 查询某应用某权限状态 |
| cache update | 重新扫描所有应用并更新缓存（并行，线程数可在 App 内配置） |
| status | 显示服务端运行状态（客户端本地处理） |

## 权限管理命令

| 命令 | 说明 |
|------|------|
| pm grant <包名> <权限> | 授予运行时权限 |
| pm revoke <包名> <权限> | 拒绝运行时权限 |
| pm reset <包名> <权限> | 恢复权限到默认状态 |

## pm list packages 参数

pm list packages 的参数和 Android 自带的 pm list packages 一致：

    -f -d -e -s -3 -i -u -U --uid

另加包名过滤。唯一不支持的是 --user。

## 缺少你需要的功能？

欢迎提 Issue：
https://github.com/nsyhykui/Dhizuku-Cli/issues
