# YouzaiWorldCore — Core Mod of Youzai World

> **Version**: 1.10.0+indev · **Minecraft**: 26.2 · **Loader**: Fabric Loader 0.19.3  
> **License**: [Apache-2.0](LICENSE.txt)

<div align="center">

#### [**简体中文**](README.md) | **English**

</div>

---

## 📖 Project Overview

**YouzaiWorldCore** is the core gameplay mod for the **Youzai World** Minecraft multiplayer server, built on the **Fabric** framework. This mod provides a comprehensive infrastructure for the server, covering account authentication, GUI menu system, custom items and blocks, skill system, cross-dimension teleportation, experimental feature toggling, and other core capabilities.

### Use Cases

- **Offline-Mode Server**: Built-in account authentication system supporting registration, login, logout, password changes, and session recovery
- **Survival Gameplay Enhancement**: Provides the YZ tool series (with special effects like chain mining, area damage, etc.), custom blocks, and crafting recipes
- **Server Administration**: GUI menu navigation, world switching, and admin operation tools
- **Player Experience Optimization**: Skill attribute system (Puffish Skills), advancement system, and placeholder integration

### Target Audience

| User Type | Description |
|-----------|-------------|
| **Server Administrators** | OPs/Admins operating the Youzai World server, managing the system through commands and menus |
| **Survival Players** | Regular players on the server, using YZ tools, skill system, world teleportation, etc. |
| **Mod Developers** | Developers who want to understand the mod architecture, extend functionality, or contribute code |

---

## ✨ Feature Overview

### 1. Account Authentication System

A complete offline-mode account system supporting password registration, login, logout, and admin management.

- **Password Security**: SHA-256 salted hash storage with login attempt limits (max 5 attempts)
- **Session Management**: Configurable session timeout with automatic recovery on reconnection
- **Location Persistence**: Automatically saves player position on logout and teleports them to the void; restores position precisely on login
- **Login Hall**: Unauthenticated players are restricted to the custom `login_hall` dimension and cannot move or interact
- **Admin Tools**: Create offline accounts, reset passwords, delete accounts, configure session timeout

### 2. GUI Menu System

A comprehensive graphical menu system built on Fabric Screen, featuring a Windows 10 Start Menu-style tile layout with animated transition effects.

| Menu | Description |
|------|-------------|
| **Main Menu** | Feature hub containing tile buttons for world switching, events, check-in, tutorials, settings, etc. |
| **Switch World** | Displays all teleportable worlds (Survival, Kingdom, Gameplay, Creative, Building, etc.) |
| **Settings** | Provides music/sound effect toggles, PVP/friendly fire toggles, difficulty selection, etc. |
| **About Me** | Displays player 3D model render, player ID, first/last join time, playtime duration |

**Shortcut**: `Shift + F` to quickly open the main menu.

### 3. YZ Tool Series & Items

A new mineral and tool set, equivalent to diamond-tier tools.

| Item | Special Effect |
|------|----------------|
| **YZ Shovel** | Sneak-dig to chain-mine up to 6 blocks forward |
| **YZ Pickaxe** | Sneak-dig to chain-mine up to 6 blocks forward |
| **YZ Hoe** | Sneak-use to till a 3×3 area of soil |
| **YZ Sword** | 4% critical hit chance on attack, double damage |
| **YZ Axe** | Jump-attack to deal area damage to surrounding enemies |
| **Heart of Guardianship** | Prevents item drop on death when carried; consumes one per death |
| **Void Staff** | Right-click to toggle flight mode; auto-disables when hunger reaches zero |
| **Logo (Youzai World)** | Server identity item |

### 4. Custom Blocks

| Block | Properties |
|-------|------------|
| **YZ Ore / Deepslate YZ Ore** | Generates in the Overworld, drops XP (2-5), requires diamond pickaxe |
| **Raw YZ Block / YZ Block** | Mineral storage blocks |
| **Decomposition Table** | GUI-based block used to decompose items back to raw materials |
| **Fly Beacon** | Grants area flight capability, toggleable activation state |

### 5. Skill System (Puffish Skills)

Integrates the Puffish Skills mod, providing 11 attribute upgrades:

| Attribute | Effect |
|-----------|--------|
| Health +1 | Increases max health by 1 per level |
| Resistance +1% | Increases damage reduction by 1% per level |
| Melee Damage +1% | Percentage increase to melee attack damage |
| Ranged Damage +1% | Percentage increase to ranged attack damage |
| Attack Speed +1% | Percentage increase to attack speed |
| Movement Speed +1% | Percentage increase to movement speed |
| Luck +0.1 | Increases luck value by 0.1 per level |
| Stamina +1% | Percentage increase to stamina |
| Healing +1% | Percentage increase to healing amount |
| Jump +1% | Percentage increase to jump height |
| Mining Speed +1% | Percentage increase to mining speed |

