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

**YouzaiWorldCore** 是悠哉世界（Youzai World）Minecraft 多人服务器的核心玩法模组，基于 **Fabric** 框架开发，深度集成 **LuckPerms** 权限系统与 **Placeholder API**。模组为服务器提供完整的基础设施，涵盖账户认证、GUI 菜单、YZUI 界面系统（物品栏 / HUD / 配方书全面重绘）、自定义物品与方块、坐姿交互、维度池、传送锚点与传送卷轴、魔力系统、AFK 挂机检测、隐身管理、冒险等级与属性成长、附魔等级语言补丁、拾取显示、世界增强（带电苦力怕 / 末影龙掉鞘翅 / 末地传送门 / 监守者战利品 / 切石机伤害 / 试炼宝库无限领奖等）、宠物系统、物品高亮与边框、邮件信箱、自定附魔（13 个）、饰品槽集成与 YZUI 饰品交互、老吴贴贴彩蛋、配置导入导出、新手教程、语音聊天集成等 40 余项核心能力。

### 目标用户群体

| 用户类型         | 说明                                                                                                |
| ---------------- | --------------------------------------------------------------------------------------------------- |
| **服务器管理员** | 通过命令和菜单管理系统，配置维度池、账户策略、宠物备份、邮件公告、事件开关等                        |
| **生存玩家**     | 使用悠哉系列工具、成就系统、传送锚点、坐姿交互、魔力法杖、宠物与属性成长、YZUI 界面与饰品槽进行游戏 |
| **模组开发者**   | 了解模组架构、扩展功能或贡献代码                                                                    |

> **版本说明**：本模组面向 Minecraft **Java 26.2**。26.1 起 Mojang 采用新的命名/源码规则，游戏 jar 已去除混淆，可直接反编译参考最新实现。

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
- **账户注销联动**：账户注销/删除时同时清空其邮件信箱（`MailManager.onAccountDeleted`）

### 2. GUI 菜单系统

Windows 10 开始菜单风格的磁贴布局，支持页面切换与动画过渡。

| 菜单 ID        | 名称     | 说明                                                 |
| -------------- | -------- | ---------------------------------------------------- |
| `main`         | 主菜单   | 功能总入口：切换世界、活动、等级、教程、邮件、设置等 |
| `switch_world` | 切换世界 | 11 个世界按钮，前 7 个集成维度池系统                 |
| `settings`     | 设置     | 音乐/音效开关、PVP/友军伤害、难度选择（客户端）      |
| `about_me`     | 关于我   | 3D 玩家模型渲染、ID、加入/游玩时间                   |

**快捷键**：`Shift + F` 打开主菜单。

### 3. 标题界面改造

通过 `TitleScreenMixin` 对 Minecraft 主菜单进行了全面改造：自定义按钮（加入服务器/选项/退出）、公告横幅（含淡入动画）、渐变背景（`GradientBackgroundUtil`）、Mojang Logo 替换为自定义资源，以及开发者模式下的测试页按钮。

### 4. 窗口定制化

- **自定义窗口图标**：运行时通过 Java ImageIO 加载 `jar_icon.png` 替换任务栏与标题栏图标
- **自定义窗口标题**：`WindowTitleMixin` 拦截 `Window.setTitle()`，标题替换为"悠哉世界"

### 5. 悠哉系列工具与物品

一套全新的矿物与工具系列，等级对标钻石工具（耐久 1800，挖掘速度 8.0，附魔等级 10）。

| 物品                | 特殊效果                                                                     |
| ------------------- | ---------------------------------------------------------------------------- |
| **悠哉铲 / 悠哉镐** | 潜行挖掘连锁前方 6 个同类方块                                                |
| **悠哉锄**          | 潜行使用耕 3×3 区域                                                          |
| **悠哉剑**          | 4% 概率触发暴击，伤害翻倍                                                    |
| **悠哉斧**          | 跳劈对 3 格范围内敌人造成 50% 横扫伤害                                       |
| **守护之心**        | 携带时死亡不掉落物品（Mixin 实现），每次消耗 1 个；剩余 10/5/3/2/1 时警告    |
| **凭虚法杖**        | 右键切换飞行，每秒消耗 1 点耐久（最大 600），每 5 秒消耗饥饿值；耗尽自动关闭 |
| **烈焰法杖**        | 蓄力发射火焰激光，消耗 10 魔力                                               |
| **天星法杖**        | 召唤陨石攻击，10 方块半径，消耗 60 魔力                                      |

### 6. 自定义方块

| 方块                    | 特性                                                           |
| ----------------------- | -------------------------------------------------------------- |
| **悠哉矿 / 深层悠哉矿** | 主世界生成，掉落经验 2–5，需钻石镐                             |
| **悠哉原矿块 / 悠哉块** | 矿物存储方块                                                   |
| **分解台**              | GUI 分解物品为原材料                                           |
| **飞行信标**            | 9.56 方块半径内提供飞行，激活时发光（亮度 12）                 |
| **传送锚点**            | 激活后右键打开传送列表，支持命名/排序/删除/复制坐标（亮度 15） |

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
- **消耗途径**：烈焰法杖（10 魔力/次）、天星法杖（60 魔力/次）

### 9. 传送锚点系统

基于方块的玩家自主传送网络，右键交互即可命名、保存和远程传送。

- **方块**：`tp_anchor`，激活后发光（亮度 15），由 `TeleportAnchorBlockEntity` 管理激活者集合
- **激活流程**：右键未激活锚点 → `TeleportAnchorNameScreen` 命名界面（最多 32 字符）→ 粒子效果 + 音效
- **传送流程**：右键已激活锚点 → `TeleportAnchorScreen` 列表（显示维度池标签、维度、坐标）→ 选择目标 → 传送
- **消耗与冷却**：同维度 1 级经验 / 跨维度 2 级经验（创造免费）；3 秒全局冷却
- **维度池隔离**：锚点激活时记录所在维度池（`poolId`），跨池传送进行归属校验
- **编辑功能**：重命名、上移/下移排序、删除、一键复制坐标到剪贴板
- **数据持久化**：基于 `SavedData`，服务端重启不丢失
- **渲染**：`TeleportAnchorBlockEntityRenderer` 通过 `RenderState` + 程序化自定义几何体（`queue.submitCustomGeometry`）绘制逐玩家纹理，无外部模型文件
- **村庄结构注入**：`VillageStructureInjector` 替换 5 种原版村庄（平原/沙漠/热带草原/雪原/针叶林）的 `town_centers` 模板池，注入带传送锚点的自定义集会点结构（基于原版 Jigsaw / 模板池 API）
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

- **命令**：`/yzwc function invisibility <true/false>`（需 OP 4 或对应权限；**客户端命令**，经 `InvisibilityPayload` 转发）
- **行为**：伪装退服消息 → Tab 列表移除 → 视野中移除实体 → 显示白色 Boss 栏"隐身中"
- **8 个 Mixin**：抑制隐身玩家产生的粒子、音效、方块事件、容器动画（箱子/木桶/末影箱/潜影盒/饰纹陶罐）
- **Tick 检查**：每 10 tick 检测是否退出创造模式，自动强制关闭

### 12. 冒险等级与属性系统

基于玩家行为的经验等级系统，并提供可分配的属性成长，二者共享同一成长框架。

- **冒险等级（经验）**
  - **经验获取途径**：挖掘 50 方块（+25）、放置 50 方块（+25）、死亡（+10）、守护之心保护（+50）、不死图腾触发（+500）、完成进度（+50）
  - **升级公式**：`C = 200 + 20 × log₁₀(2n)²⁰`（n 为当前等级，n ≥ 1；低等级 n≤5 时 C≈200–220，之后随 log₁₀(2n)²⁰ 幂律快速增长，n=10 约 4000，高等级触及 Int 上限）
  - **网络同步**：`LevelExpSyncPayload`（S→C）同步经验值
- **属性系统**
  - 升级获得的属性点可通过 `/yzwc` 属性菜单（GUI 元素）分配，映射到 10 项原版属性：`MAX_HEALTH`、`MOVEMENT_SPEED`、`JUMP_STRENGTH`、`LUCK`、`ATTACK_DAMAGE`、`BLOCK_BREAK_SPEED` 等
  - **客户端 HUD**：`AdventureLevelHudRenderer` 渲染等级与属性
  - **网络同步**：`AttributeSyncPayload`（S→C）同步属性数据；`AttributeUpgradePayload`（C→S）请求加点
  - **存储**：`config/youzaiworldcore/skill_module/player_level_data.json` 与 `player_attributes_data.json`（按玩家持久化）

