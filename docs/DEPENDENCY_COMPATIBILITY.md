# 本地运行依赖兼容

## Iceberg 1.4.3.1 与 Forge Config API Port 26.3.1

启动精妙背包或 Sophisticated Core 时出现 `WritingMode.REPLACE_ATOMIC` 的 `NoSuchFieldError`，是因为 Iceberg 1.4.3.1 直接打包了旧版 `com.electronwill.nightconfig`，与 Forge Config API Port 内嵌的 Night Config 3.8.4 同名。先加载 Iceberg 的类时，新版配置写入器找不到原子写入枚举值，游戏在初始化阶段崩溃。

在上游发布修复版本之前，本地运行目录可执行：

```sh
python3 tools/fix_iceberg_night_config.py
```

其他启动器目录可把 Iceberg jar 路径作为参数。工具将 Iceberg 自己的 Night Config 类与引用一起移到 `com.anthonyhilyard.iceberg.shadow.nightconfig`，两套库分别使用自己的版本。工具不修改模组功能、配置文件或游戏数据，也不构建本项目。

原始 jar 保存在 mods 同级的 `mod-backups/iceberg-night-config/` 中；把备份复制回原路径即可恢复。重复运行不会再次修改。修复仅作用于该本地 Iceberg jar，不随 YouzaiWorldCore 打包分发；重新下载此版本后需要再次执行，安装上游修复版本后不再使用此工具。
