# YouzaiWorldCore — Core Mod of Youzai World

<p align="center">
  <img src="https://mcyzw.top/images/banner.webp" alt="Banner">
</p>

<p align="center">
  <a href="https://github.com/Youzai-World-Team"><img src="https://img.shields.io/badge/Organization-Youzai_World_Team-blue?style=for-the-badge&logo=github" alt="Organization"></a>
  <a><img src="https://img.shields.io/badge/Minecraft-Java_26.2-brightgreen?style=for-the-badge&logo=minecraft" alt="Minecraft Version"></a>
  <a><img src="https://img.shields.io/badge/Mod_Loader-Fabric-orange?style=for-the-badge" alt="Mod Loader"></a>
  <a><img src="https://img.shields.io/badge/License-Apache_2.0-green?style=for-the-badge" alt="License"></a>
  <a href="https://nightly.link/Youzai-World-Team/YouzaiWorldCore/workflows/build/main/youzaiworldcore.zip"><img src="https://img.shields.io/badge/Download-Latest_Build-blue?style=for-the-badge&logo=githubactions" alt="Download Latest Build"></a>
</p>

<div align="center">

#### [**简体中文**](README.md) | **English**

</div>

---

## 📖 Project Overview

**YouzaiWorldCore** is the core gameplay mod for the **Youzai World** Minecraft multiplayer server, built on the **Fabric** framework with deep integration of **LuckPerms** permission system and **Placeholder API**. The mod provides a comprehensive infrastructure for the server, covering account authentication, GUI menus, custom items and blocks, sit interaction, dimension pools, teleport anchors, mana system, adventure XP, invisibility management, and more.

### Target Audience

| User Type | Description |
|-----------|-------------|
| **Server Administrators** | Manage the system through commands and menus, configure dimension pools, account policies, etc. |
| **Survival Players** | Use YZ tools, advancement system, teleport anchors, sit interaction, and mana staves for gameplay |
| **Mod Developers** | Understand the mod architecture, extend functionality, or contribute code |

---

## ✨ Feature Overview

### 1. Account Authentication System

Complete password authentication for offline-mode servers, with Mixin-based restrictions on unauthenticated behavior.

- **Password Security**: BCrypt salted hash (legacy SHA-256 compatible), 5-attempt login limit
- **Login Cooldown/Lock**: Triggers after 5 failures, default 300s (5 min) cooldown; supports permanent lock, timed cooldown, and never-lock modes; admin unlock available
- **Session Management**: Configurable session timeout with same-IP auto-recovery
- **Position Save/Restore**: Saves position on logout → teleports to End void; restores precisely on login
- **Login Hall**: Unauthenticated players confined to `youzaiworldcore:login_hall` custom dimension; Mixin blocks movement, interaction, attacking, and chat
- **Invisibility Integration**: Sensitive operations (logout, deactivate, password change) blocked while invisible

### 2. GUI Menu System

Windows 10 Start Menu-style tile layout with page switching and animated transitions.

| Menu ID | Name | Description |
|---------|------|-------------|
| `main` | Main Menu | Feature hub: world switching, events, check-in, tutorials, settings |
| `switch_world` | Switch World | 11 world buttons; first 7 integrated with dimension pool system |
| `settings` | Settings | Music/sound toggles, PVP/friendly fire, difficulty selection |
| `about_me` | About Me | 3D player model render, ID, join/playtime |

**Shortcut**: `Shift + F` to open the main menu.

### 3. Title Screen Overhaul

Fully redesigned Minecraft main menu via `TitleScreenMixin`: custom buttons (Join Server/Options/Quit), announcement banner with fade-in animation, gradient background (`GradientBackgroundUtil`), Mojang logo replaced with custom assets, and a test page button in developer mode.

### 4. Window Customization

- **Custom Window Icon**: Loads `jar_icon.png` at runtime via Java ImageIO to replace taskbar and title bar icons
- **Custom Window Title**: `WindowTitleMixin` intercepts `Window.setTitle()` to show "悠哉世界"

### 5. YZ Tools & Items

A new mineral and tool set equivalent to diamond tier (durability 1800, speed 8.0, enchantability 10).

| Item | Special Effect |
|------|----------------|
| **YZ Shovel / Pickaxe** | Sneak-mine to chain-break 6 blocks ahead |
| **YZ Hoe** | Sneak-use to till a 3×3 area |
| **YZ Sword** | 4% crit chance for double damage |
| **YZ Axe** | Jump-attack deals 50% sweeping damage in 3-block radius |
| **Heart of Guardianship** | Prevents item drop on death (Mixin); consumes 1 per death; warns at 10/5/3/2/1 remaining |
| **Void Staff** | Right-click to toggle flight; consumes 1 durability/sec (max 600) and hunger every 5s; auto-disables on depletion |
| **Flame Staff** | Charged fire laser, costs 10 mana |
| **Sky Star Staff** | Meteor attack, 10-block radius, costs 60 mana |
| **Logo (Youzai World)** | Server welcome item |

