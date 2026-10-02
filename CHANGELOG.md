# Changelog

All notable changes to this project are documented in this file.

## v3.1.0

- Parallel app scanning, configurable thread count (1-8, default 4)
- Added "Save all settings" button (port, key, bind, TTL, threads)
- cache update is now significantly faster on multi-core devices

## v3.0.3

- unhide / resume / unblock_uninstall now report "package not installed" instead of a misleading Success when the package does not exist

## v3.0.2

- Fixed: hide / suspend / block_uninstall rejected already-hidden apps as "not installed"
- Fixed: pm grant / revoke / reset reported Success on failure and could block for ~20s on non-runtime permissions
- pm grant / revoke / reset now reject non-runtime permissions early

## v3.0.1

- Disabled allowBackup to prevent the TOTP key from being included in system backups

## v3.0.0

- Breaking change: command structure and output protocol changed
- Added pm-style commands (pm list packages / pm list permissions / pm grant / pm revoke / pm reset)
- Added list hidden / list suspended / list blocked
- Added cache update
- Cache is now updated immediately after hide / suspend / block_uninstall / pm grant / revoke / reset
- Data commands no longer prefix output with Success
- Server version is read dynamically from the APK
- Removed: status hid / status suspend / status block_uninstall / status permission xxx
  (replaced by list hidden / list suspended / list blocked / pm list permissions xxx / cache update)

## v2.1.0

- Added status commands (superseded by v3.0.0)
- Added scan cache with Always / Auto / Manual modes
- Added app-side "Update app scan cache rules" settings
- Server version is now read dynamically from APK
- Fixed status hid not listing hidden apps

## v2.0.0

- Added AES-GCM encryption (all messages encrypted)
- Added UID authorization with overlay dialog
- Added authorization management UI (grant / revoke)
- Split Python and C clients into separate repositories
- Code refactored into modules (DhizukuDpm, Totp, AesGcm, AuthManager, etc.)

## v1.1.0

- Added suspend / resume / block_uninstall / unblock_uninstall
- Added English / Chinese i18n
- Added command argument validation

## v1.0.0

- First release
- Commands: ping, lock_now, hide, unhide
