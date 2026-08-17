# AGENTS.md — YouzaiWorldCore AI 开发助手上下文

> 本文件为 AI 开发助手（Claude Code / Copilot / Cursor 等）提供快速理解本项目所需的集中上下文。
> 面向人类的完整功能说明见 [README.md](./README.md)（中文）与 [README.EN.md](./README.EN.md)（英文）；界面草稿见 [DESIGN.md](./DESIGN.md)。

---

## 1. 项目概述

| 项目 | 说明 |
|------|------|
| 名称 | **YouzaiWorldCore（悠哉世界核心模组）** |
| 模组 ID | `youzaiworldcore` |
| 类型 | Minecraft **Fabric** 模组（服务端玩法核心 + 配套客户端 UI） |
| 目标 | 为「悠哉世界」多人服务器提供统一的玩法、账户、UI、管理能力，取代大量零散插件 |
| 仓库 | https://github.com/Youzai-World-Team/YouzaiWorldCore |
| 官网 | https://mcyzw.top |
| 许可证 | Apache-2.0 |

**主要功能域**（40+ 个功能模块，详见 README 的「功能介绍」章节）：

- **账户与安全**：离线服注册/登录/改密/注销、会话超时、登录冷却与锁定
- **UI 系统**：YZUI 界面重绘（物品栏 / HUD / 配方书）、GUI 主菜单、标题界面与窗口定制
- **玩法系统**：魔力、冒险等级与属性加点、传送锚点与传送卷轴、维度池、宠物、邮件、统计、AFK 挂机检测、隐身、坐姿、双开门、试炼宝库无限领奖
- **内容扩展**：悠哉系列矿物/工具/法杖、自定义方块与方块实体（含魔力台）、自定义附魔（12 个）、音乐唱片、Meme 画作、成就、村庄结构注入、云原神遗迹结构、老吴贴贴彩蛋、Technoblade 纪念皇冠
- **运营支撑**：LuckPerms 权限集成、Placeholder API 占位符、更新检查、配置导入导出、调试日志体系

**目标用户**：服务器运维/管理员（配置与指令）、服务器玩家（客户端 UI 与玩法）、参与开发的模组开发者。

---

## 2. 技术栈

| 依赖 | 版本 | 来源 / 用途 |
|------|------|------------|
| Minecraft | **26.2** | `gradle.properties: minecraft_version` |
| Java | **25**（`targetJavaVersion = 25`，Mixin `compatibilityLevel: JAVA_25`） | 编译与运行均需 JDK 25+ |
| Gradle | **9.4.0**（wrapper） | `gradle/wrapper/gradle-wrapper.properties` |
| Fabric Loom | **1.16-SNAPSHOT** | 构建插件，启用 `splitEnvironmentSourceSets()` |
| Fabric Loader | 0.19.3 | — |
| Fabric API | 0.154.0+26.2 | 事件 / 网络 / 命令 / 生物群系修改 |
| Placeholder API (eu.pb4) | 3.1.0-beta.1+26.2 | 文本占位符（硬依赖） |
| Trinkets (eu.pb4) | 4.1.0-beta.2+26.2（模组 ID `trinkets_updated`） | 饰品槽（硬依赖） |
| GeckoLib | 5.5.3+ | 实体动画与模型渲染（硬依赖，老吴贴贴 Geo 模型等） |
| Moog's Structure Lib | 3.0.4（Modrinth Maven） | 结构注入相关（硬依赖） |
| ModMenu | 20.0.0-beta.4 | `compileOnly`，配置入口（硬依赖声明） |
| Fabric Permissions API | 0.6.1 | `include(implementation(...))`，已内置打包 |
| LuckPerms API | 5.5 | `compileOnly`，运行时可选，缺失时回退原版 OP 等级 |
| Gson | 随 MC 提供 | 全部 JSON 配置持久化 |
| SLF4J | 随 MC 提供 | 日志底座（经 `DebugLogger` 包装） |

模组版本：`mod_version = 1.20.5-indev`，`maven_group = top.csituka`，`archives_base_name = YouzaiWorldCore`。

**`fabric.mod.json` 依赖声明**：`depends`（缺失则加载器拒绝启动，共 7 项）— `fabric-api`（>=0.154.0+26.2）、`minecraft`、`placeholder-api`（>=3.0.0）、`modmenu`（>=20.0.0-beta.4）、`moogs_structures`（>=3.0.4）、`trinkets_updated`（>=4.1.0-beta.2+26.2）、`geckolib`（>=5.5.3）；`suggests`（可缺失，4 项）— `luckperms`、`advancementplaques`、`EnchantmentDescriptions`、`sophisticatedbackpacks`。

**Maven 仓库**：mavenCentral、mavenLocal、`maven.nucleoid.xyz`（eu.pb4）、`maven.lucko.me`（LuckPerms）、`maven.terraformersmc.com`（ModMenu）、`api.modrinth.com/maven`（Moog's Structure Lib）；插件仓库额外含 `maven.fabricmc.net` 与 SpongePowered。