### 6. Advancement System

Contains two advancement branches:

- **Youzai World** (main progression): Covers obtaining YZ ore/ingots/blocks/tools, using the decomposition table, fly beacon, heart of guardianship, void staff, etc.
- **Fun Little Challenges**: The Cake Is a Lie, Foodie, Get Emerald Blocks, Like Cows and Pigs, Max Luck, Stuck in Cobweb, Way Home, Wearing Copper Armor

### 7. Experimental Feature System

Supports server-wide global toggle + player-level override for experimental features, with state configuration persisted to JSON files.

### 8. Placeholder System (Placeholder API)

Integrates Placeholder API and LuckPerms placeholders, supporting dynamic/static placeholder resolution.

### 9. Permission System

Provides fine-grained permission control based on **LuckPerms**, with automatic fallback to vanilla OP level checks.

### 10. Preset Item System

Four preset shulker boxes in the creative mode tab, generated with one click:

| Preset | Contents |
|--------|----------|
| **Graduation Set** (Red) | Full netherite gear, fully enchanted tools/weapons, consumables |
| **Graduation Supplement** (Orange) | Utility tools, building materials, extra armor |
| **Totem Box** (Yellow) | 27 totems of undying |
| **Explosive Pack** (Gray) | 27 stacks × 64 TNT |

---

## 📜 Command Tree

All commands use `/yzwc` as the root command, built on the Brigadier command framework.

```
/yzwc
├── (no arguments) — Displays the "Hello World" message
│
├── teleport_world <targets> <dimension> [x] [y] [z] [yRot] [xRot]
│   ├── Description: Teleports target player(s) to specified coordinates in a dimension
│   ├── Permission: youzaiworldcore.command.teleport_world (or OP level 4)
│   ├── Parameters:
│   │   • targets — Target player(s) (supports multiple players)
│   │   • dimension — Target dimension (e.g., minecraft:overworld)
│   │   • x, y, z — Target coordinates (optional, defaults to 0, 100, 0)
│   │   • yRot — Horizontal rotation angle (optional, -180 ~ 180)
│   │   • xRot — Vertical rotation angle (optional, -90 ~ 90)
│   └── Examples:
│       /yzwc teleport_world @p minecraft:the_nether
│       /yzwc teleport_world @a minecraft:overworld 0 64 0
│
├── open_menu <menu_name> [target]
│   ├── Description: Opens a specified GUI menu for a player
│   ├── Permission: youzaiworldcore.command.open_menu (or OP level 4)
│   ├── Parameters:
│   │   • menu_name — Menu name (main / switch_world / settings / about_me)
│   │   • target — Target player (optional, defaults to command executor)
│   └── Examples:
│       /yzwc open_menu main
│       /yzwc open_menu settings @p
│
├── experimental_feature <id> [true/false [all|only <player>]]
│   ├── Description: Queries or toggles experimental features
│   ├── Permissions:
│   │   • Query: youzaiworldcore.command.experimental_feature.query
│   │   • Self-toggle: youzaiworldcore.command.experimental_feature.self
│   │   • Admin (all/only): youzaiworldcore.command.experimental_feature.admin (or OP level 4)
│   ├── Parameters:
│   │   • id — Experimental feature internal ID (e.g., chicken_warden_model)
│   │   • true/false — Enable/disable (optional; omit for query mode)
│   │   • all — Toggle server-wide (requires admin permission)
│   │   • only <player> — Toggle for a specific player (requires admin permission)
│   └── Examples:
│       /yzwc experimental_feature chicken_warden_model          ← Query status
│       /yzwc experimental_feature chicken_warden_model true     ← Enable for self
│       /yzwc experimental_feature chicken_warden_model false all ← Disable server-wide
│       /yzwc experimental_feature chicken_warden_model true only Steve ← Enable for Steve
│
├── reload
│   ├── Description: Reloads mod data at runtime (account data, config, etc.) without restarting the server
│   ├── Permission: youzaiworldcore.command.reload (or OP level 4)
│   └── Examples:
│       /yzwc reload
│
└── account <subcommand>
    ├── Description: Account authentication management
    │
    ├── 📋 Player Commands:
    │   ├── register <password> <confirm>
    │   │   ├── Description: Register a new account
    │   │   ├── Permission: None (everyone)
    │   │   ├── Restriction: Password length 4-128 characters
    │   │   └── Example: /yzwc account register MyPass123 MyPass123
    │   │
    │   ├── login <password>
    │   │   ├── Description: Log in to an account
    │   │   ├── Permission: None (everyone)
    │   │   ├── Restriction: Max 5 attempts
    │   │   └── Example: /yzwc account login MyPass123
    │   │
    │   ├── logout
    │   │   ├── Description: Log out of an account and teleport to the End void
    │   │   ├── Permission: None (everyone)
    │   │   └── Example: /yzwc account logout
    │   │
    │   ├── deactivate <password>
    │   │   ├── Description: Deactivate (delete) an account
    │   │   ├── Permission: None (everyone)
    │   │   └── Example: /yzwc account deactivate MyPass123
    │   │
    │   └── change_password <oldPassword> <newPassword> <confirmPassword>
    │       ├── Description: Change account password
    │       ├── Permission: None (everyone)
    │       └── Example: /yzwc account change_password Old123 New456 New456
    │
    └── 🔧 Admin Commands (requires OP level 4):
        ├── mgr create <player> <newPassword> <confirmPassword>
        │   ├── Description: Create an account for an offline player with a password
        │   └── Example: /yzwc account mgr create Steve Pass123 Pass123
        │
        ├── mgr reset_password <player> <newPassword> <confirmPassword>
        │   ├── Description: Reset a specific player's password
        │   └── Example: /yzwc account mgr reset_password Steve NewPass456 NewPass456
        │
        ├── mgr delete <player>
        │   ├── Description: Delete a specific player's account
        │   └── Example: /yzwc account mgr delete Steve
        │
        └── mgr session_timeout [seconds]
            ├── Description: View or set session timeout duration (0 = disabled)
            └── Examples:
                /yzwc account mgr session_timeout          ← View current value
                /yzwc account mgr session_timeout 3600     ← Set to 1 hour
```

