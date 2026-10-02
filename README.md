# dhizuku-cli

Dhizuku Device Owner command-line interface.

Android server app that exposes Device Owner commands over TCP,
with TOTP authentication and AES-GCM encryption.

[English](README.md) | [简体中文](README.zh_CN.md)

## About

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

## Features

- Fills the Device Owner command-line gap (no other public DO CLI exists)
- TOTP + AES-GCM: messages are encrypted, the key never travels over the network
- UID authorization: first-time clients trigger an on-screen prompt
- Overlay dialog works in the background (HarmonyOS blocks Activity-based prompts)
- pm-style commands: list packages, query and manage app permissions
- Status queries: hidden apps, suspended apps, uninstall-blocked apps
- Parallel app scanning with configurable thread count
- Local and LAN listening modes
- Foreground service with battery optimization exemption

## Requirements

- Android 9+
- Dhizuku installed and activated as Device Owner
- Overlay permission (Display over other apps) for the authorization dialog

## Version compatibility

Client versions are always one major version behind the server.

| Server version | Required client version |
|----------------|-------------------------|
| 3.1 / 3.0      | 2.0                     |
| 2.1            | 1.1                     |
| 2.0            | 1.0                     |

For example, server 3.1.0 requires client 2.0.

## Installation

All releases are on the Releases page.

1. Download and install the latest APK.
2. Open the app. It will ask for overlay permission on first launch.
3. Grant it in system settings.

## App Configuration

1. Grant overlay permission when prompted
2. Tap Check Dhizuku to confirm activation
3. Set port (default 12345)
4. Choose bind address:
   - Localhost: only local clients can connect
   - LAN: devices on the same network can connect
5. A key is generated automatically; tap Random to regenerate
6. Tap Start TCP Service; a persistent notification will appear

Under "Update app scan cache rules":

- Cache mode: Always / Auto / Manual
- TTL: time-to-live for Auto mode (seconds / minutes / hours / days)
- Threads: number of parallel scan threads (1-8, default 4)
- Last update: timestamp of the most recent scan
- Update cache now: trigger a manual rescan
- Save all settings: writes port, key, bind address, TTL and thread count to disk

## Authorization

When a client connects for the first time, the app shows an
overlay dialog on the current screen:

- UID and package name of the client
- Allow / Deny buttons

If allowed, the UID is added to a permanent whitelist and future
commands from that client run without prompting.

To manage authorized clients, tap Manage Authorizations on the
main screen. You can revoke or re-grant any UID from the list.

## Commands

See [COMMANDS.md](COMMANDS.md) for the full command list and options.

## Scan Cache Rules

The app caches app scan results (permissions, hidden / suspended /
uninstall-blocked states) in a local database. Query commands read
from this cache.

Three cache modes:

- Always: rescan on every query
- Auto: rescan when cache expires (TTL)
- Manual: only rescan when triggered manually

Scanning runs in parallel. Thread count is configurable (1-8, default 4).
Higher thread counts reduce scan time, but the gain flattens out after a
certain point depending on the device.

When hide / unhide / suspend / resume / block_uninstall /
unblock_uninstall / pm grant / revoke / reset succeed, the cache is
updated immediately. You do not need to run cache update manually.

## Security

- The TOTP key is the only credential. Keep it safe.
- All messages are encrypted with AES-GCM (key derived from the
  TOTP key via HKDF-SHA256).
- The TOTP code itself is encrypted, so packet sniffing cannot
  capture and replay it.
- Do not enable LAN mode on untrusted networks.

## Risk Notice

This tool borrows Device Owner privileges via Dhizuku and can
perform system-level operations.

- All commands run as Device Owner
- Misuse may cause abnormal system behavior or require a factory reset
- Use only on devices you own and control
- The author is not liable for any loss caused by this tool

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history.

## Acknowledgements

See [THANKS](THANKS) for the full list of dependencies, references and tools.

## Note

The code of this project is primarily AI-assisted; the author is responsible for design, debugging and testing.

This project is for learning and personal research only. The author makes no warranty regarding the completeness, security or fitness of the code.

## License

GPL-3.0. See LICENSE for details.

Copyright (C) 2026 nsyhykui
