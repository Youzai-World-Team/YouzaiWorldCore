# YouzaiWorldCore — 悠哉世界核心模组

<p align="center">
  <img src="https://mcyzw.top/images/banner.webp" alt="Banner">
</p>

<p align="center">
  <a href="https://github.com/Youzai-World-Team"><img src="https://img.shields.io/badge/Organization-Youzai_World_Team-blue?style=for-the-badge&logo=github" alt="Organization"></a>
  <a><img src="https://img.shields.io/badge/Minecraft-Java_26.2-brightgreen?style=for-the-badge&logo=minecraft" alt="Minecraft Version"></a>
  <a><img src="https://img.shields.io/badge/Mod_Loader-Fabric-orange?style=for-the-badge" alt="Mod Loader"></a>
  <a><img src="https://img.shields.io/badge/License-Apache_2.0-green?style=for-the-badge" alt="License"></a>
  <a href="https://nightly.link/Youzai-World-Team/YouzaiWorldCore/workflows/build/main/youzaiworldcore.zip"><img src="https://img.shields.io/badge/下载-最新构建-blue?style=for-the-badge&logo=githubactions" alt="Download Latest Build"></a>
</p>

<div align="center">

#### **简体中文** | [**English**](README.EN.md)

</div>

## 📖 项目概述

**YouzaiWorldCore** 是悠哉世界（Youzai World）Minecraft 多人服务器的核心玩法模组，基于 **Fabric** 框架开发，深度集成 **LuckPerms** 权限系统与 **Placeholder API**。模组为服务器提供完整的基础设施，涵盖账户认证、GUI 菜单、自定义物品与方块、坐姿交互、维度池、传送锚点、魔力系统、动画字幕、新手教程、冒险经验、隐身管理、语音聊天集成等 21 项核心能力。

### 目标用户群体

| 用户类型 | 说明 |
|---------|------|
| **服务器管理员** | 通过命令和菜单管理系统，配置维度池、账户策略、实验性功能等 |
| **生存玩家** | 使用悠哉系列工具、成就系统、传送锚点、坐姿交互、魔力法杖进行游戏 |
| **模组开发者** | 了解模组架构、扩展功能或贡献代码 |

---

## ✨ 功能介绍

### 1. 账户认证系统

为离线模式服务器提供完整的密码认证，通过 Mixin 拦截实现未认证行为的全面限制。

- **密码安全**：BCrypt 加盐哈希（兼容旧版 SHA-256），5 次登录尝试上限
- **登录冷却/锁定**：失败 5 次后触发，默认冷却 300 秒（5 分钟）；支持永久锁定、限时冷却、永不锁定三种模式，管理员可通过命令解锁
- **会话管理**：可配置的会话超时，支持同 IP 自动恢复
- **位置保存/恢复**：登出时保存位置 → 传送至末地虚空；登录后精确保留位置恢复
- **登录大厅**：未认证玩家被限制在 `youzaiworldcore:login_hall` 自定义维度，Mixin 阻止移动、交互、攻击、聊天
- **隐身联动**：隐身状态下禁止执行登出、注销、改密等敏感操作

### 2. GUI 菜单系统

Windows 10 开始菜单风格的磁贴布局，支持页面切换与动画过渡。

| 菜单 ID | 名称 | 说明 |
|---------|------|------|
| `main` | 主菜单 | 功能总入口：切换世界、活动、签到、教程、设置等 |
| `switch_world` | 切换世界 | 11 个世界按钮，前 7 个集成维度池系统 |
| `settings` | 设置 | 音乐/音效开关、PVP/友军伤害、难度选择 |
| `about_me` | 关于我 | 3D 玩家模型渲染、ID、加入/游玩时间 |

**快捷键**：`Shift + F` 打开主菜单。

### 3. 标题界面改造

通过 `TitleScreenMixin` 对 Minecraft 主菜单进行了全面改造：自定义按钮（加入服务器/选项/退出）、公告横幅（含淡入动画）、渐变背景（`GradientBackgroundUtil`）、Mojang Logo 替换为自定义资源，以及开发者模式下的测试页按钮。