### Permission Nodes Overview

| Permission Node | Description | Fallback Level |
|----------------|-------------|----------------|
| `youzaiworldcore.command.teleport_world` | Cross-dimension teleport | OP level 4 |
| `youzaiworldcore.command.open_menu` | Open GUI menu | OP level 4 |
| `youzaiworldcore.command.reload` | Mod reload | OP level 4 |
| `youzaiworldcore.command.experimental_feature` | Experimental feature (basic) | Everyone |
| `youzaiworldcore.command.experimental_feature.query` | Experimental feature query | Everyone |
| `youzaiworldcore.command.experimental_feature.self` | Self-toggle experimental feature | Everyone |
| `youzaiworldcore.command.experimental_feature.admin` | Admin experimental feature | OP level 4 |
| `youzaiworldcore.command.*` | All commands wildcard | — |
| `youzaiworldcore.*` | Full mod wildcard | — |

---

## 🧪 Experimental Feature Internal ID List

The experimental feature system supports server-wide toggles and per-player overrides, with configuration persisted to `config/youzaiworldcore/experimental_feature/`.

| Internal ID | Name | Description | Provider | Source | Default State | Current State |
|-------------|------|-------------|----------|--------|---------------|---------------|
| `chicken_warden_model` | Chicken Warden Model | Modifies the vanilla Warden's texture and model with a "Kunkun" style using the GeckoLib animation engine | [终end](https://space.bilibili.com/397147959) | [KLpbbs](https://klpbbs.com/thread-52966-1-1.html) | ❌ Disabled | Experimental |

### Usage Notes

- Experimental features default to **disabled** and must be explicitly enabled by an admin or player
- State operates on three layers: **Global State** (server-wide) > **Player Override** (personal setting) > **Client Cache**
- Client and server configurations are persisted to separate JSON files
- State synchronization is handled via the `FeatureSyncPayload` packet
- Experimental features may affect game stability; it is recommended to thoroughly test them in a test environment before enabling them server-wide

---

## 🖥️ Menu Internal ID List

The GUI menu system is based on the `MenuScreen` + `MenuElementGroup` interface, supporting switchable pages and animated transitions.

| Internal ID | Menu Name | Hierarchy | Description |
|-------------|-----------|-----------|-------------|
| `main` | Main Menu | Root menu | Feature hub with a 5-column tile layout, including Switch Worlds, Questionnaire, Title, Events, About Me, Check-In, Tutorial Center, Settings, Mail, Website, Report, Management |
| `switch_world` | Switch World | Main Menu → Switch World | Displays 11 teleportable world tile buttons (Survival, Kingdom, Gameplay, Creative, Building, Nether, End, Command Zone, Market, Overworld, Login Hall); opens a confirmation dialog on click |
| `settings` | Settings | Main Menu → Settings | General settings (music/sound toggles), gameplay settings (PVP/friendly fire toggles, difficulty dropdown) |
| `about_me` | About Me | Main Menu → About Me | Displays player 3D model render, player ID, first/last join time, playtime duration with fade-in animation |

### Network Packets

Menus communicate between server and client via `OpenMenuPayload` (S2C packet, ID: `youzaiworldcore:open_menu`):