### 6. Custom Blocks

| Block | Properties |
|-------|------------|
| **YZ Ore / Deepslate YZ Ore** | Overworld generation, drops 2–5 XP, requires diamond pickaxe |
| **Raw YZ Block / YZ Block** | Mineral storage blocks |
| **Decomposition Table** | GUI block for decomposing items into raw materials |
| **Fly Beacon** | Grants flight within 9.56-block radius, glows when active (light 12) |
| **Teleport Anchor** | Right-click for teleport list after activation; supports naming/reordering/deleting/copying coordinates (light 15) |

### 7. Sit Interaction System

Right-click any stair (StairBlock) or slab (SlabBlock) to sit — no commands or special items required.

- **Trigger**: Empty main hand + right-click a stair/slab (no sneaking needed)
- **Implementation**: Creates an invisible, non-collidable `SeatEntity` as a vehicle → player rides it (`startRiding`)
- **Y Positioning**: Bottom stair/bottom slab → seat at y+0.5; top stair/top slab/double slab → seat at y+1.0
- **Dismount**: Vanilla sneak-to-dismount mechanic (`removeVehicle`)
- **Self-Destruct**: Entity auto-removes when no passenger is riding

### 8. Mana System

A new mana energy system powering special staves.

- **Max Mana**: 100
- **Auto-Regen**: +1 every 2 ticks
- **Client HUD**: `ManaHudRenderer` renders a mana bar
- **Network Sync**: `ManaSyncPayload` sent every 5 ticks
- **Consumption**: Flame Staff (10 mana/use), Sky Star Staff (60 mana/use)

### 9. Teleport Anchor System

Block-based player-owned teleport network — right-click to name, save, and remotely teleport.

- **Block**: `tp_anchor`, glows when active (light 15), managed by `TeleportAnchorBlockEntity`
- **Activation**: Right-click unactivated anchor → `TeleportAnchorNameScreen` (max 32 chars) → particles + sound
- **Teleport Flow**: Right-click activated anchor → `TeleportAnchorScreen` list (shows pool tag, dimension, coordinates) → select target → teleport
- **Cost & Cooldown**: 1 XP level same-dim / 2 XP cross-dim (free in Creative); 3-second global cooldown
- **Dimension Pool Isolation**: Records pool ID (`poolId`) on activation; validates pool membership on cross-pool teleport
- **Editing**: Rename, reorder (up/down), delete, one-click coordinate copy to clipboard
- **Persistence**: `SavedData`-based, survives server restarts
- **Command**: `/yzwc teleport_anchor list [player]` with clickable teleport links

### 10. Dimension Pool System

Multi-world server management with independent state pools — players have separate inventories, health, effects, and game modes across pools.

- **7 Predefined Pools**: Survival World (overworld/nether/end), Main City, Gameplay, Creation, Building, Commands, Tutorial World
- **Switching Flow**: Check target pool → save current state → clear inventory + effects → load target state → teleport → force game mode
- **Default Spawn**: Each pool can configure landing coordinates for post-death return
- **Cross-Pool Teleportation**: Supports dimension portals, commands, respawn events
- **Storage**: Pool config `config/youzaiworldcore/dimensional_inventories/pool_settings.json`, player state `<world>/youzaiworldcore/dimensional_inventories/data/<pool-id>/<uuid>.json`

### 11. Invisibility System

Deceptive Creative-mode invisibility — completely disappears from other players' tab lists and vision.

- **Command**: `/yzwc function invisibility <true/false>` (requires OP 4 or equivalent)
- **Behavior**: Fake leave message → tab list removal → entity removal from vision → white boss bar "Invisible"
- **8 Mixins**: Suppress particles, sounds, block events, and container animations (chest/barrel/ender chest/shulker box/decorated pot) generated by invisible players
- **Tick Check**: Every 10 ticks, verifies Creative mode; auto-forces off if left

### 12. Adventure XP System

A player-behavior-based experience level system providing attribute bonuses on level-up.