### 13. 占位符系统

集成 Placeholder API，注册 `%luckperms_*%` 命名空间，提供 **32 个占位符**（11 个静态 + 21 个动态参数型）：`prefix`、`suffix`、`meta`、`meta_all`、`prefix_element`、`suffix_element`、`context`、`groups`、`inherited_groups`、`primary_group_name`、`has_permission`、`inherits_permission`、`check_permission`、`in_group`、`inherits_group`、`on_track`、`has_groups_on_track`、`highest_group_by_weight`、`lowest_group_by_weight`、`highest_inherited_group_by_weight`、`lowest_inherited_group_by_weight`、`highest_group_weight`、`current_group_on_track`、`next_group_on_track`、`previous_group_on_track`、`first_group_on_tracks`、`last_group_on_tracks`、`expiry_time`、`inherited_expiry_time`、`group_expiry_time`、`inherited_group_expiry_time` 等。

### 14. 权限系统

基于 LuckPerms 的细粒度权限控制，自动回退至原版 OP 等级检查。模组内所有命令与功能均通过 `luckperms/LuckPermsHelper` 统一鉴权，提供 **20+ 个独立权限节点**，含 `account.mgr.*`、`command.*`、`mail.*`、`*` 通配符。

### 15. 创造模式标签页

创造模式物品栏重新组织为 **6 个独立标签页**：

| 标签页 ID              | 名称            | 内容                                                                          |
| ---------------------- | --------------- | ----------------------------------------------------------------------------- |
| `youzai_blocks`        | 悠哉方块        | 7 个自定义方块                                                                |
| `youzai_tools_weapons` | 悠哉工具与武器  | 5 个悠哉系列工具 + 3 个法杖                                                   |
| `youzai_materials`     | 悠哉材料        | 原矿、锭、粒                                                                  |
| `youzai_utilities`     | 悠哉实用物品    | 守护之心、隐形物品展示框、隐形发光物品展示框                                  |
| `youzai_kits`          | 悠哉工具包      | 9 个预设潜影盒                                                                |
| `youzai_enchantments`  | 悠哉世界 - 附魔 | 本模组自定义附魔的附魔书，按 `ModEnchantments.ALL` 遍历并为每个等级各生成一本 |

### 16. 预设物品系统

创造模式「悠哉工具包」标签页中的九大预设潜影盒（`PresetItems.createPreset01`–`createPreset09`）：

| 预设       | 颜色   | 内容                                      |
| ---------- | ------ | ----------------------------------------- |
| 毕业套装   | 红色   | 满配下界合金装备、全附魔工具/武器、消耗品 |
| 毕业套补充 | 橙色   | 实用工具、建筑材料、额外防具              |
| 不死图腾   | 黄色   | 27 个不死图腾                             |
| 炸药包     | 灰色   | 27 组 × 64 TNT                            |
| 烟花火箭   | 粉红色 | 27 组烟花火箭                             |
| 重锤套装   | 淡蓝色 | 3 把不同附魔配置的重锤（Mace）            |
| 附魔之瓶   | 黄绿色 | 27 组 × 64 附魔之瓶                       |
| 末影珍珠   | 绿色   | 27 组 × 64 末影珍珠                       |
| 七彩箭矢   | 淡灰色 | 普通箭 / 光灵箭 / 各类药水箭组合          |

### 17. 成就系统

两大进度分支，共 **32 个**成就：

- **悠哉世界**（主进度）：获取悠哉系列材料、制作工具、使用分解台/飞行信标/守护之心/凭虚法杖
- **趣味小挑战**：蛋糕是谎言、美食家、最大幸运、回家之路、我成了建材（站在切石机上死亡）等
- **深暗之域（Deep Dark）**：新增专属分支，共 6 个进度：
  - `visit_deep_dark` — 进入深暗之域
  - `enter_ancient_city` — 踏入远古城市
  - `loot_ancient_city` — 开启远古城市战利品箱
  - `hold_recovery_compass` — 获得回收罗盘
  - `use_disc_5`（城市回响）— 获得唱片 5
  - `kill_warden`（监守者之陨）— 击败监守者

### 18. 调试与配置

| 配置           | 文件位置                                                    | 内容                                                                                     |
| -------------- | ----------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| 服务端外部设置 | `config/youzaiworldcore/server_external_settings.json`      | `devModeEnabled`、`logToFile`（双开关控制 DebugLogger）                                  |
| 客户端外部设置 | `config/youzaiworldcore/client_external_settings.json`      | `devModeEnabled`、`logLevel`（0-3）、`yzuiEnabled`（YZUI 界面总开关）、调试地址/端口     |
| DebugLogger    | `util/DebugLogger`                                          | 四级日志（OFF/BASIC/DETAILED/DEBUG），entering/exiting/branch/stateChange/exception 追踪 |
| 更新检查设置   | `config/youzaiworldcore/update_checker.json`                | `enabled`（开关更新检查，UpdateCheckerConfig）                                           |
| 试炼宝库设置   | `config/youzaiworldcore/trial_vault.json`                   | `enabled`（无限领奖开关，TrialVaultConfig，默认 true）                                   |
| 邮件设置       | `config/youzaiworldcore/mail_settings.json`                 | 过期策略、权限节点/等级、附件上限等                                                      |
| 宠物设置       | `config/youzaiworldcore/pet_module/settings.json`           | 宠物备份间隔等                                                                          |
| AFK 设置       | `config/youzaiworldcore/afk.json`                           | 检测阈值、前缀/广播/无敌/自动踢出等                                                     |
| 老吴贴贴设置   | `config/youzaiworldcore/laowu_meme.json`                    | 全局开关、冷却时间等                                                                    |
| 玩家统计数据   | `<world>/youzaiworldcore/status/data.json` + `rank_export/` | StatsManager 持久化统计与排行榜导出目录                                                  |

### 19. 附魔等级语言补丁系统

对 Minecraft 语言文件（含原版、资源包与模组）中**附魔等级**与**药水效力**的渲染进行补丁的能力型系统，让高等级附魔以更直观的形式（如中文数字）展示。

- **API（`EnchantmentLevelLangPatch`）**：`registerPatch` 通用键谓词补丁；`registerEnchantmentPatch` / `registerPotionPatch` 注册附魔等级 / 药水效力专用渲染钩子；`intToRoman` 提供整数到罗马数字转换（1–3998，查表实现）
- **中文数字补丁（`ChineseExchange`）**：将等级数字渲染为简体中文（一二三…）或大写中文（壹贰叁…），配套 `NumResultCacheMap` 缓存与 `ValueTableHolder` 数值表优化性能
- **配置切换**：`EnchantmentLevelLangPatchConfig.setCurrentEnchantmentHooks` / `setCurrentPotionHooks` 选择启用哪个补丁；`IndependentLangPatchRegistry` 以命名空间键（`NamespacedKey`）管理注册项
- **客户端接入**：`EnchantmentLevelLangPatchMixin` 将补丁注入语言加载流程
- **用途**：使中文玩家以「十级」「一百级」等中文数字阅读高等级附魔，避免罗马数字冗长难读

### 20. 拾取显示系统

客户端拾取反馈：拾取物品与经验时，在屏幕侧边 / 指定区域浮动显示本次获得的条目，提升拾取感知。

- **处理链路**：`AddEntriesHandler` 接收拾取事件并加入队列 → `PendingPickupQueue` 维护待显示条目 → `DrawEntriesHandler` 逐帧绘制
- **条目类型**：`DisplayEntry` 抽象基类，具体实现 `ItemDisplayEntry`（物品，含数量/堆叠信息）与 `ExperienceDisplayEntry`（经验值）
- **客户端接入**：`PickUpNotifyMixin` 拦截拾取通知驱动显示；`ClientNetworking` 处理客户端网络相关逻辑

### 21. 世界增强功能

一组原生集成、不依赖外部库的"世界微调"增强（灵感来自社区经典玩法），覆盖生物行为、掉落归集、末地机制、试炼密室与农业自动化：

