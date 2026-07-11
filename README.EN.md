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

> **Version**: 1.12.5+indev · **Minecraft**: 26.2 · **Loader**: Fabric Loader 0.19.3  
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
- **Server Administration**: GUI menu navigation, Dimension Pool system (independent inventories/states/gamemodes), world switching, and admin operation tools
- **Player Experience Optimization**: Advancement system, placeholder integration

### Target Audience

| User Type | Description |
|-----------|-------------|
| **Server Administrators** | OPs/Admins operating the Youzai World server, managing the system through commands and menus |
| **Survival Players** | Regular players on the server, using YZ tools, advancement system, world teleportation, etc. |
| **Mod Developers** | Developers who want to understand the mod architecture, extend functionality, or contribute code |

---

## ✨ Feature Overview

### 1. Account Authentication System

A complete offline-mode account system supporting password registration, login, logout, and admin management.

- **Password Security**: BCrypt salted hash storage (compatible with legacy SHA-256), with login attempt limits (max 5 attempts)
- **Login Cooldown/Lock**: Configurable lockout duration after failed attempts — supports permanent lock, timed cooldown (auto-unlock), and never-lock modes; admin query/unlock tools available
- **Session Management**: Configurable session timeout with automatic recovery on reconnection
- **Location Persistence**: Automatically saves player position on logout and teleports them to the void; restores position precisely on login; JSON persistent storage prevents overwrites on reconnect
- **Login Hall**: Unauthenticated players are restricted to the custom `login_hall` dimension with Mixin-based blocking of movement, interaction, attacking, and chat
- **Admin Tools**: Create offline accounts, reset passwords, delete accounts, configure session timeout, manage login locks
- **Invisibility Integration**: Sensitive account operations (logout, deactivate, change password) are blocked while invisible

### 2. GUI Menu System

A comprehensive graphical menu system built on Fabric Screen, featuring a Windows 10 Start Menu-style tile layout with animated transition effects.

| Menu | Description |
|------|-------------|
| **Main Menu** | Feature hub containing tile buttons for world switching, events, check-in, tutorials, settings, etc. |
| **Switch World** | Displays 11 teleportable world tile buttons. The first 7 (Survival, Kingdom, Gameplay, Creative, Building, Command Zone, Tutorial World) are integrated with the Dimension Pool system, sending `WorldPoolTeleportPayload` packets for full inventory/state/gamemode switching; the remaining buttons retain legacy chat-command behavior |
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
| **Heart of Guardianship** | Prevents item drop on death when carried (Mixin-based); consumes one per death; threshold warnings at 10/5/3/2/1 remaining |
| **Void Staff** | Right-click to toggle flight mode; consumes 1 durability per second and hunger every 5 seconds; auto-disables when durability or hunger depletes |
| **Logo (Youzai World)** | Server identity item |

### 4. Custom Blocks

| Block | Properties |
|-------|------------|
| **YZ Ore / Deepslate YZ Ore** | Generates in the Overworld, drops XP (2-5), requires diamond pickaxe |
| **Raw YZ Block / YZ Block** | Mineral storage blocks |
| **Decomposition Table** | GUI-based block used to decompose items back to raw materials |
| **Fly Beacon** | Grants area flight capability, toggleable activation state |

### 5. Advancement System

Contains two advancement branches:

- **Youzai World** (main progression): Covers obtaining YZ ore/ingots/blocks/tools, using the decomposition table, fly beacon, heart of guardianship, void staff, etc.
- **Fun Little Challenges**: The Cake Is a Lie, Foodie, Get Emerald Blocks, Like Cows and Pigs, Max Luck, Stuck in Cobweb, Way Home, Wearing Copper Armor

### 6. Invisibility System

A deceptive player invisibility system — the invisible player completely disappears from other players' Tab lists and vision, with fake leave/join messages broadcast to others.

- **Requirements**: OP level 4 or `youzaiworldcore.command.function.invisibility` permission node; player must be in Creative mode
- **Behavior**: Appears as leaving to other players → removed from Tab list → entity removed from vision → shows white boss bar "Invisible" to self
- **State Recovery**: Disabling invisibility restores entity visibility, Tab list appearance, and broadcasts a fake join message
- **Auto-Cancel**: Automatically forces invisibility off when leaving Creative mode
- **Disconnect Cleanup**: Cleans up all invisibility state on player disconnect

### 7. Experimental Feature System

Supports server-wide global toggle + player-level override for experimental features, with state configuration persisted to JSON files. Supports **server-controlled** mode (`serverSide`), where server-controlled features are not stored in client configuration and do not allow player overrides.