| Packet ID | Direction | Purpose |
|-----------|-----------|---------|
| `youzaiworldcore:open_menu` | Server → Client | Opens a menu screen by name |
| `youzaiworldcore:feature_sync` | Server → Client | Synchronizes experimental feature states |
| `youzaiworldcore:open_auth_screen` | Server → Client | Opens the authentication screen |
| `youzaiworldcore:decompose_item` | Client → Server | Decomposes an item in the decomposition table |
| `youzaiworldcore:fly_beacon_active` | Client → Server | Toggles the fly beacon activation state |

---

## 🔧 Tech Stack & Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Minecraft | 26.2 | Base game engine |
| Fabric Loader | 0.19.3 | Mod loader |
| Fabric API | 0.152.1+26.2 | Fabric standard API |
| GeckoLib | 5.5.1 | 3D entity animation (Chicken Warden model) |
| Placeholder API | 3.1.0-beta.1+26.2 | Text placeholder resolution |
| Fabric Permissions API | 0.6.1 (bundled) | Cross-mod permission API |
| LuckPerms API | 5.5 (compile-only, optional runtime) | Advanced permission control |

### Build Requirements

- **JDK**: Java 25+
- **Build System**: Gradle (Fabric Loom 1.16-SNAPSHOT)

---

## 🏗️ Project Structure

```
src/
├── main/java/top/csituka/youzaiworldcore/     # Server/Common code
│   ├── YouzaiworldCore.java                    # Main entry point
│   ├── account/                                # Account authentication system
│   │   ├── command/AccountCommands.java        # Account management commands
│   │   ├── data/                               # Data storage and models
│   │   ├── mixin/                              # Authentication-related Mixins
│   │   └── util/                               # Utilities (password hashing, etc.)
│   ├── block/                                  # Custom blocks
│   ├── command/                                # Command registration
│   ├── component/                              # Data components
│   ├── event/                                  # Event listeners
│   ├── feature/                                # Experimental feature system
│   ├── item/                                   # Items and tools
│   ├── luckperms/                              # LuckPerms integration
│   ├── mixin/                                  # General Mixins
│   ├── network/                                # Network packets
│   ├── placeholders/                           # Placeholder system
│   └── screen/                                 # Container menus
│
├── client/java/top/csituka/youzaiworldcore/    # Client-only code
│   ├── client/Client.java                      # Client entry point
│   ├── network/ClientNetworking.java           # Client network handling
│   ├── mixin/client/                           # Client Mixins
│   ├── renderer/entity/                        # Entity renderers
│   └── screen/                                 # GUI screens
│       ├── MenuScreen.java                     # Main menu screen
│       ├── element/                            # Menu group elements
│       ├── widget/                             # UI widgets
│       └── block/                              # Block GUI screens
│
└── main/resources/                             # Resource files
    ├── assets/youzaiworldcore/                 # Assets (language files, textures, etc.)
    └── data/                                   # Data packs (advancements, recipes, loot tables, etc.)
```

---

## 📦 Recipe List

| Recipe | Type | Description |
|--------|------|-------------|
| `yz_ingot_from_blasting_raw_yz` | Blasting | Raw YZ → YZ Ingot |
| `yz_ingot_from_yz_block` | Crafting | YZ Block → 9 YZ Ingots |
| `yz_ingot_from_nuggets` | Crafting | 9 YZ Nuggets → YZ Ingot |
| `yz_block` | Crafting | 9 YZ Ingots → YZ Block |
| `yz_nugget_from_ingot` | Crafting | YZ Ingot → 9 YZ Nuggets |
| `yz_pickaxe` / `yz_axe` / `yz_shovel` / `yz_hoe` / `yz_sword` | Crafting | YZ tool series |
| `decomposition_table` | Crafting | Decomposition Table |
| `fly_beacon` | Crafting | Fly Beacon |
| `heart_of_guardianship` | Crafting | Heart of Guardianship |
| `void_staff` | Crafting | Void Staff |
| `raw_yz_block` / `raw_yz_from_raw_yz_block` | Crafting | Raw ore block conversion |
| `yz_block_from_blasting_raw_yz_block` | Blasting | Raw YZ Block → YZ Block |

---

## 🌐 Related Links

- **Official Website**: [https://mcyzw.top](https://mcyzw.top)
- **GitHub Repository**: [https://github.com/Youzai-World-Team/YouzaiWorldCore](https://github.com/Youzai-World-Team/YouzaiWorldCore)
- **Issue Tracker**: [Issues](https://github.com/Youzai-World-Team/YouzaiWorldCore/issues)

---

## 🤝 Contributors

**Core Authors**: ress2338396, zxabinbina, Maskviva, Youzai World Team  
**Contributors**: why, zhongbilibili, Everyone who has contributed to this project

---

> **Note**: Test the mod on a server environment; running it on the client alone will not function correctly.
