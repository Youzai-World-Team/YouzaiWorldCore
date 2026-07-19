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

## 📖 Project Overview

**YouzaiWorldCore** is the core gameplay mod for the **Youzai World** Minecraft multiplayer server, built on the **Fabric** framework with deep integration of **LuckPerms** permission system and **Placeholder API**. The mod provides a comprehensive infrastructure for the server, covering account authentication, GUI menus, custom items and blocks, sit interaction, dimension pools, teleport anchors, mana system, invisibility management, adventure level & attribute growth, enchantment-level language patches, pickup display, world enhancement features (charged creepers / dragon elytra drop / end portal, etc.), a pet system, item highlighting, beginner tutorial, voice chat integration, and 20+ core features in total.

### Target Audience

| User Type | Description |
|-----------|-------------|
| **Server Administrators** | Manage the system through commands and menus, configure dimension pools, account policies, pet backups, etc. |
| **Survival Players** | Use Youzai tools, advancement system, teleport anchors, sit interaction, mana staves, pets and attribute growth for gameplay |
| **Mod Developers** | Understand the mod architecture, extend functionality, or contribute code |

> **Version note**: This mod targets Minecraft **Java 26.2**. From 26.1 onward Mojang adopted new naming/source conventions; the game jar is deobfuscated and can be decompiled directly for reference.

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

### 5. Youzai Tools & Items

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

### 6. Custom Blocks

| Block | Properties |
|-------|------------|
| **YZ Ore / Deepslate Youzai Ore** | Overworld generation, drops 2–5 XP, requires diamond pickaxe |
| **Raw Youzai Block / Youzai Block** | Mineral storage blocks |
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
- **Auto-Regen**: +1 every 20 ticks (1 second)
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
- **Rendering**: `TeleportAnchorBlockEntityRenderer` draws per-player textures via `RenderState` + programmatic custom geometry (`queue.submitCustomGeometry`); no external model files
- **Village Structure Injection**: `VillageStructureInjector` replaces `town_centers` template pools in 5 vanilla village types (plains/desert/savanna/snowy/taiga) with custom meeting point structures containing teleport anchors (based on vanilla Jigsaw / template-pool API)
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

- **Command**: `/yzwc function invisibility <true/false>` (requires OP 4 or equivalent; **client command**, forwarded via `InvisibilityPayload`)
- **Behavior**: Fake leave message → tab list removal → entity removal from vision → white boss bar "Invisible"
- **8 Mixins**: Suppress particles, sounds, block events, and container animations (chest/barrel/ender chest/shulker box/decorated pot) generated by invisible players
- **Tick Check**: Every 10 ticks, verifies Creative mode; auto-forces off if left

### 12. Adventure Level & Attribute System

A player-behavior-based experience level system coupled with an allocatable attribute-growth system, sharing one progression framework.

- **Adventure Level (XP)**
  - **XP Sources**: Mine 50 blocks (+25), Place 50 blocks (+25), Death (+10), Guardian Heart protects (+50), Totem of Undying triggers (+500), Complete advancement (+50)
  - **Level Formula**: `expForNext = 50 + level × 50`
  - **Network Sync**: `LevelExpSyncPayload` (S→C) synchronizes XP values
- **Attribute System**
  - Attribute points earned on level-up can be allocated via the `/yzwc` attribute menu (GUI element), mapped onto 10 vanilla attributes: `MAX_HEALTH`, `MOVEMENT_SPEED`, `JUMP_STRENGTH`, `LUCK`, `ATTACK_DAMAGE`, `BLOCK_BREAK_SPEED`, etc.
  - **Client HUD**: `AdventureLevelHudRenderer` renders level & attributes
  - **Network Sync**: `AttributeSyncPayload` (S→C) syncs attribute data; `AttributeUpgradePayload` (C→S) requests a point allocation
  - **Storage**: `config/youzaiworldcore/skill_module/player_level_data.json` and `player_attributes_data.json` (per-player)

### 13. Placeholder System

Integrates Placeholder API with `%luckperms_*%` namespace, providing **32 placeholders** (11 static + 21 dynamic with parameters): `prefix`, `suffix`, `meta`, `meta_all`, `prefix_element`, `suffix_element`, `context`, `groups`, `inherited_groups`, `primary_group_name`, `has_permission`, `inherits_permission`, `check_permission`, `in_group`, `inherits_group`, `on_track`, `has_groups_on_track`, `highest_group_by_weight`, `lowest_group_by_weight`, `highest_inherited_group_by_weight`, `lowest_inherited_group_by_weight`, `highest_group_weight`, `current_group_on_track`, `next_group_on_track`, `previous_group_on_track`, `first_group_on_tracks`, `last_group_on_tracks`, `expiry_time`, `inherited_expiry_time`, `group_expiry_time`, `inherited_group_expiry_time`, etc.