- **XP Sources**: Mine 50 blocks (+25), Place 50 blocks (+25), Death (+10), Guardian Heart protects (+50), Totem of Undying triggers (+500), Complete advancement (+50)
- **Level Formula**: `expForNext = 50 + level × 50`
- **Client HUD**: `AdventureLevelHudRenderer` renders a level bar
- **Network Sync**: `LevelExpSyncPayload` synchronizes XP values

### 13. Placeholder System

Integrates Placeholder API with `%luckperms_*%` namespace, providing 20+ placeholders (`prefix`, `suffix`, `groups`, `primary_group_name`, `has_permission_<node>`, `meta_<key>`, `in_group_<name>`, `expiry_time_<node>`, etc.).

### 14. Permission System

Fine-grained LuckPerms-based permission control with automatic OP-level fallback. 19 distinct permission nodes including `account.mgr.*`, `command.*`, and `*` wildcards.

### 15. Creative Mode Tabs

The creative menu has been reorganized into **5 independent tabs**:

| Tab ID | Name | Contents |
|--------|------|----------|
| `youzai_blocks` | YZ Blocks | 7 custom blocks |
| `youzai_tools_weapons` | YZ Tools & Weapons | 5 tools + 3 staves |
| `youzai_materials` | YZ Materials | Raw ore, ingot, nugget |
| `youzai_utilities` | YZ Utilities | Heart of Guardianship, Logo |
| `youzai_kits` | YZ Kits | 4 preset shulker boxes |

### 16. Preset Item System

Four preset shulker boxes in the creative tab:

| Preset | Color | Contents |
|--------|-------|----------|
| Graduation Set | Red | Full enchanted netherite gear, tools/weapons, consumables |
| Graduation Supplement | Orange | Utility tools, building materials, extra armor |
| Totem Box | Yellow | 27 totems of undying |
| Explosive Pack | Gray | 27 stacks × 64 TNT |

### 17. Advancement System

Two branches with 20+ advancements:

- **Youzai World** (main): Obtain YZ materials, craft tools, use decomposition table / fly beacon / heart of guardianship / void staff
- **Fun Little Challenges**: The Cake Is a Lie, Foodie, Max Luck, Way Home, etc.

### 18. Debug & Configuration

| Config | File Location | Contents |
|--------|--------------|----------|
| Server External Settings | `config/youzaiworldcore/server_external_settings.json` | `devModeEnabled`, `logToFile` (dual-toggle for DebugLogger) |
| Client External Settings | `config/youzaiworldcore/client_external_settings.json` | `devModeEnabled`, `logLevel` (0–3), debug address/port |
| DebugLogger | `util/DebugLogger` | 4 log levels (OFF/BASIC/DETAILED/DEBUG), entering/exiting/branch/stateChange/exception tracing |

### 19. CI/CD

GitHub Actions workflow (`.github/workflows/build.yml`): JDK 25 auto-build on Ubuntu / Windows / macOS, with automatic Linux artifact upload.

---

## 📜 Command Tree

All commands use `/yzwc` as the root command.

```
/yzwc
├── teleport_world <targets> <dimension> [x] [y] [z] [yRot] [xRot]
│   ├── Permission: youzaiworldcore.command.teleport_world (OP 4)
│   └── Example: /yzwc teleport_world @p minecraft:overworld 0 64 0
│
├── open_menu <menu_name> [target]
│   ├── Permission: youzaiworldcore.command.open_menu (OP 4)
│   ├── Valid menus: main / switch_world / settings / about_me
│   └── Example: /yzwc open_menu main
│
├── world_pool
│   ├── Permission: youzaiworldcore.command.world_pool (OP 4)
│   ├── teleport <targets> <pool_id>     → Teleport to dimension pool
│   └── list                             → List all dimension pools
│
├── teleport_anchor list [player]
│   ├── Permission: youzaiworldcore.command.teleport_anchor (OP 4)
│   └── List anchors with clickable teleport links
│
├── function invisibility <true/false>
│   ├── Permission: youzaiworldcore.command.function.invisibility (OP 4)
│   └── Requires Creative mode
│
├── experimental_feature <id> [true/false [all|only <player>]]
│   ├── Permissions: .query (everyone) / .self (everyone) / .admin (OP 4)
│   └── Query or toggle experimental features
│
├── reload
│   ├── Permission: youzaiworldcore.command.reload (OP 4)
│   └── Reload account data and config at runtime
│
└── account
    ├── 📋 Player Commands:
    │   ├── register <password> <confirm>           ← Register (4–128 chars)
    │   ├── login <password>                        ← Login (5 attempts, 5 min cooldown)
    │   ├── logout                                  ← Logout (blocked while invisible)
    │   ├── deactivate <password>                   ← Delete account
    │   └── change_password <old> <new> <confirm>   ← Change password
    │
    └── 🔧 Admin Commands (OP 4):
        ├── mgr create <player> <pass> <confirm>         ← Create offline account
        ├── mgr reset_password <player> <pass> <confirm> ← Reset password
        ├── mgr delete <player>                          ← Delete account
        ├── mgr session_timeout [seconds]                ← Session timeout (0=disabled)
        └── mgr login_cooldown
            ├── (no args)        ← Display setting
            ├── set <seconds>    ← Set (-1=never, 0=permanent, >0=timed)
            ├── status <player>  ← Query lock status
            └── unlock <player>  ← Unlock account
```

