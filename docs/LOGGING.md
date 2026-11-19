# 模组日志

所有日志经过 `DebugLogger`，使用明确的 `SERVER` / `CLIENT` 实例或绑定模块名的 `ModuleLogger`。两端开关独立，单人模式的内置服务器也读取服务端配置；客户端改设置不会覆盖服务端，服务端重载不会覆盖客户端。

## 日志分类

| 类别 / 档位 | 接口 | 内容 |
|---|---|---|
| 普通日志，始终输出 | `notice`、`warn`、`error`、`exception`、`exceptionSummary` | 启停里程碑、更新提示、账户审计、管理员操作、实际故障及堆栈 |
| 0 关闭 | — | 关闭诊断日志，普通日志仍保留 |
| 1 基本 | `info`、`stateChange` | 配置 / 数据加载摘要、重要状态和操作结果，例如升级、AFK 状态、宠物和地图设置 |
| 2 详细 | `debug`、`branch`、`debugStateChange` | 模块注册、网络请求与确认、UI 操作、计算过程、校验分支和存储细节 |
| 3 调试 | `trace`、`entering`、`exiting`、`traceBranch`、`traceStateChange` | 每帧 / tick、逐物品 / 实体处理、AI 检查与方法调用追踪 |

1–3 档累积包含更低档诊断，且要求对应端开启开发者模式。正常冷却、玩家取消、预期的权限拒绝、界面空间不足等使用详细诊断；资源缺失、写入失败、格式错误和无法恢复的业务故障保留为普通告警。玩家每次获取少量冒险经验属于3档，实际升级结果属于1档。

## 配置与应用

| 端 | 配置文件 | 应用方式 |
|---|---|---|
| 服务端 / 内置服务器 | `yzwc/server/config/global_settings.json` 的 `core_module` | 启动时加载，修改后执行 `/yzwc reload` |
| 客户端 | `yzwc/client/global_settings.json` 的 `core_module` | 设置页即时保存、即时应用；文件重读时也即时应用 |

两端都使用以下字段，默认开发者模式关闭、级别为0：

```json
{
  "core_module": {
    "dev_mode_enabled": true,
    "log_level": 3
  }
}
```

`log_level` 只接受0–3。旧服务端布尔字段 `log_to_file` 不再读取，也不推导新级别；缺少新字段时写入默认值0。重载读取非法类型或越界值时，沿用配置错误隔离、重建默认值和停止运行的流程。已发生的启动日志不会补写。

## 输出文件

- 客户端：`logs/youzaiworldcore-client.log`
- 服务端：`logs/youzaiworldcore-server.log`
- 两端分别按20 MB或进程启动轮转，最多保留5个压缩归档，文件名为 `youzaiworldcore-<端>-<序号>.log.gz`。

路径全部由 `ModPaths` 提供。日志格式为 `[yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] [CLIENT|SERVER] [Module] 描述`，异常后跟完整堆栈。方法入口 / 出口使用真正的 SLF4J TRACE，与文本标记一致。

独立文件接收本端已启用的全部等级。模组仅配置自己的两个 Log4j 日志器及文件输出器，不修改 Minecraft 或其它模组的阈值；游戏控制台、`latest.log`、开发环境 `debug.log` 继续使用各自过滤规则。文件输出故障会在游戏日志中明确记录，业务继续使用游戏日志通道。

## 开发与验证

```java
DebugLogger.SERVER.info("Module", "已加载 {} 条记录", count);
DebugLogger.CLIENT.notice("Module", "客户端初始化完成");
DebugLogger.SERVER.exception("Module", "保存数据", error);
if (DebugLogger.CLIENT.isEnabled(DebugLogger.LEVEL_DEBUG)) {
    DebugLogger.CLIENT.trace("Module", "逐帧参数：{}", expensiveDescription());
}
```

全部日志调用统一使用 SLF4J 的 `{}` 占位符，末尾 `Throwable` 输出完整堆栈；`ModuleLogger` 只负责固定模块名，不再使用另一套格式化规则。数字小数位和十六进制补零通过按需求值的 `LogValues.decimal` / `LogValues.hex` 参数保留。带参数、拼接或复杂描述的高频诊断必须先在调用点检查等级，避免关闭时仍分配参数数组或构建文本。

共享配置、数据工具通过 `DebugLogger.forFile(path)` 选择端；仅在通用资源注册阶段使用 `bootstrap()`。更新检查将发起端实例显式传入后台任务，不按线程名猜测归属。新增业务代码不得创建裸 SLF4J / Log4j 日志器或使用 `System.out`。

执行 `python3 tools/verify_logging.py --write-audit` 可离线验证真实等级、文件过滤、两端隔离、实时切换和全源码语法，并生成[逐文件归档清单](LOGGING_AUDIT.md)。该检查不执行整包 Gradle 构建。
