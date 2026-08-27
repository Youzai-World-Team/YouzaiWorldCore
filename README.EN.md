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

**YouzaiWorldCore** is the core gameplay mod for the **Youzai World** Minecraft multiplayer server, built on the **Fabric** framework with deep integration of **LuckPerms** permission system and **Placeholder API**. The mod provides a comprehensive infrastructure for the server, covering account authentication, GUI menus, the YZUI interface system (full inventory / HUD / recipe book restyle), custom items and blocks, sit interaction, dimension pools, teleport anchors & warp scrolls, mana system, AFK detection, invisibility management, adventure level & attribute growth, enchantment-level language patches, pickup display, world enhancement features (charged creepers / dragon elytra drop / end portal / warden loot / stonecutter damage / unlimited trial vault rewards, etc.), a pet system, item highlighting & borders, a mailbox, custom enchantments (12), trinket-slot integration with YZUI trinket interaction, music disc, meme paintings, Laowu Meme easter egg, Technoblade memorial crown, config import/export, beginner tutorial, chat box position optimization, and 40+ core features in total.

### Target Audience

| User Type                 | Description                                                                                                                                                        |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Server Administrators** | Manage the system through commands and menus, configure dimension pools, account policies, pet backups, mail announcements, event toggles, etc.                    |
| **Survival Players**      | Use Youzai tools, advancement system, teleport anchors, sit interaction, mana staves, pets and attribute growth, the YZUI interface and trinket slots for gameplay |
| **Mod Developers**        | Understand the mod architecture, extend functionality, or contribute code                                                                                          |

> **Version note**: This mod targets Minecraft **Java 26.2**. From 26.1 onward Mojang adopted new naming/source conventions; the game jar is deobfuscated and can be decompiled directly for reference.

---

## ✨ Feature Overview

### 1. Account Authentication System

Complete password authentication for offline-mode servers, with Mixin-based restrictions on unauthenticated behavior.

- **Password Security**: The Api service uses salted PBKDF2-HMAC-SHA256; the mod never stores or verifies passwords, with a 5-attempt login limit
- **Login Cooldown/Lock**: Triggers after 5 failures, default 300s (5 min) cooldown; supports permanent lock, timed cooldown, and never-lock modes; admin unlock available
- **Connection Authentication**: Players must enter their password on every server join; the short-lived token after login is only validated for the current connection and is revoked on disconnect
- **Position Save/Restore**: Saves position on logout → teleports to End void; restores precisely on login
- **Login Hall**: Unauthenticated players confined to `youzaiworldcore:login_hall` custom dimension; Mixin blocks movement, interaction, attacking, and chat
- **Login/Register GUI**: On entering the login hall, the client auto-opens the register/login screens (`RegisterScreen` / `LoginScreen`, read-only pre-filled username, Enter to log in, Disconnect button), pushed by the server via `OpenAuthScreenPayload`. When the Api setting “email verification required for registration” is enabled, the flow automatically continues in `RegistrationEmailScreen` with code delivery, resend cooldown, and verification
- **Invisibility Integration**: Sensitive operations (logout, deactivate, password change) blocked while invisible
- **Account Deletion Integration**: Deactivating/deleting an account also clears its mailbox (`MailManager.onAccountDeleted`)
- **Custom Skins & Capes**: Offline accounts can upload `skin.png` (wide model), `skin_slim.png` (slim model), and a single 64×32 `cloak.png` from `yzwc/client/config/cosmetic_module/`; after validation, the mod uploads them to the Api service for storage and syncs them to other online players, with no PNG fallback on the Minecraft server. Accounts verified through a Mojang session challenge automatically use their official skin and cape, and local cosmetic uploads are ignored

### 2. GUI Menu System

Windows 10 Start Menu-style tile layout with page switching and animated transitions.

| Menu ID        | Name         | Description                                                            |
| -------------- | ------------ | ---------------------------------------------------------------------- |
| `main`         | Main Menu    | Feature hub: world switching, events, level, tutorials, mail, settings |
| `switch_world` | Switch World | 11 world buttons; first 7 integrated with dimension pool system        |
| `settings`     | Settings     | Music/sound toggles, PVP/friendly fire, difficulty selection (client)  |
| `about_me`     | About Me     | 3D player model render, ID, join/playtime                              |

**Shortcut**: `Shift + F` to open the main menu.

### 3. Title Screen Overhaul

Fully redesigned Minecraft main menu via `TitleScreenMixin`: a left panel with custom buttons (Join Server `play.mcyzw.top` / Options / Quit), a right panel with the announcement + update info block (title/version/date/content/download/ignore, fade-in animation), a gradient background (`GradientBackgroundUtil`), the Mojang logo replaced with custom assets, and a test page button in developer mode. Clicking "Join Server" while a forced update is pending intercepts and shows `ForcedUpdateScreen`.

### 4. Window Customization

- **Custom Window Icon**: Loads `jar_icon.png` at runtime via Java ImageIO to replace taskbar and title bar icons
- **Custom Window Title**: `WindowTitleMixin` intercepts `Window.setTitle()`, replacing the title with `Youzai World Server · Wanderer v<version> | [Minecraft JAVA 26.2]`
- **Quit Confirmation**: `MinecraftQuitMixin` hijacks the window close event — clicking "Quit Game" on the title screen or the window X / Alt+F4 shows a confirmation dialog (`QuitConfirmationScreen`); the game only exits after confirmation
- **Respawn Here**: Splits the vanilla respawn row into two half-width buttons in enabled dimensions. Respawning at the death position preserves inventory, does not consume a Heart of Guardianship, costs vanilla XP levels, and grants Resistance V for 10 seconds. The account-based cost is `floor(log2(current use number + 1)) + 5`, starting at 6 levels. Configure it in the `respawn_module` section of `yzwc/server/config/global_settings.json`; only `survival_world_pool` is enabled by default

### 5. Youzai Tools & Items

A new mineral and tool set equivalent to diamond tier (durability 1800, speed 8.0, enchantability 10).

| Item                      | Special Effect                                                                                                    |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| **YZ Shovel / Pickaxe**   | Sneak-mine to chain-break 6 blocks ahead                                                                          |
| **YZ Hoe**                | Sneak-use to till a 3×3 area                                                                                      |
| **YZ Sword**              | 4% crit chance for double damage                                                                                  |
| **YZ Axe**                | Jump-attack deals 50% sweeping damage in 3-block radius                                                           |
| **Heart of Guardianship** | Prevents item drop on death (Mixin); consumes 1 per death; warns at 10/5/3/2/1 remaining                          |
| **Void Staff**            | Right-click to toggle flight; consumes 1 durability/sec (max 600) and hunger every 5s; auto-disables on depletion |
| **Flame Staff**           | Charged fire laser, costs 10 mana                                                                                 |
| **Sky Star Staff**        | Meteor attack, 10-block radius, costs 60 mana                                                                     |
| **Teleport Stone**        | Right-click to open teleport list (shared GUI with anchors), costs XP/durability                                  |
| **Primogem / Sweet Madame** | Genshin-themed materials (`primogem` / `sweet_madame`), in the "Youzai Materials" tab                           |
| **Cloud Genshin Music Disc** | Epic rarity, 47s promotional track, based on MC 26.2 `JUKEBOX_PLAYABLE` data component + `JukeboxSong` datapack registry |
| **Meme Paintings (×12)**  | 12 custom paintings (`meme_01`–`meme_12`), Uncommon rarity, in the "Youzai World - Paintings" tab                |

### 6. Custom Blocks

| Block                               | Properties                                                                                                         |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| **YZ Ore / Deepslate Youzai Ore**   | Overworld generation, drops 2–5 XP, requires diamond pickaxe                                                       |
| **Raw Youzai Block / Youzai Block** | Mineral storage blocks                                                                                             |
| **Decomposition Table**             | GUI block for decomposing items into raw materials                                                                 |
| **Fly Beacon**                      | Grants flight within 9.56-block radius, glows when active (light 12)                                               |
| **Teleport Anchor**                 | Right-click for teleport list after activation; supports naming/reordering/deleting/copying coordinates (light 15) |

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
- **Storage**: Pool config in `dimensional_inventories_module.pools` of `yzwc/server/config/global_settings.json`, player state in `<world_name>/data/yzwc/data/dimensional_inventories_module/<pool-id>/<uuid>.json`

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
  - **Level Formula**: `C = 200 + 20 × log₁₀(2n)²⁰` (n = current level, n ≥ 1; ~200–220 at n ≤ 5, then grows rapidly by the log₁₀(2n)²⁰ power law — ≈ 4000 at n = 10, hitting the int cap at high levels)
  - **Network Sync**: `LevelExpSyncPayload` (S→C) synchronizes XP values
- **Attribute System**
  - Attribute points earned on level-up can be allocated via the `/yzwc` attribute menu (GUI element), mapped onto 10 vanilla attributes: `MAX_HEALTH`, `MOVEMENT_SPEED`, `JUMP_STRENGTH`, `LUCK`, `ATTACK_DAMAGE`, `BLOCK_BREAK_SPEED`, etc.
  - **Client HUD**: `AdventureLevelHudRenderer` renders level & attributes
  - **Network Sync**: `AttributeSyncPayload` (S→C) syncs attribute data; `AttributeUpgradePayload` (C→S) requests a point allocation
  - **Storage**: the `levels` and `attributes` blocks of `yzwc/server/data/skill_module/data.json` (keyed by player UUID)

### 13. Placeholder System

Integrates Placeholder API with `%luckperms_*%` namespace, providing **32 placeholders** (11 static + 21 dynamic with parameters): `prefix`, `suffix`, `meta`, `meta_all`, `prefix_element`, `suffix_element`, `context`, `groups`, `inherited_groups`, `primary_group_name`, `has_permission`, `inherits_permission`, `check_permission`, `in_group`, `inherits_group`, `on_track`, `has_groups_on_track`, `highest_group_by_weight`, `lowest_group_by_weight`, `highest_inherited_group_by_weight`, `lowest_inherited_group_by_weight`, `highest_group_weight`, `current_group_on_track`, `next_group_on_track`, `previous_group_on_track`, `first_group_on_tracks`, `last_group_on_tracks`, `expiry_time`, `inherited_expiry_time`, `group_expiry_time`, `inherited_group_expiry_time`, etc.

### 14. Permission System