- **天然带电苦力怕（Naturally Charged Creepers）**：苦力怕进入服务端世界时，以可配置概率（`chance`，默认 0.1 / 10%）标记为带电状态。通过 Mixin 暴露的 `DATA_IS_POWERED` 实体数据写入，确保客户端闪电光环正确同步；数据标签去重避免区块重载重复判定。配置 `config/youzaiworldcore/charged_creeper.json`（`enabled` 默认 true、`chance` 默认 0.1，自动钳制 [0,1]）。命令：`/yzwc event naturally_charged_creepers enable [true|false]` / `settings chance [double]`
- **紫颂果就近掉落（Chorus Fruit Drops Nearby）**：破坏紫颂植物后，其掉落的紫颂果会被就近传送至最近被破坏的紫颂植物位置（水平距离 < 20 格、2 秒时间窗内），避免果实散落满地
- **末影龙鞘翅掉落（Dragon Drops Elytra）**：末影龙被击杀时额外掉落一个鞘翅并广播提示；击杀归属优先级：直接玩家 → 弹射物发射者（弓/弩/三叉戟）→ 30 格半径内最近玩家
- **末地传送门增强**：① 末地传送门框可被精准采集镐破坏并掉落（含已嵌末影之眼），同时清除激活的传送门方块；② 末影龙被击杀时向附近玩家额外给予一个龙蛋；③ 新增合成配方 `craftable_end_portal`（末影之眼 + 龙蛋 + 末地石 → 12 个末地传送门框）。配置 `config/youzaiworldcore/end_portal_settings.json` 三项开关（精准采集要求 / 直接入背包 / 龙蛋提示）
- **监守者战利品（Warden Loot）**：玩家击杀监守者时直接发放 300 经验，并掉落 bundle 战利品（含尖啸催生体、下界合金碎片/钻石/金锭等随机池、远古城市风格物品、附魔书——50% 迅捷潜行 I–III / 50% 灵魂疾行 I–III，受抢夺附魔加成）。由 `WardenDeathHandler`（`ServerLivingEntityEvents.AFTER_DEATH`）实现，取代脆弱的数据包 tick 扫描
- **切石机伤害（Stonecutter Damage）**：站在切石机方块上会受到持续伤害——首次站上立即造成 1 颗心伤害，之后每 1.5 秒（30 tick）一次，直至离开；死亡显示自定义消息"尝试用身体测试切石机的锋利度"，并授予「我成了建材」成就。创造/旁观模式免疫。由 `StonecutterDamageHandler`（分时扫描 + 独立计时器，性能优先）实现
- **试炼宝库无限领奖（Trial Vault）★新增**：移除试炼宝库「每玩家仅可领奖一次」的限制，同一玩家可对同一宝库反复插钥匙领奖。由 `VaultServerDataMixin` 精确注入 `VaultServerData#hasRewardedPlayer`（恒返回 false）与 `#addToRewardedPlayers`（取消写入）实现，而非通配 Redirect。配置 `config/youzaiworldcore/trial_vault.json`（`enabled` 默认 true）。命令：`/yzwc event trial_vault enable [true|false]`。参考 trial-chamber-time-removal 设计思路，原生重写、无前置依赖
- **骨粉催熟甘蔗（Bone Meal Sugar Cane）★新增**：手持骨粉右键甘蔗可将其催熟一格（上限 3 格高）；同时注册**发射器行为**（`BoneMealSugarCaneDispenserBehavior`），发射器装骨粉亦可催熟正对的甘蔗，便于自动化农场
- **混凝土粉末遇水固化（Concrete Powder Solidify）★新增**：混凝土粉末以**掉落物实体**形式落入水中时自动固化为对应颜色的混凝土物品实体（原版仅方块形态固化）。`ConcretePowderSolidifyHandler` 每 20 tick（1 秒）扫描一次以控制性能，颜色映射表在初始化时由注册表构建

### 22. AFK 挂机检测系统

服务端自动检测玩家挂机状态，为长时间无操作的玩家标记 AFK 前缀并支持多种自动化处理。

- **检测机制**：每 20 tick 检查玩家鼠标移动/键盘输入/视角变化，可配置检测阈值（默认 300 秒）
- **AFK 标记**：Tab 列表昵称前方追加可配置前缀（如 `[AFK]`），由 `ServerPlayerTabDisplayNameMixin` + `AfkKeyboardHandlerMixin` / `AfkMouseHandlerMixin` 双向追踪
- **自动化处理**：可配置无敌模式（invulnerable）、自动踢出（auto_kick），AFK 期间广播提示
- **手动切换**：玩家可通过 `/yzwc afk` 手动进入/退出 AFK 状态
- **命令**（服务端）：
  - `/yzwc afk` —— 手动切换 AFK
  - `/yzwc afk status [player]` —— 查询自身/他人 AFK 状态
  - `/yzwc afk list` —— 列出所有 AFK 玩家（需管理权限）
  - `/yzwc afk settings <key> <value>` —— 运行时修改 AFK 配置（需管理权限）
- **配置**：`config/youzaiworldcore/afk.json`（`AfkConfig`，含 enabled/detect_mode/threshold/tab_prefix/broadcast/invulnerable/auto_kick/manual_toggle）

### 23. 传送卷轴系统 ★新增

一次性消耗品，右键蓄力 5 秒后打开传送列表（与传送锚点共用 GUI），传送成功后整张销毁。

- **物品**：`warp_scroll`（传送卷轴）、`return_scroll`（返回卷轴），`stacksTo(16)`
- **蓄力中断**：蓄力期间受到伤害中断，由 `TeleportStoneChargeHandler` 统一处理
- **传送卷轴（Warp Scroll）**：右键蓄力 5 秒 → 打开传送列表（与传送石共用 GUI）→ 选择目标传送 → 消耗 1 张 + 120 秒物品冷却，免 XP/耐久
- **返回卷轴（Return Scroll）**：右键蓄力 5 秒 → 自动寻找当前维度最近已激活锚点并传送 → 消耗 1 张 + 60 秒冷却；无可用锚点时动作栏提示但不消耗
- **冷却机制**：通过 `ServerPlayerGameModeCooldownMixin` 实现物品级冷却，与末影珍珠共用冷却框架

### 24. 魔力台 ★新增

装饰性方块（`magic_table`），作为服务器大厅/功能区的视觉点缀。

- **外观**：四面自定义贴图，自发光 2 级（`RenderShape.MODEL`）
- **属性**：硬度 5.0，爆炸抗性 1200（对齐原版附魔台），需用镐挖掘
- **用途**：纯装饰，无可交互 GUI；收录于「悠哉方块」创造标签页

### 25. 老吴贴贴彩蛋系统 ★新增

服务器娱乐彩蛋——两只猫在特定条件下触发贴贴动画、音效与全服粒子特效。

- **触发条件**：两只已驯服的猫在一定距离内，且有随机冷却（可配置）
- **效果**：Geo 骨骼动画、自定义音效（`laowu2.ogg` / `qiliang.ogg` / `zhanhou.ogg`）、全服粒子广播
- **实现**：`LaowuMemeHandler` 每 tick 扫描 + `SoundBufferLibraryLaowuMixin` 自定义音效加载 + 客户端 Geo 模型渲染
- **命令**：`/yzwc event laowu enable [true|false]` / `settings cd [seconds]`（全局开关与冷却）
- **配置**：`config/youzaiworldcore/laowu_meme.json`（`LaowuMemeConfig`）

### 26. 双开门系统

仅支持「同材质木门 / 栅栏门」点击双开的精简实现，按玩家独立开关。

- **触发方式**：徒手右键门 / 栅栏门，`DoorBlockMixin` / `FenceGateBlockMixin` 在 `useWithoutItem` 完成原版开关后调用 `DoubleDoorsHandler.onDoorClick`；潜行时禁用双开，仅保留原版单开
- **配对规则**：在 3×3 水平范围内搜索相邻、同类型（同为 `DoorBlock` 或同为 `FenceGateBlock`）、显示名（材质）相同的配对门，统一同步为被点击门的开合状态；不做递归（仅相邻双开）
- **支持范围**：木门（含双层）、栅栏门（自动对齐朝向）；铁门等无法徒手开启的方块、活板门、红石触发、村民 AI、连锁开门均不在范围内
- **玩家独立开关**：`/yzwc function double_doors [true|false]`（**客户端命令**）控制自身，缺省（不带参数）查询自身状态，新玩家默认开启
- **数据持久化**：`config/youzaiworldcore/double_doors_players.json`，仅保存被指令显式设置过的玩家（`DoubleDoorsState`，未设置者回退默认启用）
- **客户端转发架构**：`/yzwc` 根命令已在客户端注册（用于 `/yzwc settings` 及转发型子命令），故双开门、隐身等命令在客户端仅做解析与转发，权威状态由服务端通过 `DoubleDoorsTogglePayload` / `InvisibilityPayload`（C→S）承载

### 27. 宠物系统

基于原版狼（Wolf）的驯养追踪与管理系统，将已驯服的狼登记为"宠物"并提供长效的归属、信任与行为管理。