### 4. 窗口定制化

- **自定义窗口图标**：运行时通过 Java ImageIO 加载 `jar_icon.png` 替换任务栏与标题栏图标
- **自定义窗口标题**：`WindowTitleMixin` 拦截 `Window.setTitle()`，标题替换为"悠哉世界"

### 5.悠哉系列工具与物品

一套全新的矿物与工具系列，等级对标钻石工具（耐久 1800，挖掘速度 8.0，附魔等级 10）。

| 物品 | 特殊效果 |
|------|---------|
| **悠哉铲 / 悠哉镐** | 潜行挖掘连锁前方 6 个同类方块 |
| **悠哉锄** | 潜行使用耕 3×3 区域 |
| **悠哉剑** | 4% 概率触发暴击，伤害翻倍 |
| **悠哉斧** | 跳劈对 3 格范围内敌人造成 50% 横扫伤害 |
| **守护之心** | 携带时死亡不掉落物品（Mixin 实现），每次消耗 1 个；剩余 10/5/3/2/1 时警告 |
| **凭虚法杖** | 右键切换飞行，每秒消耗 1 点耐久（最大 600），每 5 秒消耗饥饿值；耗尽自动关闭 |
| **烈焰法杖** | 蓄力发射火焰激光，消耗 10 魔力 |
| **星辰法杖** | 召唤陨石攻击，10 方块半径，消耗 60 魔力 |

### 6. 自定义方块

| 方块 | 特性 |
|------|------|
| **悠哉矿 / 深层悠哉矿** | 主世界生成，掉落经验 2–5，需钻石镐 |
| **悠哉原矿块 / 悠哉块** | 矿物存储方块 |
| **分解台** | GUI 分解物品为原材料 |
| **飞行信标** | 9.56 方块半径内提供飞行，激活时发光（亮度 12） |
| **传送锚点** | 激活后右键打开传送列表，支持命名/排序/删除/复制坐标（亮度 15） |

### 7. 坐姿交互系统

右键点击楼梯（StairBlock）或台阶（SlabBlock）即可坐下，无需命令或特殊物品。

- **触发条件**：主手为空 + 右键楼梯/台阶（无需潜行）
- **实现方式**：创建不可见、无碰撞箱的 `SeatEntity` 作为载具 → 玩家骑乘（`startRiding`）
- **Y 轴定位**：楼梯下半部分/台阶下半部分 → 坐高 +0.5；楼梯上半部分/台阶上半部分/双层台阶 → 坐高 +1.0
- **离开座位**：原版潜行下马机制（`removeVehicle`）
- **实体自毁**：无乘客时自动销毁

### 8. 魔力系统

全新的魔力值系统，为特殊法杖提供能量。

- **最大魔力**：100
- **自动恢复**：每 20 tick（1 秒）恢复 1 点
- **客户端 HUD**：`ManaHudRenderer` 渲染魔力条
- **网络同步**：每 5 tick 通过 `ManaSyncPayload` 向客户端同步
- **消耗途径**：烈焰法杖（10 魔力/次）、星辰法杖（60 魔力/次）

### 9. 传送锚点系统

基于方块的玩家自主传送网络，右键交互即可命名、保存和远程传送。