### 8. Placeholder System (Placeholder API)

Integrates Placeholder API and LuckPerms placeholders, supporting dynamic/static placeholder resolution. The mod registers `%luckperms_*%` placeholder series for use by other mods.

### 9. Permission System

Provides fine-grained permission control based on **LuckPerms**, with automatic fallback to vanilla OP level checks. Includes granular sub-permissions for account management commands (e.g., `youzaiworldcore.command.account.mgr.*`).

### 10. Client External Settings

A client-side persistent configuration system, stored at `config/youzaiworldcore/client_external_settings.json`:

| Setting | Description |
|---------|-------------|
| **Dev Mode** | Enables developer mode features |
| **Log to File** | Outputs debug logs to latest.log |
| **Debug Mode Type** | "embedded" (integrated server) / "dedicated" (separate server) |
| **Debug Address/Port** | Connection config for dedicated server debug mode |

### 11. Server External Settings

A server-side persistent configuration system, stored at `config/youzaiworldcore/server_external_settings.json`:

| Setting | Description |
|---------|-------------|
| **Dev Mode** | Enables developer mode (server-side; works together with logToFile to control debug logging) |
| **Log to File** | Outputs detailed noisy logs (experimental feature registration, config loading, account data, etc.) to latest.log |

The dual-toggle system (`devModeEnabled` + `logToFile`) works in tandem: debug output is only produced when **both** toggles are enabled.

### 12. Debug Logging System (DebugLogger)

A professional debug logging utility, controlled by the dual-toggle system, providing comprehensive tracing methods:

| Method Category | Description |
|----------------|-------------|
| **entering / exiting** | Method entry/exit tracing with optional parameter/return value logging |
| **branch** | Conditional branch decision logging with result and context |
| **stateChange** | State change logging with old → new value comparison |
| **exception** | Exception catch-block logging with full stack trace or summary |
| **trace / debug / info / warn / error** | Standard log levels |

Log format: `[yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] [Module] description`

### 13. Window Customization

- **Custom Window Icon**: Replaces the default Minecraft window icon (taskbar and title bar) with `jar_icon.png` from mod assets
- **Custom Window Title**: Intercepts `Window.setTitle()` via `WindowTitleMixin`, replacing the title with the mod's custom format (`YouzaiWorldCore 1.12.5+indev — Minecraft 26.2`)

### 14. Title Screen Overhaul

The main menu screen has been fully redesigned via `TitleScreenMixin`:

- **Custom Buttons**: Join Server, Options, Quit Game buttons using the custom `TitleScreenTextButton` widget
- **Announcement Banner**: Displays mod announcements (title + server description) above the title screen with fade-in animation
- **Gradient Background**: Intercepts loading screen background rendering via `ScreenMixinForProgressBg` to add gradient backgrounds
- **Logo Texture**: Replaces Mojang loading screen logo texture with custom resources via `LogoTextureMixin`
- **Debug Mode**: Additional "Test Page" button visible when developer mode is enabled

### 15. CI/CD Build

The project includes a GitHub Actions continuous integration workflow (`.github/workflows/build.yml`):

- Automatically builds on **Ubuntu / Windows / macOS** platforms
- JDK 25
- Automatically uploads Linux build artifacts on success

### 16. Dimension Pool System

A multi-world server management system providing **independent state pools** — players have separate inventories, health, status effects, and game modes across different dimension pools.

- **How it works**: Dimensions are grouped into "pools"; when a player crosses between pools, state save/load happens automatically
- **7 predefined pools**: Survival World (with vanilla 3 dimensions), Main City, Gameplay, Creation, Building, Commands, Tutorial World
- **Pool switching flow**: Check target pool → save current state → clear inventory + remove effects → load target pool state → teleport → force-set game mode
- **Default spawn**: Each pool can independently configure landing coordinates for players returning after death in another pool
- **Cross-pool teleportation**: Supports dimension portals, command teleportation, respawn events, and other trigger methods
- **Configuration persistence**: Pool definitions stored at `config/youzaiworldcore/dimensional_inventories/pool_settings.json`; player states stored at `<world>/youzaiworldcore/dimensional_inventories/data/<pool-id>/<uuid>.json`
- **Experimental**: The dimension pool system is currently managed as an experimental feature (ID: `dimension_pool`), disabled by default