- **核心数据结构**：`PetEntry` 记录内部名称（`internalName`）、显示名、行为模式、主人 UUID、信任玩家集合、驯服时间、实体 UUID；全局注册表 `PetGlobalState` 持久化所有宠物
- **行为模式**：`hunting`（狩猎）/ `companionship`（陪伴）/ `attack`（攻击）/ `guard`（守卫）——经 `/yzwc pet set <内部名> mode` 切换
- **信任系统**：主人可将其他玩家加入信任列表，被信任者可以查看列表、高亮宠物；`trust add/remove/list <玩家>`
- **归属操作**：`rename`（重命名）、`transfer <新主人>`（转让所有权，原主人自动进入信任列表）、`release_life [force]`（放生，需二次确认）
- **快速定位**：`highlight <内部名>` 对目标狼施加 5 秒发光效果，便于在群体中定位
- **管理员运维**：`admin restore`（从最新备份恢复）、`admin backup_list`（列出备份）、`admin backup_interval <秒>`（设置定时备份间隔，60–3600 秒）
- **持久化**：`config/youzaiworldcore/pet_module/settings.json` + 定时备份 `pet_module/pet_backup_<时间戳>.json`
- **命令架构**：`/yzwc pet` 为**客户端命令**，客户端将参数经 `PetCommandPayload`（C→S）整体转发，服务端 `PetCommand` 持有完整 Brigadier 命令树与权限校验

### 28. 物品高亮系统

纯客户端功能，为手持或指定物品提供描边高亮，便于在背包 / 世界中快速定位目标物品（不影响服务端逻辑）。

- **控制方式**
  - **键位**：`F10` 切换高亮（`key.youzaiworldcore.highlight.toggle`），`B` 切换比较模式（`key.youzaiworldcore.highlight.comparator`）
  - **命令**：`/yzwc settings highlight_item`
    - `toggle` —— 开/关高亮
    - `color <名称 | custom r g b a>` —— 预设颜色或自定义 RGBA（r/g/b 0–255，a 0.0–1.0）
    - `mode <比较器>` —— 选择触发高亮的物品匹配规则
  - **设置界面**：客户端设置中提供「启用高亮 / 比较模式 / 提示偏好」选项
- **比较模式（Comparators）**：`item_only`（仅物品）、`item_and_amount`（物品+数量）、`item_and_nbt`（物品+NBT）、`item_and_nbt_and_amount`（物品+NBT+数量）、`name_only`（仅名称）、`name_and_amount`（名称+数量）、`namespace`（命名空间）
- **提示偏好（Notification）**：`none`（默认）/ `toast`（吐司）/ `chat`（聊天）/ `overlay`（覆盖层）
- **实现**：`highlightitem` 包（`HighlightItemClient` / `HighLightCommands` / `Configurator` / `Colors` / `ItemComparator`），通过客户端 Mixin 在渲染层注入描边；键位与命令即时生效

### 29. 统计系统（Status）

读取原版统计系统（`Stats`）的玩家行为数据，持久化保存并支持查询与排行榜导出。

- **入口**：`status/StatsManager`；数据持久化于 `<world>/youzaiworldcore/status/data.json`
- **指标**：共 **21 项**，涵盖在线时间、跳跃/死亡/击杀、伤害、步行/疾跑/鞘翅/坠落距离、钓鱼、交易、丢弃、睡觉、附魔、袭击、繁殖、敲钟、吃蛋糕，以及「红石大蛇榜」汇总的红石放置量
- **命令**（服务端）：
  - `/yzwc status <player> list` —— 查看该玩家各项统计（权限 `youzaiworldcore.command.status.query`）
  - `/yzwc status <player> delete` —— 删除该玩家统计记录（权限 `youzaiworldcore.command.status.delete`）
  - `/yzwc status rank_export <day|week|month|year|all> [name]` —— 导出排行榜至 `rank_export/<name>.json`（权限 `youzaiworldcore.command.status.export`）
- **权限**：`status.query` / `status.delete` / `status.export`（默认 OP 4）

### 30. 邮件系统（Mailbox）

单向的**管理员 → 玩家**服务器信箱 / 公告箱（非玩家间私聊），由管理员经 GUI 发布，玩家在信箱（Shift+F → 邮件）中查收、领取奖励。

- **定位**：发送方仅管理员（OP / LuckPerms `youzaiworldcore.mail` 节点）；接收方二选一——全体成员 / 指定玩家（从账户系统已注册名单中勾选）
- **邮件类型**：公告（ANNOUNCEMENT）、通知（NOTICE）、奖励（REWARD）
- **奖励载体**（REWARD 类型）：物品（最多 10 个槽位，从管理员物品栏复制为模板，不消耗原物）、命令（以控制台执行，支持 `%player%` / `%uuid%` 占位符）、原版经验值、原版等级、本项目冒险经验值、本项目冒险等级（四项可同时选择）
- **过期策略**：可选 1 天 / 7 天 / 30 天（默认）/ 永久；过期未星标自动清理（服务端每次启动时亦会清理「已过期且无任何玩家星标」的邮件），已星标保留文本但禁用领取
- **GUI**
  - **界面适配**：三个邮件界面均以 960×540 GUI 单位为设计基准等比缩放并居中，任意分辨率 / 界面尺寸下排版一致
  - **玩家信箱**（`MailScreen`）：筛选（全部/未读/已收藏）、详情、领取/星标/删除；右上角「发布邮件」「已发送邮件」按钮仅权限持有者可见，另有「返回」（回主菜单）与「关闭」按钮；主菜单进入带过渡动画
  - **发布/编辑页**（`MailComposeScreen`）：接收范围二选一（指定玩家经「选取玩家」弹窗勾选，支持搜索）+ 类型 + 主题 + 正文 + 附件（≤10 物品槽，显示稀有度边框，经「从物品栏选取」按钮挑选）+ 过期下拉；编辑已发送邮件亦复用此界面（`MailSentScreen`[编辑] → 预填 → `MailAdminEditPayload`）
  - **已发送页**（`MailSentScreen`）：已过期邮件不再提供「编辑」「撤回」入口
  - **操作反馈**：领取 / 撤回 / 权限不足等结果在界面顶部以浮动提示条显示（界面未打开时回落到聊天栏）
- **命令**（**客户端命令**，解析后转发；服务端统一鉴权）
  - `/yzwc mail send_mail` —— 打开发布邮件 GUI
  - `/yzwc mail sent` —— 打开已发送邮件管理列表
  - `/yzwc mail recall <mailId>` —— 撤回已发送邮件（删除仓库条目 + 在线推送移除）
  - `/yzwc mail purge [player|all]` —— 清理过期邮件
  - `/yzwc mail list [player]` —— 查看指定玩家信箱
- **权限**：`youzaiworldcore.mail`（默认 OP 4）；未装 LuckPerms 时以 `mail_permission_level` 回退
- **存储**：全局仓库 `config/youzaiworldcore/mail/sent.json` + 每玩家索引 `config/youzaiworldcore/mail/box/<uuid>.json` + 设置 `mail_settings.json`；跨世界一致，绑定账户系统（离线账户同样入索引，登录可见）
- **网络**：共 18 个专用数据包（C2S `mail_compose_open` / `mail_open` / `mail_sent_list_request` / `mail_recall` / `mail_purge` / `mail_list_request` / `mail_fetch` / `mail_action` / `mail_admin_send` / `mail_admin_edit` / `mail_player_list_request`；S2C `open_mail_compose` / `mail_list` / `mail_sent_list` / `mail_update` / `mail_op_result` / `mail_unread_count` / `mail_player_list`）

### 27. 自定义附魔

模组注册 **13 个数据驱动附魔**（定义于 `data/youzaiworldcore/enchantment/` JSON，由 `ModEnchantments` 注册 ResourceKey），含 2 个原创附魔与 11 个来自 Raiyon's More Enchantments 的移植附魔（原生重写，无外部依赖）。

#### 原创附魔

- **阳光修复（Sun Repair，`sun_repair`）**：「在阳光下修复工具」。由 `SunRepairHandler` 每 5–10 秒随机间隔检查在线玩家，对处于阳光下（有天空光照、非雨/雷、非夜晚、头顶无遮挡）且带此附魔的损坏物品每 tick 恢复 1 点耐久；覆盖手持、盔甲栏与整个物品栏
- **乐魂涡轮加速器（Spirit Turbo Booster，`spirit_turbo`）**：「附魔在挽具上以提升乐魂移动速度」。由 `HappyGhastTurboHandler` 每 20 tick 检查所有乐魂（Happy Ghast），若其挽具带此附魔，则为其 `FLYING_SPEED` 属性每级追加 +20% 飞行速度

