# YouzaiWorldCore — 悠哉世界核心模组

> **版本**: 1.10.0+indev · **Minecraft**: 26.2 · **加载器**: Fabric Loader 0.19.3  
> **许可协议**: [Apache-2.0](LICENSE.txt)

<div align="center">

#### **简体中文** | [**English**](README.EN.md)

</div>

---

## 📖 项目概述

**YouzaiWorldCore** 是悠哉世界（Youzai World）Minecraft 多人服务器的核心玩法模组，基于 **Fabric** 框架开发。该模组为服务器提供了一套完整的基础设施，涵盖账户认证、GUI菜单系统、自定义物品与方块、技能系统、跨维度传送、实验性功能切换等核心能力。

### 应用场景

- **离线模式服务器**：内置账户认证系统，支持注册/登录/登出/密码修改与会话恢复
- **生存玩法增强**：提供悠哉系列工具（具有连锁挖掘、范围伤害等特殊效果）、自定义方块与合成配方
- **服务器运营管理**：GUI 菜单导航、世界切换、管理员运营工具
- **玩家体验优化**：技能属性系统（Puffish Skills）、成就系统、占位符集成

### 目标用户群体

| 用户类型 | 说明 |
|---------|------|
| **服务器管理员** | 运营悠哉世界服务器的 OP/管理员，通过命令与菜单管理系统 |
| **生存玩家** | 在服务器游玩的普通玩家，使用悠哉工具、技能系统、世界传送等 |
| **模组开发者** | 希望了解模组架构、扩展功能或贡献代码的开发者 |

---

## ✨ 功能介绍

### 1. 账户认证系统

完整的离线模式账户系统，支持密码注册、登录、登出与管理员管理。

- **密码安全**：SHA-256 加盐哈希存储，支持登录次数限制（最多 5 次）
- **会话管理**：支持可配置的会话超时时间，断线重连自动恢复
- **位置保存**：登出时自动保存玩家位置并传送至虚空，登录后精确恢复
- **登录大厅**：未认证玩家被限制在自定义的 `login_hall` 维度，无法移动或交互
- **管理员工具**：创建离线账户、重置密码、删除账户、设置会话超时

### 2. GUI 菜单系统

基于 Fabric Screen 构建的全方位图形界面菜单系统，采用 Windows 10 开始菜单风格的磁贴布局，支持动画过渡效果。

| 菜单 | 说明 |
|------|------|
| **主菜单** | 功能总入口，包含切换世界、活动、签到、教程、设置等磁贴按钮 |
| **切换世界** | 展示所有可传送的世界（生存、王城、玩法、创造、建筑等） |
| **设置** | 提供音乐/音效开关、PVP/友军伤害开关、难度选择等配置 |
| **关于我** | 显示玩家 3D 模型渲染、玩家ID、首次/最后加入时间、游玩时长 |

**快捷键**：`Shift + F` 快捷打开主菜单。

### 3.悠哉系列工具与物品

一套全新的矿物与工具系列，等级对标钻石工具。

| 物品 | 特殊效果 |
|------|---------|
| **悠哉铲** | 蹲下挖掘可向前连锁 6 格 |
| **悠哉镐** | 蹲下挖掘可向前连锁 6 格 |
| **悠哉锄** | 蹲下使用可耕 3×3 的地 |
| **悠哉剑** | 攻击时有 4% 概率触发暴击，伤害翻倍 |
| **悠哉斧** | 跳劈时对周围敌人造成范围伤害 |
| **守护之心** | 携带时死亡不掉落物品，每次死亡消耗一个 |
| **凭虚法杖** | 右键切换飞行模式，饥饿值为零时自动关闭 |
| **悠哉世界（Logo）** | 服务器标识物品 |

### 4. 自定义方块

| 方块 | 特性 |
|------|------|
| **悠哉矿 / 深层悠哉矿** | 主世界生成，掉落经验（2-5），需钻石镐采集 |
| **悠哉原矿块 / 悠哉块** | 矿物存储方块 |
| **分解台** | GUI 界面，用于分解物品为原材料 |
| **飞行信标** | 提供区域飞行能力，可切换激活状态 |