- **方块**：`tp_anchor`，激活后发光（亮度 15），由 `TeleportAnchorBlockEntity` 管理激活者集合
- **激活流程**：右键未激活锚点 → `TeleportAnchorNameScreen` 命名界面（最多 32 字符）→ 粒子效果 + 音效
- **传送流程**：右键已激活锚点 → `TeleportAnchorScreen` 列表（显示维度池标签、维度、坐标）→ 选择目标 → 传送
- **消耗与冷却**：同维度 1 级经验 / 跨维度 2 级经验（创造免费）；3 秒全局冷却
- **维度池隔离**：锚点激活时记录所在维度池（`poolId`），跨池传送进行归属校验
- **编辑功能**：重命名、上移/下移排序、删除、一键复制坐标到剪贴板
- **数据持久化**：基于 `SavedData`，服务端重启不丢失
- **世界生成**：传送锚点遗迹在主世界（森林/针叶林/山地/平原/沼泽等 14 种生物群系，间距 28 chunks）、下界（间距 18 chunks）和末地（末地高地/末地内陆，间距 22 chunks）自然生成，基于 Moog's Structure Lib
- **村庄结构注入**：通过 `VillageStructureInjector` 替换 5 种原版村庄（平原/沙漠/热带草原/雪原/针叶林）的 `town_centers` 模板池，加入自定义传送锚点集会点结构
- **命令支持**：`/yzwc teleport_anchor list [player]` 列出传送点（含可点击传送链接）

### 10. 维度池系统

多世界服务器的独立状态池系统——不同池之间玩家拥有独立的背包、生命、效果和游戏模式。

- **7 个预定义池**：生存世界（含 overworld/nether/end）、主城、玩法、创造、建筑、指令区、教程世界
- **池切换流程**：检查目标池 → 保存当前状态到源池 → 清空背包 + 移除效果 → 加载目标池历史状态 → 传送 → 强制游戏模式
- **默认出生点**：每个池可配置玩家死亡后首次传送回的降落坐标
- **跨池传送**：支持维度传送门、命令传送、复活事件等多种触发
- **数据存储**：池配置 `config/youzaiworldcore/dimensional_inventories/pool_settings.json`，玩家状态 `<world>/youzaiworldcore/dimensional_inventories/data/<pool-id>/<uuid>.json`

### 11. 隐身系统

创造模式下的欺骗性隐身，从其他玩家的 Tab 列表和视野中完全消失。

- **命令**：`/yzwc function invisibility <true/false>`（需 OP 4 或对应权限）
- **行为**：伪装退服消息 → Tab 列表移除 → 视野中移除实体 → 显示白色 Boss 栏"隐身中"
- **8 个 Mixin**：抑制隐身玩家产生的粒子、音效、方块事件、容器动画（箱子/木桶/末影箱/潜影盒/饰纹陶罐）
- **Tick 检查**：每 10 tick 检测是否退出创造模式，自动强制关闭

### 12. 动画字幕系统

基于实体的动态字幕系统，支持逐字弹出动画和碎片分裂效果，用于 NPC 对话、剧情演出等场景。

- **实现方式**：`AnimationSubtitleEntity`（不可选中、不可推动、渲染距离 256 方块）
- **双模式架构**：主字幕（逐字弹出 → 保持显示 → 逐字掉落为碎片）和碎片（物理下落 → 沉降 → 静止 → 缩小消失）
- **命令**：`/yzwc function animation_subtitles`（需 OP 4 或对应权限）
  - `set pos <pos> <rot1> <rot2> <text> [time]` — 在指定坐标与朝向上生成
  - `set player_location <text> [time] [player]` — 在目标玩家面前 2 格处生成，自动匹配朝向
- **格式码支持**：使用 `&` 作为 § 格式码前缀，支持颜色（0-9, a-f）和样式（k/l/m/n/o/r）
- **动画参数**：弹出间隔 4 tick、弹出时长 8 tick、保持默认 100 tick（5 秒）、掉落间隔 1 tick
- **碎片物理**：重力 0.018、地面弹力 0.22、沉降 6 tick、静止 90 tick、缩小 20 tick

### 13. 冒险经验系统

基于玩家行为的经验等级系统，等级提升提供额外属性增益。

- **经验获取途径**：挖掘 50 方块（+25）、放置 50 方块（+25）、死亡（+10）、守护之心保护（+50）、不死图腾触发（+500）、完成进度（+50）
- **升级公式**：`expForNext = 50 + level × 50`
- **客户端 HUD**：`AdventureLevelHudRenderer` 渲染等级条
- **网络同步**：通过 `LevelExpSyncPayload` 同步经验值

