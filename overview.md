# LuckPerms API 集成 — 完成总结

## 改动清单

### 1. 新建文件：`LuckPermsHelper.java`
**路径**: `src/main/java/top/csituka/youzaiworldcore/luckperms/LuckPermsHelper.java`

核心权限集成工具类，提供：

- **权限节点常量** — 遵循 `domain.subdomain.action` 规范定义了所有命令的权限节点:
  - `youzaiworldcore.command.teleport_world`
  - `youzaiworldcore.command.open_menu`
  - `youzaiworldcore.command.account.mgr` (及所有子命令)
  - `youzaiworldcore.command.account.*` / `youzaiworldcore.*` (通配符)

- **`checkPermission(CommandSourceStack, String, PermissionCheck)`** — 核心权限检查方法：
  - LP 已安装 → 优先使用 LuckPerms 原生 API 检查权限节点
  - LP 有值但未明确设置 → 回退到 `PermissionCheck` 对应的原版 OP 等级
  - LP 未安装 → 直接使用 `PermissionCheck.check(PermissionSet)` 降级到 OP 等级
  - 非玩家执行者（控制台）→ 始终放行

- **`getApi()`** — 懒加载的 `LuckPerms` API 实例，LP 未安装时返回空 Optional

- **`getPlayerGroups()` / `getPrimaryGroup()` / `checkLuckPermsOnly()`** — 额外的 LP 高级功能

- **降级保障**：当 LP 未加载时，所有方法静默返回空值/`false`，不会抛出异常或崩溃

### 2. 修改文件：`YouzaiworldCore.java`

将命令注册处的 3 处原版 OP 检查替换为 LuckPerms 权限节点检查：

| 命令 | 原检查方式 | 新检查方式 |
|------|-----------|-----------|
| `/yzwc teleport_world` | `Commands.hasPermission(LEVEL_ADMINS)` | `LuckPermsHelper.checkPermission(source, PERMISSION_TELEPORT_WORLD, LEVEL_ADMINS)` |
| `/yzwc open_menu` | `Commands.hasPermission(LEVEL_ADMINS)` | `LuckPermsHelper.checkPermission(source, PERMISSION_OPEN_MENU, LEVEL_ADMINS)` |
| `/yzwc account mgr` | `Commands.hasPermission(LEVEL_ADMINS)` | `LuckPermsHelper.checkPermission(source, PERMISSION_ACCOUNT_MGR_WILDCARD, LEVEL_ADMINS)` |

### 3. 现有依赖配置（未修改）

`build.gradle` 中已配置好所需依赖：
- `compileOnly 'net.luckperms:api:5.5'` — LP API 编译依赖（运行时由 LP 模组提供）
- LuckPerms Maven 仓库已配置
- `fabric.mod.json` 的 `suggests` 中已声明 `luckperms`

## 构建状态

✅ **BUILD SUCCESSFUL** — 项目编译通过，无错误无警告。

## 服主使用说明

1. 在服务端安装 LuckPerms Fabric 版本
2. 使用以下命令分配权限（示例）：
   - `/lp user <玩家> permission set youzaiworldcore.command.teleport_world true` — 给予跨维度传送权限
   - `/lp user <玩家> permission set youzaiworldcore.command.open_menu true` — 给予打开菜单权限
   - `/lp group admin permission set youzaiworldcore.command.account.mgr.* true` — 给予管理组所有账户管理权限
   - `/lp user <玩家> permission set youzaiworldcore.* true` — 给予所有模组权限
3. 若未安装 LuckPerms，所有命令仍会按原版 OP 等级 (LEVEL_ADMINS/4) 正常运作