### 17. Preset Item System

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
├── world_pool
│   ├── Description: Dimension pool management system (requires experimental feature dimension_pool enabled)
│   ├── Permission: youzaiworldcore.command.world_pool (or OP level 4)
│   │
│   ├── teleport <targets> <dimension_pool>
│   │   ├── Description: Teleports target player(s) to a specified dimension pool
│   │   ├── Parameters:
│   │   │   • targets — Target player(s) (supports multiple players)
│   │   │   • dimension_pool — Target pool ID (Tab-completion supported)
│   │   ├── Available pool IDs: survival_world_pool, main_city_pool, gameplay_pool, creation_pool, building_pool, commands_pool, tutorial_world_pool
│   │   └── Examples:
│   │       /yzwc world_pool teleport @p survival_world_pool
│   │       /yzwc world_pool teleport @a building_pool
│   │
│   └── list
│       ├── Description: Lists all dimension pools with their dimensions, game mode, and advancement/stat toggles
│       └── Examples:
│           /yzwc world_pool list
│
├── function
│   └── invisibility <true/false>
│       ├── Description: Toggle invisibility mode (requires Creative mode)
│       ├── Permission: youzaiworldcore.command.function.invisibility (or OP level 4)
│       ├── Requirement: Player must be in Creative mode; auto-cancels when leaving Creative
│       └── Examples:
│           /yzwc function invisibility true    ← Enable invisibility
│           /yzwc function invisibility false   ← Disable invisibility
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
│   │   • id — Experimental feature internal ID
│   │   • true/false — Enable/disable (optional; omit for query mode)
│   │   • all — Toggle server-wide (requires admin permission)
│   │   • only <player> — Toggle for a specific player (requires admin permission)
│   └── Examples:
│       /yzwc experimental_feature <id>              ← Query status
│       /yzwc experimental_feature <id> true         ← Enable for self
│       /yzwc experimental_feature <id> false all    ← Disable server-wide
│       /yzwc experimental_feature <id> true only Steve ← Enable for Steve
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
    │   │   ├── Restriction: Max 5 attempts; locked beyond limit per cooldown config
    │   │   └── Example: /yzwc account login MyPass123
    │   │
    │   ├── logout
    │   │   ├── Description: Log out of an account and teleport to the End void
    │   │   ├── Permission: None (everyone)
    │   │   ├── Restriction: Cannot logout while invisible
    │   │   └── Example: /yzwc account logout
    │   │
    │   ├── deactivate <password>
    │   │   ├── Description: Deactivate (delete) an account
    │   │   ├── Permission: None (everyone)
    │   │   ├── Restriction: Cannot deactivate while invisible
    │   │   └── Example: /yzwc account deactivate MyPass123
    │   │
    │   └── change_password <oldPassword> <newPassword> <confirmPassword>
    │       ├── Description: Change account password
    │       ├── Permission: None (everyone)
    │       ├── Restriction: Cannot change password while invisible
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
        ├── mgr session_timeout [seconds]
        │   ├── Description: View or set session timeout duration (0 = disabled)
        │   └── Examples:
        │       /yzwc account mgr session_timeout          ← View current value
        │       /yzwc account mgr session_timeout 3600     ← Set to 1 hour
        │
        ├── mgr login_cooldown
        │   ├── Description: Manage login failure lock cooldown system
        │   ├── (no args)         → Display current cooldown setting
        │   ├── set <seconds>     → Set cooldown (-1=never lock, 0=permanent, >0=seconds)
        │   ├── status <player>   → Query a player's lock status
        │   └── unlock <player>   → Unlock a player's account
        │   └── Examples:
        │       /yzwc account mgr login_cooldown                   ← Display settings
        │       /yzwc account mgr login_cooldown set 600           ← Set to 10 minutes
        │       /yzwc account mgr login_cooldown status Steve      ← Query Steve
        │       /yzwc account mgr login_cooldown unlock Steve      ← Unlock Steve
        │
        └── (no subcommand) — Displays account management help
```

### Permission Nodes Overview

| Permission Node | Description | Fallback Level |
|----------------|-------------|----------------|
| `youzaiworldcore.command.teleport_world` | Cross-dimension teleport | OP level 4 |
| `youzaiworldcore.command.open_menu` | Open GUI menu | OP level 4 |
| `youzaiworldcore.command.reload` | Mod reload | OP level 4 |
| `youzaiworldcore.command.world_pool` | Dimension pool management | OP level 4 |
| `youzaiworldcore.command.function.invisibility` | Invisibility function | OP level 4 |
| `youzaiworldcore.command.experimental_feature` | Experimental feature (basic) | Everyone |
| `youzaiworldcore.command.experimental_feature.query` | Experimental feature query | Everyone |
| `youzaiworldcore.command.experimental_feature.self` | Self-toggle experimental feature | Everyone |
| `youzaiworldcore.command.experimental_feature.admin` | Admin experimental feature | OP level 4 |
| `youzaiworldcore.command.account.mgr.create` | Account mgr: create | OP level 4 |
| `youzaiworldcore.command.account.mgr.reset_password` | Account mgr: reset password | OP level 4 |
| `youzaiworldcore.command.account.mgr.delete` | Account mgr: delete | OP level 4 |
| `youzaiworldcore.command.account.mgr.session_timeout` | Account mgr: session timeout | OP level 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown` | Account mgr: login cooldown | OP level 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown.status` | Account mgr: lock status query | OP level 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown.unlock` | Account mgr: unlock account | OP level 4 |
| `youzaiworldcore.command.account.mgr.*` | All account mgr commands wildcard | OP level 4 |
| `youzaiworldcore.command.*` | All commands wildcard | — |
| `youzaiworldcore.*` | Full mod wildcard | — |