### 14. 占位符系统

集成 Placeholder API，注册 `%luckperms_*%` 命名空间，提供 **32 个占位符**（11 个静态 + 21 个动态参数型）：`prefix`、`suffix`、`meta`、`meta_all`、`prefix_element`、`suffix_element`、`context`、`groups`、`inherited_groups`、`primary_group_name`、`has_permission`、`inherits_permission`、`check_permission`、`in_group`、`inherits_group`、`on_track`、`has_groups_on_track`、`highest_group_by_weight`、`lowest_group_by_weight`、`highest_inherited_group_by_weight`、`lowest_inherited_group_by_weight`、`highest_group_weight`、`current_group_on_track`、`next_group_on_track`、`previous_group_on_track`、`first_group_on_tracks`、`last_group_on_tracks`、`expiry_time`、`inherited_expiry_time`、`group_expiry_time`、`inherited_group_expiry_time` 等。

### 15. 权限系统

基于 LuckPerms 的细粒度权限控制，自动回退至原版 OP 等级检查。提供 **20 个独立权限节点**，含 `account.mgr.*`、`command.*`、`*` 通配符。

### 16. 创造模式标签页

创造模式物品栏重新组织为 **5 个独立标签页**：

| 标签页 ID | 名称 | 内容 |
|-----------|------|------|
| `youzai_blocks` | 悠哉方块 | 7 个自定义方块 |
| `youzai_tools_weapons` | 悠哉工具与武器 | 5 个悠哉系列工具 + 3 个法杖 |
| `youzai_materials` | 悠哉材料 | 原矿、锭、粒 |
| `youzai_utilities` | 悠哉实用物品 | 守护之心、Logo |
| `youzai_kits` | 悠哉工具包 | 4 个预设潜影盒 |

### 17. 预设物品系统

创造模式标签页中的四大预设潜影盒：

| 预设 | 颜色 | 内容 |
|------|------|------|
| 毕业套装 | 红色 | 满配下界合金装备、全附魔工具/武器、消耗品 |
| 毕业套补充 | 橙色 | 实用工具、建筑材料、额外防具 |
| 不死图腾 | 黄色 | 27 个不死图腾 |
| 炸药包 | 灰色 | 27 组 × 64 TNT |

### 18. 成就系统

两大进度分支，共 20+ 个成就：

- **悠哉世界**（主进度）：获取悠哉系列材料、制作工具、使用分解台/飞行信标/守护之心/凭虚法杖
- **趣味小挑战**：蛋糕是谎言、美食家、最大幸运、回家之路等

### 19. 调试与配置

| 配置 | 文件位置 | 内容 |
|------|---------|------|
| 服务端外部设置 | `config/youzaiworldcore/server_external_settings.json` | `devModeEnabled`、`logToFile`（双开关控制 DebugLogger） |
| 客户端外部设置 | `config/youzaiworldcore/client_external_settings.json` | `devModeEnabled`、`logLevel`（0-3）、调试地址/端口 |
| DebugLogger | `util/DebugLogger` | 四级日志（OFF/BASIC/DETAILED/DEBUG），entering/exiting/branch/stateChange/exception 追踪 |

### 20. CI/CD

GitHub Actions 工作流（`.github/workflows/build.yml`）：Ubuntu / Windows / macOS 三平台 JDK 25 自动构建，Linux 构建产物自动上传。

---

## 📜 指令树

所有指令以 `/yzwc` 为根命令。