### Permission Nodes Overview

| Permission Node | Description | Fallback |
|----------------|-------------|----------|
| `youzaiworldcore.command.teleport_world` | Cross-dimension teleport | OP 4 |
| `youzaiworldcore.command.open_menu` | Open GUI menu | OP 4 |
| `youzaiworldcore.command.reload` | Mod reload | OP 4 |
| `youzaiworldcore.command.world_pool` | Dimension pool management | OP 4 |
| `youzaiworldcore.command.teleport_anchor` | Teleport anchor management | OP 4 |
| `youzaiworldcore.command.function.invisibility` | Invisibility function | OP 4 |
| `youzaiworldcore.command.experimental_feature` | Experimental feature (basic) | Everyone |
| `youzaiworldcore.command.experimental_feature.query` | Query | Everyone |
| `youzaiworldcore.command.experimental_feature.self` | Self-toggle | Everyone |
| `youzaiworldcore.command.experimental_feature.admin` | Admin | OP 4 |
| `youzaiworldcore.command.account.mgr.create` | Create account | OP 4 |
| `youzaiworldcore.command.account.mgr.reset_password` | Reset password | OP 4 |
| `youzaiworldcore.command.account.mgr.delete` | Delete account | OP 4 |
| `youzaiworldcore.command.account.mgr.session_timeout` | Session timeout | OP 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown` | Login cooldown | OP 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown.status` | Lock status query | OP 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown.unlock` | Unlock | OP 4 |
| `youzaiworldcore.command.account.mgr.*` | Account mgr wildcard | OP 4 |
| `youzaiworldcore.command.*` | All commands wildcard | — |
| `youzaiworldcore.*` | Full mod wildcard | — |

---

## 🧪 Experimental Features

The experimental feature system framework is fully implemented, supporting server-wide toggle + player-level override + server-controlled mode (`serverSide`). Configuration persists to `config/youzaiworldcore/experimental_feature/` (`server_settings.json` / `client_settings.json`), synchronized via `FeatureSyncPayload`.

> **Current Status**: No experimental features are currently registered. The dimension pool system has graduated from experimental status and is now enabled as a core feature.

---

## 🖥️ Menus & Network Packets

### GUI Menu IDs

| Internal ID | Name | Hierarchy |
|-------------|------|-----------|
| `main` | Main Menu | Root |
| `switch_world` | Switch World | Main → Switch World |
| `settings` | Settings | Main → Settings |
| `about_me` | About Me | Main → About Me |

### Container Menu Types

| ID | Block |
|----|-------|
| `decomposition_table` | Decomposition Table |
| `fly_beacon` | Fly Beacon |

### Network Packets (15 total)

| Packet ID | Direction | Purpose |
|-----------|-----------|---------|
| `open_menu` | S→C | Open GUI menu |
| `feature_sync` | S→C | Sync experimental feature states |
| `open_auth_screen` | S→C | Open auth screen |
| `mana_sync` | S→C | Sync mana values |
| `level_exp_sync` | S→C | Sync adventure level XP |
| `world_pool_teleport` | C→S | Request dimension pool teleport |
| `teleport_anchor_open_name` | S→C | Open anchor naming screen |
| `teleport_anchor_list` | S→C | Send point list |
| `teleport_anchor_activate` | C→S | Activate anchor |
| `teleport_anchor_teleport` | C→S | Request teleport |
| `teleport_anchor_delete` | C→S | Delete point |
| `teleport_anchor_rename` | C→S | Rename point |
| `teleport_anchor_reorder` | C→S | Reorder points |
| `decompose_item` | C→S | Decompose item |
| `fly_beacon_active` | C→S | Toggle fly beacon |

---

## 🔧 Tech Stack & Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Minecraft | 26.2 | Engine |
| Fabric Loader | 0.19.3 | Mod loader |
| Fabric API | 0.154.0+26.2 | Standard API |
| ModMenu | 20.0.0-beta.4 | Mod menu integration |
| Placeholder API | 3.1.0-beta.1+26.2 | Text placeholders |
| Fabric Permissions API | 0.6.1 (bundled) | Cross-mod permission API |
| LuckPerms | 5.5 (suggested runtime) | Advanced permission control |

**Build Requirements**: JDK 25+ · Gradle (Fabric Loom 1.16-SNAPSHOT)

---

## 🏗️ Project Structure

```
src/
├── main/java/top/csituka/youzaiworldcore/
│   ├── YouzaiworldCore.java              # Main entry point
│   ├── account/                          # Account auth (Mixin + JSON storage)
│   ├── block/ + entity/                  # Custom blocks & block entities
│   ├── command/                          # Command registration
│   ├── component/                        # Data components
│   ├── config/                           # Server external settings
│   ├── data/                             # Teleport anchor SavedData
│   ├── dimensionalinventories/           # Dimension pool system
│   ├── event/                            # Event handlers (anvil repair, fly beacon, void staff, sit)
│   ├── entity/seat/                      # Seat entity system
│   ├── feature/                          # Experimental features system
│   ├── invisibility/                     # Invisibility system
│   ├── item/                             # Items, tools, creative tabs, presets
│   ├── luckperms/                        # LuckPerms permission integration
│   ├── mana/                             # Mana system
│   ├── mixin/                            # Main Mixins (+ invis containers/particles/sounds + seat + skill)
│   ├── network/                          # Network packets (15 total)
│   ├── placeholders/                     # Placeholder API (24 placeholders)
│   ├── screen/                           # Container menus
│   ├── skill/                            # Adventure level XP system
│   └── util/                             # DebugLogger, etc.
│
├── client/java/top/csituka/youzaiworldcore/
│   ├── client/Client.java                # Client entry point
│   ├── config/                           # Client external settings
│   ├── effect/                           # Teleport FOV effect
│   ├── higherchat/                       # Simple Voice Chat integration
│   ├── hud/                              # Mana bar / adventure level HUD
│   ├── mixin/client/                     # Client Mixins (title, options, pause, chat, loading, seat, etc.)
│   ├── renderer/                         # Block/entity renderers
│   └── screen/                           # GUI screens
│       ├── MenuScreen.java               # Menu container
│       ├── LoginScreen / RegisterScreen  # Auth screens
│       ├── element/                      # Menu element groups
│       ├── widget/                       # UI components
│       └── block/                        # Block screens
│
└── main/resources/
    ├── assets/youzaiworldcore/           # Textures, models, language files
    └── data/                             # Advancements, recipes, loot tables, dimensions

