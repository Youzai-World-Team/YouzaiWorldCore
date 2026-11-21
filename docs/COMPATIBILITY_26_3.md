# Minecraft 26.3 兼容说明

## 依赖基线

2026-11-21 联网核对最新发布版，以本地 Minecraft 26.3 原版 jar、下载的新依赖和 `run/mods` 中的 26.3 模组核验接口。

| 依赖 | 核验版本 |
| --- | --- |
| Minecraft / Java | 26.3 / 25 |
| Fabric Loader / API | 0.20.1 / 0.166.2+26.3 |
| ModMenu | 21.0.1 |
| Placeholder API | 3.2.0+26.3 |
| Trinkets Updated | 4.2.1+26.3，模组 ID 为 `trinkets_updated` |
| GeckoLib | 5.5.7 |
| Moog's Structure Lib | 3.4.2-fabric-26.3，模组元数据版本为 `3.4.2+fabric` |
| Jade | 26.3.5+fabric |
| Sophisticated Backpacks / Core | 3.24.1.3 / 1.2.10.4，Fabric 维护分支，已替换 `run/mods` 文件 |
| Enchantment Descriptions | 26.3.0.2，模组 ID 为 `enchdesc` |
| Fabric Permissions API | 0.7.0，支持 Minecraft >=26.1，内置打包 |
| Reflect / unchecked | 1.6.4 / 1.9.2，两个库均内置打包 |
| SQLite JDBC | 3.54.1.0，实际原生驱动、WAL 与事务读写通过 |
| ASM / ASM Tree | 9.11.1 编译接口；另核验 Loader 提供的 9.10.1 运行接口 |

依赖版本统一从 `gradle.properties` 读取，需要声明的模组版本在构建时展开到 `fabric.mod.json`。Minecraft 限定 `>=26.3 <26.4`，显式声明 Loader 0.20.1 和 Java 25 下限。Trinkets 允许同一 4.2 系列的修订版；实际接口核验仍以 4.2.1+26.3 为准。

本次新增版本更新：Reflect 1.4.0 → 1.6.4、ASM/ASM Tree 9.10.1 → 9.11.1、SQLite JDBC 3.53.4.0 → 3.54.1.0、Fabric Permissions API 0.6.1 → 0.7.0。Reflect 1.6.x 新增 `unchecked` 1.9.2 运行依赖；Gradle `include` 不递归嵌入传递依赖，因此显式将其内置，避免发布后出现缺类。

Fabric Loader、Fabric API、ModMenu、Placeholder API、Trinkets Updated、GeckoLib、Moog's Structure Lib 和 Jade 当前已是适配 Fabric 26.3 的最新发布版。LuckPerms API 仍为 5.5；对应 Fabric 模组已核验 5.5.85。ASM 保持 `compileOnly`，不会把第二套 ASM 嵌入游戏类加载器。