### 14. Permission System

Fine-grained LuckPerms-based permission control with automatic OP-level fallback. All commands and features route authorization through `luckperms/LuckPermsHelper`, providing **20+ distinct permission nodes** including `account.mgr.*`, `command.*`, and `*` wildcards.

### 15. Creative Mode Tabs

The creative menu has been reorganized into **5 independent tabs**:

| Tab ID | Name | Contents |
|--------|------|----------|
| `youzai_blocks` | Youzai Blocks | 7 custom blocks |
| `youzai_tools_weapons` | Youzai Tools & Weapons | 5 tools + 3 staves |
| `youzai_materials` | Youzai Materials | Raw ore, ingot, nugget |
| `youzai_utilities` | Youzai Utilities | Heart of Guardianship, Logo |
| `youzai_kits` | Youzai Kits | 4 preset shulker boxes |

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

- **Youzai World** (main): Obtain Youzai materials, craft tools, use decomposition table / fly beacon / heart of guardianship / void staff
- **Fun Little Challenges**: The Cake Is a Lie, Foodie, Max Luck, Way Home, etc.

### 18. Debug & Configuration

| Config | File Location | Contents |
|--------|--------------|----------|
| Server External Settings | `config/youzaiworldcore/server_external_settings.json` | `devModeEnabled`, `logToFile` (dual-toggle for DebugLogger) |
| Client External Settings | `config/youzaiworldcore/client_external_settings.json` | `devModeEnabled`, `logLevel` (0–3), debug address/port |
| DebugLogger | `util/DebugLogger` | 4 log levels (OFF/BASIC/DETAILED/DEBUG), entering/exiting/branch/stateChange/exception tracing |

### 19. Enchantment Level Language Patch System

A capability-style system that patches the rendering of **enchantment levels** and **potion potencies** in Minecraft language files (vanilla, resource packs, and mods), allowing high levels to be displayed in a more readable form (e.g. Chinese numerals).

- **API (`EnchantmentLevelLangPatch`)**: `registerPatch` for general key-predicate patches; `registerEnchantmentPatch` / `registerPotionPatch` to register dedicated enchantment-level / potion-potency render hooks; `intToRoman` provides int-to-roman conversion (1–3998, table-based)
- **Chinese Numeral Patch (`ChineseExchange`)**: Renders level numbers as simplified Chinese (一二三…) or uppercase Chinese (壹贰叁…), with `NumResultCacheMap` caching and a `ValueTableHolder` value table for performance
- **Config Switching**: `EnchantmentLevelLangPatchConfig.setCurrentEnchantmentHooks` / `setCurrentPotionHooks` selects which patch to enable; `IndependentLangPatchRegistry` manages registrations by namespaced key (`NamespacedKey`)
- **Client Integration**: `EnchantmentLevelLangPatchMixin` injects the patch into the language-loading flow
- **Purpose**: Lets Chinese players read high-level enchantments as "十级" / "一百级" instead of long Roman numerals

### 20. Pickup Display System

Client-side pickup feedback: when picking up items or XP, float the obtained entries in a side / designated screen area to improve pickup awareness.

- **Pipeline**: `AddEntriesHandler` receives pickup events and enqueues them → `PendingPickupQueue` holds pending entries → `DrawEntriesHandler` renders them each frame
- **Entry Types**: `DisplayEntry` abstract base, with concrete `ItemDisplayEntry` (items, including count/stack info) and `ExperienceDisplayEntry` (XP)
- **Client Integration**: `PickUpNotifyMixin` intercepts pickup notifications to drive the display; `ClientNetworking` handles client-side network logic

### 21. World Enhancement Features

A set of native, dependency-free "world tweak" enhancements (inspired by classic community gameplay) covering mob behavior, drop collection, and End mechanics:

- **Naturally Charged Creepers**: When a creeper enters the server world, it is marked charged with a configurable probability (`chance`, default 0.1 / 10%). The charge is written via the `DATA_IS_POWERED` entity data exposed through a Mixin, ensuring correct client-side lightning halo sync; a data tag dedups to avoid re-rolls on chunk reload. Config `config/youzaiworldcore/charged_creeper.json` (`enabled` default true, `chance` default 0.1, auto-clamped to [0,1]). Commands: `/yzwc event naturally_charged_creepers enable [true|false]` / `settings chance [double]`
- **Chorus Fruit Drops Nearby**: After a chorus plant is broken, its dropped chorus fruit is teleported to the nearest recently-broken chorus plant location (horizontal distance < 20 blocks within a 2-second window), preventing fruit from scattering everywhere
- **Dragon Drops Elytra**: When the Ender Dragon is slain, an extra elytra drops and a broadcast message is sent; kill attribution priority: direct player → projectile owner (bow/crossbow/trident) → nearest player within a 30-block radius
- **End Portal Enhancements**: ① End portal frames can be broken with a silk-touch pickaxe and drop (including embedded ender eyes) while clearing the activated portal blocks; ② An extra dragon egg is granted to nearby players when the Ender Dragon is slain; ③ New recipe `craftable_end_portal` (ender eyes + dragon egg + end stone → 12 end portal frames). Config `config/youzaiworldcore/end_portal_settings.json` with three toggles (silk-touch requirement / direct-to-inventory / dragon-egg message)

### 22. Double Doors System

A streamlined implementation that supports click-to-open only for "same-material wooden doors / fence gates", with a per-player independent toggle.

- **Trigger**: Right-click a door / fence gate with an empty hand; `DoorBlockMixin` / `FenceGateBlockMixin` calls `DoubleDoorsHandler.onDoorClick` after `useWithoutItem` performs the vanilla toggle; sneaking disables double-open, keeping only the vanilla single-open behavior
- **Pairing Rule**: Searches adjacent, same-type (both `DoorBlock` or both `FenceGateBlock`), same display-name (material) partner doors within a 3×3 horizontal area and synchronizes them to the clicked door's open/closed state; no recursion (adjacent pairs only)
- **Supported Scope**: Wooden doors (including double doors), fence gates (auto-aligned facing); iron doors (not hand-openable), trapdoors, redstone triggers, villager AI, and chain opening are out of scope
- **Per-Player Toggle**: `/yzwc function double_doors [true|false]` (**client command**) controls the player's own setting; omitting the argument queries the player's own status; new players enabled by default
- **Persistence**: `config/youzaiworldcore/double_doors_players.json`, storing only players explicitly set via command (`DoubleDoorsState`; unset players fall back to the default enabled state)
- **Client Forwarding Architecture**: The `/yzwc` root command is registered on the client (for `/yzwc settings` and forwarding-type subcommands), so double doors, invisibility, and experimental features only parse and forward on the client; the authoritative state is held by the server via `DoubleDoorsTogglePayload` / `InvisibilityPayload` / `ExperimentalFeaturePayload` (C→S)

### 23. Pet System

A tamed-wolf (Wolf) tracking and management system that registers tamed wolves as "pets" with persistent ownership, trust, and behavior management.

- **Core Data**: `PetEntry` records internal name (`internalName`), display name, behavior mode, owner UUID, trusted players, tame time, and entity UUID; global registry `PetGlobalState` persists all pets
- **Behavior Modes**: `hunting` / `companionship` / `attack` / `guard` — switched via `/yzwc pet set <internalName> mode`
- **Trust System**: Owners can add other players to a trust list; trusted players may view the list and highlight the pet; `trust add/remove/list <player>`
- **Ownership Ops**: `rename`, `transfer <newOwner>` (transfers ownership; former owner auto-added to trust list), `release_life [force]` (release, requires confirmation)
- **Quick Locate**: `highlight <internalName>` applies a 5-second Glowing effect to the target wolf for easy spotting
- **Admin Ops**: `admin restore` (restore from latest backup), `admin backup_list` (list backups), `admin backup_interval <seconds>` (set scheduled backup interval, 60–3600s)
- **Persistence**: `config/youzaiworldcore/pet_module/settings.json` + scheduled backups `pet_module/pet_backup_<timestamp>.json`
- **Command Architecture**: `/yzwc pet` is a **client command** that forwards its full argument string via `PetCommandPayload` (C→S); the server-side `PetCommand` holds the complete Brigadier tree and permission checks

### 24. Item Highlight System

A purely client-side feature that renders an outline around the held or targeted item, helping players quickly locate items in the inventory / world (no server-side effect).