#### 移植附魔（Raiyon's More Enchantments）

| 附魔                   | ID              | 适用物品 | 效果                                                     |
| ---------------------- | --------------- | -------- | -------------------------------------------------------- |
| **生命汲取（Leeching）**   | `leeching`      | 武器     | 击杀目标回复生命值                                       |
| **毒雾（Poison Puff）**   | `poison_puff`   | 武器     | 攻击时释放毒雾效果                                       |
| **火焰弹（Fire Charge）**  | `fire_charge`   | 弩       | 弩射出火焰弹                                             |
| **音爆弹（Sonic Charge）** | `sonic_charge`  | 弩       | 弩射出监守者音爆                                         |
| **发光光环（Glowing Aura）** | `glowing_aura` | 护甲     | 对周围实体施加发光效果，由 `GlowingAuraHandler` 每 tick 扫描 |
| **懦弱（Cowardice）**      | `cowardice`     | 护甲     | 低生命值时提升速度                                       |
| **风弹（Wind Charge）**    | `wind_charge`   | 护甲     | 受伤时释放风弹击退周围实体，由 `WindChargeHandler` 处理  |
| **尖刺（Spikes）**         | `spikes`        | 盾牌     | 反弹攻击者伤害                                           |
| **弹跳（Bounce）**         | `bounce`        | 盾牌     | 格挡时弹飞攻击者                                         |
| **熔炼（Smelting）**       | `smelting`      | 工具     | 挖掘方块时自动熔炼掉落物，由 `SmeltingHandler` 处理      |
| **陨星重击（Meteor Smash）** | `meteor_smash` | 重锤     | 重锤砸地时召唤陨石                                       |

> 以上 11 个移植附魔源自 Raiyon's More Enchantments，本项目参考其设计理念并原生重写，无外部依赖。

### 32. 铁砧使用次数显示

客户端提示增强（`anviluses` 包）。在物品悬浮提示中显示该物品经过铁砧加工的次数与剩余可维修次数：

- **已使用铁砧**：依据 `DataComponents.REPAIR_COST` 反推（`floor(log2(repairCost+1))`）
- **预计剩余可修**：递推模拟原版 `calculateIncreasedRepairCost` 规则，直到下次维修费用达到「过于昂贵」上限（40 级）；为 0 时提示「已无法再被铁砧维修」
- 灵感来自 Anvil Uses（Z1proW），参考 26.2 原版 `AnvilMenu` 反编译实现，独立重写、无外部依赖

### 33. 物品边框系统

客户端视觉增强（`itemborder` 包，参考 ItemBorders 设计理念）。为物品槽位绘制**稀有度渐变色边框**，纯原生 26.2 API（GuiGraphicsExtractor 管线 + `ItemStack.getRarity`），无外部依赖：

- **行为**：基于硬编码常量（总开关、快捷栏绘制、直角、完整四边、辉光增强、稀有度自动着色均默认开启）；普通（白色）稀有度物品默认不绘制
- **预设稀有度分配**：内置约 60 项分配——UNCOMMON（黄）18 项、RARE（青）19 项、EPIC（亮紫）22 项（含悠哉锭/系列工具/守护之心等本模组物品）
- 所有配置为硬编码常量，无可修改配置文件

### 34. Trinkets 饰品槽集成

通过 `data/trinkets/` 数据包为 **Trinkets** 模组声明 4 个自定义饰品槽（Trinkets 为硬依赖），让特定物品可装备至饰品栏而非主物品栏：

| 槽位（slots）    | 物品                              | 说明               |
| ---------------- | --------------------------------- | ------------------ |
| `chest/elytra`   | 鞘翅（Elytra）                    | 胸饰槽装备鞘翅     |
| `chest/backpack` | 背包（Backpack）                  | 胸饰槽装备背包     |
| `offhand/totem`  | 不死图腾（Totem of Undying）      | 副手槽装备不死图腾 |
| `offhand/heart`  | 守护之心（Heart of Guardianship） | 副手槽装备守护之心 |

每个槽位含自定义图标与 `trinkets:default` 校验器，order 控制排序。

**YZUI 饰品交互 ★新增**：YZUI 物品栏（生存 / 创造）不使用 Trinkets 注入的原生槽位，而是实现了一套**悬停指示器**交互——鼠标悬停在可装备槽位上时，在其旁弹出对应饰品槽指示器，点击即可装卸：

- **服务端权威**：所有操作经 C→S 数据包 `trinket_interact`（`TrinketInteractPayload`）提交，服务端通过 Trinkets API 修改权威数据后由 Trinkets 网络层同步回客户端并持久化
- **四种操作**：`ACTION_PLACE`（光标→槽，0）、`ACTION_TAKE`（槽→光标，1）、`ACTION_SWAP`（互换，2）、`ACTION_QUICK_MOVE`（Shift+左键，槽→主物品栏 0–35，3）
- **光标兜底**：数据包携带客户端当前鼠标物品（`cursor`）。服务端优先使用自身 `containerMenu.getCarried()`，两者不同步时（如点击拿起物品后立刻点击指示器、点击包尚未被服务端处理）以客户端上报值兜底，避免操作被静默丢弃；槽位状态与校验始终以服务端为准
- **本地预览**：客户端在收到服务端确认前先行本地预览，消除交互延迟感
- **原生槽位屏蔽**：`SurvivalTrinketSlotYzuiMixin` 在 YZUI 屏幕下强制 Trinkets 的 `SurvivalTrinketSlot#isActive()` 返回 `false`，避免其注入槽位在装备位两侧渲染出"无用格子"并与 YZUI 指示器坐标重叠误触；YZUI 关闭时不拦截，Trinkets 原生物品栏行为不受影响（该 Mixin 以 `targets = "eu.pb4.trinkets.impl.SurvivalTrinketSlot"` 字符串声明，避免编译期强依赖实现包）

### 35. 配置导入/导出

客户端设置中新增「导出/导入配置」侧栏（YZUI）。基于 26.2 Headless 限制（AWT/文件对话框不可用），采用自动路径 + 备份策略，不依赖外部文件选择器：

- **导出**：将 `config/youzaiworldcore/` 与 `options.txt` 打包为 ZIP 并保存到本地（PC 端手动选择路径；Android 端自动存入 `config_backups`，保留最近 5 份）
- **导入**：从 ZIP 还原配置，要求客户端重启生效（自动备份当前配置至 `config_backups` 以防失败回滚）
- **入口**：`screen.youzaiworldcore.settings.sidebar_config_io`；`ConfigIOManager` 在客户端启动时自愈检测上次导入中断遗留的孤立 `config_bak_*` 备份并恢复

### 36. 更新检查系统（Update Checker）

异步检测模组新版本，提示在线更新或强制更新。

- **入口**：`update/UpdateChecker`（客户端与服务端共用）；运行时从 `https://mcyzw.top/yzwc/version.json` 拉取版本信息，基于 `SemanticVersion` 比较
- **配置**：`config/youzaiworldcore/update_checker.json`（`UpdateCheckerConfig`，可开关检查）
- **命令**（服务端）：`/yzwc update [check]` —— 触发一次即时检查并反馈结果（普通/强制更新提示 + 可点击下载链接），权限 `youzaiworldcore.command.update`（OP 4）
- **客户端**：`client/update/ClientUpdateState` + `client/screen/ForcedUpdateScreen` 提供强制更新界面

### 37. YZUI 界面系统 ★新增

纯客户端的整体界面重绘方案，将原版物品栏、HUD、配方书与上下文栏统一替换为 **YZUI 风格**（半透明白色圆角面板 + 圆角填充条）。由**全局开关**控制：客户端设置「视觉」分栏中的「启用 YZUI」复选框（`screen.youzaiworldcore.settings.toggle_yzui`），持久化于 `client_external_settings.json` 的 `yzuiEnabled` 字段（默认开启）。**关闭后全部回退原版渲染**，便于资源包接管。

#### 33.1 物品栏屏幕

`InventoryScreenSwitchMixin` 拦截 `Gui#setScreen`，在 YZUI 开启时将原版屏幕替换为自定义实现（`InventoryScreen` → 按游戏模式分流；`CreativeModeInventoryScreen` → YZUI 创造屏）：