```
/yzwc
├── teleport_world <targets> <dimension> [x] [y] [z] [yRot] [xRot]
│   ├── 权限：youzaiworldcore.command.teleport_world（OP 4）
│   └── 示例：/yzwc teleport_world @p minecraft:overworld 0 64 0
│
├── open_menu <menu_name> [target]
│   ├── 权限：youzaiworldcore.command.open_menu（OP 4）
│   ├── 可选菜单：main / switch_world / settings / about_me
│   └── 示例：/yzwc open_menu main
│
├── world_pool
│   ├── 权限：youzaiworldcore.command.world_pool（OP 4）
│   ├── teleport <targets> <pool_id>     → 传送到指定维度池
│   └── list                             → 列出所有维度池
│
├── teleport_anchor list [player]
│   ├── 权限：youzaiworldcore.command.teleport_anchor（OP 4）
│   └── 列出传送锚点（含可点击传送链接）
│
├── function invisibility <true/false>
│   ├── 权限：youzaiworldcore.command.function.invisibility（OP 4）
│   └── 限制：必须在创造模式
│
├── function animation_subtitles
│   ├── 权限：youzaiworldcore.command.function.animation_subtitles（OP 4）
│   ├── set pos <pos> <rot1> <rot2> <text> [time]  → 在指定坐标生成动画字幕
│   └── set player_location <text> [time] [player]  → 在目标面前生成动画字幕
│
├── experimental_feature <id> [true/false [all|only <player>]]
│   ├── 权限：.query（所有人）/ .self（所有人）/ .admin（OP 4）
│   └── 查询或切换实验性功能
│
├── reload
│   ├── 权限：youzaiworldcore.command.reload（OP 4）
│   └── 运行时重载账户数据和配置
│
└── account
    ├── 📋 玩家命令：
    │   ├── register <password> <confirm>           ← 注册（4-128 字符）
    │   ├── login <password>                        ← 登录（5 次上限，超限冷却 5 分钟）
    │   ├── logout                                  ← 登出（隐身状态下禁止）
    │   ├── deactivate <password>                   ← 注销账户
    │   └── change_password <old> <new> <confirm>   ← 改密
    │
    └── 🔧 管理员命令（OP 4）：
        ├── mgr create <player> <pass> <confirm>         ← 创建离线账户
        ├── mgr reset_password <player> <pass> <confirm> ← 重置密码
        ├── mgr delete <player>                          ← 删除账户
        ├── mgr session_timeout [seconds]                ← 会话超时（0=关闭）
        └── mgr login_cooldown
            ├── (无参数)         ← 显示当前冷却设置
            ├── set <seconds>    ← 设置（-1=永不，0=永久，>0=秒数）
            ├── status <player>  ← 查询锁定状态
            └── unlock <player>  ← 解锁账户
```

### 权限节点一览

| 权限节点 | 说明 | 回退等级 |
|---------|------|---------|
| `youzaiworldcore.command.teleport_world` | 跨维度传送 | OP 4 |
| `youzaiworldcore.command.open_menu` | 打开 GUI 菜单 | OP 4 |
| `youzaiworldcore.command.reload` | 模组重载 | OP 4 |
| `youzaiworldcore.command.world_pool` | 维度池管理 | OP 4 |
| `youzaiworldcore.command.teleport_anchor` | 传送锚点管理 | OP 4 |
| `youzaiworldcore.command.function.invisibility` | 隐身功能 | OP 4 |
| `youzaiworldcore.command.function.animation_subtitles` | 动画字幕 | OP 4 |
| `youzaiworldcore.command.experimental_feature` | 实验性功能（基础） | 所有人 |
| `youzaiworldcore.command.experimental_feature.query` | 查询 | 所有人 |
| `youzaiworldcore.command.experimental_feature.self` | 自切换 | 所有人 |
| `youzaiworldcore.command.experimental_feature.admin` | 管理 | OP 4 |
| `youzaiworldcore.command.account.mgr.create` | 创建账户 | OP 4 |
| `youzaiworldcore.command.account.mgr.reset_password` | 重置密码 | OP 4 |
| `youzaiworldcore.command.account.mgr.delete` | 删除账户 | OP 4 |
| `youzaiworldcore.command.account.mgr.session_timeout` | 会话超时 | OP 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown` | 登录冷却 | OP 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown.status` | 锁定状态查询 | OP 4 |
| `youzaiworldcore.command.account.mgr.login_cooldown.unlock` | 解锁 | OP 4 |
| `youzaiworldcore.command.account.mgr.*` | 账户管理通配符 | OP 4 |
| `youzaiworldcore.command.*` | 所有命令通配符 | — |
| `youzaiworldcore.*` | 全模组通配符 | — |