- **Command**: `/yzwc settings highlight_item`
  - `toggle` —— enable/disable highlight
  - `color <name | custom r g b a>` —— preset color or custom RGBA (r/g/b 0–255, a 0.0–1.0)
  - `mode <comparator>` —— choose the item-matching rule that triggers highlight (`ItemComparator.Comparators`)
- **Implementation**: `highlightitem` package (`HighlightItem` / `Configurator` / `Colors` / `ItemComparator`) injects the outline via a client-side Mixin on the render layer; all configuration applies immediately through client commands

### 25. Experimental Features

The experimental feature system framework is fully implemented, supporting server-wide toggle + player-level override + server-controlled mode (`serverSide`). The registration API `ExperimentalFeatures.register(...)` and config persistence (`config/youzaiworldcore/experimental_feature/server_settings.json` / `client_settings.json`) are ready, and sync runs through `FeatureSyncPayload`.

> **Current Status**: The framework is implemented but **no experimental feature is currently registered** (`REGISTRY` is empty). The dimension pool system has graduated from experimental status and is now enabled as a core feature.

---

## 📜 Command Tree

All commands use `/yzwc` as the root command. Subcommands marked **(client command)** only parse arguments and forward them; the authoritative logic runs in the server-side packet receiver.

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
├── event naturally_charged_creepers
│   ├── enable [true|false]               → Enable/disable naturally charged creepers (omit to query)
│   ├── settings chance [double]           → Set charge probability 0.0~1.0 (omit to query)
│   └── Permissions: .query (everyone) / .set (OP 4)
│
├── pet <args...>                         ← (client command, forwarded to server)
│   ├── list                                          → List own and trusted pets
│   ├── set <internalName> rename <newName>           → Rename (owner)
│   ├── set <internalName> mode <hunting|companionship|attack|guard> → Switch behavior mode
│   ├── set <internalName> trust add|remove <player> | trust list → Trust management
│   ├── set <internalName> release_life [force]       → Release (confirmation required)
│   ├── set <internalName> transfer <newOwner>        → Transfer ownership
│   ├── highlight <internalName>                      → Highlight (Glowing 5s)
│   └── admin restore | backup_list | backup_interval <sec> → Admin backup/restore
│
├── function invisibility <true/false>    ← (client command)
│   ├── Permission: youzaiworldcore.command.function.invisibility (OP 4)
│   └── Requires Creative mode
│
├── function double_doors <true|false>    ← (client command)
│   ├── Permission: youzaiworldcore.command.function.double_doors (self, everyone can run)
│   └── Omit to query own status; new players enabled by default; state persisted to double_doors_players.json
│
├── experimental_feature <id> [true/false [all|only <player>]]   ← (client command)
│   ├── Permissions: .query (everyone) / .self (everyone) / .admin (OP 4)
│   └── Query or toggle experimental features (forwarded to server)
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

> **Client command note**: `/yzwc pet`, `/yzwc function invisibility`, `/yzwc function double_doors`, and `/yzwc experimental_feature` are registered on the client and only parse arguments, forwarding them through the corresponding C→S packets (`PetCommandPayload` / `InvisibilityPayload` / `DoubleDoorsTogglePayload` / `ExperimentalFeaturePayload`); the server holds the authoritative state and permission checks. All other subcommands (`teleport_world` / `open_menu` / `world_pool` / `teleport_anchor` / `event` / `reload` / `account`) are server-side.

### Permission Nodes Overview

| Permission Node | Description | Fallback |
|----------------|-------------|----------|
| `youzaiworldcore.command.teleport_world` | Cross-dimension teleport | OP 4 |
| `youzaiworldcore.command.open_menu` | Open GUI menu | OP 4 |
| `youzaiworldcore.command.reload` | Mod reload | OP 4 |
| `youzaiworldcore.command.world_pool` | Dimension pool management | OP 4 |
| `youzaiworldcore.command.teleport_anchor` | Teleport anchor management | OP 4 |
| `youzaiworldcore.command.function.invisibility` | Invisibility function | OP 4 |
| `youzaiworldcore.command.function.double_doors` | Double Doors function (self toggle / query) | Everyone (self-only) |
| `youzaiworldcore.command.experimental_feature` | Experimental feature (basic) | Everyone |
| `youzaiworldcore.command.experimental_feature.query` | Query | Everyone |
| `youzaiworldcore.command.experimental_feature.self` | Self-toggle | Everyone |
| `youzaiworldcore.command.experimental_feature.admin` | Admin | OP 4 |
| `youzaiworldcore.command.event.query` | Event management query (omit arg = query) | Everyone |
| `youzaiworldcore.command.event.set` | Event management modify (enable / settings) | OP 4 |
| `youzaiworldcore.command.pet.list` | View pet list | Everyone |
| `youzaiworldcore.command.pet.set` | Pet settings (rename/mode/trust/release/transfer) | Everyone (own pets) |
| `youzaiworldcore.command.pet.highlight` | Highlight pet | Everyone (owner/trusted) |
| `youzaiworldcore.command.pet.admin` | Pet admin (backup/restore/interval) | OP 4 |
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