Fine-grained LuckPerms-based permission control with automatic OP-level fallback. All commands and features route authorization through `luckperms/LuckPermsHelper`, providing **20+ distinct permission nodes** including `account.mgr.*`, `command.*`, `mail.*`, and `*` wildcards.

### 15. Creative Mode Tabs

The creative menu has been reorganized into **7 independent tabs**:

| Tab ID                 | Name                        | Contents                                                                                                       |
| ---------------------- | --------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `youzai_blocks`        | Youzai Blocks               | 8 custom blocks                                                                                                |
| `youzai_tools_weapons` | Youzai Tools & Weapons      | 5 tools + 3 staves                                                                                             |
| `youzai_materials`     | Youzai Materials            | Raw ore, ingot, nugget, primogem, sweet madame                                                                 |
| `youzai_utilities`     | Youzai Utilities            | Heart of Guardianship, Invisible Item Frame, Invisible Glow Item Frame, Teleport Stone, Warp/Return Scrolls, Music Disc |
| `youzai_paintings`     | Youzai World - Paintings    | 12 custom meme paintings (meme_01–meme_12)                                                                     |
| `youzai_kits`          | Youzai Kits                 | 9 preset shulker boxes                                                                                         |
| `youzai_enchantments`  | Youzai World - Enchantments | Enchanted books for the mod's custom enchantments; iterates `ModEnchantments.ALL` and emits one book per level |

### 16. Preset Item System

Nine preset shulker boxes in the "Youzai Kits" creative tab (`PresetItems.createPreset01`–`createPreset09`):

| Preset                | Color      | Contents                                                  |
| --------------------- | ---------- | --------------------------------------------------------- |
| Graduation Set        | Red        | Full enchanted netherite gear, tools/weapons, consumables |
| Graduation Supplement | Orange     | Utility tools, building materials, extra armor            |
| Totem Box             | Yellow     | 27 totems of undying                                      |
| Explosive Pack        | Gray       | 27 stacks × 64 TNT                                        |
| Firework Rockets      | Pink       | 27 stacks of firework rockets                             |
| Mace Set              | Light Blue | 3 maces with different enchantment loadouts               |
| Bottles o' Enchanting | Lime       | 27 stacks × 64 bottles o' enchanting                      |
| Ender Pearls          | Green      | 27 stacks × 64 ender pearls                               |
| Rainbow Arrows        | Light Gray | Arrows / spectral arrows / assorted tipped arrows         |

### 17. Advancement System

Two branches with **31** advancements (`data/youzaiworldcore/advancement/`):

- **Youzai World** (`youzaiworld/`, 21): Obtain Youzai materials (`have_raw_yz` / `have_yz_ore` / `have_yz_ingot` / `have_yz_block`), craft tools (`have_yz_series_tool` / `have_yz_series_all_tools`), use the decomposition table / fly beacon / heart of guardianship / void staff (`used_*` / `have_*`), discover teleport anchors and the teleport network (`discover_teleport_anchor` / `teleport_network` / `village_teleport_network`), explore the Cloud Genshin ruins and ancient ruins (`discover_cloud_genshin_ruins` / `ancient_ruins`), and complete all beginner tutorials (`complete_the_tutorials`)
- **Fun Little Challenges** (`fun_little_challenge/`, 10): `cake_is_a_lie` (The Cake Is a Lie), `foodie` (Foodie), `get_emerald_blocks`, `like_cows_and_pigs`, `max_luck` (Max Luck), `stuck_in_cobweb`, `tested_stonecutter` (I Became Building Material — died on a stonecutter), `way_home` (Way Home), `wearing_copper_armor`

### 18. Debug & Configuration

#### 18.1 File Layout (Server Side)

As of this version, every server-side config / data / backup / cache file lives under `yzwc/server/`
in the game root directory; anything that must travel with the save goes to `<world_name>/data/yzwc/`.
All paths are resolved through `config/ModPaths` — modules no longer hand-roll their own paths.

```
<game root>/yzwc/server/
├── config/
│   ├── global_settings.json                # Global config (world-independent), sectioned per module
│   └── user_settings/
│       └── <player UUID>.json              # Per-player config (created on register, deleted on deactivate)
├── data/
│   └── <module>/data.json                  # Per-module data files
├── backup/
│   └── <module>/*.zip                      # Per-module backup archives
└── temp/
    └── <module>/                           # Per-module cache / temp files (cleared on every server start)

<world folder>/data/yzwc/
├── config/ · data/<module>/ · backup/<module>/ · temp/<module>/
```

**JSON structure convention**: every config file classifies by *feature module* first, then writes
that module's settings:

```json
{
  "pet_module": {
    "auto_backup_enabled": true,
    "backup_interval_seconds": 600
  },
  "afk_module": {
    "enabled": true,
    "threshold_seconds": 300
  }
}
```

**Generated on first run**: on a fresh server (file absent) the mod writes a complete
`global_settings.json` **containing every module's default values**, plus per-module data files, and
creates the `config/ data/ backup/ temp/` skeleton — you never have to boot
once just to discover which keys exist. A player's personal config file is created at registration,
likewise pre-filled with the defaults for every personal setting.

**Error handling**: there is no config migration and no silent fallback. As soon as a malformed or
wrongly-typed value is read, the mod:

1. renames the offending file aside to `<filename>.error` (timestamp appended if taken — an existing
   quarantined file is never overwritten);
2. **regenerates a default config** at the original path so you have a correctly-formatted reference;
3. prints *which file, which key, why, where it was quarantined, and what was regenerated*;
4. **crashes on purpose** — port your changes from the `.error` file into the new one and start again.

If step 1 fails (file locked, etc.) step 2 is skipped so the original is never clobbered.

#### 18.2 Configuration Overview

| Config                   | Location                                                                       | Contents                                                                                             |
| ------------------------ | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| Mod Core                 | `yzwc/server/config/global_settings.json` → `core_module`                      | `dev_mode_enabled`, `log_to_file` (dual-toggle for DebugLogger)                                      |
| Api Bridge               | `global_settings.json` → `api_module`                                          | Api URL (production: `https://api.mcyzw.top`), HMAC shared key, request timeout (10 seconds by default) |
| Accounts & Authentication| Api-side SQLite `game_accounts` / `game_sessions`                              | Player name, password hash, UUID, sessions and login cooldown; the mod only keeps a runtime non-credential cache |
| Custom Cosmetics         | Api-side SQLite `game_cosmetics`                                               | Skin and cape bytes; no PNG files are stored on the Minecraft server                                 |
| Per-Player Config        | `yzwc/server/config/user_settings/<UUID>.json`                                 | `double_doors_module`, `function_module` (7 per-player toggles: ladder extend / crop XP / tool info / block animation / craft sound / item sparkle / damage numbers) |
| Client External Settings | `config/youzaiworldcore/client_external_settings.json` (client, not migrated)  | `devModeEnabled`, `logLevel` (0–3), `yzuiEnabled` (YZUI interface master toggle), debug address/port |
| DebugLogger              | `util/DebugLogger`                                                             | 4 log levels (OFF/BASIC/DETAILED/DEBUG), entering/exiting/branch/stateChange/exception tracing       |
| Update Checker Settings  | `global_settings.json` → `update_module`                                       | `enabled` (toggles update checks, UpdateCheckerConfig)                                               |
| Trial Vault Settings     | `global_settings.json` → `trial_vault_module`                                  | `enabled` (unlimited-reward toggle, TrialVaultConfig, default true)                                  |
| Mail Settings            | `global_settings.json` → `mail_module`                                         | Expiry policy, permission node/level, attachment caps                                                |
| Pet Settings             | `global_settings.json` → `pet_module`                                          | Backup interval, retention count, auto-backup toggle                                                 |
| AFK Settings             | `global_settings.json` → `afk_module`                                          | Detection threshold, prefix/broadcast/invulnerable/auto_kick                                         |
| Laowu Meme Settings      | `global_settings.json` → `laowu_meme_module`                                   | Global toggle, cooldown                                                                              |
| Global Event Toggles     | `global_settings.json` → `event_module`                                        | Death sound / jukebox loop / baby zombie nerf / wither skull / trident void protect / crop XP        |
| Charged Creepers         | `global_settings.json` → `charged_creeper_module`                              | `enabled`, `chance`                                                                                  |
| End Portal               | `global_settings.json` → `end_portal_module`                                   | Silk-touch requirement / direct-to-inventory / dragon-egg message                                    |
| Respawn Here             | `global_settings.json` → `respawn_module`                                      | Enabled dimension pools and standalone dimensions                                                    |
| Dimension Pools          | `global_settings.json` → `dimensional_inventories_module`                      | `pools` pool definition list                                                                         |
| Adventure Level / Attrs  | `yzwc/server/data/skill_module/data.json`                                      | `levels` / `attributes` blocks, keyed by player UUID                                                 |
| Mail Data                | Api-side SQLite `game_mails` / `game_mail_refs`                                | Mail bodies (with attachments) + per-player inbox refs; no mail files on the Minecraft server        |
| Pet Backups              | `yzwc/server/backup/pet_module/pet_backup_<timestamp>.zip`                     | Scheduled backup archives (containing a `.json` of the same name)                                    |
| Player Stats Data        | `<world_name>/data/yzwc/data/status_module/data.json` + `rank_export/`         | StatsManager persistence & leaderboard export dir                                                    |
| Dimension Pool State     | `<world_name>/data/yzwc/data/dimensional_inventories_module/<pool>/<uuid>.json`| Per-pool independent inventory and player state                                                      |

### 19. Enchantment Level Language Patch System

A capability-style system that patches the rendering of **enchantment levels** and **potion potencies** in Minecraft language files (vanilla, resource packs, and mods), allowing high levels to be displayed in a more readable form (e.g. Chinese numerals).

- **API (`EnchantmentLevelLangPatch`)**: `registerPatch` for general key-predicate patches; `registerEnchantmentPatch` / `registerPotionPatch` to register dedicated enchantment-level / potion-potency render hooks; `intToRoman` provides int-to-roman conversion (1–3998, table-based)
- **Chinese Numeral Patch (`ChineseExchange`)**: Renders level numbers as simplified Chinese (一二三…) or uppercase Chinese (壹贰叁…), with `NumResultCacheMap` caching and a `ValueTableHolder` value table for performance
- **Config Switching**: `EnchantmentLevelLangPatchConfig.setCurrentEnchantmentHooks` / `setCurrentPotionHooks` selects which patch to enable; `IndependentLangPatchRegistry` manages registrations by namespaced key (`NamespacedKey`)
- **Client Integration**: `EnchantmentLevelLangPatchMixin` injects the patch into the language-loading flow
- **Purpose**: Lets Chinese players read high-level enchantments as "十级" / "一百级" instead of long Roman numerals