---

## 3. 项目结构

本项目使用 **Loom 分环境源集**（`splitEnvironmentSourceSets`），共两个源集，**不存在 `test` 源集（项目无自动化测试）**。

```
YouzaiWorldCore/
├── build.gradle / settings.gradle / gradle.properties   # 构建脚本与版本变量（改版本号只改 gradle.properties）
├── gradlew / gradlew.bat / gradle/wrapper/              # Gradle Wrapper 9.4.0
├── .github/workflows/build.yml                          # CI：JDK 25 + ./gradlew jar，上传 build/libs
├── README.md / README.EN.md                             # 面向用户的完整功能文档（含指令树、权限表、数据包表）
├── DESIGN.md                                            # 菜单草稿设计 + 固定的 AI 提示词
├── 邮件系统规划.md                                       # 邮件系统详细设计文档
├── AGENTS.md                                            # 本文件
├── minecraft_jar/{26.1.1,26.2}/                         # ⚠️ 反编译参考用的原版 jar（已 gitignore）
├── json/                                                # 更新检查器远程 JSON 样例（版本/公告）
├── Resources/                                           # 设计源文件（.psd）与截图
├── run/                                                 # 开发运行目录（已 gitignore），运行期文件落在 run/yzwc/server/ 与 run/<world>/data/yzwc/
├── config/ · bin/ · build/ · .gradle/                    # 生成物 / IDE 输出（勿手工编辑）
│
├── src/main/java/top/csituka/youzaiworldcore/           # 通用 + 服务端（权威逻辑）
│   ├── YouzaiworldCore.java          # ModInitializer 主入口：所有子系统在此按序 initialize()/register()
│   ├── account/                      # 账户认证（command / data / mixin / util 子包）
│   ├── afk/                          # AFK 挂机检测
│   ├── block/ + block/entity/        # 自定义方块与方块实体（分解台 / 飞行信标 / 传送锚点 / 魔力台）
│   ├── command/                      # 服务端命令（Afk / Event / Function / Reload / TeleportAnchor / Update）
│   ├── component/                    # 自定义数据组件（ModDataComponents）
│   ├── config/                       # ⭐ 统一存放层（ModPaths / GlobalSettings / UserSettings / JsonFileStore / ConfigSection / ConfigCrash / TempManager）+ 各模块配置类
│   ├── damagenumber/                 # 伤害跳字服务端广播（DamageNumberHandler）
│   ├── data/                         # SavedData 持久化（传送锚点）
│   ├── dimensionalinventories/       # 维度池（跨维度独立背包）
│   ├── enchantment/ · enchlevellangpatch/  # 自定义附魔 / 附魔等级语言补丁（api + impl）
│   ├── entity/seat/                  # 座椅实体
│   ├── event/                        # 31 个事件处理器（末地门 / 双开门 / 老吴贴贴 / 虚空杖 / 骨粉甘蔗 / 带电苦力怕 / 监守者 / 切石机 / 传送卷轴蓄力 / 生命汲取 / 风弹 / 熔炼 / 阳光修复 / 乐魂涡轮 / 幼年僵尸 / 唱片机循环 / 合成音效 / Meme画作掉落 / 铁锭修铁砧 / 死亡音效 …）
│   ├── invisibility/ · mana/ · respawn/ · skill/ · status/ · trialvault/   # 各玩法子系统
│   ├── item/{,preset,tool}/          # 物品、预设物品、悠哉工具、创造标签页（7 个）
│   ├── luckperms/LuckPermsHelper.java# 统一鉴权入口（权限节点常量 + OP 回退）
│   ├── mail/                         # 邮件系统（Mail / MailManager / MailDataStorage …）
│   ├── mixin/                        # 服务端 Mixin（35 个；子包：afk / babyzombie / chargedcreeper / craftsound / damagenumber / doubledoors / invisibility / jukebox / painting / pet / seat / skill / trialvault）
│   ├── network/                      # 48 个 Payload 记录类 + ModNetworking 统一注册 + MailStreamCodecs
│   ├── pet/{,command,config,event}/  # 宠物系统
│   ├── placeholders/                 # Placeholder API 占位符（32 个）
│   ├── screen/{,slot}/               # 容器菜单（AbstractContainerMenu）
│   ├── sound/                         # 自定义 SoundEvent（cloud_genshin）
│   ├── update/                       # 更新检查器
│   ├── util/BackupArchive.java       # 备份 zip 读写工具
│   ├── util/DebugLogger.java         # ⭐ 调试日志工具（新增功能必须使用）
│   └── worldgen/                     # 村庄传送锚点结构注入
│
├── src/main/resources/
│   ├── fabric.mod.json               # 模组元数据、entrypoints、mixins、depends（版本占位符由 processResources 展开）
│   ├── youzaiworldcore.mixins.json / youzaiworldcore.account.mixins.json
│   ├── assets/youzaiworldcore/       # 纹理、模型、blockstates、sounds（4 个 .ogg）、画作纹理（12 张）
│   ├── assets/youzaiworldcore/lang/  # ⭐ 10 种语言（zh_cn/zh_hk/zh_tw/lzh/en_us/en_gb/de_de/es_es/fr_fr/ru_ru），每个文件 738 行 / 736 键，新增语言键需 10 个文件全部补齐
│   ├── assets/minecraft/ · assets/advancementplaques/  # 原版与第三方资源覆盖（字体、标题、GUI）
│   └── data/                         # 成就（31 个）、配方（20 个）、战利品表（方块 6 + 箱子 4）、维度、结构（9 个：5 村庄变体 + 3 遗迹 + cloud_genshin_ruins）、模板池、tags、附魔（12 个 JSON）、jukebox_song、trinkets 槽位定义
│
├── src/client/java/top/csituka/youzaiworldcore/         # 仅客户端
│   ├── client/Client.java            # ClientModInitializer 入口
│   ├── client/DataGenerator.java     # fabric-datagen 入口
│   ├── client/ModMenuIntegration.java
│   ├── client/{config,hud,screen,renderer,render,pickup,effect,skill,update,video,resource,accessor,laowumeme,particle,higherchat,afk}/
│   ├── command/                      # 客户端命令（仅解析并转发数据包）
│   ├── highlightitem/ · itemborder/ · anviluses/        # 纯客户端增强
│   ├── laowumeme/                     # 老吴贴贴客户端（Geo 模型/渲染/音效池）
│   ├── mixin/client/                 # 60 个客户端 Mixin（已注册于 mixins.json；子包：afk / enchlevellangpatch / food / highlightitem / itemborder / laowumeme / seat / technocrown）
│   └── network/ClientNetworking.java
│
└── src/client/resources/
    ├── youzaiworldcore.client.mixins.json
    └── youzaiworldcore.client.accesswidener   # AccessWidener（仅客户端环境）
```