### Network Packets (21 total)

> Note: the `world_pool_teleport` packet class lives in the `dimensionalinventories` package; the other 20 are in the `network` package.

| Packet ID | Direction | Purpose |
|-----------|-----------|---------|
| `open_menu` | S→C | Open GUI menu |
| `open_auth_screen` | S→C | Open auth screen |
| `feature_sync` | S→C | Sync experimental feature states |
| `mana_sync` | S→C | Sync mana values |
| `level_exp_sync` | S→C | Sync adventure level XP |
| `attribute_sync` | S→C | Sync player attribute data (skill points / attributes / level) |
| `teleport_anchor_list` | S→C | Send point list |
| `teleport_anchor_open_name` | S→C | Open anchor naming screen |
| `world_pool_teleport` | C→S | Request dimension pool teleport |
| `teleport_anchor_activate` | C→S | Activate anchor |
| `teleport_anchor_teleport` | C→S | Request teleport |
| `teleport_anchor_delete` | C→S | Delete point |
| `teleport_anchor_rename` | C→S | Rename point |
| `teleport_anchor_reorder` | C→S | Reorder points |
| `decompose_item` | C→S | Decompose item |
| `fly_beacon_active` | C→S | Toggle fly beacon |
| `invisibility_toggle` | C→S | Toggle / disable own invisibility |
| `experimental_feature` | C→S | Forward experimental feature command (query / self / all / specific player) |
| `attribute_upgrade` | C→S | Request to allocate a point to an attribute |
| `double_doors_toggle` | C→S | Toggle / query own Double Doors setting |
| `pet_command` | C→S | Forward `/yzwc pet` client command to server |

---

## 🔧 Tech Stack & Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Minecraft | 26.2 | Engine |
| Fabric Loader | 0.19.3 | Mod loader |
| Fabric API | 0.154.0+26.2 | Standard API |
| ModMenu | 20.0.0-beta.4 | Mod menu integration |
| Placeholder API | 3.1.0-beta.1+26.2 | Text placeholders |
| Moog's Structure Lib | 3.0.4 | Declared dependency (referenced by village structure injection) |
| Fabric Permissions API | 0.6.1 (bundled) | Cross-mod permission API |
| LuckPerms | 5.5 (suggested runtime) | Advanced permission control |

**Build Requirements**: JDK 25+ · Gradle (Fabric Loom 1.16-SNAPSHOT) · Mod version `1.19.0-indev`

**Build & CI/CD**: GitHub Actions workflow (`.github/workflows/build.yml`) auto-builds on Ubuntu / Windows / macOS with JDK 25, with automatic Linux artifact upload.

---

## 🏗️ Project Structure