版本来源：[Fabric Loader](https://meta.fabricmc.net/v2/versions/loader)、[Modrinth](https://modrinth.com/)、Maven Central 的 [Reflect](https://repo.maven.apache.org/maven2/net/lenni0451/Reflect/maven-metadata.xml)、[ASM](https://repo.maven.apache.org/maven2/org/ow2/asm/asm/maven-metadata.xml)、[SQLite JDBC](https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/maven-metadata.xml)、[权限 API](https://repo.maven.apache.org/maven2/me/lucko/fabric-permissions-api/maven-metadata.xml)，以及精妙背包 Fabric 维护分支的 [Backpacks 发布页](https://github.com/Huziyang520/Sophisticated-Backpacks-Unofficial-Fabric-Continuation/releases) 与 [Core 发布页](https://github.com/Huziyang520/Sophisticated-Core-Unofficial-Fabric-Continuation/releases)。

## 修复内容

| 问题 | 处理 |
| --- | --- |
| 猫动画烘焙时报 `Failed to parse expression 'linear'`，丢失 idle、running、crouching | 29 个旋转/位移轴的旧占位字符串改为数值 0；保留真正的关键帧插值参数 |
| 苦力怕 `DATA_IS_POWERED` 是静态字段，访问器却声明为实例方法 | 改为静态 Mixin 访问器，服务端直接调用 |
| Trinkets 默认 `isEquipped`、`findFirst` 查询包含外观槽 | 死亡保护、计数和消耗统一使用 `requireActive=true`，避免外观守护之心提供保护或被消耗 |
| 手动消耗饰品没有通知新版库存同步 | 用 `TrinketSlotAccess.set` 写回并调用 `inventory.setChanged` |
| 精妙背包桥接未区分有效槽和外观槽 | 功能处理仅允许可生效饰品；渲染优先使用外观背包，隐藏状态保留 |
| 原生饰品界面外观模式与有效槽共用编号 | 外观槽的悬停快捷键不向同编号有效背包发送打开请求 |
| Fabric 开发环境在语言适配器阶段尚未向 Knot 开放 Reflect 库 | 延迟反射访问，并在普通库不可见时从系统类加载器读取，保留原始异常 |
| 依赖声明中使用旧模组 ID 和硬编码版本 | 更新 `enchdesc`，同步 Fabric API、ModMenu 版本，集中 Placeholder、结构库和 Jade 版本 |
| 精妙背包 3.24.1.3 原生处理器绕过 Core 的隐藏、外观与有效槽规则 | 对 `TrinketsCompat` 的库存读取注入统一规则，保留作者原生注册 |
| 原生槽位列表缓存跨玩家复用，外观槽可能被默认背包校验遗漏 | 按当前玩家即时获取库存标识，功能读取仍经过 `canApplyEffects` |
| B 键仍发送旧处理器名 `trinkets` | 从作者 `CompatModIds.TRINKETS` 读取原生名 `trinkets_updated`，旧版补注册保留旧名 |
| Trinkets 对无效槽也调用原生背包 tick | 在 `BackpackTrinket.tick` 入口过滤无效槽，隐藏的有效背包仍执行原生升级 tick |

## 验证

未执行整包 Gradle 构建。项目 [AGENTS.md](../AGENTS.md) 约定由开发者本人构建；下列检查仅进行源码分析或编译隔离检查程序，不编译打包整个模组。

```bash
# 验证全部源码，已安装模组及其内嵌 jar 优先于 IDE 旧缓存
python3 tools/verify_java_types.py --runtime-mods run/mods

# 使用实际 MC、Fabric、Trinkets、GeckoLib 类验证依赖范围、动画、饰品与启动追踪
python3 tools/verify_minecraft_runtime_compat.py

# 核验临时目录中的更新候选，避免旧 run/mods 文件覆盖指定的测试依赖
python3 tools/verify_minecraft_runtime_compat.py --classpath-file <依赖列表> --runtime-mods <候选目录>

# 使用实际精妙背包库存类验证接口、有效/外观/隐藏槽和快捷键
node tools/verify-backpack-compat.mjs <精妙背包.jar> <JDK25目录>

# Jade 启动、重载及主题切换回归
python3 tools/verify_jade_theme_bridge.py --java-home <JDK25目录>
```

前两个 Python 工具默认读取 IDE 已解析的依赖，亦支持 `--classpath-file <文件>`（每行一个 jar 绝对路径）、`--runtime-mods <目录>` 和 `--java-home <JDK25目录>`。运行期检查在临时目录执行，避免向游戏配置、存档和日志目录写入测试数据。

本次用更新后的依赖完成 775 个 Java 文件的类型与符号分析，0 个错误；实际依赖回归 57 项通过，覆盖模型/动画、饰品、Reflect 启动追踪、Loader 字节码转换和 SQLite 原生读写。精妙背包新版本的 41 项接口/饰品/分组检查通过，未安装时的类加载与已有原生适配时的避让检查亦通过。

后续原生桥接修复将检查扩展为：旧版补注册路径 41 项；作者未注入路径 10 项，复现隐藏/外观/有效槽/tick 缺口；原生库存与 tick 的实际注入点分别 9 项和 4 项；注入后原生路径 25 项，覆盖注册避让、渲染、功能枚举、即时槽位刷新、跨玩家隔离、缺失/越界处理、B 键处理器名及升级 tick。全源码类型分析扩展至 777 个文件，0 个错误。原生检查按生产注解定位字节码并接入实际处理方法，不启动 Fabric/Mixin 服务，不能视为完整游戏验证。

本次还核对了 155 个外部 Mixin 目标的字段、注入方法描述符和方法内字节码调用位置；项目自身的设置页 Mixin 由源码分析核验。接口与隔离检查不能替代完整游戏验证。构建后需在服务端和客户端确认：加入服务器、猫的全部姿态、自然带电苦力怕、有效/外观守护之心的死亡行为、背包升级和快捷键，以及 Jade 资源重载。