### 5. 技能系统（Puffish Skills）

集成 Puffish Skills 技能模组，提供 11 种属性提升：

| 属性 | 效果 |
|------|------|
| 生命值 +1 | 每级增加 1 点最大生命值 |
| 抵抗 +1% | 每级增加 1% 伤害减免 |
| 近战伤害 +1% | 近战攻击伤害百分比提升 |
| 远程伤害 +1% | 远程攻击伤害百分比提升 |
| 攻击速度 +1% | 攻击速度百分比提升 |
| 移动速度 +1% | 移动速度百分比提升 |
| 幸运 +0.1 | 每级增加 0.1 幸运值 |
| 耐力 +1% | 每级增加 1% 耐力 |
| 治疗 +1% | 治疗量百分比提升 |
| 跳跃 +1% | 跳跃高度百分比提升 |
| 挖掘速度 +1% | 挖掘速度百分比提升 |

### 6. 成就系统（进度）

包含两大进度分支：

- **悠哉世界**（主要进度）：涵盖获取悠哉矿石/锭/块/工具、使用分解台/飞行信标/守护之心/凭虚法杖等
- **趣味小挑战**：蛋糕是谎言、美食家、绿宝石块、像牛和猪一样、最大幸运、困在蜘蛛网、回家之路、铜盔甲

### 7. 实验性功能系统

支持服务端全局开关 + 玩家级覆写的实验性功能管理系统，状态配置持久化到 JSON 文件。

### 8. 占位符系统（Placeholder API）

集成 Placeholder API 和 LuckPerms 占位符，支持动态/静态占位符解析。

### 9. 权限系统

提供基于 **LuckPerms** 的细粒度权限控制，自动回退至原版 OP 等级检查。

### 10. 预设物品系统

创造模式标签页中的四大预设潜影盒，一键生成：

| 预设 | 内容 |
|------|------|
| **毕业套装**（红色） | 满配下界合金装备、全附魔工具/武器、消耗品 |
| **毕业套补充**（橙色） | 实用工具、建筑材料、额外防具 |
| **不死图腾**（黄色） | 27 个不死图腾 |
| **炸药包**（灰色） | 27 组 × 64 TNT |

---

## 📜 指令树

所有指令以 `/yzwc` 为根命令，基于 Brigadier 命令框架构建。