### 20. Pickup Display & Damage Numbers

Immediate client-side feedback: pickups show obtained item/XP entries, while damaged entities display their actual damage at the hit position.

- **Pipeline**: `AddEntriesHandler` receives pickup events and enqueues them → `PendingPickupQueue` holds pending entries → `DrawEntriesHandler` renders them each frame
- **Entry Types**: `DisplayEntry` abstract base, with concrete `ItemDisplayEntry` (items, including count/stack info), `ExperienceDisplayEntry` (XP), and `SubtitleDisplayEntry` (subtitle text + direction indicator, captured by `SubtitleCaptureHandler` and rendered in the same region as pickup notifications)
- **Client Integration**: `PickUpNotifyMixin` intercepts pickup notifications to drive the display; `ClientNetworking` handles client-side network logic
- **Damage Numbers**: `DamageNumberLivingEntityMixin` compares health plus absorption before and after `LivingEntity#hurtServer`, yielding the actual loss after armor, enchantments, resistance, and shield processing. The server sends the hit position through `DamageNumberPayload` (S→C), and the client `DamageNumberRenderer` uses the 26.2 submit-based world rendering pipeline for red numbers that rise, drift, and fade
- **Per-Player Toggle**: `/yzwc function damage_numbers [true|false]` controls whether the executing player receives damage numbers; omitting the argument queries the current state. New players default to enabled, with UUID-keyed state persisted in the `function_module` section of `yzwc/server/config/user_settings/<UUID>.json`
- **Visibility & Performance**: Packets are sent only to enabled players tracking the target; invisible targets do not leak their location to unrelated observers. The client retains at most 256 numbers and renders only those within 64 blocks

### 21. World Enhancement Features

A set of native, dependency-free "world tweak" enhancements (inspired by classic community gameplay) covering mob behavior, drop collection, End mechanics, trial chambers, and farming automation:

