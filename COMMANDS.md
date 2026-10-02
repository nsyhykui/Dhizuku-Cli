# Commands

## Operation commands

| Command | Description |
|---------|-------------|
| ping | Test connection |
| lock_now | Lock the screen |
| hide / unhide | Hide / unhide an app |
| suspend / resume | Suspend / resume an app |
| block_uninstall / unblock_uninstall | Block / allow uninstall |

## Query commands

| Command | Description |
|---------|-------------|
| list hidden | List hidden apps |
| list suspended | List suspended apps |
| list blocked | List apps with uninstall blocked |
| pm list packages [options] | List packages (same as pm list packages) |
| pm list permissions <perm> | List apps with this permission |
| pm list permissions --package <pkg> | List all permissions of an app |
| pm list permissions <perm> --package <pkg> | Query one app's permission state |
| cache update | Rescan all apps and update cache (parallel, thread count configurable in the app) |
| status | Show server running status (client-side) |

## Permission management commands

| Command | Description |
|---------|-------------|
| pm grant <pkg> <perm> | Grant a runtime permission |
| pm revoke <pkg> <perm> | Revoke a runtime permission |
| pm reset <pkg> <perm> | Reset a permission to default |

## pm list packages options

pm list packages supports the same options as Android's pm list packages:

    -f -d -e -s -3 -i -u -U --uid

plus a package name filter. The only exception is --user, which is not supported.

## Missing a command?

Open an issue:
https://github.com/nsyhykui/Dhizuku-Cli/issues
