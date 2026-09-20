# dhizuku-cli

Execute Dhizuku Device Owner commands remotely over TCP.

English | [简体中文](README_zh.md)

## Introduction

Dhizuku CLI is a tool that executes Dhizuku Device Owner commands over TCP.

The project consists of two parts:

- **Server**: an Android app that holds the Dhizuku authorization and listens on a local TCP port
- **Client dcli**: a command-line tool that sends commands to the server over TCP

When the server receives a command, it calls system APIs as the Device Owner.

## Features

- Fills the gap for Device Owner command-line tools—there is currently no other public DO CLI
- TOTP authentication; the key never travels over the network
- Client compiles to a single binary with no runtime dependencies
- No Root required; only needs Dhizuku activated as Device Owner
- Supports both localhost and LAN connection modes

## Supported Platforms

- **Server**: all Android architectures (pure Java, no native dependency)
- **Client (binary)**: Android aarch64, no runtime dependency
- **Client (Python)**: any platform with Python 3.6+

## Prerequisites

- Dhizuku installed and correctly activated as Device Owner
- At least one terminal app installed

## Installation

All releases are available on [Releases](https://github.com/nsyhykui/dhizuku-cli/releases).

### Server (Android App)

Download the latest APK from [Releases](https://github.com/nsyhykui/dhizuku-cli/releases) and install it.

### Client (dcli)

Download the client for your platform from [Releases](https://github.com/nsyhykui/dhizuku-cli/releases).

- **Binary version**: download the dcli file for your platform and make it executable.

  ```bash
  chmod +x dcli
  ./dcli help
  ```

- **Python version**: download dcli.py, requires Python 3.6+.

  ```bash
  python3 dcli.py help
  ```

## Usage

### App Configuration

1. Manually enable background running for the app
2. Open the app, tap Check Dhizuku to confirm activation
3. Set port (default 12345)
4. Choose bind address:
   - **Localhost**: only local clients can connect
   - **LAN**: devices on the same network can connect
5. A key is generated automatically; tap Random to regenerate
6. Tap Start TCP Service; a persistent notification will appear on success

### Client Configuration

Write the key shown in the app to the client config:

```bash
echo "<key from app>" > ~/.dcli_key
```

For LAN connections, also write the server IP:

```bash
echo "192.168.1.100" > ~/.dcli_host
```

Or specify it on the command line:

```bash
dcli --host 192.168.1.100 ping
```

### Examples

```bash
dcli ping                               # test connection
dcli lock_now                           # lock screen
dcli hide com.example.app               # hide app
dcli unhide com.example.app             # unhide app
dcli suspend com.example.app            # suspend app
dcli resume com.example.app             # resume app
dcli block_uninstall com.example.app    # block uninstall
dcli unblock_uninstall com.example.app  # unblock uninstall
```

### Connection Priority

The client resolves the server address in this order:

1. --host / -H argument
2. DCLI_HOST environment variable
3. ~/.dcli_host file
4. default 127.0.0.1

## Command List

| Command | Arguments | Description |
|---------|-----------|-------------|
| ping | none | Test connectivity between client and server |
| lock_now | none | Lock the screen immediately |
| hide | <package> | Hide an app (still installed, but invisible) |
| unhide | <package> | Unhide a hidden app |
| suspend | <package> | Suspend an app (icon greyed out) |
| resume | <package> | Resume a suspended app |
| block_uninstall | <package> | Prevent an app from being uninstalled |
| unblock_uninstall | <package> | Allow an app to be uninstalled |

## Protocol

Client and server communicate over TCP; the port is configured in the app (default 12345).

## Security

### Key

- The TOTP key is the only authentication credential—leaking it means the device is compromised
- The key is stored only in the app UI and on the client; it never travels over the network
- Do not screenshot or share the key; do not write it into public scripts
- If leaked, tap Random in the app to regenerate and update the client config

### Connection Modes

- Default Localhost mode: only local processes can connect
- In LAN mode, any device on the same network can attempt to connect
- Do not enable LAN mode on public WiFi or untrusted networks
- Stop the service when not in use

### Commands

- All commands run as Device Owner with high privileges
- Verify the target package name before executing, especially for hide-type operations
- It is recommended to run ping first to verify connectivity

### Device

- Use only on devices you own and control
- Do not install or run on other people's devices
- Periodically check the authorization status and key in the app

## Risk Notice

This tool borrows Device Owner privileges via Dhizuku and can perform system-level operations. Please fully understand the following risks before use.

### High-Risk Operations

- All commands run as Device Owner, with privileges equivalent to a device administrator
- Misuse may render apps inaccessible, cause abnormal system behavior, or require a factory reset
- Hiding critical system apps may prevent the system from booting normally

### Data Safety

- This tool does not provide backup or restore
- Data lost due to operations cannot be recovered through this tool
- Back up important data before use

### Compatibility

- Tested only on a limited set of Android versions and devices
- Custom systems like HarmonyOS may have unknown limitations
- System updates may break this tool

### Liability

- This tool is for learning and personal research only
- Use only on devices you own and fully control
- Any direct or indirect consequences of using this tool are the user's responsibility
- The author is not liable for any loss caused by this tool

## Acknowledgements

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

## Note

The code of this project is primarily AI-assisted; the author is responsible for design, debugging and testing.

This project is for learning and personal research only. The author makes no warranty regarding the completeness, security or fitness of the code.

## Changelog

### v1.1.0

- Added commands: suspend, resume, block_uninstall, unblock_uninstall
- Added English support

## License

This project is released under the [GPL-3.0](LICENSE) license.

Copyright (C) 2026 nsyhykui