- **Naturally Charged Creepers**: When a creeper enters the server world, it is marked charged with a configurable probability (`chance`, default 0.1 / 10%). The charge is written via the `DATA_IS_POWERED` entity data exposed through a Mixin, ensuring correct client-side lightning halo sync; a data tag dedups to avoid re-rolls on chunk reload. Config in the `charged_creeper_module` section of `global_settings.json` (`enabled` default true, `chance` default 0.1; values outside [0,1] abort startup with an error). Commands: `/yzwc event naturally_charged_creepers enable [true|false]` / `settings chance [double]`
- **Chorus Fruit Drops Nearby**: After a chorus plant is broken, its dropped chorus fruit is teleported to the nearest recently-broken chorus plant location (horizontal distance < 20 blocks within a 2-second window), preventing fruit from scattering everywhere
- **Dragon Drops Elytra**: When the Ender Dragon is slain, an extra elytra drops and a broadcast message is sent; kill attribution priority: direct player → projectile owner (bow/crossbow/trident) → nearest player within a 30-block radius
- **End Portal Enhancements**: ① End portal frames can be broken with a silk-touch pickaxe and drop (including embedded ender eyes) while clearing the activated portal blocks; ② An extra dragon egg is granted to nearby players when the Ender Dragon is slain; ③ New recipe `craftable_end_portal` (ender eyes + dragon egg + end stone → 12 end portal frames). Config in the `end_portal_module` section of `global_settings.json` with three toggles (silk-touch requirement / direct-to-inventory / dragon-egg message)
- **Warden Loot**: When a player kills a Warden, 300 XP is granted directly and bundle loot drops (sculk shrieker, random pools of netherite scrap / diamonds / gold / iron, ancient-city-style items, enchanted books — 50% Swift Sneak I–III / 50% Soul Speed I–III, boosted by Looting). Implemented by `WardenDeathHandler` (`ServerLivingEntityEvents.AFTER_DEATH`), replacing the fragile datapack tick-scan approach
- **Stonecutter Damage**: Standing on a stonecutter block deals continuous damage — an immediate 1-heart hit on first contact, then once every 1.5s (30 ticks) until you step off; death shows the custom message "attempted to test the sharpness of the stonecutter with their own body" and grants the "I Became Building Material" advancement. Creative/Spectator modes are immune. Implemented by `StonecutterDamageHandler` (time-sliced scan + per-player timer, performance-first)
- **Unlimited Trial Vault Rewards ★NEW**: Removes the vanilla "one reward per player per vault" limit, letting the same player insert keys and claim from the same vault repeatedly. Implemented by `VaultServerDataMixin` with precise injections into `VaultServerData#hasRewardedPlayer` (always returns false) and `#addToRewardedPlayers` (cancelled), rather than a wildcard Redirect. Config in the `trial_vault_module` section of `global_settings.json` (`enabled`, default true). Command: `/yzwc event trial_vault enable [true|false]`. Inspired by trial-chamber-time-removal, rewritten natively with no upstream dependency
- **Bone Meal Sugar Cane ★NEW**: Right-clicking sugar cane with bone meal grows it by one segment (up to 3 blocks tall); a **dispenser behavior** (`BoneMealSugarCaneDispenserBehavior`) is registered as well, so a dispenser loaded with bone meal can grow the sugar cane in front of it — useful for automated farms
- **Concrete Powder Solidify ★NEW**: Concrete powder in **dropped-item entity** form solidifies into the matching concrete item when it lands in water (vanilla only solidifies the block form). `ConcretePowderSolidifyHandler` scans once every 20 ticks (1 second) to bound the performance cost; the color mapping table is built from the registry at initialization
- **Baby Zombie Weakening ★NEW**: Reduces baby zombie spawn speed and max health via `ZombieFinalizeSpawnMixin` on entity finalization
- **Jukebox Loop ★NEW**: Jukebox auto-loops the current disc instead of ejecting it when playback ends. `JukeboxLoopMixin` injects the loop logic into `JukeboxBlockEntity` tick
- **Craft Sound ★NEW**: Plays a crafting sound when taking the result item from the output slot, replacing vanilla silent extraction. `ResultSlotOnTakeMixin` injects the sound on item pickup
- **Painting Drop ★NEW**: Breaking custom meme paintings drops the corresponding painting item (vanilla paintings don't drop when broken). `MemePaintingDropMixin` injects drop logic on entity removal
- **Experimental Warning Skip ★NEW**: Automatically skips the "Experimental Features" warning dialog when creating a world, improving dev/test efficiency. `ExperimentalWarningSkipMixin` intercepts the corresponding screen
- **Anvil Repair ★NEW**: Sneak right-click an anvil while holding an iron ingot to repair it one stage (severely damaged → damaged → normal), consuming 1 ingot and preserving orientation. Implemented by `AnvilRepairHandler` (`UseBlockCallback`)
- **Death Sound ★NEW**: On player death, a random global sound from 10 variants (player death / generic death / ender dragon / thunder / bell / warden / portal, etc.) plays on top of the vanilla death sound. Implemented by `DeathSoundHandler` (`ServerLivingEntityEvents.AFTER_DEATH`), toggleable in `event_module`

### 22. AFK Detection System

Server-side automatic AFK (Away From Keyboard) detection that marks idle players and supports configurable automated actions.

- **Detection**: Checks mouse/keyboard input and view angle every 20 ticks, configurable threshold (default 300s)
- **AFK Marking**: Prepends a configurable prefix (e.g. `[AFK]`) to the player's tab-list name via `ServerPlayerTabDisplayNameMixin` + `AfkKeyboardHandlerMixin` / `AfkMouseHandlerMixin`
- **Automation**: Configurable invulnerability (`invulnerable`), auto-kick (`auto_kick`), and broadcast on AFK state change
- **Manual Toggle**: Players can toggle AFK manually via `/yzwc afk`
- **Commands** (server-side):
  - `/yzwc afk` — toggle AFK state
  - `/yzwc afk status [player]` — query own/other's AFK status
  - `/yzwc afk list` — list all AFK players (admin)
  - `/yzwc afk settings <key> <value>` — modify AFK config at runtime (admin)
- **Config**: `global_settings.json` → `afk_module` (`AfkConfig`: enabled/detect_mode/threshold_seconds/tab_prefix_enabled/broadcast_enabled/invulnerable_enabled/auto_kick_seconds/manual_toggle_enabled)

### 23. Warp Scroll System ★NEW

Single-use consumable items: right-click and hold for 5 seconds to open the teleport list (shared GUI with teleport anchors); the entire scroll is consumed on successful teleport.

- **Items**: `warp_scroll` (Warp Scroll), `return_scroll` (Return Scroll), `stacksTo(16)`
- **Charge Interruption**: Taking damage during the 5s charge cancels it, handled by `TeleportStoneChargeHandler`
- **Warp Scroll**: 5s charge → opens teleport list → select target → teleport → consumes 1 scroll + 120s item cooldown, no XP/durability cost
- **Return Scroll**: 5s charge → auto-finds nearest active anchor in current dimension → teleport → consumes 1 scroll + 60s cooldown; action bar notice (no consumption) if no anchor available
- **Cooldown**: Item-level cooldown implemented via `ServerPlayerGameModeCooldownMixin`, sharing the framework with ender pearls

### 24. Magic Table ★NEW

A decorative block (`magic_table`) serving as a visual centerpiece for server lobbies and functional areas.

- **Appearance**: Custom textures on all 4 sides, emissive level 2 (`RenderShape.MODEL`)
- **Properties**: Hardness 5.0, blast resistance 1200 (matches vanilla enchanting table), requires pickaxe
- **Usage**: Purely decorative, no interactive GUI; listed in the "Youzai Blocks" creative tab

### 25. Laowu Meme Easter Egg ★NEW

A server entertainment easter egg — two tamed cats trigger a nuzzling animation, custom sounds, and server-wide particle effects under certain conditions.

- **Trigger**: Two tamed cats (one named exactly "老吴") within 6 blocks, with a configurable random cooldown (default 180s)
- **Effects**: Geo skeletal animation, custom sounds (`laowu2.ogg` / `qiliang.ogg` / `zhanhou.ogg`, one of three chosen at random), server-wide particle broadcast; right-clicking either cat releases the pairing early (restores AI + enters cooldown)
- **Implementation**: `LaowuMemeHandler` scans every 10 ticks + `SoundBufferLibraryLaowuMixin` custom audio loading (incl. user-imported tracks in `config/youzaiworldcore/laowu_meme/sounds/`) + client Geo model renderer
- **Commands**: `/yzwc event laowu enable [true|false]` / `settings cd [seconds]` (global toggle & cooldown)
- **Config**: `global_settings.json` → `laowu_meme_module` (`LaowuMemeConfig`)

### 26. Double Doors System

A streamlined implementation that supports click-to-open only for "same-material wooden doors / fence gates", with a per-player independent toggle.

- **Trigger**: Right-click a door / fence gate with an empty hand; `DoorBlockMixin` / `FenceGateBlockMixin` calls `DoubleDoorsHandler.onDoorClick` after `useWithoutItem` performs the vanilla toggle; sneaking disables double-open, keeping only the vanilla single-open behavior
- **Pairing Rule**: Searches adjacent, same-type (both `DoorBlock` or both `FenceGateBlock`), same display-name (material) partner doors within a 3×3 horizontal area and synchronizes them to the clicked door's open/closed state; no recursion (adjacent pairs only)
- **Supported Scope**: Wooden doors (including double doors), fence gates (auto-aligned facing); iron doors (not hand-openable), trapdoors, redstone triggers, villager AI, and chain opening are out of scope
- **Per-Player Toggle**: `/yzwc function double_doors [true|false]` (**client command**) controls the player's own setting; omitting the argument queries the player's own status; new players enabled by default
- **Persistence**: the `double_doors_module` section of `yzwc/server/config/user_settings/<UUID>.json`, storing only players explicitly set via command (`DoubleDoorsState`; unset players fall back to the default enabled state)
- **Client Forwarding Architecture**: The `/yzwc` root command is registered on the client (for `/yzwc settings` and forwarding-type subcommands), so double doors and invisibility only parse and forward on the client; the authoritative state is held by the server via `DoubleDoorsTogglePayload` / `InvisibilityPayload` (C→S)

### 27. Pet System

A tamed-wolf (Wolf) tracking and management system that registers tamed wolves as "pets" with persistent ownership, trust, and behavior management.

- **Core Data**: `PetEntry` records internal name (`internalName`), display name, behavior mode, owner UUID, trusted players, tame time, and entity UUID; global registry `PetGlobalState` persists all pets
- **Behavior Modes**: `hunting` / `companionship` / `attack` / `guard` — switched via `/yzwc pet set <internalName> mode`
- **Trust System**: Owners can add other players to a trust list; trusted players may view the list and highlight the pet; `trust add/remove/list <player>`
- **Ownership Ops**: `rename`, `transfer <newOwner>` (transfers ownership; former owner auto-added to trust list), `release_life [force]` (release, requires confirmation)
- **Quick Locate**: `highlight <internalName>` applies a 5-second Glowing effect to the target wolf for easy spotting
- **Admin Ops**: `admin restore` (restore from latest backup), `admin backup_list` (list backups), `admin backup_interval <seconds>` (set scheduled backup interval, 60–3600s)
- **Persistence**: config in `global_settings.json` → `pet_module`; scheduled backups at `yzwc/server/backup/pet_module/pet_backup_<timestamp>.zip` (containing a `.json` of the same name)
- **Command Architecture**: `/yzwc pet` is a **client command** that forwards its full argument string via `PetCommandPayload` (C→S); the server-side `PetCommand` holds the complete Brigadier tree and permission checks

### 28. Item Highlight System

A purely client-side feature that renders an outline around the held or targeted item, helping players quickly locate items in the inventory / world (no server-side effect).

- **Controls**
  - **Keybinds**: `F10` toggles highlight (`key.youzaiworldcore.highlight.toggle`), `B` cycles comparison mode (`key.youzaiworldcore.highlight.comparator`)
  - **Command**: `/yzwc settings highlight_item`
    - `toggle` —— enable/disable highlight
    - `color <name | custom r g b a>` —— preset color or custom RGBA (r/g/b 0–255, a 0.0–1.0)
    - `mode <comparator>` —— choose the item-matching rule that triggers highlight
  - **Settings Screen**: Client settings offer "Enable Highlight / Comparison Mode / Notification Preference" options
- **Comparison Modes**: `item_only`, `item_and_amount`, `item_and_nbt`, `item_and_nbt_and_amount`, `name_only`, `name_and_amount`, `namespace`
- **Notification Preference**: `none` (default) / `toast` / `chat` / `overlay`
- **Implementation**: `highlightitem` package (`HighlightItemClient` / `HighLightCommands` / `Configurator` / `Colors` / `ItemComparator`) injects the outline via a client-side Mixin on the render layer; keybinds and commands apply immediately

### 29. Stats System (Status)

Reads player behavior data from the vanilla `Stats` system, persists it, and supports querying and leaderboard export.

- **Entry**: `status/StatsManager`; data persisted to `<world>/data/yzwc/data/status_module/data.json` (per-save, with daily snapshots for day/week/month/year deltas; corrupt files auto-backup to zip); leaderboard export in the sibling `rank_export/`
- **Metrics**: **21 metrics** in total — play time, jumps, deaths, mob/player kills, damage dealt/taken, walk/sprint/elytra/fall distance, fish caught, villager trades, items dropped, sleep-in-bed, enchantments, raid wins, animals bred, bell rings, cake eaten, and an aggregated "redstone placement" leaderboard
- **Commands** (server-side):
  - `/yzwc status <player> list` — view a player's stats (perm `youzaiworldcore.command.status.query`)
  - `/yzwc status <player> delete` — delete a player's stats (perm `youzaiworldcore.command.status.delete`)
  - `/yzwc status rank_export <day|week|month|year|all> [name]` — export leaderboard to `rank_export/<name>.json` (perm `youzaiworldcore.command.status.export`)
- **Permissions**: `status.query` / `status.delete` / `status.export` (default OP 4)

### 30. Mailbox

A one-way **admin → player** server mailbox / announcement box (not player-to-player), published by admins through a GUI and received by players in their mailbox (Shift+F → Mail).

- **Scope**: Senders are admins only (OP / LuckPerms `youzaiworldcore.mail` node); recipients are either-or — All Members / Specific Players (ticked from the account system's registered list)
- **Mail Types**: Announcement (ANNOUNCEMENT), Notice (NOTICE), Reward (REWARD)
- **Reward Carriers** (REWARD type): Items (up to 10 slots, copied from admin's inventory as templates, originals not consumed), Command (run as console, supports `%player%` / `%uuid%` placeholders), Vanilla XP, Vanilla Levels, this mod's Adventure XP, this mod's Adventure Level (all four selectable at once)
- **Expiry**: 1 day / 7 days / 30 days (default) / permanent; unstarred expired mail auto-purges (the server also deletes "expired and starred by nobody" mail on every startup), starred expired mail keeps text but disables claiming
- **GUI**
  - **Scaling**: all three mail screens are laid out against a 960×540 GUI-unit design space and uniformly scaled + centered, so the layout is identical at any resolution / GUI scale
  - **Player Mailbox** (`MailScreen`): filters (all/unread/starred), details, claim/star/delete; top-right "Compose" / "Sent" buttons visible to permission holders only, plus "Back" (to the main menu) and "Close" buttons; entering from the main menu plays a transition
  - **Compose/Edit** (`MailComposeScreen`): recipients are either-or (specific players are ticked in a searchable "Pick Players" popup) + type + subject + body + attachments (≤10 item slots, rarity borders shown, filled via the "Pick From Inventory" button) + expiry dropdown; editing sent mail reuses this screen (`MailSentScreen`[Edit] → prefill → `MailAdminEditPayload`)
  - **Sent list** (`MailSentScreen`): expired mail no longer offers "Edit" / "Recall"
  - **Feedback**: claim / recall / permission results appear as a floating banner at the top of the screen (falls back to chat when no mail screen is open)
- **Commands** (**client command**, parse-and-forward; server-side authorization)
  - `/yzwc mail send_mail` —— open compose GUI
  - `/yzwc mail sent` —— open sent-mail management list
  - `/yzwc mail recall <mailId>` —— recall a sent mail (remove from repo + push removal to online recipients)
  - `/yzwc mail purge [player|all]` —— purge expired mail
  - `/yzwc mail list [player]` —— view a player's mailbox
- **Permission**: `youzaiworldcore.mail` (default OP 4); falls back to `mail_permission_level` when LuckPerms is absent
- **Storage**: Mail bodies and per-player inbox refs live in the Api server's SQLite (`game_mails` / `game_mail_refs`); only the settings stay local in `global_settings.json` → `mail_module`. The client keeps using the same packets — the Minecraft server checks permissions and then forwards to `/api/game/mail/*` (HMAC-signed), so both fetching and publishing are backed by Api data. Recipient resolution (needs LuckPerms) and reward granting stay on the mod side, and every Api call is async with results applied back on the server main thread. Cross-world consistent, bound to the account system (offline accounts also indexed, visible on login)
- **Network**: 18 dedicated packets (C→S `mail_compose_open` / `mail_open` / `mail_sent_list_request` / `mail_recall` / `mail_purge` / `mail_list_request` / `mail_fetch` / `mail_action` / `mail_admin_send` / `mail_admin_edit` / `mail_player_list_request`; S→C `open_mail_compose` / `mail_list` / `mail_sent_list` / `mail_update` / `mail_op_result` / `mail_unread_count` / `mail_player_list`)

### 31. Custom Enchantments

The mod registers **12 data-driven enchantments** (defined in `data/youzaiworldcore/enchantment/` JSON, registered by `ModEnchantments` ResourceKeys) -- 2 original enchantments plus 10 ported from Raiyon's More Enchantments (reimplemented natively, no external dependency).

#### Original Enchantments

- **Sun Repair (`sun_repair`)**: "Repairs tools in sunlight." `SunRepairHandler` checks online players every 5–10s (random interval); for damaged items carrying this enchantment that are in sunlight (sky light present, not raining/thundering, not night, unobstructed overhead), it restores 1 durability per tick. Covers main hand, offhand, armor slots, and the whole inventory.
- **Spirit Turbo Booster (`spirit_turbo`)**: "Enchanted on harnesses to increase spirit movement speed." `HappyGhastTurboHandler` checks all Happy Ghasts every 20 ticks; if its harness carries this enchantment, it adds +20% flying speed per level to the `FLYING_SPEED` attribute.

#### Ported Enchantments (Raiyon's More Enchantments)

| Enchantment             | ID              | Applies to | Effect                                                |
| ----------------------- | --------------- | ---------- | ----------------------------------------------------- |
| **Leeching**            | `leeching`      | Weapons    | Heal on kill                                          |
| **Poison Puff**         | `poison_puff`   | Trident    | Release poison cloud on attack (`post_attack` → `apply_mob_effect`, poison 60s×level) |
| **Fire Charge**         | `fire_charge`   | Crossbow   | Shoot fire charges (`projectile_spawned` → `ignite` 100 ticks) |
| **Sonic Charge**        | `sonic_charge`  | Crossbow   | Shoot warden sonic booms (`damage` → `add`, base 10 +2/level; recoil handled in Java) |
| **Cowardice**           | `cowardice`     | Leggings   | Speed boost at full health (Java-side)                |
| **Wind Charge**         | `wind_charge`   | Chest/Elytra | Continuous acceleration while elytra gliding (Java-side, cap 1.5 blocks/tick) |
| **Spikes**              | `spikes`        | Shield     | Reflect damage to attackers (`post_attack` → `damage_entity`, 3 dmg/level, 40%+30%/level chance) |
| **Bounce**              | `bounce`        | Shield     | Knock back attackers on block (Java-side)             |
| **Smelting**            | `smelting`      | Tools      | Auto-smelt mined blocks (Java-side)                   |
| **Meteor Smash**        | `meteor_smash`  | Mace       | Summon meteor on smash attack (Java-side, 3-block AoE ignite for 10s) |

> These 10 ported enchantments are inspired by Raiyon's More Enchantments, reimplemented natively with no external dependency.

### 32. Anvil Use-Count Display

A client-side tooltip enhancement (`anviluses` package). Shows how many times an item has been worked on an anvil and how many more repairs remain:

- **Anvil Uses**: Derived from `DataComponents.REPAIR_COST` (`floor(log2(repairCost+1))`)
- **Estimated Remaining Repairs**: Simulates vanilla `calculateIncreasedRepairCost` growth until the next repair cost hits the "too expensive" cap (40 levels); at 0 it shows "can no longer be repaired by an anvil"
- Inspired by Anvil Uses (Z1proW); independently rewritten against decompiled 26.2 `AnvilMenu`, no external dependency

### 33. Item Border System

A client-side visual enhancement (`itemborder` package, inspired by ItemBorders). Draws **rarity-gradient borders** on item slots, using pure native 26.2 APIs (GuiGraphicsExtractor pipeline + `ItemStack.getRarity`), no external dependency:

- **Behavior**: Hardcoded constants (master toggle, hotbar drawing, square corners, full border, extra glow, auto rarity coloring all on by default); common (white) rarity items are not bordered by default
- **Preset Rarity Assignment**: ~60 built-in items — UNCOMMON (yellow) 18, RARE (aqua) 19, EPIC (light purple) 22 (including Youzai ingot / tools / Heart of Guardianship)
- All settings are hardcoded constants; no user-editable config file

### 34. Trinkets Slot Integration

Declares 4 custom trinket slots for the **Trinkets** mod via `data/trinkets/` data packs (Trinkets is a hard dependency), letting specific items equip into trinket slots instead of the main inventory:

| Slot (slots)     | Item                  | Description                                             |
| ---------------- | --------------------- | ------------------------------------------------------- |
| `chest/elytra`   | Elytra                | Equip elytra in the chest trinket slot                  |
| `chest/backpack` | Backpack              | Equip backpack in the chest trinket slot                |
| `offhand/totem`  | Totem of Undying      | Equip totem in the offhand trinket slot                 |
| `offhand/heart`  | Heart of Guardianship | Equip Heart of Guardianship in the offhand trinket slot |

Each slot has a custom icon and the `trinkets:default` validator; `order` controls sorting.

**YZUI Trinket Interaction ★NEW**: The YZUI inventories (survival / creative) do not use the slots Trinkets injects. Instead they implement a **hover-indicator** interaction — hovering an equippable slot pops up the matching trinket slot indicator beside it, and clicking equips/unequips:

- **Server-authoritative**: All operations are submitted through the C→S packet `trinket_interact` (`TrinketInteractPayload`); the server mutates authoritative data via the Trinkets API, and the Trinkets network layer syncs it back to the client and persists it
- **Four actions**: `ACTION_PLACE` (cursor → slot, 0), `ACTION_TAKE` (slot → cursor, 1), `ACTION_SWAP` (2), `ACTION_QUICK_MOVE` (Shift + left click, slot → main inventory 0–35, 3)
- **Cursor validation**: The packet carries the client's current cursor stack (`cursor`). Survival trusts only the server's `containerMenu.getCarried()`, while creative may use its client-generated virtual cursor; slot state, stack limits, and validators always remain server-authoritative
- **Local preview**: The client previews the result locally before the server confirmation arrives, removing perceived interaction latency
- **Native slot suppression**: `SurvivalTrinketSlotYzuiMixin` forces Trinkets' `SurvivalTrinketSlot#isActive()` to return `false` while a YZUI screen is open, preventing its injected slots from rendering as "useless cells" beside the armor slots and from overlapping the YZUI indicator coordinates. With YZUI off nothing is intercepted, so the native Trinkets inventory behaves normally (the mixin targets `eu.pb4.trinkets.impl.slots.SurvivalTrinketSlot`, matching the implementation package in Trinkets 4.1.0-rc.1)

### 35. Config Import/Export

A new "Export/Import Config" sidebar in client settings (YZUI). Based on 26.2 Headless limits (AWT/file dialogs unavailable), it uses automatic paths + backups instead of external file pickers:

- **Export**: Packages `config/youzaiworldcore/` and `options.txt` into a ZIP saved locally (manual path on PC; auto-saved to `config_backups` on Android, keeping the latest 5)
- ⚠️ **Client config only**: server-side config now lives under `yzwc/server/` and is deliberately out of scope here (this feature exists for players to back up their own UI/control settings)
- **Import**: Restores config from a ZIP; requires a client restart to take effect (auto-backs up current config to `config_backups` for rollback on failure)
- **Entry**: `screen.youzaiworldcore.settings.sidebar_config_io`; `ConfigIOManager` self-heals at client startup by detecting and restoring orphaned `config_bak_*` backups left from an interrupted import

### 36. Update Checker

Asynchronously detects new mod versions, prompting for online or forced updates.

- **Entry**: `update/UpdateChecker` (shared by client and server); fetches `https://mcyzw.top/yzwc/version.json` at runtime and compares via `SemanticVersion`
- **Config**: `global_settings.json` → `update_module` (`UpdateCheckerConfig`, toggleable)
- **Command** (server-side): `/yzwc update [check]` — trigger an immediate check and report result (normal/forced update notice + clickable download link); perm `youzaiworldcore.command.update` (OP 4)
- **Client**: `client/update/ClientUpdateState` + `client/screen/ForcedUpdateScreen` provide the forced-update screen

### 37. YZUI Interface System ★NEW

A purely client-side, whole-interface restyle that replaces the vanilla inventory, HUD, recipe book, and contextual bars with the **YZUI look** (translucent white rounded panels + rounded fill bars). Controlled by a **global toggle**: the "Enable YZUI" checkbox in the Visual section of client settings (`screen.youzaiworldcore.settings.toggle_yzui`), persisted as `yzuiEnabled` in `client_external_settings.json` (on by default). **Turning it off reverts everything to vanilla rendering**, so resource packs can take over.

#### 37.1 Inventory Screens

`InventoryScreenSwitchMixin` intercepts `Gui#setScreen` and swaps vanilla screens for custom implementations while YZUI is on (`InventoryScreen` → routed by game mode; `CreativeModeInventoryScreen` → YZUI creative screen):

- **`YzuInventoryScreen` (survival inventory)**: Keeps the vanilla `InventoryMenu` slot coordinates, changing only the look — translucent white rounded panel, rounded slot backgrounds (highlighted on hover), brown offhand slot tint. When the recipe book is open, a YZUI-styled recipe book panel renders on the left with its toggle button above the offhand slot
- **`YzuCreativeInventoryScreen` (creative inventory)**: A fully custom-drawn creative screen (356×168) using the complete item variants from the vanilla creative search tab, with category tabs, a remembered search box, a 9×7 item grid and scrollbar, plus a right column holding the 3D player model, 2×2 armor and offhand slots, the 3×9 survival inventory, and the hotbar
- **Mouse Tweaks gestures**: The survival inventory and the creative screen's right-side storage support right-drag distribution, left-drag collection of matching items, continuous Shift + left-drag quick-move (matching items only while holding a stack), and wheel-based single-item push/pull between the main inventory and hotbar
- **Vanilla creative controls**: The left item grid supports Q / Ctrl+Q drop, F to offhand, 1–9 to hotbar, and pick-item clone; the right-side real inventory uses creative slot synchronization so equipment and quick-move actions do not create ghost items

#### 37.2 HUD Components

`HealthBarMixin` cancels four vanilla renders — `Hud#extractPlayerHealth` / `extractFood` / `extractArmor` / `extractAirBubbles` — delegating to custom renderers under `client/hud/` that draw long progress bars; `HotbarMixin` cancels `extractItemHotbar` and delegates to `HotbarRenderer` for the YZUI hotbar (184×24 rounded panel + nine 18×18 rounded slots + number-key labels + smoothly animated selection highlight + brown offhand slot + attack-cooldown indicator; scroll direction is fed via `ScrollHotbarInputMixin` to support wraparound animation):

| Renderer            | Replaces          | Features                                                                                                                                                                                                               |
| ------------------- | ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `HealthBarRenderer` | Heart health bar  | 85×5 bar with "current/max" text; blinking estimated-restore overlay on the right when holding food; gold absorption overlay; poison (purple vertical stripes) / wither (dark gray horizontal stripes) status striping |
| `FoodBarRenderer`   | Hunger drumsticks | Bar form, reading exhaustion via `FoodDataExhaustionAccessor`                                                                                                                                                          |
| `ArmorBarRenderer`  | Armor icons       | Bar form                                                                                                                                                                                                               |
| `OxygenBarRenderer` | Air bubbles       | Bar form                                                                                                                                                                                                               |

`ScoreboardSidebarMixin` takes over the vanilla sidebar scoreboard whenever either "Enable YZUI" or "Show YZHUD" is on, rendering it as a translucent white rounded panel while preserving vanilla objective selection, hidden-entry filtering, score ordering, team colors/prefixes/suffixes, and number formats. With both switches off the vanilla scoreboard is kept untouched at its fixed vanilla position, and the scoreboard entry on the YZHUD customization screen is greyed out and locked so it can be neither selected nor dragged. The YZHUD customization screen lets players drag the inventory, equipment, status-effect, and scoreboard components independently and preview their shared opacity slider in real time. Positions are stored as normalized `-1..1` offsets; the scoreboard recalculates them from its current frame dimensions so content changes keep it within the screen bounds.

`InventoryHudMixin` also draws inventory, equipment, and status-effect panels in the lower-left corner. All three use the 640×360 GUI coordinate space produced by 1920×1080 at GUI scale 3 as their design baseline, then apply the smaller of the current GUI width and height ratios as one shared scale. This keeps panels, slots, items, icons, text, and spacing proportional across resolutions, window sizes, and GUI scales. The status-effect panel sits to the right of the equipment HUD and above the inventory HUD, and appears only while effects are active. Its bottom edge stays fixed while it grows upward, listing each effect's icon, name, Roman-numeral level, and remaining duration from oldest at the bottom to newest at the top; the corresponding full row or compact cell flashes during the final 10 seconds, and the panel replaces the vanilla top-right effect icons. The panel uses at most 13 rows. Beyond 13 effects, the bottom row is compressed to two, then three, then four compact cells before compression proceeds to the row above; newer effects appear to the left of older effects within each compact row. Compact cells omit names and levels and overlay the remaining duration on the icon's lower-right. Up to the newest 52 effects are shown so newly acquired effects remain visible.

The bottom of the equipment panel shows, in order, the total arrow count (normal, tipped, and spectral arrows), the total firework-rocket count (combining every flight duration and firework-effect component), and the number of empty inventory/hotbar slots.

All three panels provide continuous animation feedback: an item entering an empty slot fades in with a flash, while one leaving a slot shrinks and fades out; increases to item or indicator counts pulse the icon and text together; equipment and tools shake with increasing intensity at 10% durability or less. Newly acquired status effects stretch and fade in, removed effects contract and fade out, and position or width changes caused by additions, removals, or row compression slide smoothly. Additional feedback includes a green count-increase highlight, a cyan durability-repair pulse, and a red breathing warning at low durability.

#### 37.3 Contextual Bars & Recipe Book

- **`ContextualBarMixin`**: For the experience bar (`ExperienceBar`), locator bar (`LocatorBar`), and jumpable-vehicle bar (`JumpableVehicleBar`), cancels the vanilla sprite-sheet background and substitutes a YZUI rounded fill bar (width matches vanilla `ContextualBar#WIDTH` = 182) while preserving `LocatorBar`'s waypoint indicator rendering; the XP number is centered within the health/hunger bar area
- **Recipe book restyle**: `RecipeBookBackgroundMixin` (uses `@Redirect` to replace the background blit rather than `ci.cancel()`, which would also cancel tab/search-box/grid rendering), `RecipeBookLayoutMixin` (shifts the search box left, widens the filter button), `RecipeBookTabButtonMixin`, `RecipeButtonMixin`
- **Button styling**: `CycleButtonYzuiMixin` (filter button: green check `recipe_filter_craftable.png` when selected / red X `recipe_filter_all.png` when not) and `ImageButtonYzuiMixin` (recipe book show/hide button, `recipe_book_show.png` / `recipe_book_hide.png`); both apply only when the YZUI toggle is on **and** the current screen is a YZUI custom screen

### 38. Invisible Item Frames ★NEW

Two custom items whose placed frame entity is invisible (only the displayed item shows), ideal for decoration and display walls:

| Item                                                        | Recipe                                         |
| ----------------------------------------------------------- | ---------------------------------------------- |
| **Invisible Item Frame** (`invisible_item_frame`)           | Item Frame + Phantom Membrane (shapeless)      |
| **Invisible Glow Item Frame** (`invisible_glow_item_frame`) | Glow Item Frame + Phantom Membrane (shapeless) |

`InvisibleItemFrameItem` / `InvisibleGlowItemFrameItem` override `useOn` to perform their own placement validation, attachment-face computation, and entity spawning, marking the entity invisible after spawn. Both appear in the "Youzai Utilities" creative tab.

### 39. Technoblade Memorial Crown ★NEW

An easter egg: name a pig **`Technoblade`** and a crown renders on its head (separate adult / baby models and textures).

- **Implementation**: `TechnoCrownFeatureRenderer` is attached as a `RenderLayer` by `PigRendererMixin` at the tail of the `PigRenderer` constructor; `PigRenderStateMixin` + the `RenderCrownDuck` accessor compute crown visibility each frame from the custom name
- **Textures**: `assets/minecraft/textures/entity/pig/technocrown_adult.png` / `technocrown_baby.png`
- Adapted from technomodel by thecolonel63 (MIT License)

### 40. Music Disc System ★NEW

Music disc items implemented using MC 26.2's new `JUKEBOX_PLAYABLE` DataComponent + `JukeboxSong` datapack registry system.

- **Disc**: `music_disc_cloud_genshin` (Cloud Genshin promotional track), Epic rarity, ~47s duration
- **Implementation**: The item uses `Item.Properties.jukeboxPlayable(ResourceKey<JukeboxSong>)` to inject playback capability; the `JukeboxSong` is defined in `data/youzaiworldcore/jukebox_song/cloud_genshin.json`; the corresponding `SoundEvent` (`youzaiworldcore:cloud_genshin`) is registered by `ModSoundEvents` during mod init into `BuiltInRegistries.SOUND_EVENT`
- **Tab**: Located in the "Youzai Utilities" creative tab

### 41. Meme Painting System ★NEW

12 custom paintings in a dedicated "Youzai World - Paintings" creative tab (`youzai_paintings`).

- **Paintings**: `meme_01`–`meme_12`, Uncommon rarity, sizes from 1×1 to 4×4
- **Registration**: Defined via `PaintingVariant` in `data/youzaiworldcore/painting_variant/`, textures in `assets/youzaiworldcore/textures/painting/`
- **Drop**: Breaking custom paintings drops the corresponding item (vanilla paintings don't drop), implemented by `MemePaintingDropMixin`

### 42. Tool HUD Overlay ★NEW

Client-side HUD enhancement (`client/hud/ToolInfoOverlay`). Every 10 ticks (0.5s) it shows a line of tool info in the action bar, auto-refreshing with the held item:

- **Clock**: in-game time + day count
- **Compass**: 8-direction heading
- **Recovery Compass**: last death coordinates + dimension

Controlled by the `tool_info_overlay` per-player toggle (default on, server-authoritative, synced via `FunctionToggleSyncPayload`).

### 43. Per-Player Function Toggle System ★NEW

Server-authoritative per-player toggles for preference features. `FunctionToggleManager` persists UUID-keyed state in the `function_module` section of `yzwc/server/config/user_settings/<UUID>.json` — 7 keys total (all default on): `ladder_extend_downward` (sneak-place ladder extension), `crop_xp_drop` (crop XP), `tool_info_overlay` (tool info HUD), `block_animation` (block environment particles), `crafting_sound` (crafting sound), `item_sparkle` (item spark particles), `damage_numbers` (damage numbers). Client-rendered feature toggles are synchronized through `FunctionToggleSyncPayload` (S→C); damage numbers are filtered per recipient on the server, so disabled players no longer receive `DamageNumberPayload`. Command `/yzwc function <key> [true|false]` (omit to query); `damage_numbers` is self-service, the rest require `function.set` (OP 4).

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
├── event
│   ├── naturally_charged_creepers
│   │   ├── enable [true|false]           → Enable/disable naturally charged creepers (omit to query)
│   │   └── settings chance [double]      → Set charge probability 0.0~1.0 (omit to query)
│   ├── trial_vault
│   │   └── enable [true|false]           → Enable/disable unlimited trial vault rewards (omit to query)
│   ├── laowu
│   │   ├── enable [true|false]           → Enable/disable Laowu Meme global toggle (omit to query)
│   │   └── settings cd [seconds]         → Set/query Laowu Meme cooldown (≥60s)
│   ├── death_sound enable [true|false]       → Death sound (omit to query)
│   ├── jukebox_loop enable [true|false]      → Jukebox loop (omit to query)
│   ├── baby_zombie_weak enable [true|false]  → Baby zombie nerf (omit to query)
│   ├── wither_skull_drop enable [true|false] → Wither skull guaranteed drop (omit to query)
│   ├── trident_void_protect enable [true|false] → Trident void protection (omit to query)
│   ├── crop_xp_drop enable [true|false]      → Crop XP drop (omit to query)
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
├── mail <args...>                         ← (client command, forwarded to server)
│   ├── send_mail                                    → Open compose GUI (requires mail permission)
│   ├── sent                                        → Open sent-mail management list
│   ├── recall <mailId>                             → Recall a sent mail
│   ├── purge [player|all]                          → Purge expired mail
│   └── list [player]                               → View a player's mailbox
│
├── function invisibility <true/false>    ← (client command)
│   ├── Permission: youzaiworldcore.command.function.invisibility (OP 4)
│   └── Requires Creative mode
│
├── function double_doors <true/false>    ← (client command)
│   ├── Permission: youzaiworldcore.command.function.double_doors (self, everyone can run)
│   └── Omit to query own status; new players enabled by default; state persisted to double_doors_players.json
│
├── function ladder_extend_downward [true|false]  ← sneak-place ladder extension downward (per-player, default on)
├── function tool_info_overlay [true|false]       ← tool info HUD (clock/compass/recovery compass, default on)
├── function block_animation [true|false]         ← block environment particles (beacon/brewing/enchanting/dragon egg, default on)
├── function crafting_sound [true|false]          ← crafting sound (default on)
├── function item_sparkle [true|false]            ← item entity spark particles (default on)
│
├── function damage_numbers [true|false]
│   ├── Permission: youzaiworldcore.command.function.damage_numbers (self, everyone can run)
│   └── Omit to query own status; new players enabled by default; state persisted to function_toggles.json
│
├── reload
│   ├── Permission: youzaiworldcore.command.reload (OP 4)
│   └── Reload account data and config at runtime
│
├── afk                                     ← (server-side command)
│   ├── Permission: youzaiworldcore.command.function.afk (self toggle) / admin (status/list/settings)
│   ├── (no args)                          → Toggle AFK state
│   ├── status [player]                    → Query own/other's AFK status
│   ├── list                               → List all AFK players (admin)
│   └── settings <key> <value>             → Modify AFK config at runtime (admin)
│
├── account
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
        └── mgr login_cooldown
            ├── (no args)        ← Display setting
            ├── set <seconds>    ← Set (-1=never, 0=permanent, >0=timed)
            ├── status <player>  ← Query lock status
            └── unlock <player>  ← Unlock account
├── status
│   ├── <player> list                          → View player's stats (perm .query)
│   ├── <player> delete                        → Delete player's stats (perm .delete)
│   ├── rank_export <day|week|month|year|all> [name] → Export leaderboard (perm .export)
│   └── Permissions: .query / .delete / .export (OP 4)
└── update [check]
    ├── Permission: youzaiworldcore.command.update (OP 4)
    └── Check for mod updates (fetches remote version info, reports normal/forced update + download link)
```

> **Client command note**: `/yzwc pet`, `/yzwc mail`, `/yzwc function invisibility`, and `/yzwc function double_doors` are registered on the client and only parse arguments, forwarding them through the corresponding C→S packets (`PetCommandPayload` / `MailComposeOpenPayload` and other mail packets / `InvisibilityPayload` / `DoubleDoorsTogglePayload`); the server holds the authoritative state and permission checks. `/yzwc function damage_numbers` and the other `FunctionCommand` children are server-side commands forwarded unchanged by the client placeholder mirror.

### Permission Nodes Overview

| Permission Node                                             | Description                                       | Fallback                 |
| ----------------------------------------------------------- | ------------------------------------------------- | ------------------------ |
| `youzaiworldcore.command.teleport_world`                    | Cross-dimension teleport                          | OP 4                     |
| `youzaiworldcore.command.open_menu`                         | Open GUI menu                                     | OP 4                     |
| `youzaiworldcore.command.reload`                            | Mod reload                                        | OP 4                     |
| `youzaiworldcore.command.world_pool`                        | Dimension pool management                         | OP 4                     |
| `youzaiworldcore.command.teleport_anchor`                   | Teleport anchor management                        | OP 4                     |
| `youzaiworldcore.command.function.invisibility`             | Invisibility function                             | OP 4                     |
| `youzaiworldcore.command.function.double_doors`             | Double Doors function (self toggle / query)       | Everyone (self-only)     |
| `youzaiworldcore.command.function.damage_numbers`           | Damage number display (self toggle / query)       | Everyone (self-only)     |
| `youzaiworldcore.command.function.afk`                     | AFK self toggle                                   | Everyone (self-only)     |
| `youzaiworldcore.command.admin.afk`                        | AFK admin (status/list/settings)                  | OP 4                     |
| `youzaiworldcore.command.event.query`                       | Event management query (omit arg = query)         | Everyone                 |
| `youzaiworldcore.command.event.set`                         | Event management modify (enable / settings)       | OP 4                     |
| `youzaiworldcore.command.function.query`                    | Per-player function toggle query (omit arg = query) | Everyone               |
| `youzaiworldcore.command.function.set`                      | Per-player function toggle modify (enable, etc.)  | OP 4                     |
| `youzaiworldcore.command.pet.list`                          | View pet list                                     | Everyone                 |
| `youzaiworldcore.command.pet.set`                           | Pet settings (rename/mode/trust/release/transfer) | Everyone (own pets)      |
| `youzaiworldcore.command.pet.highlight`                     | Highlight pet                                     | Everyone (owner/trusted) |
| `youzaiworldcore.command.pet.admin`                         | Pet admin (backup/restore/interval)               | OP 4                     |
| `youzaiworldcore.command.pet`                               | Pet module parent permission (base)               | OP 4                     |
| `youzaiworldcore.mail`                                      | Mail system (compose/sent/recall/purge/list)      | OP 4                     |
| `youzaiworldcore.command.status.query`                      | View stats                                        | OP 4                     |
| `youzaiworldcore.command.status.delete`                     | Delete stats                                      | OP 4                     |
| `youzaiworldcore.command.status.export`                     | Export stats leaderboard                          | OP 4                     |
| `youzaiworldcore.command.update`                            | Update check                                      | OP 4                     |
| `youzaiworldcore.command.account.mgr.create`                | Create account                                    | OP 4                     |
| `youzaiworldcore.command.account.mgr.reset_password`        | Reset password                                    | OP 4                     |
| `youzaiworldcore.command.account.mgr.delete`                | Delete account                                    | OP 4                     |
| `youzaiworldcore.command.account.mgr.login_cooldown`        | Login cooldown                                    | OP 4                     |
| `youzaiworldcore.command.account.mgr.login_cooldown.status` | Lock status query                                 | OP 4                     |
| `youzaiworldcore.command.account.mgr.login_cooldown.unlock` | Unlock                                            | OP 4                     |
| `youzaiworldcore.command.account.mgr.*`                     | Account mgr wildcard                              | OP 4                     |
| `youzaiworldcore.command.*`                                 | All commands wildcard                             | —                        |
| `youzaiworldcore.*`                                         | Full mod wildcard                                 | —                        |

---

## 🖥️ Menus & Network Packets

### GUI Menu IDs

| Internal ID    | Name         | Hierarchy           |
| -------------- | ------------ | ------------------- |
| `main`         | Main Menu    | Root                |
| `switch_world` | Switch World | Main → Switch World |
| `settings`     | Settings     | Main → Settings     |
| `about_me`     | About Me     | Main → About Me     |

### Container Menu Types

| ID                    | Block               |
| --------------------- | ------------------- |
| `decomposition_table` | Decomposition Table |
| `fly_beacon`          | Fly Beacon          |

### Network Packets (50 total)

> Note: the `world_pool_teleport` packet class lives in the `dimensionalinventories` package; the rest (including the 18 mail packets) are in the `network` package. Direction split: 22 S→C, 28 C→S.

| Packet ID                   | Direction | Purpose                                                                                       |
| --------------------------- | --------- | --------------------------------------------------------------------------------------------- |
| `open_menu`                 | S→C       | Open GUI menu                                                                                 |
| `open_auth_screen`          | S→C       | Open auth screen                                                                              |
| `registration_email_state` | S→C       | Sync email-registration steps, request results, and countdowns                                |
| `mana_sync`                 | S→C       | Sync mana values                                                                              |
| `level_exp_sync`            | S→C       | Sync adventure level XP                                                                       |
| `damage_number`             | S→C       | Sync the hit position and actual damage for client-side world-space numbers                   |
| `attribute_sync`            | S→C       | Sync player attribute data (skill points / attributes / level)                                |
| `teleport_anchor_list`      | S→C       | Send point list                                                                               |
| `teleport_anchor_open_name` | S→C       | Open anchor naming screen                                                                     |
| `mail_unread_count`         | S→C       | Sync unread count + compose permission (canSend)                                              |
| `mail_player_list`          | S→C       | Registered player-name list (for the compose screen's "Pick Players" popup)                   |
| `open_mail_compose`         | S→C       | Open compose GUI                                                                              |
| `mail_list`                 | S→C       | Send inbox list                                                                               |
| `mail_sent_list`            | S→C       | Send sent-mail list                                                                           |
| `mail_update`               | S→C       | Add/update/remove a single mail                                                               |
| `mail_op_result`            | S→C       | Mail operation result feedback                                                                |
| `function_toggle_sync`      | S→C       | Sync function toggle state (double doors, etc.)                                              |
| `laowu_meme_trigger`        | S→C       | Trigger Laowu Meme particles/sounds                                                          |
| `laowu_meme_stop`           | S→C       | Stop Laowu Meme client effects                                                               |
| `teleport_stone_interrupt`  | S→C       | Interrupt teleport stone/scroll charge                                                       |
| `in_place_respawn_info`     | S→C       | Sync whether the death dimension allows respawning here and its level cost                    |
| `in_place_respawn_result`   | S→C       | Return approval or rejection for a respawn-here request                                       |
| `registration_email_request` | C→S      | Submit an email address or email verification code                                             |
| `world_pool_teleport`       | C→S       | Request dimension pool teleport                                                               |
| `in_place_respawn_request`  | C→S       | Request a level-paid respawn at the death position                                             |
| `teleport_anchor_activate`  | C→S       | Activate anchor                                                                               |
| `teleport_anchor_teleport`  | C→S       | Request teleport                                                                              |
| `teleport_anchor_delete`    | C→S       | Delete point                                                                                  |
| `teleport_anchor_rename`    | C→S       | Rename point                                                                                  |
| `teleport_anchor_reorder`   | C→S       | Reorder points                                                                                |
| `decompose_item`            | C→S       | Decompose item                                                                                |
| `fly_beacon_active`         | C→S       | Toggle fly beacon                                                                             |
| `invisibility_toggle`       | C→S       | Toggle / disable own invisibility                                                             |
| `attribute_upgrade`         | C→S       | Request to allocate a point to an attribute                                                   |
| `double_doors_toggle`       | C→S       | Toggle / query own Double Doors setting                                                       |
| `pet_command`               | C→S       | Forward `/yzwc pet` client command to server                                                  |
| `trinket_interact`          | C→S       | YZUI trinket interaction (place/take/swap/quick-move; trusts client cursor in creative only)  |
| `inventory_collect`        | C→S       | Collect a matching slot stack into the cursor during YZUI survival left-drag                 |
| `mail_compose_open`         | C→S       | Request to open compose GUI                                                                   |
| `mail_open`                 | C→S       | Request inbox list                                                                            |
| `mail_sent_list_request`    | C→S       | Request sent-mail list                                                                        |
| `mail_recall`               | C→S       | Recall mail                                                                                   |
| `mail_purge`                | C→S       | Purge expired mail                                                                            |
| `mail_list_request`         | C→S       | View a player's mailbox                                                                       |
| `mail_fetch`                | C→S       | Fetch full mail for editing                                                                   |
| `mail_action`               | C→S       | Open/read/star/claim/delete                                                                   |
| `mail_admin_send`           | C→S       | Publish mail                                                                                  |
| `mail_admin_edit`           | C→S       | Edit/cancel-edit mail                                                                         |
| `mail_player_list_request`  | C→S       | Request the registered player-name list                                                       |
| `afk_heartbeat`             | C→S       | AFK heartbeat packet (client reports input activity)                                         |

---

## 🔧 Tech Stack & Dependencies

| Dependency             | Version                                | Purpose                                                         |
| ---------------------- | -------------------------------------- | --------------------------------------------------------------- |
| Minecraft              | 26.2                                   | Engine                                                          |
| Fabric Loader          | 0.19.3                                 | Mod loader                                                      |
| Fabric API             | 0.154.0+26.2                           | Standard API                                                    |
| ModMenu                | 20.0.0-beta.4                          | Mod menu integration                                            |
| Placeholder API        | 3.1.0-beta.1+26.2                      | Text placeholders                                               |
| Trinkets               | Exactly 4.1.0-rc.1+26.2 (`trinkets_updated`) | Trinket slot system (Feature 34 depends on it; hard dependency; beta.2 APIs are no longer supported) |
| GeckoLib               | 5.5.3+                                 | Entity animation & model rendering (hard dependency)            |
| Moog's Structure Lib   | 3.0.4                                  | Declared dependency (referenced by village structure injection) |
| Fabric Permissions API | 0.6.1 (bundled)                        | Cross-mod permission API                                        |
| LuckPerms              | 5.5 (suggested runtime)                | Advanced permission control                                     |

**Build Requirements**: JDK 25+ · Gradle (Fabric Loom 1.16-SNAPSHOT) · Mod version `1.20.5-indev`

**Build & CI/CD**: GitHub Actions workflow (`.github/workflows/build.yml`) auto-builds on Ubuntu / Windows / macOS with JDK 25, with automatic Linux artifact upload.

---

## 🏗️ Project Structure

```
src/                                       # 452 Java source files (main 273 / client 179)
├── main/java/top/csituka/youzaiworldcore/
│   ├── YouzaiworldCore.java              # Main entry point
│   ├── account/                          # Account auth (data/command/mixin/util subpackages)
│   ├── block/ + entity/                  # Custom blocks & block entities
│   ├── command/                          # Command registration (Afk/Event/Function/Reload/TeleportAnchor/Update/Status)
│   ├── api/                              # Api bridge (ApiHttp shared HMAC transport + ApiServiceClient accounts/cosmetics)
│   ├── component/                        # Data components
│   ├── config/                           # Server external settings (global/per-player/event + per-module config, 17 sections)
│   ├── data/                             # Teleport anchor SavedData
│   ├── dimensionalinventories/           # Dimension pool system (incl. WorldPoolTeleportPayload)
│   ├── enchantment/                      # Custom enchantment ResourceKey registration (ModEnchantments, 12)
│   ├── enchlevellangpatch/               # Enchantment-level language patch (api + impl)
│   ├── event/                            # Event handlers (31: end portal, double doors, Laowu Meme, void staff, bone-meal sugar cane, charged creeper, warden, stonecutter, teleport-stone charge, leeching, wind charge, smelting, sun repair, spirit turbo, baby zombie, jukebox loop, craft sound, painting drop, anvil repair, death sound, etc.)
│   ├── entity/seat/                      # Seat entity system
│   ├── invisibility/                     # Invisibility system
│   ├── item/                             # Items, tools, creative tabs (7), presets (9), invisible item frames
│   ├── luckperms/                        # LuckPerms integration (LuckPermsHelper unified auth)
│   ├── mail/                             # Mail system (Mail / MailManager / MailApiClient / MailSettings / MailPermissionHelper; data lives on the Api server)
│   ├── mana/                             # Mana system
│   ├── mixin/                            # Mixins (35; subpackages: afk / babyzombie / chargedcreeper / craftsound / damagenumber / doubledoors / invisibility / jukebox / painting / pet / seat / skill / trialvault)
│   ├── network/                          # Network packets (48 Payload classes + ModNetworking)
│   ├── pet/                              # Pet system (config/command/event subpackages + PetGlobalState/PetEntry)
│   ├── placeholders/                     # Placeholder API (32 placeholders)
│   ├── respawn/                          # In-place respawn (InPlaceRespawnManager)
│   ├── screen/                           # Container menus
│   ├── skill/                            # Adventure level + attribute system
│   ├── sound/                            # Custom SoundEvent (1: cloud_genshin)
│   ├── status/                           # Stats system (StatsManager, 21 metrics + commands)
│   ├── trialvault/                       # Unlimited trial vault reward config (TrialVaultConfig)
│   ├── update/                           # Update checker (UpdateChecker + 5 supporting files)
│   ├── util/                             # DebugLogger, TrinketHelper, BackupArchive, etc.
│   └── worldgen/                         # World generation (VillageStructureInjector)
│
├── client/java/top/csituka/youzaiworldcore/
│   ├── client/Client.java                # Client entry (registers highlight/border/anvil/mail commands, etc.)
│   ├── command/                          # Client commands (Invisibility / DoubleDoors / Pet / Mail forwarding + YzwcServerMirrorCommand placeholder mirror)
│   ├── config/                           # Client external settings (incl. yzuiEnabled) + ConfigIOManager (config import/export) + PlatformDetector
│   ├── effect/                           # Teleport FOV effect
│   ├── higherchat/                       # Chat box position optimization (HUD icon position tracking)
│   ├── highlightitem/                    # Item highlight (HighlightItemClient / HighLightCommands / Configurator / Colors / ItemComparator)
│   ├── itemborder/                       # Item border (ItemBorderClient / ItemBorderConfig / ItemBorderRenderer)
│   ├── anviluses/                        # Anvil use-count display (AnvilUsesClient)
│   ├── client/accessor/                  # Render accessors (RenderCrownDuck, ConnectScreenCancelAccess)
│   ├── hud/                              # Mana / adventure-level HUDs / YZUI inventory·equipment·status-effect·scoreboard·health·food·armor·oxygen HUDs / hotbar / ToolInfoOverlay
│   ├── skill/                            # Client adventure level / attribute data (ClientAttributeData)
│   ├── update/                           # Update checker client state (ClientUpdateState)
│   ├── mixin/client/                     # Client Mixins (60: title, options, button, pause, chat, loading, seat, rendering, pickup, enchant-patch, itemborder, YZUI inventory·health bar·contextual bar·recipe book, technocrown, AFK input, experimental warning skip, etc.)
│   ├── network/                          # Client network handling (ClientNetworking)
│   ├── laowumeme/                        # Laowu Meme client (Geo model/render/audio pool)
│   ├── particle/                         # Particle rendering (block animation / item sparkle)
│   ├── pickup/                           # Pickup display (item/XP floating notifications + subtitle capture)
│   ├── renderer/                         # Block/entity renderers (incl. teleport anchor BER, fly beacon BER, feature/TechnoCrownFeatureRenderer)
│   └── screen/                           # GUI screens (MenuScreen, Login/Register, YzuInventoryScreen/YzuCreativeInventoryScreen, MailScreen/MailComposeScreen/MailSentScreen, element/widget/block subpackages)
│
└── main/resources/
    ├── assets/youzaiworldcore/           # Textures, models, language files (10 languages × 736 keys), sounds (4 .ogg: cloud_genshin + laowu2/qiliang/zhanhou), painting textures (12 meme paintings)
    ├── data/                             # Advancements (31), recipes (20), loot tables (6 block + 4 chest), dimensions (login_hall), structures (9: 5 village variants + 3 ruins + cloud_genshin_ruins), structure sets, template pools, enchantments (12 JSON), beginner tutorial functions (19), jukebox_song (1), trinkets slots
    └── fabric.mod.json                   # Mod metadata (declares fabric-api / placeholder-api / modmenu / moogs_structures / trinkets_updated / geckolib as hard dependencies)

.github/workflows/
└── build.yml                             # CI/CD build workflow
```

---

## 📦 Recipe List

| Recipe                                                        | Type      | Description                                                    |
| ------------------------------------------------------------- | --------- | -------------------------------------------------------------- |
| `yz_ingot_from_blasting_raw_yz`                               | Blasting  | Raw Youzai → Youzai Ingot                                      |
| `yz_block_from_blasting_raw_yz_block`                         | Blasting  | Raw Block → Youzai Block                                       |
| `yz_ingot_from_yz_block`                                      | Crafting  | Youzai Block → 9 Ingots                                        |
| `yz_ingot_from_nuggets`                                       | Crafting  | 9 Nuggets → Ingot                                              |
| `yz_block`                                                    | Crafting  | 9 Ingots → Block                                               |
| `yz_nugget_from_ingot`                                        | Crafting  | Ingot → 9 Nuggets                                              |
| `yz_pickaxe` / `yz_axe` / `yz_shovel` / `yz_hoe` / `yz_sword` | Crafting  | Youzai tools                                                   |
| `decomposition_table`                                         | Crafting  | Decomposition Table                                            |
| `fly_beacon`                                                  | Crafting  | Fly Beacon                                                     |
| `heart_of_guardianship`                                       | Crafting  | Heart of Guardianship                                          |
| `void_staff`                                                  | Crafting  | Void Staff                                                     |
| `invisible_item_frame`                                        | Shapeless | Item Frame + Phantom Membrane → Invisible Item Frame           |
| `invisible_glow_item_frame`                                   | Shapeless | Glow Item Frame + Phantom Membrane → Invisible Glow Item Frame |
| `raw_yz_block` / `raw_yz_from_raw_yz_block`                   | Crafting  | Ore block conversion                                           |
| `craftable_end_portal`                                        | Crafting  | End Portal Frame ×12 (ender eyes + dragon egg + end stone)     |

> 20 recipe files total (`data/youzaiworldcore/recipe/`). The **Flame Staff** and **Sky Star Staff** currently have no crafting recipe — they are only obtainable from the creative tab or via commands.

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

> **Note**: Test the mod on a server environment; running it on the client alone will not function correctly. Client commands (`/yzwc pet`, `/yzwc mail`, `/yzwc function *`, `/yzwc settings highlight_item`) require being connected to a server to take effect.
>
> **On YZUI**: The YZUI interface system (inventory / HUD / recipe book restyle) is purely client-side and can be turned off via the "Enable YZUI" checkbox in the Visual section of client settings; everything then reverts to vanilla rendering so resource packs can take over.
