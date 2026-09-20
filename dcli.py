#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
dcli - DO Server command-line client
"""

import sys
import os
import hmac
import hashlib
import struct
import time
import socket

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 12345
DEFAULT_PACKAGE = "com.termux"
KEY_FILE = ".dcli_key"
HOST_FILE = ".dcli_host"
PKG_FILE = ".dcli_package"

_LANG = os.environ.get("LANG", "en").lower()
_IS_ZH = _LANG.startswith("zh")

_S = {
    "en": {
        "usage": "Usage:",
        "opt_host": "  --host, -H <ip>      Server IP (default 127.0.0.1)",
        "opt_pkg": "  --package, -P <pkg>  Client package name (default com.termux)",
        "opt_end": "  --                   Stop option parsing",
        "available": "Available commands:",
        "remote": "Remote examples:",
        "or_env": "  or env:",
        "or_file": "  or file:",
        "examples": "Examples:",
        "unknown_cmd": "Unknown command: %s",
        "see_help": "Run 'dcli help' for all commands",
        "usage_short": "Usage:  %s",
        "desc": "Desc:   %s",
        "example": "Example: %s",
        "err_no_key": "Error: no key found",
        "err_key_hint1": "Copy the key from the App to ~/.dcli_key",
        "err_key_hint2": "or set env DCLI_KEY",
        "err_timeout": "Error: connect %s timeout",
        "err_refused": "Error: %s not listening or server not started",
        "err_connect": "Error: cannot connect to %s (%s)",
        "err_generic": "Error: %s",
        "err_missing_arg": "Error: %s requires an argument",
        "err_unknown_opt": "Error: unknown option: %s",
    },
    "zh": {
        "usage": "用法:",
        "opt_host": "  --host, -H <ip>      服务端 IP（默认 127.0.0.1）",
        "opt_pkg": "  --package, -P <包名> 客户端包名（默认 com.termux）",
        "opt_end": "  --                   停止解析后续选项",
        "available": "可用命令:",
        "remote": "远程连接示例:",
        "or_env": "  或环境变量:",
        "or_file": "  或写入文件:",
        "examples": "示例:",
        "unknown_cmd": "未知命令: %s",
        "see_help": "用 'dcli help' 查看所有命令",
        "usage_short": "用法:  %s",
        "desc": "说明:  %s",
        "example": "示例:  %s",
        "err_no_key": "错误: 未找到密钥",
        "err_key_hint1": "请从 App 复制密钥，写入 ~/.dcli_key",
        "err_key_hint2": "或设置环境变量 DCLI_KEY",
        "err_timeout": "错误: 连接 %s 超时",
        "err_refused": "错误: %s 未监听或服务端未启动",
        "err_connect": "错误: 无法连接 %s (%s)",
        "err_generic": "错误: %s",
        "err_missing_arg": "错误: %s 后缺参数",
        "err_unknown_opt": "错误: 未知选项: %s",
    }
}


def t(key):
    return _S["zh" if _IS_ZH else "en"].get(key, key)


HELP = {
    "ping": {
        "desc": "测试连接是否正常" if _IS_ZH else "Test connection",
        "usage": "dcli ping",
        "example": "dcli ping",
    },
    "lock_now": {
        "desc": "立即锁屏" if _IS_ZH else "Lock screen now",
        "usage": "dcli lock_now",
        "example": "dcli lock_now",
    },
    "hide": {
        "desc": "隐藏指定应用" if _IS_ZH else "Hide app",
        "usage": "dcli hide <package>",
        "example": "dcli hide com.example.app",
    },
    "unhide": {
        "desc": "取消隐藏指定应用" if _IS_ZH else "Unhide app",
        "usage": "dcli unhide <package>",
        "example": "dcli unhide com.example.app",
    },
    "suspend": {
        "desc": "挂起指定应用" if _IS_ZH else "Suspend app",
        "usage": "dcli suspend <package>",
        "example": "dcli suspend com.example.app",
    },
    "resume": {
        "desc": "恢复挂起指定应用" if _IS_ZH else "Resume app",
        "usage": "dcli resume <package>",
        "example": "dcli resume com.example.app",
    },
    "block_uninstall": {
        "desc": "阻止卸载指定应用" if _IS_ZH else "Block uninstall",
        "usage": "dcli block_uninstall <package>",
        "example": "dcli block_uninstall com.example.app",
    },
    "unblock_uninstall": {
        "desc": "允许卸载指定应用" if _IS_ZH else "Unblock uninstall",
        "usage": "dcli unblock_uninstall <package>",
        "example": "dcli unblock_uninstall com.example.app",
    },
}


def print_global_help():
    print("dcli - " + ("DO Server 命令行客户端" if _IS_ZH else "DO Server CLI client"))
    print()
    print(t("usage"))
    print("  dcli [--host <ip>] [--package <pkg>] help [command]")
    print("  dcli [--host <ip>] [--package <pkg>] <command> [args]")
    print()
    print("Options:" if not _IS_ZH else "选项:")
    print(t("opt_host"))
    print(t("opt_pkg"))
    print(t("opt_end"))
    print()
    print(t("available"))
    for name in sorted(HELP.keys()):
        print("  %-18s %s" % (name, HELP[name]["desc"]))
    print()
    print(t("remote"))
    print("  dcli --host 192.168.1.100 ping")
    print(t("or_env") + " export DCLI_HOST=192.168.1.100")
    print(t("or_file") + " echo 192.168.1.100 > ~/.dcli_host")
    print()
    print(t("examples"))
    print("  dcli ping")
    print("  dcli lock_now")
    print("  dcli hide com.example.app")


def print_command_help(cmd):
    if cmd not in HELP:
        print(t("unknown_cmd") % cmd)
        print(t("see_help"))
        sys.exit(1)
    info = HELP[cmd]
    print(t("usage_short") % info["usage"])
    print(t("desc") % info["desc"])
    print(t("example") % info["example"])


def read_field(fname, envname):
    env = os.environ.get(envname, "").strip()
    if env:
        return env

    paths = [fname]
    home = os.environ.get("HOME")
    if home:
        paths.append(os.path.join(home, fname))

    for p in paths:
        if os.path.exists(p):
            with open(p, "r") as f:
                v = f.read().strip()
            if v:
                return v
    return None


def totp(key, t_val=None):
    if t_val is None:
        t_val = int(time.time()) // 30
    msg = struct.pack(">Q", t_val)
    h = hmac.new(key.encode("utf-8"), msg, hashlib.sha1).digest()
    off = h[-1] & 0x0F
    code = ((h[off] & 0x7F) << 24 |
            (h[off + 1] & 0xFF) << 16 |
            (h[off + 2] & 0xFF) << 8 |
            (h[off + 3] & 0xFF)) % 1000000
    return "%06d" % code


def send_command(host, pkg, cmd_line, key):
    code = totp(key)
    payload = ("%s %s %s\n" % (pkg, code, cmd_line)).encode("utf-8")

    s = socket.socket()
    s.settimeout(5)
    try:
        s.connect((host, DEFAULT_PORT))
        s.sendall(payload)

        data = b""
        while not data.endswith(b"\n"):
            chunk = s.recv(4096)
            if not chunk:
                break
            data += chunk

        return data.decode("utf-8").strip()
    finally:
        s.close()


def parse_args(args):
    """
    Parse options anywhere in argv. Supports:
      --host <ip> / -H <ip> / --host=<ip>
      --package <pkg> / -P <pkg> / --package=<pkg>
      --            stop parsing
    Returns (host, pkg, remaining)
    """
    host = None
    pkg = None
    remaining = []
    stop = False

    i = 0
    while i < len(args):
        a = args[i]

        if stop:
            remaining.append(a)
            i += 1
            continue

        if a == "--":
            stop = True
            i += 1
            continue

        if a in ("--host", "-H"):
            if i + 1 >= len(args):
                print(t("err_missing_arg") % a, file=sys.stderr)
                sys.exit(2)
            host = args[i + 1]
            i += 2
            continue

        if a.startswith("--host="):
            host = a[len("--host="):]
            i += 1
            continue

        if a in ("--package", "-P"):
            if i + 1 >= len(args):
                print(t("err_missing_arg") % a, file=sys.stderr)
                sys.exit(2)
            pkg = args[i + 1]
            i += 2
            continue

        if a.startswith("--package="):
            pkg = a[len("--package="):]
            i += 1
            continue

        # 允许 --help / -h 作为命令后缀，交给 main 处理
        if a in ("--help", "-h"):
            remaining.append(a)
            i += 1
            continue

        # 未知的以 - 开头的选项 → 报错
        if a.startswith("-") and a != "-":
            print(t("err_unknown_opt") % a, file=sys.stderr)
            sys.exit(2)

        remaining.append(a)
        i += 1

    return host, pkg, remaining


def main():
    host_arg, pkg_arg, args = parse_args(sys.argv[1:])

    if not args:
        print_global_help()
        return 0

    # dcli help / dcli --help
    if args[0] in ("help", "--help"):
        if len(args) < 2:
            print_global_help()
        else:
            print_command_help(args[1])
        return 0

    # dcli <cmd> --help / dcli <cmd> -h
    if len(args) >= 2 and args[0] in HELP and args[1] in ("--help", "-h"):
        print_command_help(args[0])
        return 0

    cmd_line = " ".join(args)

    key = read_field(KEY_FILE, "DCLI_KEY")
    if not key:
        print(t("err_no_key"), file=sys.stderr)
        print(t("err_key_hint1"), file=sys.stderr)
        print(t("err_key_hint2"), file=sys.stderr)
        return 1

    if pkg_arg:
        pkg = pkg_arg
    else:
        pkg = read_field(PKG_FILE, "DCLI_PACKAGE") or DEFAULT_PACKAGE

    if host_arg:
        host = host_arg
    else:
        host = read_field(HOST_FILE, "DCLI_HOST") or DEFAULT_HOST

    try:
        result = send_command(host, pkg, cmd_line, key)
    except socket.timeout:
        print(t("err_timeout") % host, file=sys.stderr)
        return 1
    except ConnectionRefusedError:
        print(t("err_refused") % host, file=sys.stderr)
        return 1
    except OSError as e:
        print(t("err_connect") % (host, e), file=sys.stderr)
        return 1
    except Exception as e:
        print(t("err_generic") % e, file=sys.stderr)
        return 1

    if result == "Success":
        print("Success")
        return 0
    if result.startswith("Success "):
        print(result[len("Success "):])
        return 0

    print(result)
    return 1


if __name__ == "__main__":
    sys.exit(main())