**入口点（`fabric.mod.json`）**

| 类型 | 类 |
|------|-----|
| `main` | `top.csituka.youzaiworldcore.YouzaiworldCore`、`...placeholders.LuckPermsFabricPlaceholders` |
| `client` | `top.csituka.youzaiworldcore.client.Client` |
| `fabric-datagen` | `top.csituka.youzaiworldcore.client.DataGenerator` |
| `modmenu` | `top.csituka.youzaiworldcore.client.ModMenuIntegration` |

---

## 4. 开发规范

### 4.1 Minecraft 26.2 映射（最重要）

- 26.1 之后 Mojang 采用**新命名规则且官方 jar 已去混淆**，可直接反编译 `minecraft_jar/26.2/26.2.jar` 查证实现。
- **实现新功能前务必先反编译核验签名**，不要凭旧版本（1.20.x / 1.21.x）记忆写代码，API 变动很大。
- 已知重命名示例（本项目全量遵循）：
  - `ResourceLocation` → **`net.minecraft.resources.Identifier`**（构造用 `Identifier.fromNamespaceAndPath(MOD_ID, path)`），项目中 `ResourceLocation` 出现次数为 **0**。
  - `RenderType` 迁至 `net.minecraft.client.renderer.rendertype` 包（见 accesswidener）。
  - 权限检查相关：`net.minecraft.server.permissions.PermissionCheck`。