```
/yzwc
├── (无参数) — 显示 "Hello World" 提示信息
│
├── teleport_world <targets> <dimension> [x] [y] [z] [yRot] [xRot]
│   ├── 描述：将目标玩家传送到指定维度的坐标
│   ├── 权限：youzaiworldcore.command.teleport_world（或 OP 4 级）
│   ├── 参数：
│   │   • targets — 目标玩家（支持多选）
│   │   • dimension — 目标维度（如 minecraft:overworld）
│   │   • x, y, z — 目标坐标（可选，默认为 0, 100, 0）
│   │   • yRot — 水平旋转角度（可选，-180 ~ 180）
│   │   • xRot — 垂直旋转角度（可选，-90 ~ 90）
│   └── 示例：
│       /yzwc teleport_world @p minecraft:the_nether
│       /yzwc teleport_world @a minecraft:overworld 0 64 0
│
├── open_menu <menu_name> [target]
│   ├── 描述：为玩家打开指定 GUI 菜单
│   ├── 权限：youzaiworldcore.command.open_menu（或 OP 4 级）
│   ├── 参数：
│   │   • menu_name — 菜单名称（main / switch_world / settings / about_me）
│   │   • target — 目标玩家（可选，默认为命令执行者）
│   └── 示例：
│       /yzwc open_menu main
│       /yzwc open_menu settings @p
│
├── experimental_feature <id> [true/false [all|only <player>]]
│   ├── 描述：查询或切换实验性功能
│   ├── 权限：
│   │   • 查询：youzaiworldcore.command.experimental_feature.query
│   │   • 自切换：youzaiworldcore.command.experimental_feature.self
│   │   • 管理（all/only）：youzaiworldcore.command.experimental_feature.admin（或 OP 4 级）
│   ├── 参数：
│   │   • id — 实验性功能内部 ID（如 chicken_warden_model）
│   │   • true/false — 启用/禁用（可选，省略则为查询模式）
│   │   • all — 全服切换（需要管理员权限）
│   │   • only <player> — 为指定玩家切换（需要管理员权限）
│   └── 示例：
│       /yzwc experimental_feature chicken_warden_model          ← 查询状态
│       /yzwc experimental_feature chicken_warden_model true     ← 为自己启用
│       /yzwc experimental_feature chicken_warden_model false all ← 全服禁用
│       /yzwc experimental_feature chicken_warden_model true only Steve ← 为 Steve 启用
│
├── reload
│   ├── 描述：运行时重新加载模组数据（账户数据、配置等），无需重启服务器
│   ├── 权限：youzaiworldcore.command.reload（或 OP 4 级）
│   └── 示例：
│       /yzwc reload
│
└── account <子命令>
    ├── 描述：账户认证管理
    │
    ├── 📋 玩家命令：
    │   ├── register <password> <confirm>
    │   │   ├── 描述：注册新账户
    │   │   ├── 权限：无（所有人）
    │   │   ├── 限制：密码长度 4-128 字符
    │   │   └── 示例：/yzwc account register MyPass123 MyPass123
    │   │
    │   ├── login <password>
    │   │   ├── 描述：登录账户
    │   │   ├── 权限：无（所有人）
    │   │   ├── 限制：最多 5 次尝试
    │   │   └── 示例：/yzwc account login MyPass123
    │   │
    │   ├── logout
    │   │   ├── 描述：登出账户，传送至末地虚空
    │   │   ├── 权限：无（所有人）
    │   │   └── 示例：/yzwc account logout
    │   │
    │   ├── deactivate <password>
    │   │   ├── 描述：注销（删除）账户
    │   │   ├── 权限：无（所有人）
    │   │   └── 示例：/yzwc account deactivate MyPass123
    │   │
    │   └── change_password <oldPassword> <newPassword> <confirmPassword>
    │       ├── 描述：修改密码
    │       ├── 权限：无（所有人）
    │       └── 示例：/yzwc account change_password Old123 New456 New456
    │
    └── 🔧 管理员命令（需 OP 4 级）：
        ├── mgr create <player> <newPassword> <confirmPassword>
        │   ├── 描述：为离线玩家创建账户并设置密码
        │   └── 示例：/yzwc account mgr create Steve Pass123 Pass123
        │
        ├── mgr reset_password <player> <newPassword> <confirmPassword>
        │   ├── 描述：重置指定玩家的密码
        │   └── 示例：/yzwc account mgr reset_password Steve NewPass456 NewPass456
        │
        ├── mgr delete <player>
        │   ├── 描述：删除指定玩家的账户
        │   └── 示例：/yzwc account mgr delete Steve
        │
        └── mgr session_timeout [seconds]
            ├── 描述：查看或设置会话超时时间（0 = 关闭）
            └── 示例：
                /yzwc account mgr session_timeout          ← 查看当前值
                /yzwc account mgr session_timeout 3600     ← 设为 1 小时
```

### 权限节点一览

| 权限节点 | 说明 | 默认回退等级 |
|---------|------|------------|
| `youzaiworldcore.command.teleport_world` | 跨维度传送 | OP 4 级 |
| `youzaiworldcore.command.open_menu` | 打开菜单 | OP 4 级 |
| `youzaiworldcore.command.reload` | 模组重载 | OP 4 级 |
| `youzaiworldcore.command.experimental_feature` | 实验性功能（基础） | 所有人 |
| `youzaiworldcore.command.experimental_feature.query` | 实验性功能查询 | 所有人 |
| `youzaiworldcore.command.experimental_feature.self` | 自切换实验性功能 | 所有人 |
| `youzaiworldcore.command.experimental_feature.admin` | 管理实验性功能 | OP 4 级 |
| `youzaiworldcore.command.*` | 所有命令通配符 | — |
| `youzaiworldcore.*` | 整个模组通配符 | — |

