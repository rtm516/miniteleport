# MiniTeleport

<p align="center">
  <img alt="miniteleport logo" src="src/main/resources/assets/miniteleport/icon.png">
</p>

<p align="center">
Minimal zero-configuration server-side teleport commands.
</p>

## Commands

| Type | Command                | OP | Description                                           |
|:----:|------------------------|----|-------------------------------------------------------|
| 🗺️  | `/setspawn`            | ✓  | Create a `spawn` warp and set world spawn             |
| 🗺️  | `/setwarp <name>`      | ✓  | Create a warp at your location                        |
| 🗺️  | `/delwarp <name>`      | ✓  | Delete a warp                                         |
| 🗺️  | `/spawn`               |    | Teleport to spawn                                     |
| 🗺️  | `/warps`               |    | List all warps                                        |
| 🗺️  | `/warp <name>`         |    | Teleport to a warp                                    |
|  🏠  | `/sethome [<name>]`    |    | Set a new home (default: home)                        |
|  🏠  | `/delhome [<name>]`    |    | Delete an existing home (default: home)               |
|  🏠  | `/home [<name>]`       |    | Teleport to a home (default: home)                    |
|  🏠  | `/homes`               |    | List all current homes                                |
|  🏠  | `/back`                |    | Teleport to your last location (including death)      |
|  🔮  | `/tpa <player>`        |    | Request teleport to `<player>`                        |
|  🔮  | `/tpahere <player>`    |    | Request `<player>` to teleport to you                 |
|  🔮  | `/tpcancel`            |    | Cancel all your sent requests                         |
|  🔮  | `/tpaccept [<player>]` |    | Accept request from `<player>` (default: most recent) |
|  🔮  | `/tpdeny [<player>]`   |    | Deny request from `<player>` (default: most recent)   |

## Installation

Requires the [Fabric API](https://modrinth.com/mod/fabric-api).
Drop the mod `.jar` into your `mods` folder.
No configuration required.

## Supported maps
- [Pl3x Map](https://modrinth.com/plugin/pl3xmap)
