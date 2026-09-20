#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
dcli - DO Server 命令行客户端
用法:
    dcli [--host <ip>] help [command]
    dcli [--host <ip>] <command> [args]
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
KEY_FILE = ".dcli_key"
HOST_FILE = ".dcli_host"

HELP = {
    "ping": {
        "desc": "测试连接是否正常",
        "usage": "dcli ping",
        "example": "dcli ping",
    },
    "lock_now": {
        "desc": "立即锁屏",
        "usage": "dcli lock_now",
        "example": "dcli lock_now",
    },
    "hide": {
        "desc": "隐藏指定应用",
        "usage": "dcli hide <package>",
        "example": "dcli hide com.example.app",
    },
    "unhide": {
        "desc": "取消隐藏指定应用",
        "usage": "dcli unhide <package>",
        "example": "dcli unhide com.example.app",
    },
}


def print_global_help():
    print("dcli - DO Server 命令行客户端\n")
    print("用法:")
    print("  dcli [--host <ip>] help [command]")
    print("  dcli [--host <ip>] <command> [args]\n")
    print("选项:")
    print("  --host, -H <ip>   指定服务端 IP（默认 127.0.0.1）\n")
    print("可用命令:")
    for name in sorted(HELP.keys()):
        print("  %-12s %s" % (name, HELP[name]["desc"]))
    print("\n远程连接示例:")
    print("  dcli --host 192.168.1.100 ping")
    print("  或设置环境变量: export DCLI_HOST=192.168.1.100")
    print("  或写入文件:    echo 192.168.1.100 > ~/.dcli_host")
    print("\n示例:")
    print("  dcli ping")
    print("  dcli lock_now")
    print("  dcli hide com.example.app")
    print("  dcli help hide")


def print_command_help(cmd):
    if cmd not in HELP:
        print("未知命令: %s" % cmd)
        print("用 'dcli help' 查看所有命令")
        sys.exit(1)
    info = HELP[cmd]
    print("用法:  %s" % info["usage"])
    print("说明:  %s" % info["desc"])
    print("示例:  %s" % info["example"])


def read_field(fname, envname):
    """按 环境变量 → 当前目录 → HOME 的顺序读取配置"""
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


def totp(key, t=None):
    if t is None:
        t = int(time.time()) // 30
    msg = struct.pack(">Q", t)
    h = hmac.new(key.encode("utf-8"), msg, hashlib.sha1).digest()
    off = h[-1] & 0x0F
    code = ((h[off] & 0x7F) << 24 |
            (h[off + 1] & 0xFF) << 16 |
            (h[off + 2] & 0xFF) << 8 |
            (h[off + 3] & 0xFF)) % 1000000
    return "%06d" % code


def send_command(host, port, cmd_line, key):
    code = totp(key)
    payload = ("%s %s\n" % (code, cmd_line)).encode("utf-8")

    s = socket.socket()
    s.settimeout(5)
    try:
        s.connect((host, port))
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
    """解析 --host / -H，返回 (host, remaining)"""
    host = None
    remaining = []
    i = 0
    while i < len(args):
        a = args[i]
        if a in ("--host", "-H"):
            if i + 1 >= len(args):
                print("错误: %s 后缺参数" % a, file=sys.stderr)
                sys.exit(1)
            host = args[i + 1]
            i += 2
        elif a.startswith("--host="):
            host = a[len("--host="):]
            i += 1
        else:
            remaining.append(a)
            i += 1
    return host, remaining


def main():
    host_arg, args = parse_args(sys.argv[1:])

    # 无参数 → 全局帮助
    if not args:
        print_global_help()
        return 0

    # help / --help → 本地处理
    if args[0] in ("help", "--help"):
        if len(args) < 2:
            print_global_help()
        else:
            print_command_help(args[1])
        return 0

    cmd_line = " ".join(args)

    # 读密钥
    key = read_field(KEY_FILE, "DCLI_KEY")
    if not key:
        print("错误: 未找到密钥", file=sys.stderr)
        print("请从 App 复制密钥，写入 ~/.dcli_key", file=sys.stderr)
        print("或设置环境变量 DCLI_KEY", file=sys.stderr)
        return 1

    # 解析 host
    if host_arg:
        host = host_arg
    else:
        host = read_field(HOST_FILE, "DCLI_HOST") or DEFAULT_HOST

    # 发送
    try:
        result = send_command(host, DEFAULT_PORT, cmd_line, key)
    except socket.timeout:
        print("错误: 连接 %s 超时" % host, file=sys.stderr)
        return 1
    except ConnectionRefusedError:
        print("错误: %s 未监听或服务端未启动" % host, file=sys.stderr)
        return 1
    except OSError as e:
        print("错误: 无法连接 %s (%s)" % (host, e), file=sys.stderr)
        return 1
    except Exception as e:
        print("错误: %s" % e, file=sys.stderr)
        return 1

    # 解析返回
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