---

## 🧪 Experimental Feature Internal ID List

The experimental feature system supports server-wide toggles and per-player overrides, with configuration persisted to `config/youzaiworldcore/experimental_feature/`. The `chicken_warden_model` feature was registered in earlier versions but has since been removed.

| Internal ID | Name | Description | Control Mode | Default State |
|-------------|------|-------------|--------------|---------------|
| `dimension_pool` | Dimension Pool System | Provides independent player inventories, states, and game mode management across dimension pools | 🔒 Server-controlled | ❌ Disabled |

> **Server-Controlled**: `serverSide=true`, this feature can only be toggled globally by the server. It is not stored in client configuration and does not allow player-level overrides.

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
| `switch_world` | Switch World | Main Menu → Switch World | 5-column tile layout with 11 world buttons. The first 7 (Survival, Kingdom, Gameplay, Creative, Building, Command Zone, Tutorial World) use the Dimension Pool system via `WorldPoolTeleportPayload`; the Nether/End/Overworld/Login Hall retain legacy chat-command behavior |
| `settings` | Settings | Main Menu → Settings | General settings (music/sound toggles), gameplay settings (PVP/friendly fire toggles, difficulty dropdown) |
| `about_me` | About Me | Main Menu → About Me | Displays player 3D model render, player ID, first/last join time, playtime duration with fade-in animation |

### Network Packets

Menus communicate between server and client via `OpenMenuPayload` (S2C packet, ID: `youzaiworldcore:open_menu`):

| Packet ID | Direction | Purpose |
|-----------|-----------|---------|
| `youzaiworldcore:open_menu` | Server → Client | Opens a menu screen by name |
| `youzaiworldcore:feature_sync` | Server → Client | Synchronizes experimental feature states |
| `youzaiworldcore:open_auth_screen` | Server → Client | Opens the authentication screen |
| `youzaiworldcore:world_pool_teleport` | Client → Server | Requests teleportation to a specified dimension pool |
| `youzaiworldcore:decompose_item` | Client → Server | Decomposes an item in the decomposition table |
| `youzaiworldcore:fly_beacon_active` | Client → Server | Toggles the fly beacon activation state |

---

## 🔧 Tech Stack & Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Minecraft | 26.2 | Base game engine |
| Fabric Loader | 0.19.3 | Mod loader |
| Fabric API | 0.154.0+26.2 | Fabric standard API |
| ModMenu | 20.0.0-beta.4 | Mod menu integration (settings screen) |
| Placeholder API | 3.1.0-beta.1+26.2 | Text placeholder resolution |
| Fabric Permissions API | 0.6.1 (bundled) | Cross-mod permission API |
| LuckPerms API | 5.5 (compile-only, suggested runtime) | Advanced permission control |

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
│   ├── config/                                 # Server external settings
│   ├── dimensionalinventories/                 # Dimension pool system
│   ├── event/                                  # Event listeners
│   ├── feature/                                # Experimental feature system
│   ├── invisibility/                           # Invisibility system
│   ├── item/                                   # Items and tools
│   ├── luckperms/                              # LuckPerms permission integration
│   ├── mixin/                                  # General Mixins (Guardian Heart, etc.)
│   ├── network/                                # Network packets
│   ├── placeholders/                           # Placeholder system
│   ├── screen/                                 # Container menus
│   └── util/                                   # Utilities (DebugLogger, etc.)
│
├── client/java/top/csituka/youzaiworldcore/    # Client-only code
│   ├── client/Client.java                      # Client entry point (window icon setup)
│   ├── config/                                 # Client external settings
│   ├── higherchat/                             # Chat display optimization
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

.github/
└── workflows/
    └── build.yml                               # GitHub Actions build workflow
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