.github/workflows/
└── build.yml                             # CI/CD build workflow
```

---

## 📦 Recipe List

| Recipe | Type | Description |
|--------|------|-------------|
| `yz_ingot_from_blasting_raw_yz` | Blasting | Raw YZ → YZ Ingot |
| `yz_block_from_blasting_raw_yz_block` | Blasting | Raw Block → YZ Block |
| `yz_ingot_from_yz_block` | Crafting | YZ Block → 9 Ingots |
| `yz_ingot_from_nuggets` | Crafting | 9 Nuggets → Ingot |
| `yz_block` | Crafting | 9 Ingots → Block |
| `yz_nugget_from_ingot` | Crafting | Ingot → 9 Nuggets |
| `yz_pickaxe` / `yz_axe` / `yz_shovel` / `yz_hoe` / `yz_sword` | Crafting | YZ tools |
| `decomposition_table` | Crafting | Decomposition Table |
| `fly_beacon` | Crafting | Fly Beacon |
| `heart_of_guardianship` | Crafting | Heart of Guardianship |
| `void_staff` | Crafting | Void Staff |
| `flame_staff` | Crafting | Flame Staff |
| `sky_star_staff` | Crafting | Sky Star Staff |
| `raw_yz_block` / `raw_yz_from_raw_yz_block` | Crafting | Ore block conversion |

---

## 🌐 Related Links

- **Official Website**: [https://mcyzw.top](https://mcyzw.top)
- **GitHub Repository**: [Youzai-World-Team/YouzaiWorldCore](https://github.com/Youzai-World-Team/YouzaiWorldCore)
- **Issue Tracker**: [Issues](https://github.com/Youzai-World-Team/YouzaiWorldCore/issues)

---

## 🤝 Contributors

**Core Authors**：Maskviva, ress2338396, zxabinbina, Youzai World Team  
**Contributors**：byzzdemy, Fogg05, lucko, MDLC01, why

---

> **Note**: Test the mod on a server environment; running it on the client alone will not function correctly.