- 反编译速查命令见 [§5.3](#53-反编译原版-jar)。

### 4.2 日志规范（强制）

所有新增功能**必须**通过 `top.csituka.youzaiworldcore.util.DebugLogger` 打日志，禁止裸用 `System.out`。

```java
DebugLogger.entering("ModuleName", "methodName", "param1=" + a + ", param2=" + b);
DebugLogger.branch("ModuleName", "player has permission", hasPerm);      // 条件分支
DebugLogger.stateChange("ModuleName", playerName, "manaValue", old, now); // 状态变更
DebugLogger.info("ModuleName", "已加载 %d 条记录", count);                // 支持 String.format 占位符
DebugLogger.exception("ModuleName", "loadConfig", e);                     // 异常（含堆栈）
DebugLogger.exiting("ModuleName", "methodName", "result=" + r);
```

日志级别与门槛（`DebugLogger.java` 顶部注释为权威说明）：

| 级别 | 值 | 输出内容 |
|------|----|---------|
| `LEVEL_OFF` | 0 | 不输出 |
| `LEVEL_BASIC` | 1 | `info` / `warn` / `error` / `stateChange` / `exception` |
| `LEVEL_DETAILED` | 2 | 追加 `debug` / `branch` |
| `LEVEL_DEBUG` | 3 | 追加 `trace` / `entering` / `exiting` |

**双开关**：仅当 `devModeEnabled == true` **且** `logLevel > 0` 时才输出。二者由 `yzwc/server/config/global_settings.json` 的 `core_module.dev_mode_enabled` / `core_module.log_to_file` 控制，在 `YouzaiworldCore.onInitialize()` 开头加载并同步。
格式固定为 `[yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] [Module] 描述`。模组启动横幅与关键里程碑另用 `YouzaiworldCore.LOGGER`（SLF4J，无条件输出）。

### 4.3 文件存放规范（强制）

所有配置 / 数据 / 备份 / 缓存文件**只能**通过 `config/ModPaths` 取路径，禁止手写
`getConfigDir().resolve("youzaiworldcore")` 之类的散装路径。目录布局：

```
<gameDir>/yzwc/client/                       # 客户端配置（纯本机，不跨端同步）
├── global_settings.json                     # 客户端全部结构化配置，按功能模块分节
└── config/<模块名>/                         # 玩家直接提供的外部资源例外（如 cosmetic_module PNG）

<gameDir>/yzwc/server/                       # 服务端配置（与世界无关的内容）
├── config/
│   ├── global_settings.json                 # 全局配置，按功能模块分节
│   ├── account_module/registerd_users_data.json   # 玩家代号 / 密码 / UUID
│   └── user_settings/<玩家UUID>.json        # 玩家个人配置，按功能模块分节
├── data/<模块名>/data.json                  # 各模块数据
├── backup/<模块名>/*.zip                    # 各模块备份（必须是 zip）
└── temp/<模块名>/                           # 各模块缓存（每次开服清空）

<world_name>/data/yzwc/                      # 需要随存档走的内容，同样的四层结构
└── config/ · data/<模块名>/ · backup/<模块名>/ · temp/<模块名>/
```

**客户端 vs 服务端**：

- **客户端配置**（`yzwc/client/global_settings.json`）：纯本机生效、不涉及服务端权威逻辑的设置
  （开发者模式、日志级别、YZUI 开关、物品高亮颜色、音频启用状态等）。
  客户端没有 data / backup / temp 子目录；需要玩家直接放置资源文件的模块可使用
  `yzwc/client/config/<模块名>/`，但结构化设置仍必须写入唯一的 `global_settings.json`。
  由 `ClientGlobalSettings.load()` 在 `Client.onInitializeClient()` 最开头加载。
- **服务端配置**（`yzwc/server/` 与 `<world_name>/data/yzwc/`）：涉及游戏逻辑、需要服务端权威保存的设置
  （账号系统、维度池、宠物数据、传送锚点坐标等）。由 `GlobalSettings.load()` 在服务端启动时加载。

**五条硬规则：**

1. **先分类再写配置**：所有 `.json` 配置文件的根节点是「模块名 → 该模块配置对象」，禁止在根节点直接写配置项。模块名统一登记在 `GlobalSettings` / `ClientGlobalSettings` 的常量里（`AFK_MODULE`、`PET_MODULE`、`CORE_MODULE` …），并与服务端的 `data/` `backup/` `temp/` 下的文件夹名保持一致。
2. **全局 vs 个人 vs 客户端**：
   - 与世界无关、对所有人生效的服务端配置写 `GlobalSettings.section(模块名)`；
   - 每位玩家各自一份的**且必须由服务端保存**的设置写 `UserSettings.section(uuid, 模块名)`；
   - 纯本机生效的客户端配置写 `ClientGlobalSettings.section(模块名)`。
   个人配置文件在账户注册时由 `AccountDataStorage.register` 创建、注销/删号时删除。
3. **读配置用强类型 getter**：`ConfigSection.getBoolean/getInt/getDouble/getString/getEnum/getStringList` 等。键缺失回落默认值；类型不符或越界一律走下面第 5 条的失败流程。本项目不做配置迁移，也不做静默降级。
4. **每个模块都要能生成自己的默认值**：模块配置类必须提供 `public static void writeDefaults()`（把字段**重置为 `DEFAULT_*` 常量**再 `save()`），并在 `DefaultSettingsWriter.writeAllDefaults()` / `ClientDefaultSettingsWriter.writeAllDefaults()` 里登记一行。新开服（文件不存在）时 `GlobalSettings.load()` / `ClientGlobalSettings.load()` 会直接写出一份含全部模块默认值的完整配置，而不是等各模块 `load()` 零散补齐。非全局的文件（个人配置 / 账户凭据 / 各模块 `data.json`）通过 `JsonFileStore.setDefaultsWriter(...)` 注册自己的默认内容，再用 `loadOrCreateDefaults()` 读取。
5. **备份必须是 zip**：用 `util/BackupArchive` 写 `backup/<模块名>/xxx.zip`（压缩包内放同名 `.json`）。临时文件用 `TempManager.serverTempDir(模块名)` / `worldTempDir(server, 模块名)`，并且不得假设能跨重启存活。

**读到坏配置时的固定流程**（`JsonFileStore.fail` → `ConfigCrash`）：

1. 把出错文件改名隔离为 `<原文件名>.error`（已存在则追加 `-<时间戳>`，绝不覆盖已有隔离文件）；
2. 在原路径用该文件注册的默认内容**重新生成一份默认配置**；
3. 控制台打印「文件 / 位置 / 原因 / 隔离到哪 / 已重建哪个」；
4. 抛 `ConfigCrash.ConfigFormatException` 崩溃退出。

若第 1 步改名失败（文件被占用等），则**跳过第 2 步**，绝不覆盖管理员的现场。

新增模块时：
- **客户端模块**：在 `ClientGlobalSettings` 加一个模块名常量 → 写 `writeDefaults()` 并登记进 `ClientDefaultSettingsWriter` → 配置走 `ClientGlobalSettings.section(...)`。
- **服务端模块**：在 `GlobalSettings` 加一个模块名常量 → 写 `writeDefaults()` 并登记进 `DefaultSettingsWriter` → 配置走 `GlobalSettings.section(...)` / `UserSettings.section(...)` → 数据走 `ModPaths.serverData(模块名)` 或 `ModPaths.worldData(server, 模块名)`。

### 4.4 代码风格

- **缩进 4 空格**，UTF-8 编码（`options.encoding = "UTF-8"`），Java 25 语法（record / pattern matching / sealed 均可用）。
- **注释与用户可见文案一律中文**；类/公开方法写 Javadoc，说明用途、配置文件路径、权限节点。
- 大段逻辑用 `// ===== 分节标题 =====` 分隔（全项目统一风格）。
- 因 MC 源码缺少 `@Nullable` 标注，普遍在类上加 `@SuppressWarnings("null")`（必要时加 `"unused"`）以消除 IDE 噪音警告。
- 工具类写成 `public final class` + 私有构造 + 全静态方法（如 `DebugLogger`、`LuckPermsHelper`、`ServerExternalSettings`）。

### 4.5 命名约定

| 对象 | 约定 | 示例 |
|------|------|------|
| 包 | `top.csituka.youzaiworldcore.<功能域>` | `...youzaiworldcore.pet.command` |
| 注册聚合类 | `Mod<复数名>` + 静态 `initialize()` | `ModItems` / `ModBlocks` / `ModNetworking` / `ModMenuTypes` |
| 事件处理器 | `<功能>Handler` + 静态 `register()` | `DoubleDoorsHandler` |
| 配置类 | `<功能>Config` / `<功能>State` + `load()` / `save()` | `LaowuMemeConfig` |
| 网络包 | `<用途>Payload`（record，含 `ID`/`TYPE` 与 `STREAM_CODEC`） | `MailFetchPayload` |
| Mixin | `<目标类>Mixin` / `<目标类>Accessor` | `DoorBlockMixin`、`TargetGoalAccessor` |
| 客户端屏幕 | `<功能>Screen` | `MailComposeScreen` |
| 资源 ID | `snake_case` | `youzaiworldcore:teleport_anchor` |
| 语言键 | `<类别>.youzaiworldcore.<id>[.<后缀>]` | `item.youzaiworldcore.flame_staff.tooltip` |
| 权限节点 | `youzaiworldcore.command.<命令>[.<动作>]` | `youzaiworldcore.command.teleport_world` |
| Mixin 私有成员 | `youzaiworldcore$` 前缀 | `youzaiworldcore$logUpdate` |

### 4.6 提交与分支

- **主分支 `main`**，直接在 `main` 上开发并推送；PR 亦合入 `main`（CI 对 push 与 pull_request 均触发）。
- **提交信息为中文**，惯用形式（无强制 Conventional Commits）：
  - `功能：优化 YzuCreativeInventoryScreen 的拖拽逻辑，添加物品预览`
  - `为老吴贴贴功能添加愤怒粒子效果，优化猫模型动画参数`
  - `修复命令树` / `fix`（小修）
  - 极不稳定的中间态会显式标注，如 `(极度不稳定)fix`
- 一次提交聚焦一个功能点，正文说明"改了什么 + 为什么"。

---

## 5. 常用命令

> 仓库自带 Gradle Wrapper；Windows 下用 Git Bash 执行 `./gradlew`，PowerShell/CMD 用 `gradlew.bat`。
> **注意：项目约定由开发者本人执行构建，AI 助手默认不代为编译/构建。**

### 5.1 构建与运行

| 命令 | 说明 |
|------|------|
| `./gradlew build` | 全量构建（编译 + 打包 + sourcesJar） |
| `./gradlew jar` | 仅打包（CI 使用），产物在 `build/libs/` |
| `./gradlew runServer` | 启动开发服务端（**功能验证请用这个**） |
| `./gradlew runClient` | 启动开发客户端 |
| `./gradlew runDatagen` | 运行数据生成（`fabricApi.configureDataGeneration`） |
| `./gradlew genSources` | 生成带映射的 Minecraft 源码，供 IDE 跳转 |
| `./gradlew clean` | 清理 `build/` |
| `./gradlew --refresh-dependencies` | 依赖变更后刷新 |
| `./gradlew publishToMavenLocal` | 发布到本地 Maven（`maven-publish`） |

- 无格式化 / lint / 测试任务：**项目未配置 checkstyle、spotless，也没有测试源集**，`./gradlew test` 无意义。
- 依赖"安装"即 Gradle 自动解析，无需额外命令。

### 5.2 Git

```bash
git status
git add -A && git commit -m "功能：<中文描述>"
git pull --rebase origin main
git push origin main
```

### 5.3 反编译原版 jar

原版 jar 位于 `minecraft_jar/26.2/26.2.jar`（另有 `26.1.1/` 可对比差异）。该目录已 gitignore；**若不存在请直接询问开发者路径**。

```bash
# 列出 jar 内类
unzip -l minecraft_jar/26.2/26.2.jar | grep -i "ClassName"

# 解出目标类到临时目录后用 javap 查看签名 / 字节码
unzip -o -q minecraft_jar/26.2/26.2.jar "net/minecraft/<pkg>/<Class>.class" -d .tmp_decompile
"/c/Program Files/Java/25/bin/javap" -p .tmp_decompile/net/minecraft/<pkg>/<Class>.class      # 成员签名
"/c/Program Files/Java/25/bin/javap" -p -c .tmp_decompile/net/minecraft/<pkg>/<Class>.class   # 含字节码，可推断实现
```

同样的手法可用于 Fabric API / 第三方模组 jar（位于 `~/.gradle/caches/modules-2/files-2.1/`）。
`.tmp_decompile/` 是约定的临时解包目录，用完可清空。

---

## 6. 架构说明

### 6.1 环境划分（最关键的设计决策）

```
src/main  →  通用 + 服务端：权威状态、持久化、权限判定、命令逻辑
src/client →  仅客户端：渲染、GUI、HUD、输入、客户端命令（仅解析并转发）
```

- Loom 的 `splitEnvironmentSourceSets()` 保证客户端类不会被打进服务端 classpath；在 `src/main` 中**禁止**引用 `net.minecraft.client.*`。
- 客户端命令（`/yzwc pet`、`/yzwc mail`、`/yzwc function *`、`/yzwc settings highlight_item`）**只做参数解析并发包**，权威校验一律在服务端接收器中完成。

### 6.2 初始化时序

```
YouzaiworldCore.onInitialize()
 ├─ GlobalSettings.load()                  # 最先：读 yzwc/server/config/global_settings.json
 ├─ ServerExternalSettings.load()          # 取 core_module 分节 → 同步 DebugLogger 开关
 ├─ SERVER_STARTING: TempManager.clear...   # 每次开服清空 temp/
 ├─ LOGGER.info(LOGO)                      # ASCII 启动横幅
 ├─ ModDataComponents / ModBlocks / ModBlockEntities / ModItems
 ├─ ModSoundEvents / ModCreativeModeTabs / ModMenuTypes / ModNetworking / ModSeatEntities
 ├─ DamageNumberHandler.initialize()       # 伤害跳字
 ├─ 各 XxxHandler.register()               # 事件挂载（连锁采集/铁砧修复/坐姿/传送锚点/法杖/信标/切石机/龙翼/监守者/死亡音效/梯子/作物/头颅/三叉戟/紫颂/带电苦力怕/双开门/末地门/骨粉甘蔗/混凝土固化/附魔处理器/老吴贴贴/隐身…）
 ├─ 各 XxxConfig.load()                    # 子系统配置（事件开关/带电苦力怕/末地门/试炼宝库/老吴贴贴/更新检查/AFK/维度池/原地重生）
 ├─ 各子系统 Manager/Storage.initialize()   # 账户 / 冒险等级 / 属性 / 原地重生 / 邮件 / 宠物 / 统计 …
 ├─ BiomeModifications.addFeature(...)     # 矿物生成
 ├─ ServerLifecycleEvents.SERVER_STARTING  # 村庄结构注入 + 宠物备份
 ├─ ServerLifecycleEvents.SERVER_STARTED   # 异步更新检查 + 邮件过期清理
 ├─ ServerPlayConnectionEvents.JOIN        # AFK 登记 / 功能开关同步 / 邮件未读数推送
 ├─ ServerPlayConnectionEvents.DISCONNECT  # 隐身 / 维度池 / AFK / 邮件收尾
 └─ CommandRegistrationCallback            # /yzwc 命令树注册
```

新增子系统时，按同样的模式在此处插入一行注册，并配对 `DebugLogger.info/entering/exiting`。

### 6.3 数据流

```
玩家操作
  ├─(A) 命令 /yzwc ...
  │      → Brigadier 解析
  │      → LuckPermsHelper.checkPermission(source, NODE, Commands.LEVEL_ADMINS)   # LP 优先，缺失回退 OP 等级
  │      → Manager 修改权威状态 → 持久化 → S2C 同步包
  │
  ├─(B) GUI / 按键
  │      → 客户端 Screen/命令构造 Payload → ServerPlayNetworking.send()
  │      → ModNetworking 中的 registerGlobalReceiver 校验并执行
  │      → 回发 S2C Payload → ClientNetworking 更新客户端缓存 → 界面重绘
  │
  └─(C) 世界交互（放置/破坏/攻击/右键）
         → Fabric 事件回调 或 Mixin 注入
         → Handler 处理 → 必要时同步客户端
```

### 6.4 关键设计决策

1. **配置全部为 JSON + Gson，且集中收拢**（详见 [§4.3](#43-文件存放规范强制)）。服务端只有三类配置文件：全局 `yzwc/server/config/global_settings.json`、账户凭据 `yzwc/server/config/account_module/registerd_users_data.json`、玩家个人 `yzwc/server/config/user_settings/<UUID>.json`；全部按功能模块分节。数据 / 备份 / 缓存分别在 `yzwc/server/` 下的 `data/` `backup/` `temp/`，随存档走的同结构放在 `<world_name>/data/yzwc/`。各配置类仍是静态单例 `load()` / `save()`，但读写都走 `GlobalSettings` / `UserSettings`，分节缺失时自动写出默认值，格式非法则崩溃退出。**客户端配置尚未迁移**，仍在 `config/youzaiworldcore/`（`client_external_settings.json`、`item_borders.json`、`fancy_tooltips.json`、`highlight_item/` 等）。
2. **网络层集中注册**：所有 Payload 的 `PayloadTypeRegistry` 注册与服务端接收器都写在 `ModNetworking.initialize()`，客户端接收器写在 `ClientNetworking`。Payload 一律用 `record` + `CustomPacketPayload.Type ID` + `StreamCodec STREAM_CODEC`。
3. **权限双轨制**：`LuckPermsHelper` 暴露权限节点常量与 `checkPermission(...)`；LuckPerms 未安装时**不抛异常**，自动回退到原版 OP 等级（`Commands.LEVEL_ADMINS` = 4）。新增命令请在此类中补充节点常量并同步更新 README 权限表。
4. **Mixin 三配置分离**：`youzaiworldcore.mixins.json`（服务端/通用）、`youzaiworldcore.account.mixins.json`（账户系统独立）、`youzaiworldcore.client.mixins.json`（客户端，`environment: client`）。新增 Mixin 必须登记进对应 JSON，否则不生效；`compatibilityLevel` 均为 `JAVA_25`，`injectors.defaultRequire = 1`，通用与客户端配置还开启了 `overwrites.requireAnnotations`。
5. **AccessWidener 仅客户端**：`src/client/resources/youzaiworldcore.client.accesswidener`，能用 Mixin `Accessor` 解决的优先用 Accessor，避免扩大可见性。
6. **单一命令根 `/yzwc`**：服务端子命令在 `YouzaiworldCore` 的 `CommandRegistrationCallback` 与 `command/` 包内注册；客户端子命令由 `src/client/.../command/` 下各类通过 `ClientCommandRegistrationCallback` 自行注册，并在 `Client.onInitializeClient()` 中调用其 `register()`。
7. **移植功能原生重写**：参考其他模组（Double Doors、trial-chamber-time-removal、老吴贴贴等）的功能均为原生实现，不引入其依赖，注释中标注参考来源。

---

## 7. 常见问题

### 环境要求
- **必须 JDK 25+**（`java -version` 确认）。低版本会在 `options.release = 25` 处直接失败。
- Windows 下 Gradle 需要足够内存：`gradle.properties` 中 `org.gradle.jvmargs=-Xmx1G`；大改动构建慢时可临时调高。
- 首次构建需联网下载 MC、映射与依赖，耗时较长；`genSources` 额外耗时数分钟。

### 运行与调试
- ⚠️ **测试必须在服务端进行**（`./gradlew runServer`）。单开客户端时账户、邮件、宠物、传送锚点等依赖服务端权威状态的功能都不会工作 —— 这是 `DESIGN.md` 首要提示。
- **日志默认全部静默**。调不出日志时先检查 `yzwc/server/config/global_settings.json` 的 `core_module` 分节是否 `dev_mode_enabled: true` 且 `log_to_file: true`；两者缺一，`DebugLogger` 全部方法直接 return。
- `YouzaiworldCore.logToFile` / `devModeEnabled` 是配置的镜像字段，只读不要手工赋值，改动请走 `ServerExternalSettings`。
- **配置写错会直接崩服**：这是刻意行为（见 [§4.3](#43-文件存放规范强制)）。模组会先把坏文件改名成 `<原名>.error` 保留现场，再在原路径生成一份默认配置，然后打印「文件 / 位置 / 原因 / 隔离到哪 / 已重建哪个」并退出。照着新生成的默认文件把 `.error` 里的改动搬回去即可。
- **新开服会自动生成完整默认配置**：`global_settings.json` 不存在时由 `DefaultSettingsWriter` 一次性写出全部模块分节，不需要先跑一遍再看有哪些键。

### 编码坑点
- **配置 / 数据文件路径必须走 `ModPaths`**：不要再写 `FabricLoader.getInstance().getConfigDir().resolve("youzaiworldcore")`。新模块的配置分节名同时也是它在 `data/` `backup/` `temp/` 下的文件夹名，先在 `GlobalSettings` 里登记常量。
- **不要凭旧版本 API 记忆写代码**：26.2 与 1.21.x 差异极大。用 `Identifier` 而非 `ResourceLocation`；不确定的签名一律先 `javap` 核对（[§5.3](#53-反编译原版-jar)）。
- **新增 Mixin 忘记登记 JSON** 是最常见的"改了没反应"原因。
- **多语言必须同步**：`assets/youzaiworldcore/lang/` 下 10 个文件当前每个均为 738 行 / 736 个键，新增语言键需要 10 个文件全部补齐，否则部分语言下显示原始键名。
- **硬依赖不可缺**：`fabric-api`、`minecraft`、`placeholder-api`、`modmenu`、`moogs_structures`、`trinkets_updated`、`geckolib` 共 7 项在 `fabric.mod.json` 中声明为 `depends`，缺失会导致加载器直接拒绝启动；LuckPerms、AdvancementPlaques、EnchantmentDescriptions、Sophisticated Backpacks 是 `suggests`，可缺失。
- **物品模型自检**：客户端启动时 `ItemModelDefinitionValidator` 会校验物品模型定义文件是否存在，新增物品若日志报缺失，需补 `assets/youzaiworldcore/items/<id>.json`。
- **`src/main` 中禁止 client 类**：误引用会在服务端运行时抛 `NoClassDefFoundError`，且开发期客户端运行不报错，很难发现。
- **`@SuppressWarnings("null")`** 是既有约定而非疏漏，新类可沿用；但请勿用它掩盖真实的空指针风险。

### 目录与版本控制
- `run/`、`bin/`、`build/`、`.gradle/`、`minecraft_jar/`、`.idea/`、`.vscode/`、`.claude/`、`.workbuddy/`、`logo.txt` 均已 gitignore，勿提交。
- 版本号只改 `gradle.properties`；`fabric.mod.json` 中的 `${version}` / `${minecraft_version}` / `${loader_version}` 由 `processResources` 自动展开。
- 根目录 `config/` 为空占位目录；服务端运行期文件实际生成在 `run/yzwc/server/`（开发期）与 `run/<world>/data/yzwc/`，客户端配置仍在 `run/config/youzaiworldcore/`。

---

## 8. 参考资料

### 项目内文档
| 文件 | 内容 |
|------|------|
| [README.md](./README.md) | 完整功能清单（40+ 项）、指令树、权限节点表、网络数据包表、配方清单、项目结构 |
| [README.EN.md](./README.EN.md) | 上述内容的英文版 |
| [DESIGN.md](./DESIGN.md) | 菜单 UI 草稿设计、固定 AI 提示词、"必须在服务端测试"提示 |
| [邮件系统规划.md](./邮件系统规划.md) | 邮件系统完整设计文档 |
| `src/main/java/.../util/DebugLogger.java` | 日志体系权威说明（类顶部 Javadoc） |
| `src/main/java/.../luckperms/LuckPermsHelper.java` | 权限节点常量与鉴权用法示例 |
| `src/main/java/.../YouzaiworldCore.java` | 子系统注册时序总览 |
| `src/main/java/.../enchantment/ModEnchantments.java` | 自定义附魔 12 个 ResourceKey 定义 |
| `src/main/java/.../sound/ModSoundEvents.java` | 自定义 SoundEvent（音乐唱片） |
| `src/main/java/.../network/ModNetworking.java` | 48 个网络数据包的统一注册与服务端接收器 |
| `NOTICE.txt` / `LICENSE*.txt` | 第三方代码与字体（Noto Sans、FluentEmoji）授权说明 |

### 外部文档
- Fabric 开发文档：https://docs.fabricmc.net/
- Fabric API 源码：https://github.com/FabricMC/fabric
- Fabric Loom：https://github.com/FabricMC/fabric-loom
- Mixin 使用手册：https://github.com/SpongePowered/Mixin/wiki
- Minecraft Wiki（数据包/资源包格式）：https://zh.minecraft.wiki/
- LuckPerms API：https://luckperms.net/wiki/Developer-API
- Placeholder API (eu.pb4)：https://placeholders.pb4.eu/
- Trinkets：https://github.com/emilyalexandra/trinkets
- Fabric 版本查询：https://modmuss50.me/fabric.html
- 项目 Issues：https://github.com/Youzai-World-Team/YouzaiWorldCore/issues