- **`YzuInventoryScreen`（生存物品栏）**：沿用原版 `InventoryMenu` 固定槽位坐标，仅外观改为 YZUI 圆角风格——半透明白色圆角面板、圆角槽位背景（悬浮高亮）、褐色副手槽底色；配方书打开时于左侧渲染 YZUI 风格配方书面板，切换按钮置于副手槽上方
- **`YzuCreativeInventoryScreen`（创造物品栏）**：完全自绘的创造屏（356×168），内部经 `BuiltInRegistries.ITEM` 自填充物品，含分类标签页（每页配色独立）、搜索框（跨会话记忆上次搜索文本）、9×7 物品网格 + 右侧滚动条，以及右栏的玩家 3D 模型、装备 2×2 与副手槽、3×9 生存物品栏、底部快捷栏
- **拖拽手势**：左键拖拽在有物品时合并同种物品，`Shift + 左键拖拽`批量快速转移；创造屏支持右键取半并实时计算鼠标预期剩余数量

#### 33.2 HUD 组件

`HealthBarMixin` 取消原版 `Hud#extractPlayerHealth` / `extractFood` / `extractArmor` / `extractAirBubbles` 四项渲染，改由 `client/hud/` 下的自定义渲染器绘制长条状进度条：

| 渲染器              | 替换对象   | 特性                                                                                                                                        |
| ------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `HealthBarRenderer` | 爱心血条   | 85×5 长条 + 「当前/最大」文字；手持食物时右侧闪烁预估恢复量叠加层；伤害吸收金色叠加；中毒（紫色垂直条纹）/ 凋零（灰黑水平条纹）状态条纹指示 |
| `FoodBarRenderer`   | 鸡腿饥饿值 | 长条化，配合 `FoodDataExhaustionAccessor` 读取消耗度                                                                                        |
| `ArmorBarRenderer`  | 盔甲图标   | 长条化                                                                                                                                      |
| `OxygenBarRenderer` | 氧气气泡   | 长条化                                                                                                                                      |

#### 33.3 上下文栏与配方书

- **`ContextualBarMixin`**：对经验条（`ExperienceBar`）、定位条（`LocatorBar`）、跳跃条（`JumpableVehicleBar`）取消原版精灵表背景，替换为 YZUI 圆角填充条（宽度与原版 `ContextualBar#WIDTH` = 182 一致），同时保留 `LocatorBar` 的航点指示器渲染；经验数值文字居中显示于血条与饥饿条区域内
- **配方书重绘**：`RecipeBookBackgroundMixin`（以 `@Redirect` 替换背景 blit 而非 `ci.cancel()`，避免连带取消 Tab/搜索框/网格渲染）、`RecipeBookLayoutMixin`（搜索框左移、过滤按钮加宽）、`RecipeBookTabButtonMixin`、`RecipeButtonMixin`
- **按钮样式**：`CycleButtonYzuiMixin`（过滤按钮：选中态绿色勾 `recipe_filter_craftable.png` / 未选中态红色叉 `recipe_filter_all.png`）、`ImageButtonYzuiMixin`（配方书显隐按钮 `recipe_book_show.png` / `recipe_book_hide.png`）；两者均限定在 YZUI 开关开启**且**当前屏幕为 YZUI 自定义屏幕时生效

### 38. 隐形物品展示框 ★新增

两个自定义物品，放置后展示框实体为隐形状态（仅展示其中的物品），适合装饰与展示墙：

| 物品                                                  | 合成配方                            |
| ----------------------------------------------------- | ----------------------------------- |
| **隐形物品展示框**（`invisible_item_frame`）          | 物品展示框 + 幻翼膜（无序合成）     |
| **隐形发光物品展示框**（`invisible_glow_item_frame`） | 发光物品展示框 + 幻翼膜（无序合成） |

由 `InvisibleItemFrameItem` / `InvisibleGlowItemFrameItem` 重写 `useOn`，自行完成放置位置校验、附着面计算与实体生成，并在生成后设置隐形标记；收录于「悠哉实用物品」创造标签页。

### 39. Technoblade 纪念皇冠 ★新增

彩蛋功能：将猪命名为 **`Technoblade`** 后，其头顶会渲染一顶皇冠（成年 / 幼年两套模型与纹理）。

- **实现**：`TechnoCrownFeatureRenderer` 作为 `RenderLayer` 由 `PigRendererMixin` 在 `PigRenderer` 构造末尾挂载；`PigRenderStateMixin` + `RenderCrownDuck` 访问器每帧根据自定义名称计算皇冠可见性
- **纹理**：`assets/minecraft/textures/entity/pig/technocrown_adult.png` / `technocrown_baby.png`
- 改编自 thecolonel63 的 technomodel（MIT 许可）

---

## 📜 指令树

所有指令以 `/yzwc` 为根命令。标注 **（客户端命令）** 的子命令仅做参数解析与转发，权威逻辑由服务端数据包接收器执行。

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
├── event
│   ├── naturally_charged_creepers
│   │   ├── enable [true|false]           → 开启/关闭天然带电苦力怕（缺省查询）
│   │   └── settings chance [double]      → 设置带电概率 0.0~1.0（缺省查询）
│   ├── trial_vault
│   │   └── enable [true|false]           → 开启/关闭试炼宝库无限领奖（缺省查询）
│   ├── laowu
│   │   ├── enable [true|false]           → 开启/关闭老吴贴贴全局开关（缺省查询）
│   │   └── settings cd [seconds]         → 设置/查询老吴贴贴冷却时间
│   └── 权限：.query（所有人）/ .set（OP 4）
│
├── pet <args...>                         ← （客户端命令，整体转发至服务端）
│   ├── list                                          → 列出自己的宠物与受信任宠物
│   ├── set <内部名> rename <新显示名>                 → 重命名（主人）
│   ├── set <内部名> mode <hunting|companionship|attack|guard> → 切换行为模式
│   ├── set <内部名> trust add|remove <玩家> | trust list → 信任管理
│   ├── set <内部名> release_life [force]              → 放生（需二次确认）
│   ├── set <内部名> transfer <新主人>                 → 转让所有权
│   ├── highlight <内部名>                            → 高亮定位（发光 5 秒）
│   └── admin restore | backup_list | backup_interval <秒> → 管理员备份/恢复
│
├── mail <args...>                         ← （客户端命令，整体转发至服务端）
│   ├── send_mail                                    → 打开发布邮件 GUI（需邮件权限）
│   ├── sent                                        → 打开已发送邮件管理列表
│   ├── recall <mailId>                             → 撤回已发送邮件
│   ├── purge [player|all]                          → 清理过期邮件
│   └── list [player]                               → 查看指定玩家信箱
│
├── function invisibility <true/false>    ← （客户端命令）
│   ├── 权限：youzaiworldcore.command.function.invisibility（OP 4）
│   └── 限制：必须在创造模式
│
├── function double_doors <true|false>    ← （客户端命令）
│   ├── 权限：youzaiworldcore.command.function.double_doors（玩家自身，所有人可执行）
│   └── 缺省查询自身状态；新玩家默认启用，状态持久化至 double_doors_players.json
│
├── reload
│   ├── 权限：youzaiworldcore.command.reload（OP 4）
│   └── 运行时重载账户数据和配置
│
├── afk                                     ← （服务端命令）
│   ├── 权限：youzaiworldcore.command.function.afk（自身切换）/ 管理权限（status/list/settings）
│   ├── (无参数)                            → 手动切换 AFK 状态
│   ├── status [player]                    → 查询自身/他人 AFK 状态
│   ├── list                               → 列出所有 AFK 玩家（需管理权限）
│   └── settings <key> <value>             → 运行时修改 AFK 配置（需管理权限）
│
├── account
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
├── status
│   ├── <player> list                          → 查看该玩家统计（权限 .query）
│   ├── <player> delete                        → 删除该玩家统计（权限 .delete）
│   ├── rank_export <day|week|month|year|all> [name] → 导出排行榜（权限 .export）
│   └── 权限：.query / .delete / .export（OP 4）
└── update [check]
    ├── 权限：youzaiworldcore.command.update（OP 4）
    └── 检查模组更新（拉取远程版本信息，反馈普通/强制更新与下载链接）