---

## 🧪 实验性功能

实验性功能系统框架已完全实现，支持服务端全局开关 + 玩家级覆写 + 服务端控制模式（`serverSide`）。配置持久化到 `config/youzaiworldcore/experimental_feature/`（`server_settings.json` / `client_settings.json`），通过 `FeatureSyncPayload` 同步。

> **当前状态**：暂无已注册的实验性功能。维度池系统已脱离实验性阶段，作为核心功能直接启用。

---

## 🖥️ 菜单与网络数据包

### GUI 菜单 ID

| 内部 ID | 名称 | 层级 |
|---------|------|------|
| `main` | 主菜单 | 根菜单 |
| `switch_world` | 切换世界 | 主菜单 → 切换世界 |
| `settings` | 设置 | 主菜单 → 设置 |
| `about_me` | 关于我 | 主菜单 → 关于我 |

### 容器型 MenuType

| ID | 对应方块 |
|----|---------|
| `decomposition_table` | 分解台 |
| `fly_beacon` | 飞行信标 |

### 网络数据包（共 15 个）

| 数据包 ID | 方向 | 用途 |
|-----------|------|------|
| `open_menu` | S→C | 打开 GUI 菜单 |
| `feature_sync` | S→C | 同步实验性功能状态 |
| `open_auth_screen` | S→C | 打开认证界面 |
| `mana_sync` | S→C | 同步魔力值 |
| `level_exp_sync` | S→C | 同步冒险等级经验 |
| `world_pool_teleport` | C→S | 请求维度池传送 |
| `teleport_anchor_open_name` | S→C | 打开传送锚点命名界面 |
| `teleport_anchor_list` | S→C | 发送传送点列表 |
| `teleport_anchor_activate` | C→S | 激活传送锚点 |
| `teleport_anchor_teleport` | C→S | 请求传送 |
| `teleport_anchor_delete` | C→S | 删除传送点 |
| `teleport_anchor_rename` | C→S | 重命名传送点 |
| `teleport_anchor_reorder` | C→S | 调整排序 |
| `decompose_item` | C→S | 分解物品 |
| `fly_beacon_active` | C→S | 切换飞行信标 |

---

## 🔧 技术栈与依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Minecraft | 26.2 | 基础引擎 |
| Fabric Loader | 0.19.3 | 模组加载器 |
| Fabric API | 0.154.0+26.2 | Fabric 标准 API |
| ModMenu | 20.0.0-beta.4 | 模组菜单集成 |
| Placeholder API | 3.1.0-beta.1+26.2 | 文本占位符 |
| Moog's Structure Lib | 3.0.4 | 传送锚点遗迹世界生成 |
| Fabric Permissions API | 0.6.1（内置） | 跨模组权限 API |
| LuckPerms | 5.5（建议运行时） | 高级权限控制 |

**构建要求**：JDK 25+ · Gradle（Fabric Loom 1.16-SNAPSHOT）

---

## 🏗️ 项目结构