---

## 🧪 实验性功能内部 ID 列表

实验性功能系统支持服务端全局开关和玩家级覆写，配置持久化到 `config/youzaiworldcore/experimental_feature/` 目录。

| 内部 ID | 名称 | 功能描述 | 提供者 | 来源 | 默认状态 | 当前状态 |
|---------|------|---------|-------|------|---------|---------|
| `chicken_warden_model` | 鸡管者模型 | 修改 Minecraft 原版监守者（Warden）的材质与模型为"坤坤"样式，使用 GeckoLib 动画引擎 | [终end](https://space.bilibili.com/397147959) | [苦力怕论坛](https://klpbbs.com/thread-52966-1-1.html) | ❌ 禁用 | 实验性 |

### 使用注意事项

- 实验性功能默认为**禁用**状态，需由管理员或玩家主动启用
- 状态分为三层：**全局状态**（服务端范围）> **玩家覆写**（个人设置）> **客户端缓存**
- 客户端与服务端分别持久化配置到不同 JSON 文件
- 服务端通过 `FeatureSyncPayload` 数据包实现状态同步
- 实验性功能可能影响游戏稳定性，建议在测试环境中充分验证后再全服启用

---

## 🖥️ 菜单内部 ID 列表

GUI 菜单系统基于 `MenuScreen` + `MenuElementGroup` 接口实现，支持可切换页面与动画过渡。

| 内部 ID | 菜单名称 | 层级关系 | 功能说明 |
|---------|---------|---------|---------|
| `main` | 主菜单 | 根菜单 | 功能总入口，展示 5 列磁贴布局，包含切换世界、问卷调查、称号、活动、关于我、签到、教程中心、设置、邮箱、官网、举报、管理 |
| `switch_world` | 切换世界 | 主菜单 → 切换世界 | 显示 11 个可传送世界的磁贴按钮（生存、王城、玩法、创造、建筑、下界、末地、指令区、市场、主世界、登录大厅），点击弹出传送确认对话框 |
| `settings` | 设置 | 主菜单 → 设置 | 通用设置（音乐/音效开关）、游戏设置（PVP/友军伤害开关、难度下拉选择） |
| `about_me` | 关于我 | 主菜单 → 关于我 | 显示玩家 3D 模型渲染、玩家ID、首次/最后加入时间、游玩时长，带淡入动画效果 |

### 网络数据包

菜单通过 `OpenMenuPayload` （S2C 数据包，ID: `youzaiworldcore:open_menu`）在服务端与客户端之间通信：

| 数据包 ID | 方向 | 用途 |
|-----------|------|------|
| `youzaiworldcore:open_menu` | 服务端 → 客户端 | 打开指定名称的菜单界面 |
| `youzaiworldcore:feature_sync` | 服务端 → 客户端 | 同步实验性功能状态 |
| `youzaiworldcore:open_auth_screen` | 服务端 → 客户端 | 打开认证界面 |
| `youzaiworldcore:decompose_item` | 客户端 → 服务端 | 分解台物品分解请求 |
| `youzaiworldcore:fly_beacon_active` | 客户端 → 服务端 | 飞行信标激活状态切换 |

---

## 🔧 技术栈与依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Minecraft | 26.2 | 基础游戏引擎 |
| Fabric Loader | 0.19.3 | 模组加载器 |
| Fabric API | 0.152.1+26.2 | Fabric 标准 API |
| GeckoLib | 5.5.1 | 3D 实体动画（鸡管者模型） |
| Placeholder API | 3.1.0-beta.1+26.2 | 文本占位符解析 |
| Fabric Permissions API | 0.6.1（内置） | 跨模组权限 |
| LuckPerms API | 5.5（编译时，可选运行时） | 高级权限控制 |

### 构建要求

- **JDK**: Java 25+
- **构建系统**: Gradle（Fabric Loom 1.16-SNAPSHOT）

---

## 🏗️ 项目结构

```
src/
├── main/java/top/csituka/youzaiworldcore/     # 服务端/通用代码
│   ├── YouzaiworldCore.java                    # 主入口
│   ├── account/                                # 账户认证系统
│   │   ├── command/AccountCommands.java        # 账户管理命令
│   │   ├── data/                               # 数据存储与模型
│   │   ├── mixin/                              # 认证相关 Mixin
│   │   └── util/                               # 工具类（密码哈希等）
│   ├── block/                                  # 自定义方块
│   ├── command/                                # 命令注册
│   ├── component/                              # 数据组件
│   ├── event/                                  # 事件监听器
│   ├── feature/                                # 实验性功能系统
│   ├── item/                                   # 物品与工具
│   ├── luckperms/                              # LuckPerms 集成
│   ├── mixin/                                  # 通用 Mixin
│   ├── network/                                # 网络数据包
│   ├── placeholders/                           # 占位符系统
│   └── screen/                                 # 容器菜单
│
├── client/java/top/csituka/youzaiworldcore/    # 客户端专用代码
│   ├── client/Client.java                      # 客户端入口
│   ├── network/ClientNetworking.java           # 客户端网络处理
│   ├── mixin/client/                           # 客户端 Mixin
│   ├── renderer/entity/                        # 实体渲染器
│   └── screen/                                 # GUI 界面
│       ├── MenuScreen.java                     # 主菜单屏幕
│       ├── element/                            # 菜单组元素
│       ├── widget/                             # UI 小部件
│       └── block/                              # 方块 GUI 屏幕
│
└── main/resources/                             # 资源文件
    ├── assets/youzaiworldcore/                 # 资产（语言文件、纹理等）
    └── data/                                   # 数据包（进度、配方、战利品等）
```

---

## 📦 配方清单

| 配方 | 类型 | 描述 |
|------|------|------|
| `yz_ingot_from_blasting_raw_yz` | 熔炼 | 悠哉原矿 → 悠哉锭 |
| `yz_ingot_from_yz_block` | 合成 | 悠哉块 → 9 悠哉锭 |
| `yz_ingot_from_nuggets` | 合成 | 9 悠哉粒 → 悠哉锭 |
| `yz_block` | 合成 | 9 悠哉锭 → 悠哉块 |
| `yz_nugget_from_ingot` | 合成 | 悠哉锭 → 9 悠哉粒 |
| `yz_pickaxe` / `yz_axe` / `yz_shovel` / `yz_hoe` / `yz_sword` | 合成 |悠哉系列工具 |
| `decomposition_table` | 合成 | 分解台 |
| `fly_beacon` | 合成 | 飞行信标 |
| `heart_of_guardianship` | 合成 | 守护之心 |
| `void_staff` | 合成 | 凭虚法杖 |
| `raw_yz_block` / `raw_yz_from_raw_yz_block` | 合成 | 原矿块转换 |
| `yz_block_from_blasting_raw_yz_block` | 熔炼 | 悠哉原矿块 → 悠哉块 |

---

## 🌐 相关链接

- **官方网站**: [https://mcyzw.top](https://mcyzw.top)
- **GitHub 仓库**: [https://github.com/Youzai-World-Team/YouzaiWorldCore](https://github.com/Youzai-World-Team/YouzaiWorldCore)
- **问题反馈**: [Issues](https://github.com/Youzai-World-Team/YouzaiWorldCore/issues)

---

## 🤝 贡献者

**核心作者**: ress2338396, zxabinbina, Maskviva, Youzai World Team  
**贡献者**: why, zhongbilibili, Everyone who has contributed to this project

---

> **注意**：测试模组请在服务端进行，客户端单独运行无法正常工作。