```

> **客户端命令说明**：`/yzwc pet`、`/yzwc mail`、`/yzwc function invisibility`、`/yzwc function double_doors` 均在客户端注册，仅负责解析参数并通过对应 C→S 数据包（`PetCommandPayload` / `MailComposeOpenPayload` 等邮件包 / `InvisibilityPayload` / `DoubleDoorsTogglePayload`）转发；服务端持有权威状态与权限判定。其余子命令（`teleport_world` / `open_menu` / `world_pool` / `teleport_anchor` / `event` / `reload` / `account` / `status` / `update`）为服务端命令。

### 权限节点一览

| 权限节点                                                    | 说明                                   | 回退等级                |
| ----------------------------------------------------------- | -------------------------------------- | ----------------------- |
| `youzaiworldcore.command.teleport_world`                    | 跨维度传送                             | OP 4                    |
| `youzaiworldcore.command.open_menu`                         | 打开 GUI 菜单                          | OP 4                    |
| `youzaiworldcore.command.reload`                            | 模组重载                               | OP 4                    |
| `youzaiworldcore.command.world_pool`                        | 维度池管理                             | OP 4                    |
| `youzaiworldcore.command.teleport_anchor`                   | 传送锚点管理                           | OP 4                    |
| `youzaiworldcore.command.function.invisibility`             | 隐身功能                               | OP 4                    |
| `youzaiworldcore.command.function.double_doors`             | 双开门功能（自身开关 / 查询）          | 所有人（仅自身）        |
| `youzaiworldcore.command.function.afk`                     | AFK 自身切换                           | 所有人（仅自身）        |
| `youzaiworldcore.command.afk.admin`                        | AFK 管理（status/list/settings）       | OP 4                    |
| `youzaiworldcore.command.event.query`                       | 事件管理查询（省略参数即为查询）       | 所有人                  |
| `youzaiworldcore.command.event.set`                         | 事件管理修改（enable / settings）      | OP 4                    |
| `youzaiworldcore.command.pet.list`                          | 查看宠物列表                           | 所有人                  |
| `youzaiworldcore.command.pet.set`                           | 宠物设置（重命名/模式/信任/放生/转让） | 所有人（仅自身宠物）    |
| `youzaiworldcore.command.pet.highlight`                     | 高亮宠物                               | 所有人（主人/信任玩家） |
| `youzaiworldcore.command.pet.admin`                         | 宠物管理员（备份/恢复/间隔）           | OP 4                    |
| `youzaiworldcore.command.pet`                               | 宠物模块父权限（基础）                 | OP 4                    |
| `youzaiworldcore.mail`                                      | 邮件系统（发布/已发送/撤回/清理/查看） | OP 4                    |
| `youzaiworldcore.command.status.query`                      | 查看统计                               | OP 4                    |
| `youzaiworldcore.command.status.delete`                     | 删除统计                               | OP 4                    |
| `youzaiworldcore.command.status.export`                     | 导出统计排行榜                         | OP 4                    |
| `youzaiworldcore.command.update`                            | 更新检查                               | OP 4                    |
| `youzaiworldcore.command.account.mgr.create`                | 创建账户                               | OP 4                    |
| `youzaiworldcore.command.account.mgr.reset_password`        | 重置密码                               | OP 4                    |
| `youzaiworldcore.command.account.mgr.delete`                | 删除账户                               | OP 4                    |
| `youzaiworldcore.command.account.mgr.session_timeout`       | 会话超时                               | OP 4                    |
| `youzaiworldcore.command.account.mgr.login_cooldown`        | 登录冷却                               | OP 4                    |
| `youzaiworldcore.command.account.mgr.login_cooldown.status` | 锁定状态查询                           | OP 4                    |
| `youzaiworldcore.command.account.mgr.login_cooldown.unlock` | 解锁                                   | OP 4                    |
| `youzaiworldcore.command.account.mgr.*`                     | 账户管理通配符                         | OP 4                    |
| `youzaiworldcore.command.*`                                 | 所有命令通配符                         | —                       |
| `youzaiworldcore.*`                                         | 全模组通配符                           | —                       |

---

## 🖥️ 菜单与网络数据包

### GUI 菜单 ID

| 内部 ID        | 名称     | 层级              |
| -------------- | -------- | ----------------- |
| `main`         | 主菜单   | 根菜单            |
| `switch_world` | 切换世界 | 主菜单 → 切换世界 |
| `settings`     | 设置     | 主菜单 → 设置     |
| `about_me`     | 关于我   | 主菜单 → 关于我   |

### 容器型 MenuType

| ID                    | 对应方块 |
| --------------------- | -------- |
| `decomposition_table` | 分解台   |
| `fly_beacon`          | 飞行信标 |

### 网络数据包（共 38 个）

> 注：`world_pool_teleport` 数据包类位于 `dimensionalinventories` 包，其余位于 `network` 包；邮件相关 18 个数据包亦位于 `network` 包。方向统计：S→C 14 个，C→S 24 个。

| 数据包 ID                   | 方向 | 用途                                                               |
| --------------------------- | ---- | ------------------------------------------------------------------ |
| `open_menu`                 | S→C  | 打开 GUI 菜单                                                      |
| `open_auth_screen`          | S→C  | 打开认证界面                                                       |
| `mana_sync`                 | S→C  | 同步魔力值                                                         |
| `level_exp_sync`            | S→C  | 同步冒险等级经验                                                   |
| `attribute_sync`            | S→C  | 同步玩家属性数据（技能点 / 各项属性 / 等级）                       |
| `teleport_anchor_list`      | S→C  | 发送传送点列表                                                     |
| `teleport_anchor_open_name` | S→C  | 打开传送锚点命名界面                                               |
| `mail_unread_count`         | S→C  | 同步未读数与发布权限（canSend）                                    |
| `mail_player_list`          | S→C  | 返回已注册玩家代号名单（发布页「选取玩家」弹窗）                   |
| `open_mail_compose`         | S→C  | 打开发布邮件 GUI                                                   |
| `mail_list`                 | S→C  | 发送收件箱列表                                                     |
| `mail_sent_list`            | S→C  | 发送已发送邮件列表                                                 |
| `mail_update`               | S→C  | 新增/更新/移除单封邮件                                             |
| `mail_op_result`            | S→C  | 邮件操作结果反馈                                                   |
| `world_pool_teleport`       | C→S  | 请求维度池传送                                                     |
| `teleport_anchor_activate`  | C→S  | 激活传送锚点                                                       |
| `teleport_anchor_teleport`  | C→S  | 请求传送                                                           |
| `teleport_anchor_delete`    | C→S  | 删除传送点                                                         |
| `teleport_anchor_rename`    | C→S  | 重命名传送点                                                       |
| `teleport_anchor_reorder`   | C→S  | 调整排序                                                           |
| `decompose_item`            | C→S  | 分解物品                                                           |
| `fly_beacon_active`         | C→S  | 切换飞行信标                                                       |
| `invisibility_toggle`       | C→S  | 切换 / 关闭自身隐身                                                |
| `attribute_upgrade`         | C→S  | 请求为某项属性加点                                                 |
| `double_doors_toggle`       | C→S  | 切换 / 查询自身双开门开关                                          |
| `pet_command`               | C→S  | 转发 `/yzwc pet` 客户端命令至服务端执行                            |
| `trinket_interact`          | C→S  | YZUI 饰品槽交互（放入/取出/交换/快捷移动，携带客户端光标物品兜底） |
| `mail_compose_open`         | C→S  | 请求打开发布邮件 GUI                                               |
| `mail_open`                 | C→S  | 请求收件箱列表                                                     |
| `mail_sent_list_request`    | C→S  | 请求已发送邮件列表                                                 |
| `mail_recall`               | C→S  | 撤回邮件                                                           |
| `mail_purge`                | C→S  | 清理过期邮件                                                       |
| `mail_list_request`         | C→S  | 查看指定玩家信箱                                                   |
| `mail_fetch`                | C→S  | 编辑前拉取单封完整邮件                                             |
| `mail_action`               | C→S  | 打开/已读/星标/领取/删除                                           |
| `mail_admin_send`           | C→S  | 发布邮件                                                           |
| `mail_admin_edit`           | C→S  | 编辑/取消编辑邮件                                                  |
| `mail_player_list_request`  | C→S  | 请求已注册玩家代号名单                                             |

---

## 🔧 技术栈与依赖

| 依赖                   | 版本                                    | 用途                                   |
| ---------------------- | --------------------------------------- | -------------------------------------- |
| Minecraft              | 26.2                                    | 基础引擎                               |
| Fabric Loader          | 0.19.3                                  | 模组加载器                             |
| Fabric API             | 0.154.0+26.2                            | Fabric 标准 API                        |
| ModMenu                | 20.0.0-beta.4                           | 模组菜单集成                           |
| Placeholder API        | 3.1.0-beta.1+26.2                       | 文本占位符                             |
| Trinkets               | 4.1.0-beta.2+26.2（`trinkets_updated`） | 饰品槽系统（第 34 项功能依赖，硬依赖） |
| GeckoLib               | 5.5.3+                                  | 实体动画与模型渲染（硬依赖）           |
| Moog's Structure Lib   | 3.0.4                                   | 声明依赖（村庄结构注入所引用）         |
| Fabric Permissions API | 0.6.1（内置）                           | 跨模组权限 API                         |
| LuckPerms              | 5.5（建议运行时）                       | 高级权限控制                           |

**构建要求**：JDK 25+ · Gradle（Fabric Loom 1.16-SNAPSHOT）· 模组版本 `1.20.5-indev`

**构建与 CI/CD**：GitHub Actions 工作流（`.github/workflows/build.yml`）在 Ubuntu / Windows / macOS 三平台使用 JDK 25 自动构建，Linux 构建产物自动上传。

---

## 🏗️ 项目结构

```
src/                                       # 383 个 Java 源文件（main 233 / client 150）
├── main/java/top/csituka/youzaiworldcore/
│   ├── YouzaiworldCore.java              # 主入口
│   ├── account/                          # 账户认证（data/command/mixin/util 子包）
│   ├── block/ + entity/                  # 自定义方块与方块实体
│   ├── command/                          # 命令注册（TeleportAnchor / Reload / Event / Mail 客户端转发）
│   ├── component/                        # 数据组件
│   ├── config/                           # 服务端外部设置（带电苦力怕 / 末地传送门 / 双开门 / 宠物 / 邮件 配置）
│   ├── data/                             # 传送锚点 SavedData
│   ├── dimensionalinventories/           # 维度池系统（含 WorldPoolTeleportPayload）
│   ├── enchantment/                      # 自定义附魔 ResourceKey 注册（ModEnchantments）
│   ├── enchlevellangpatch/               # 附魔等级语言补丁（api + impl）
│   ├── event/                            # 事件处理器（飞行信标/双开门/末地门/虚空杖/龙翼/chorus/带电苦力怕/分解/坐姿/监守者/切石机/铁砧修复/阳光修复/乐魂涡轮/骨粉甘蔗/混凝土固化 等）
│   ├── entity/seat/                      # 座椅实体系统
│   ├── invisibility/                     # 隐身系统
│   ├── item/                             # 物品、工具、创造标签页（6 个）、预设（9 个）、隐形展示框
│   ├── luckperms/                        # LuckPerms 集成（LuckPermsHelper 统一鉴权）
│   ├── mail/                             # 邮件系统（Mail / MailManager / SentMailRepository / MailDataStorage / MailSettings / MailPermissionHelper）
│   ├── mana/                             # 魔力系统
│   ├── mixin/                            # Mixin（含子包 chargedcreeper / doubledoors / invisibility / pet / seat / skill / trialvault）
│   ├── network/                          # 网络数据包（36 个数据包类 + ModNetworking）
│   ├── pet/                              # 宠物系统（config/command/event 子包 + PetGlobalState/PetEntry）
│   ├── placeholders/                     # Placeholder API 集成（32 个占位符）
│   ├── screen/                           # 容器菜单
│   ├── skill/                            # 冒险等级 + 属性系统
│   ├── status/                           # 统计系统（StatsManager，21 项指标 + 命令）
│   ├── trialvault/                       # 试炼宝库无限领奖配置（TrialVaultConfig）
│   ├── update/                           # 更新检查（UpdateChecker 等 5 文件）
│   ├── util/                             # DebugLogger、TrinketHelper 等工具
│   └── worldgen/                         # 世界生成（VillageStructureInjector 村庄结构注入）