```
src/
├── main/java/top/csituka/youzaiworldcore/
│   ├── YouzaiworldCore.java              # 主入口
│   ├── account/                          # 账户认证（Mixin + JSON 存储）
│   ├── block/ + entity/                  # 自定义方块与方块实体
│   ├── command/                          # 命令注册
│   ├── component/                        # 数据组件
│   ├── config/                           # 服务端外部设置
│   ├── data/                             # 传送锚点 SavedData
│   ├── dimensionalinventories/           # 维度池系统
│   ├── event/                            # 事件处理器（铁砧修复/飞行信标/虚空法杖/坐姿交互）
│   ├── entity/seat/                      # 座椅实体系统
│   ├── entity/animation_subtitle/        # 动画字幕实体系统
│   ├── feature/                          # 实验性功能系统
│   ├── invisibility/                     # 隐身系统
│   ├── item/                             # 物品、工具、创造标签页、预设
│   ├── luckperms/                        # LuckPerms 权限集成
│   ├── mana/                             # 魔力系统
│   ├── mixin/                            # 主 Mixin（+ 隐身容器/粒子/音效 + 座椅 + 技能）
│   ├── network/                          # 网络数据包（15 个）
│   ├── placeholders/                     # Placeholder API 集成（32 个占位符）
│   ├── screen/                           # 容器菜单
│   ├── skill/                            # 冒险经验等级系统
│   ├── worldgen/                         # 世界生成（村庄结构注入器）
│   └── util/                             # DebugLogger 等工具
│
├── client/java/top/csituka/youzaiworldcore/
│   ├── client/Client.java                # 客户端入口
│   ├── config/                           # 客户端外部设置
│   ├── effect/                           # 传送 FOV 效果
│   ├── higherchat/                       # Simple Voice Chat 集成（HUD 图标位置跟踪，优化聊天框位置避免遮挡）
│   ├── hud/                              # 魔力条 / 冒险等级 HUD
│   ├── mixin/client/                     # 客户端 Mixin（标题/选项/按钮/暂停/聊天/加载/座椅/渲染 等，共 19 个）
│   ├── renderer/                         # 方块/实体渲染器
│   └── screen/                           # GUI 屏幕
│       ├── MenuScreen.java               # 菜单容器
│       ├── LoginScreen / RegisterScreen  # 认证界面
│       ├── element/                      # 菜单元素组
│       ├── widget/                       # UI 组件
│       └── block/                        # 方块 GUI
│
└── main/resources/
    ├── assets/youzaiworldcore/           # 纹理、模型、语言文件
    └── data/                             # 成就、配方、战利品表、维度、结构、结构集、模板池

.github/workflows/
└── build.yml                             # CI/CD 构建工作流
```

---

## 📦 配方清单

| 配方 | 类型 | 描述 |
|------|------|------|
| `yz_ingot_from_blasting_raw_yz` | 熔炼 | 悠哉原矿 → 悠哉锭 |
| `yz_block_from_blasting_raw_yz_block` | 熔炼 | 原矿块 → 悠哉块 |
| `yz_ingot_from_yz_block` | 合成 | 悠哉块 → 9 悠哉锭 |
| `yz_ingot_from_nuggets` | 合成 | 9 悠哉粒 → 悠哉锭 |
| `yz_block` | 合成 | 9 悠哉锭 → 悠哉块 |
| `yz_nugget_from_ingot` | 合成 | 悠哉锭 → 9 悠哉粒 |
| `yz_pickaxe` / `yz_axe` / `yz_shovel` / `yz_hoe` / `yz_sword` | 合成 |悠哉系列工具 |
| `decomposition_table` | 合成 | 分解台 |
| `fly_beacon` | 合成 | 飞行信标 |
| `heart_of_guardianship` | 合成 | 守护之心 |
| `void_staff` | 合成 | 凭虚法杖 |
| `flame_staff` | 合成 | 烈焰法杖 |
| `sky_star_staff` | 合成 | 星辰法杖 |
| `raw_yz_block` / `raw_yz_from_raw_yz_block` | 合成 | 原矿块转换 |

---

## 🌐 相关链接

- **官方网站**：[https://mcyzw.top](https://mcyzw.top)
- **GitHub 仓库**：[Youzai-World-Team/YouzaiWorldCore](https://github.com/Youzai-World-Team/YouzaiWorldCore)
- **问题反馈**：[Issues](https://github.com/Youzai-World-Team/YouzaiWorldCore/issues)

---

## 🤝 贡献者

**核心作者**：Maskviva, ress2338396, zxabinbina, Youzai World Team  
**贡献者**：byzzdemy, Fogg05, lucko, MDLC01, why

---

> **注意**：测试模组请在服务端进行，客户端单独运行无法正常工作。