```
src/
├── main/java/top/csituka/youzaiworldcore/
│   ├── YouzaiworldCore.java              # Main entry point
│   ├── account/                          # Account auth (data/command/mixin/util subpackages)
│   ├── block/ + entity/                  # Custom blocks & block entities
│   ├── command/                          # Command registration (TeleportAnchor / Reload / Event)
│   ├── component/                        # Data components
│   ├── config/                           # Server external settings (charged creeper / end portal / double doors / pet config)
│   ├── data/                             # Teleport anchor SavedData
│   ├── dimensionalinventories/           # Dimension pool system (incl. WorldPoolTeleportPayload)
│   ├── enchlevellangpatch/               # Enchantment-level language patch (api + impl)
│   ├── event/                            # Event handlers (fly beacon, double doors, end portal, void staff, dragon, chorus, charged creeper, decompose, sit, etc.)
│   ├── entity/seat/                      # Seat entity system
│   ├── feature/                          # Experimental features (ExperimentalFeatures registration framework)
│   ├── invisibility/                     # Invisibility system
│   ├── item/                             # Items, tools, creative tabs, presets
│   ├── luckperms/                        # LuckPerms integration (LuckPermsHelper unified auth)
│   ├── mana/                             # Mana system
│   ├── mixin/                            # Mixins (subpackages: chargedcreeper / doubledoors / invisibility / pet / seat / skill)
│   ├── network/                          # Network packets (20 packet classes + ModNetworking)
│   ├── pet/                              # Pet system (config/command/event subpackages + PetGlobalState/PetEntry)
│   ├── placeholders/                     # Placeholder API (32 placeholders)
│   ├── screen/                           # Container menus
│   ├── skill/                            # Adventure level + attribute system
│   ├── util/                             # DebugLogger, etc.
│   └── worldgen/                         # World generation (VillageStructureInjector)
│
├── client/java/top/csituka/youzaiworldcore/
│   ├── client/Client.java                # Client entry point
│   ├── command/                          # Client commands (ExperimentalFeature / Invisibility / DoubleDoors / Pet forwarding)
│   ├── config/                           # Client external settings
│   ├── effect/                           # Teleport FOV effect
│   ├── higherchat/                       # Simple Voice Chat integration (HUD icon position tracking)
│   ├── highlightitem/                    # Item highlight (HighlightItem / Configurator / Colors / ItemComparator)
│   ├── hud/                              # Mana bar / adventure level HUD
│   ├── mixin/client/                     # Client Mixins (title, options, button, pause, chat, loading, seat, rendering, pickup, enchant-patch, etc.)
│   ├── network/                          # Client network handling (ClientNetworking)
│   ├── pickup/                           # Pickup display (item/XP floating notifications)
│   ├── renderer/                         # Block/entity renderers (incl. teleport anchor BER)
│   └── screen/                           # GUI screens (MenuScreen, Login/Register, element/widget/block subpackages)
│       └── skill/                        # Client adventure level / attribute menu elements
│
└── main/resources/
    ├── assets/youzaiworldcore/           # Textures, models, language files
    ├── data/                             # Advancements, recipes, loot tables, dimensions, structures, structure sets, template pools, beginner tutorial functions
    └── fabric.mod.json                   # Mod metadata (declares moogs_structures as a hard dependency)

.github/workflows/
└── build.yml                             # CI/CD build workflow
```

---

## 📦 Recipe List

| Recipe | Type | Description |
|--------|------|-------------|
| `yz_ingot_from_blasting_raw_yz` | Blasting | Raw Youzai → Youzai Ingot |
| `yz_block_from_blasting_raw_yz_block` | Blasting | Raw Block → Youzai Block |
| `yz_ingot_from_yz_block` | Crafting | Youzai Block → 9 Ingots |
| `yz_ingot_from_nuggets` | Crafting | 9 Nuggets → Ingot |
| `yz_block` | Crafting | 9 Ingots → Block |
| `yz_nugget_from_ingot` | Crafting | Ingot → 9 Nuggets |
| `yz_pickaxe` / `yz_axe` / `yz_shovel` / `yz_hoe` / `yz_sword` | Crafting | Youzai tools |
| `decomposition_table` | Crafting | Decomposition Table |
| `fly_beacon` | Crafting | Fly Beacon |
| `heart_of_guardianship` | Crafting | Heart of Guardianship |
| `void_staff` | Crafting | Void Staff |
| `flame_staff` | Crafting | Flame Staff |
| `sky_star_staff` | Crafting | Sky Star Staff |
| `raw_yz_block` / `raw_yz_from_raw_yz_block` | Crafting | Ore block conversion |
| `craftable_end_portal` | Crafting | End Portal Frame ×12 (ender eyes + dragon egg + end stone) |

---

## 🌐 Related Links

- **Official Website**: [https://mcyzw.top](https://mcyzw.top)
- **GitHub Repository**: [Youzai-World-Team/YouzaiWorldCore](https://github.com/Youzai-World-Team/YouzaiWorldCore)
- **Issue Tracker**: [Issues](https://github.com/Youzai-World-Team/YouzaiWorldCore/issues)

---

## 🤝 Contributors

**Core Authors:** Maskviva, ress2338396, zxabinbina, Youzai World Team  
**Contributors:** byzzdemy, Fogg05, lucko, MDLC01, why

---

> **Note**: Test the mod on a server environment; running it on the client alone will not function correctly. Client commands (`/yzwc pet`, `/yzwc function *`, `/yzwc experimental_feature`, `/yzwc settings highlight_item`) require being connected to a server to take effect.