├── client/java/top/csituka/youzaiworldcore/
│   ├── client/Client.java                # 客户端入口（注册高亮/边框/铁砧/邮件命令等）
│   ├── command/                          # 客户端命令（Invisibility / DoubleDoors / Pet / Mail 转发）
│   ├── config/                           # 客户端外部设置（含 yzuiEnabled）+ ConfigIOManager（配置导入导出）
│   ├── effect/                           # 传送 FOV 效果
│   ├── higherchat/                       # Simple Voice Chat 集成（HUD 图标位置跟踪，优化聊天框位置避免遮挡）
│   ├── highlightitem/                    # 物品高亮（HighlightItemClient / HighLightCommands / Configurator / Colors / ItemComparator）
│   ├── itemborder/                       # 物品边框（ItemBorderClient / ItemBorderConfig / ItemBorderRenderer）
│   ├── anviluses/                        # 铁砧使用次数显示（AnvilUsesClient）
│   ├── client/accessor/                  # 渲染访问器（RenderCrownDuck）
│   ├── hud/                              # 魔力条 / 冒险等级 HUD / YZUI 生命·饥饿·盔甲·氧气条
│   ├── skill/                            # 客户端冒险等级/属性数据（ClientAttributeData）
│   ├── update/                           # 更新检查客户端状态（ClientUpdateState）
│   ├── mixin/client/                     # 客户端 Mixin（标题/选项/按钮/暂停/聊天/加载/座椅/渲染/拾取/附魔补丁/itemborder/YZUI 物品栏·血条·上下文栏·配方书/technocrown 等）
│   ├── network/                          # 客户端网络处理（ClientNetworking）
│   ├── pickup/                           # 拾取显示（item/XP 浮动提示）
│   ├── renderer/                         # 方块/实体渲染器（含传送锚点 BER、feature/TechnoCrownFeatureRenderer）
│   └── screen/                           # GUI 屏幕（MenuScreen、Login/Register、YzuInventoryScreen/YzuCreativeInventoryScreen、MailScreen/MailComposeScreen/MailSentScreen、element/widget/block 子包）

└── main/resources/
    ├── assets/youzaiworldcore/           # 纹理、模型、语言文件（10 种语言）、音效（3 个 .ogg）
    ├── data/                             # 成就（含 deep_dark 分支）、配方、战利品表、维度、结构、结构集、模板池、新手教程函数、trinkets 饰品槽
    └── fabric.mod.json                   # 模组元数据（声明 moogs_structures / trinkets_updated / modmenu / placeholder-api 为硬依赖）

.github/workflows/
└── build.yml                             # CI/CD 构建工作流
```

---

## 📦 配方清单

| 配方                                                          | 类型     | 描述                                         |
| ------------------------------------------------------------- | -------- | -------------------------------------------- |
| `yz_ingot_from_blasting_raw_yz`                               | 熔炼     | 悠哉原矿 → 悠哉锭                            |
| `yz_block_from_blasting_raw_yz_block`                         | 熔炼     | 原矿块 → 悠哉块                              |
| `yz_ingot_from_yz_block`                                      | 合成     | 悠哉块 → 9 悠哉锭                            |
| `yz_ingot_from_nuggets`                                       | 合成     | 9 悠哉粒 → 悠哉锭                            |
| `yz_block`                                                    | 合成     | 9 悠哉锭 → 悠哉块                            |
| `yz_nugget_from_ingot`                                        | 合成     | 悠哉锭 → 9 悠哉粒                            |
| `yz_pickaxe` / `yz_axe` / `yz_shovel` / `yz_hoe` / `yz_sword` | 合成     | 悠哉系列工具                                 |
| `decomposition_table`                                         | 合成     | 分解台                                       |
| `fly_beacon`                                                  | 合成     | 飞行信标                                     |
| `heart_of_guardianship`                                       | 合成     | 守护之心                                     |
| `void_staff`                                                  | 合成     | 凭虚法杖                                     |
| `invisible_item_frame`                                        | 无序合成 | 物品展示框 + 幻翼膜 → 隐形物品展示框         |
| `invisible_glow_item_frame`                                   | 无序合成 | 发光物品展示框 + 幻翼膜 → 隐形发光物品展示框 |
| `raw_yz_block` / `raw_yz_from_raw_yz_block`                   | 合成     | 原矿块转换                                   |
| `craftable_end_portal`                                        | 合成     | 末地传送门框 ×12（末影之眼 + 龙蛋 + 末地石） |

> 共 20 个配方文件（`data/youzaiworldcore/recipe/`）。**烈焰法杖**与**天星法杖**目前无合成配方，仅可通过创造模式标签页或命令获取。

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

> **注意**：测试模组请在服务端进行，客户端单独运行无法正常工作。客户端命令（`/yzwc pet`、`/yzwc mail`、`/yzwc function *`、`/yzwc settings highlight_item`）需连入服务器后方可生效。
>
> **YZUI 说明**：YZUI 界面系统（物品栏 / HUD / 配方书重绘）为纯客户端功能，可在客户端设置的「视觉」分栏中通过「启用 YZUI」复选框关闭，关闭后全部回退原版渲染，便于资源包接管。
