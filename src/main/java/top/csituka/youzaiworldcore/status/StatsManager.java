package top.csituka.youzaiworldcore.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 玩家统计管理器。
 * <p>
 * 从 vanilla 统计系统读取玩家数据，持久化到 {@code .<world>/youzaiworldcore/status/data.json}，
 * </p>
 * <p>
 * 提供 /yzwc status 命令支持查询、删除和排行榜导出（支持日/周/月/年/总周期）。
 * </p>
 */
@SuppressWarnings("null")
public final class StatsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/StatsManager");
    private static final String MODULE = "StatsManager";

    private static final int REFRESH_INTERVAL_TICKS = 6000;

    /**
     * 状态数据写盘专用线程（单线程 FIFO，保证写入顺序）。
     * <p>
     * 设为守护线程：即便服务端异常退出也不会挂住 JVM。正常关服路径会先
     * {@code shutdownIoExecutor()} 排空队列，再做最终同步保存，不会丢数据。
     * </p>
     */
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "YZWC-Stats-IO");
        t.setDaemon(true);
        return t;
    });

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 内存缓存：UUID字符串 -> PlayerStats */
    private static final ConcurrentHashMap<String, PlayerStats> CACHE = new ConcurrentHashMap<>();

    /**
     * 每日快照缓存：日期 -> UUID字符串 -> 指标键 -> 值
     * 用于计算日/周/月/年区间差值
     */
    private static final ConcurrentHashMap<LocalDate, Map<String, Map<String, Long>>> SNAPSHOTS = new ConcurrentHashMap<>();

    /** 红石元件列表（参考 rankboard-main 的设计整理） */
    private static final Set<String> REDSTONE_COMPONENTS = Set.of(
            "redstone", "redstone_torch", "repeater", "comparator", "observer",
            "piston", "sticky_piston", "dispenser", "dropper", "hopper", "lever",
            "tripwire_hook", "target", "daylight_detector", "note_block",
            "redstone_block", "sculk_sensor", "calibrated_sculk_sensor",
            "lightning_rod", "trapped_chest", "powered_rail", "detector_rail",
            "activator_rail", "rail", "lectern", "jukebox", "bell",
            "redstone_lamp", "tnt", "big_dripleaf", "crafter",
            "command_block", "chain_command_block", "repeating_command_block");

    /** 缓存的红石 Item 对象，服务器启动后初始化 */
    private static volatile Set<Item> REDSTONE_ITEMS = null;

    private static int tickCounter = 0;
    private static volatile boolean initialized = false;

    /** 当前 snapshot 日期，用于检测是否跨日 */
    private static volatile LocalDate currentSnapshotDate = null;

    // ===== 统计指标定义 =====
    private static final List<MetricDef> METRICS = List.of(
            MetricDef.custom("play_time", "在线时间", Stats.PLAY_TIME, StatsManager::formatPlayTime),
            MetricDef.custom("jumps", "跳跃次数", Stats.JUMP, StatsManager::formatRaw),
            MetricDef.custom("deaths", "死亡次数", Stats.DEATHS, StatsManager::formatRaw),
            MetricDef.custom("mob_kills", "击杀怪物", Stats.MOB_KILLS, StatsManager::formatRaw),
            MetricDef.custom("player_kills", "击杀玩家", Stats.PLAYER_KILLS, StatsManager::formatRaw),
            MetricDef.custom("damage_dealt", "造成伤害", Stats.DAMAGE_DEALT, StatsManager::formatDamage),
            MetricDef.custom("damage_taken", "受到伤害", Stats.DAMAGE_TAKEN, StatsManager::formatDamage),
            MetricDef.custom("walk_cm", "步行距离", Stats.WALK_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("sprint_cm", "疾跑距离", Stats.SPRINT_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("aviate_cm", "鞘翅飞行距离", Stats.AVIATE_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("fall_cm", "坠落距离", Stats.FALL_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("fish_caught", "钓鱼数量", Stats.FISH_CAUGHT, StatsManager::formatRaw),
            MetricDef.custom("traded", "村民交易次数", Stats.TRADED_WITH_VILLAGER, StatsManager::formatRaw),
            MetricDef.custom("items_dropped", "丢弃物品", Stats.DROP, StatsManager::formatRaw),
            MetricDef.custom("sleep_in_bed", "睡觉次数", Stats.SLEEP_IN_BED, StatsManager::formatRaw),
            MetricDef.custom("enchanted", "附魔次数", Stats.ENCHANT_ITEM, StatsManager::formatRaw),
            MetricDef.custom("raid_wins", "袭击胜利", Stats.RAID_WIN, StatsManager::formatRaw),
            MetricDef.custom("animals_bred", "繁殖动物", Stats.ANIMALS_BRED, StatsManager::formatRaw),
            MetricDef.custom("bell_ring", "敲钟次数", Stats.BELL_RING, StatsManager::formatRaw),
            MetricDef.custom("cake_eaten", "吃蛋糕", Stats.EAT_CAKE_SLICE, StatsManager::formatRaw),
            MetricDef.aggregate("redstone_placed", "红石大蛇榜", StatsManager::formatRaw, StatsManager::readRedstonePlaced));

    /** 辅助：为周期 literal 添加可选 [name] 子节点 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> periodLiteral(
            String period,
            com.mojang.brigadier.Command<CommandSourceStack> withoutName) {
        return Commands.literal(period)
                .executes(withoutName)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> executeRankExport(ctx,
                                StringArgumentType.getString(ctx, "name"), period)));
    }

    private StatsManager() {
    }

    // ==================== 初始化 ====================

    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        if (initialized) {
            DebugLogger.warn(MODULE, "StatsManager 已被初始化，跳过");
            return;
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DebugLogger.info(MODULE, "服务器启动，初始化状态目录...");
            createDataDir(server);
            load(server);
            buildRedstoneItems();
            scanOfflineStats(server);

            // 快照当天现有数据作为基线
            currentSnapshotDate = LocalDate.now();
            snapshotToday(server);
            DebugLogger.info(MODULE, "状态数据加载完成，共 %s 位玩家，%s 个红石元件，快照日期 %s",
                    CACHE.size(), REDSTONE_ITEMS != null ? REDSTONE_ITEMS.size() : 0, currentSnapshotDate);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DebugLogger.info(MODULE, "服务器停止，保存状态数据...");
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                refreshPlayer(server, player);
            }
            snapshotToday(server);
            // 先排空异步写盘队列，再做最终同步保存，
            // 避免排队中的旧数据在最终保存之后落盘覆盖新数据
            shutdownIoExecutor();
            save(server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer player) {
                DebugLogger.info(MODULE, "玩家 %s 断开，刷新统计到缓存（延迟写盘）", player.getName().getString());
                refreshPlayer(server, player);
                // 不再立即全量写盘：刷新到 CACHE 后由周期性 tick (每 5 分钟) 或服务器停止时落盘
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                refreshAllOnline(server);
                // 检查是否跨日 → 生成新的快照基线
                LocalDate today = LocalDate.now();
                if (!today.equals(currentSnapshotDate)) {
                    currentSnapshotDate = today;
                    snapshotToday(server);
                } else {
                    // 更新今天快照（使增量计算更精确）
                    updateTodaySnapshot(server);
                }
                // 周期性落盘（替代每次玩家断线时的立即写盘）
                // 序列化在本线程完成，阻塞写盘交给后台线程，避免每 5 分钟一次的卡顿尖刺
                saveAsync(server);
            }
        });

        CommandRegistrationCallback.EVENT.register(StatsManager::registerCommands);

        initialized = true;
        DebugLogger.exiting(MODULE, "initialize");
    }

    /** 为今天创建/替换所有在线玩家的快照基线 */
    private static void snapshotToday(MinecraftServer server) {
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Long>> dayData = new HashMap<>();
        for (PlayerStats ps : CACHE.values()) {
            dayData.put(ps.getUuid().toString(), new HashMap<>(ps.getStats()));
        }
        SNAPSHOTS.put(today, dayData);
        // 清理 365 天前的旧快照
        LocalDate cutoff = today.minusDays(365);
        SNAPSHOTS.keySet().removeIf(d -> d.isBefore(cutoff));
        DebugLogger.info(MODULE, "快照已更新：%s，%s 位玩家", today, dayData.size());
    }

    /** 更新今天快照（增量覆盖已有值） */
    private static void updateTodaySnapshot(MinecraftServer server) {
        LocalDate today = LocalDate.now();
        Map<String, Map<String, Long>> existing = SNAPSHOTS.get(today);
        if (existing == null) {
            snapshotToday(server);
            return;
        }
        for (PlayerStats ps : CACHE.values()) {
            existing.put(ps.getUuid().toString(), new HashMap<>(ps.getStats()));
        }
    }

    // ==================== 红石元件 ====================

    private static void buildRedstoneItems() {
        if (REDSTONE_ITEMS != null)
            return;
        Set<Item> items = BuiltInRegistries.ITEM.stream()
                .filter(StatsManager::isRedstoneComponent)
                .collect(Collectors.toSet());
        REDSTONE_ITEMS = Collections.unmodifiableSet(items);
        DebugLogger.info(MODULE, "红石元件缓存初始化完成：%s 个", items.size());
    }

    static boolean isRedstoneComponent(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return REDSTONE_COMPONENTS.contains(path)
                || path.endsWith("_button")
                || path.endsWith("_pressure_plate")
                || path.endsWith("_door")
                || path.endsWith("_trapdoor")
                || path.endsWith("_fence_gate")
                || path.endsWith("_bulb");
    }

    private static long readRedstonePlaced(ServerPlayer player) {
        Set<Item> items = REDSTONE_ITEMS;
        if (items == null || items.isEmpty())
            return 0L;
        long total = 0L;
        for (Item item : items) {
            total += player.getStats().getValue(Stats.ITEM_USED.get(item));
        }
        return total;
    }

    // ==================== 命令注册 ====================

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext ignored,
            Commands.CommandSelection environment) {
        DebugLogger.entering(MODULE, "registerCommands");

        // rank_export <day|week|month|year|all> [name]
        var rankExportNode = Commands.literal("rank_export")
                .requires(src -> LuckPermsHelper.checkPermission(
                        src, LuckPermsHelper.PERMISSION_STATUS_EXPORT, Commands.LEVEL_ADMINS))
                // 5 个周期 literal，每个可选接 name 参数
                .then(periodLiteral("day", ctx -> executeRankExport(ctx, null, "day")))
                .then(periodLiteral("week", ctx -> executeRankExport(ctx, null, "week")))
                .then(periodLiteral("month", ctx -> executeRankExport(ctx, null, "month")))
                .then(periodLiteral("year", ctx -> executeRankExport(ctx, null, "year")))
                .then(periodLiteral("all", ctx -> executeRankExport(ctx, null, "all")));

        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("list")
                                        .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_STATUS_QUERY, Commands.LEVEL_ADMINS))
                                        .executes(ctx -> executeList(ctx, EntityArgument.getPlayer(ctx, "player"))))
                                .then(Commands.literal("delete")
                                        .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_STATUS_DELETE, Commands.LEVEL_ADMINS))
                                        .executes(ctx -> executeDelete(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(rankExportNode)));

        DebugLogger.exiting(MODULE, "registerCommands");
    }

    // ==================== 命令执行 ====================

    private static int executeList(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        DebugLogger.entering(MODULE, "executeList", "target=" + target.getName().getString());
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        refreshPlayer(server, target);

        PlayerStats stats = CACHE.get(target.getUUID().toString());
        if (stats == null) {
            source.sendFailure(Component.literal("该玩家暂无统计数据。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== " + target.getName().getString() + " 的统计 ==="), false);

        int count = 0;
        for (MetricDef metric : METRICS) {
            long value = stats.get(metric.key);
            String formatted = metric.formatter.apply(value);
            source.sendSuccess(() -> Component.literal("  " + metric.label + ": " + formatted), false);
            count++;
        }

        DebugLogger.exiting(MODULE, "executeList", "count=" + count);
        return count;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        DebugLogger.entering(MODULE, "executeDelete", "target=" + target.getName().getString());
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        String uuidStr = target.getUUID().toString();
        PlayerStats removed = CACHE.remove(uuidStr);
        if (removed == null) {
            source.sendFailure(Component.literal("该玩家暂无统计数据可清除。"));
            return 0;
        }

        save(server);
        source.sendSuccess(() -> Component.literal("已清除 " + target.getName().getString() + " 的统计数据。"), true);
        LOGGER.info("管理员 {} 清除了玩家 {} 的统计数据", source.getTextName(), target.getName().getString());

        return 1;
    }

    /** 获取指定玩家的周期基线值（快照值，用于做差） */
    private static long getBaseline(String uuidStr, String metricKey, LocalDate baselineDate) {
        Map<String, Map<String, Long>> dayData = SNAPSHOTS.get(baselineDate);
        if (dayData == null)
            return -1L;
        Map<String, Long> playerData = dayData.get(uuidStr);
        if (playerData == null)
            return -1L;
        return playerData.getOrDefault(metricKey, -1L);
    }

    /** 计算周期内的差值 */
    private static long computePeriodValue(MinecraftServer server, PlayerStats ps,
            String metricKey, String period) {
        long currentValue = ps.get(metricKey);
        if ("all".equals(period))
            return currentValue; // 总榜直接使用累计值

        LocalDate now = LocalDate.now();
        LocalDate baselineDate = switch (period) {
            case "day" -> now; // 当日快照是在当天的基线
            case "week" -> now.minusDays(6);
            case "month" -> now.minusDays(29);
            case "year" -> now.minusDays(364);
            default -> now.minusDays(364);
        };

        // 先尝试从快照获取基线
        long baseline = getBaseline(ps.getUuid().toString(), metricKey, baselineDate);
        if (baseline >= 0 && currentValue >= baseline) {
            return currentValue - baseline;
        }

        // 回退：尝试用最早的可用快照做差
        if (SNAPSHOTS.isEmpty() || !"all".equals(period)) {
            // 没有任何快照 → 只能返回累计值
            return currentValue;
        }

        // 用最早的快照日期作为准基线
        LocalDate earliest = SNAPSHOTS.keySet().stream().min(LocalDate::compareTo).orElse(null);
        if (earliest != null && !earliest.equals(now)) {
            baseline = getBaseline(ps.getUuid().toString(), metricKey, earliest);
            if (baseline >= 0 && currentValue >= baseline) {
                return currentValue - baseline;
            }
        }

        return currentValue;
    }

    private static int executeRankExport(CommandContext<CommandSourceStack> ctx, String name, String period) {
        DebugLogger.entering(MODULE, "executeRankExport", "name=" + name + ", period=" + period);
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();

        refreshAllOnline(server);

        if (CACHE.isEmpty()) {
            load(server);
            scanOfflineStats(server);
        }

        String exportName = (name == null || name.isBlank())
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now())
                : name;

        Map<String, List<Map<String, Object>>> rankings = new LinkedHashMap<>();
        for (MetricDef metric : METRICS) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (PlayerStats ps : CACHE.values()) {
                long value = computePeriodValue(server, ps, metric.key, period);
                if (value > 0) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", ps.getName());
                    entry.put("uuid", ps.getUuid().toString());
                    entry.put("value", value);
                    entry.put("formatted", metric.formatter.apply(value));
                    entries.add(entry);
                }
            }
            entries.sort((a, b) -> Long.compare((Long) b.get("value"), (Long) a.get("value")));
            for (int i = 0; i < entries.size(); i++) {
                entries.get(i).put("rank", i + 1);
            }
            rankings.put(metric.key, entries);
        }

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("name", exportName);
        export.put("period", period);
        export.put("generatedAt", System.currentTimeMillis());
        export.put("generatedAtFormatted",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
        export.put("totalPlayers", CACHE.size());
        export.put("metrics", rankings);

        Path exportDir = getDataRoot(server).resolve("rank_export");
        try {
            Files.createDirectories(exportDir);
            String fileName = period.equals("all") ? exportName : exportName + "_" + period;
            Path exportFile = exportDir.resolve(fileName + ".json");

            // 同名检测：防止覆盖已有文件
            if (Files.exists(exportFile)) {
                source.sendFailure(Component.literal("导出失败：文件 \"" + fileName + ".json\" 已存在，请换个名称。"));
                DebugLogger.info(MODULE, "排行榜导出被拦截：文件已存在 %s", exportFile.toAbsolutePath());
                return 0;
            }

            Files.writeString(exportFile, GSON.toJson(export));
            source.sendSuccess(() -> Component.literal("排行榜已导出 (" + period + "): " + exportFile.toAbsolutePath()),
                    true);
            LOGGER.info("排行榜已导出 ({}): {}", period, exportFile.toAbsolutePath());
            return 1;
        } catch (IOException e) {
            LOGGER.error("排行榜导出失败", e);
            source.sendFailure(Component.literal("排行榜导出失败: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== 数据管理 ====================

    static Path getDataRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("youzaiworldcore")
                .resolve("status");
    }

    private static void createDataDir(MinecraftServer server) {
        try {
            Path dir = getDataRoot(server);
            Files.createDirectories(dir);
            Files.createDirectories(dir.resolve("rank_export"));
        } catch (IOException e) {
            LOGGER.error("创建状态数据目录成功", e);
        }
    }

    private static Path getDataFile(MinecraftServer server) {
        return getDataRoot(server).resolve("data.json");
    }

    @SuppressWarnings("unchecked")
    static void load(MinecraftServer server) {
        Path file = getDataFile(server);
        if (!Files.isRegularFile(file)) {
            DebugLogger.info(MODULE, "状态数据文件不存在: %s", file);
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> raw = GSON.fromJson(reader, Map.class);

            // 读取玩家数据
            Object playersObj = raw.get("players");
            Map<String, Map<String, Object>> playersMap = new HashMap<>();
            if (playersObj instanceof Map<?, ?> pm) {
                for (Map.Entry<?, ?> e : pm.entrySet()) {
                    Object val = e.getValue();
                    if (val instanceof Map) {
                        Map<String, Object> typed = (Map<String, Object>) val;
                        playersMap.put(e.getKey().toString(), typed);
                    }
                }
            } else {
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    if ("version".equals(e.getKey()) || "snapshots".equals(e.getKey()))
                        continue;
                    Object val = e.getValue();
                    if (val instanceof Map) {
                        Map<String, Object> typed = (Map<String, Object>) val;
                        playersMap.put(e.getKey().toString(), typed);
                    }
                }
            }

            int loaded = 0;
            for (Map.Entry<String, Map<String, Object>> entry : playersMap.entrySet()) {
                Map<String, Object> data = entry.getValue();
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    String name = (String) data.getOrDefault("name", uuid.toString().substring(0, 8));
                    long lastUpdated = ((Number) data.getOrDefault("lastUpdated", 0L)).longValue();

                    Object statsRaw = data.get("stats");
                    Map<String, Long> statsMap = new HashMap<>();
                    if (statsRaw instanceof Map<?, ?> rawStats) {
                        for (Map.Entry<?, ?> se : rawStats.entrySet()) {
                            String sk = se.getKey().toString();
                            long sv = ((Number) se.getValue()).longValue();
                            statsMap.put(sk, sv);
                        }
                    }
                    CACHE.put(entry.getKey(), new PlayerStats(uuid, name, lastUpdated, statsMap));
                    loaded++;
                } catch (Exception e) {
                    DebugLogger.warn(MODULE, "解析玩家 %s 数据失败: %s", entry.getKey(), e.getMessage());
                }
            }

            // 读取快照数据
            Object snapObj = raw.get("snapshots");
            if (snapObj instanceof Map<?, ?> snapRaw) {
                for (Map.Entry<?, ?> se : snapRaw.entrySet()) {
                    try {
                        LocalDate date = LocalDate.parse(se.getKey().toString());
                        Object dayDataRaw = se.getValue();
                        if (dayDataRaw instanceof Map<?, ?> dayMap) {
                            Map<String, Map<String, Long>> dayData = new HashMap<>();
                            for (Map.Entry<?, ?> de : dayMap.entrySet()) {
                                String uuidStr = de.getKey().toString();
                                Object statsRaw2 = de.getValue();
                                if (statsRaw2 instanceof Map<?, ?> statsMap2) {
                                    Map<String, Long> statsMap = new HashMap<>();
                                    for (Map.Entry<?, ?> ste : statsMap2.entrySet()) {
                                        statsMap.put(ste.getKey().toString(), ((Number) ste.getValue()).longValue());
                                    }
                                    dayData.put(uuidStr, statsMap);
                                }
                            }
                            SNAPSHOTS.put(date, dayData);
                        }
                    } catch (Exception e) {
                        DebugLogger.warn(MODULE, "解析快照 %s 失败: %s", se.getKey(), e.getMessage());
                    }
                }
            }

            DebugLogger.info(MODULE, "已加载 %s 位玩家状态 + %s 天快照", loaded, SNAPSHOTS.size());
        } catch (IOException e) {
            LOGGER.error("读取状态数据文件失败", e);
        } catch (RuntimeException e) {
            // 健壮性：状态数据 JSON 损坏（如异常中断写入导致文件截断/非法）不应让服务器启动崩溃。
            // 备份损坏文件保留现场，然后以空数据继续——后续 save() 会重建一份干净的数据文件。
            DebugLogger.error(MODULE, "状态数据文件损坏，已备份并跳过加载: %s (%s)", file, e.getMessage());
            backupCorruptFile(file);
        }
    }

    /** 将损坏的状态数据文件备份为 {@code data.json.corrupt-<时间戳>}，避免覆盖现场 */
    private static void backupCorruptFile(Path file) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backup = file.resolveSibling("data.json.corrupt-" + stamp);
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            DebugLogger.warn(MODULE, "损坏文件已备份: %s", backup);
        } catch (IOException ex) {
            LOGGER.error("备份损坏的状态数据文件失败", ex);
        }
    }

    static void save(MinecraftServer server) {
        writeJson(getDataFile(server), serializeRoot(), CACHE.size(), SNAPSHOTS.size());
    }

    /**
     * 周期性落盘：序列化仍在服务端线程完成（读取实时的 {@code CACHE} / {@code SNAPSHOTS}，
     * 必须与 tick 同线程以保证快照一致），仅把<b>阻塞写盘</b>挪到后台单线程。
     * <p>
     * 原实现每 6000 tick 在 {@code END_SERVER_TICK} 里同步执行
     * {@code GSON.toJson(全部玩家 + 365 天快照)} 加 {@code Files.writeString}，
     * 玩家基数大时是一次可感知的周期性卡顿（每 5 分钟一次尖刺）。
     * </p>
     * <p>
     * 顺序保证：executor 为单线程 FIFO；关服时先 {@link #shutdownIoExecutor()}
     * 等待队列排空，再执行同步 {@link #save}，因此不存在「旧数据后落盘覆盖新数据」的竞态。
     * </p>
     */
    static void saveAsync(MinecraftServer server) {
        String json = serializeRoot();
        Path target = getDataFile(server);
        // 统计数量在本线程读取后按值传入：CACHE / SNAPSHOTS 由服务端线程持续改写，
        // 后台线程直接读取会构成数据竞争
        int playerCount = CACHE.size();
        int snapshotCount = SNAPSHOTS.size();
        try {
            IO_EXECUTOR.execute(() -> writeJson(target, json, playerCount, snapshotCount));
        } catch (RejectedExecutionException e) {
            // executor 已关闭（关服流程中）：退化为同步写，绝不丢数据
            writeJson(target, json, playerCount, snapshotCount);
        }
    }

    /** 把当前缓存序列化为 JSON 字符串（调用方须在服务端线程）。 */
    private static String serializeRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("players", CACHE);

        // 保存快照
        Map<String, Map<String, Map<String, Long>>> snapshotsData = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, Map<String, Map<String, Long>>> se : SNAPSHOTS.entrySet()) {
            snapshotsData.put(se.getKey().toString(), se.getValue());
        }
        root.put("snapshots", snapshotsData);
        return GSON.toJson(root);
    }

    /** 实际写盘。可能运行在后台 IO 线程，因此不读取任何共享可变状态。 */
    private static void writeJson(Path target, String json, int playerCount, int snapshotCount) {
        try {
            Files.writeString(target, json);
            DebugLogger.info(MODULE, "状态数据已保存，共 %s 位玩家 + %s 天快照", playerCount, snapshotCount);
        } catch (IOException e) {
            LOGGER.error("保存状态数据失败", e);
        }
    }

    /** 关服时排空并关闭 IO 线程，确保所有异步写盘落地后再做最终同步保存。 */
    private static void shutdownIoExecutor() {
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warn("状态数据异步写盘未在 10 秒内完成，继续执行最终保存");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void scanOfflineStats(MinecraftServer server) {
        Path statsDir = server.getWorldPath(LevelResource.ROOT).resolve("stats");
        if (!Files.isDirectory(statsDir)) {
            DebugLogger.info(MODULE, "stats 目录不存在: %s", statsDir);
            return;
        }
        int added = 0;
        try (Stream<Path> files = Files.list(statsDir)) {
            List<Path> statFiles = files.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
            for (Path statFile : statFiles) {
                String fileName = statFile.getFileName().toString();
                String uuidStr = fileName.substring(0, fileName.length() - 5);
                if (CACHE.containsKey(uuidStr))
                    continue;

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                PlayerStats playerStats = new PlayerStats(uuid, uuidStr.substring(0, 8));

                try (Reader reader = Files.newBufferedReader(statFile)) {
                    com.google.gson.JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    com.google.gson.JsonObject statsObj = root.getAsJsonObject("stats");

                    // 读取 minecraft:custom 节
                    com.google.gson.JsonObject custom = statsObj.getAsJsonObject("minecraft:custom");
                    if (custom != null) {
                        for (MetricDef metric : METRICS) {
                            if (metric.isAggregate)
                                continue;
                            String mcKey = "minecraft:" + metric.statId.getPath();
                            JsonElement elem = custom.get(mcKey);
                            if (elem != null) {
                                playerStats.set(metric.key, elem.getAsLong());
                            }
                        }
                    }

                    // 读取 minecraft:used 节 → 红石
                    com.google.gson.JsonObject used = statsObj.getAsJsonObject("minecraft:used");
                    if (used != null) {
                        long rs = 0L;
                        for (Map.Entry<String, JsonElement> ue : used.entrySet()) {
                            if (isRedstoneItemId(ue.getKey())) {
                                rs += ue.getValue().getAsLong();
                            }
                        }
                        if (rs > 0) {
                            playerStats.set("redstone_placed", rs);
                        }
                    }
                } catch (Exception e) {
                    DebugLogger.warn(MODULE, "读取玩家统计文件失败: %s - %s", statFile, e.getMessage());
                }

                CACHE.put(uuidStr, playerStats);
                added++;
            }
        } catch (IOException e) {
            LOGGER.error("扫描 stats 目录失败", e);
        }
        DebugLogger.info(MODULE, "新增 %s 位离线玩家统计", added);
    }

    private static boolean isRedstoneItemId(String itemId) {
        if (REDSTONE_ITEMS == null)
            return false;
        int colon = itemId.indexOf(':');
        String path = (colon >= 0) ? itemId.substring(colon + 1) : itemId;
        return REDSTONE_COMPONENTS.contains(path)
                || path.endsWith("_button")
                || path.endsWith("_pressure_plate")
                || path.endsWith("_door")
                || path.endsWith("_trapdoor")
                || path.endsWith("_fence_gate")
                || path.endsWith("_bulb");
    }

    static void refreshPlayer(MinecraftServer server, ServerPlayer player) {
        String uuidStr = player.getUUID().toString();
        PlayerStats stats = CACHE.computeIfAbsent(uuidStr,
                k -> new PlayerStats(player.getUUID(), player.getName().getString()));

        stats.setLastUpdated(System.currentTimeMillis());

        String onlineName = player.getName().getString();
        if (!stats.getName().equals(onlineName)) {
            PlayerStats updated = new PlayerStats(player.getUUID(), onlineName,
                    stats.getLastUpdated(), stats.getStats());
            CACHE.put(uuidStr, updated);
            stats = updated;
        }

        for (MetricDef metric : METRICS) {
            try {
                long value = metric.reader.applyAsLong(player);
                stats.set(metric.key, value);
            } catch (Exception e) {
                DebugLogger.warn(MODULE, "读取统计 %s 失败: %s", metric.key, e.getMessage());
            }
        }
    }

    private static void refreshAllOnline(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshPlayer(server, player);
        }
        DebugLogger.info(MODULE, "已刷新 %s 位在线玩家统计", server.getPlayerList().getPlayers().size());
    }

    // ==================== 格式化方法 ====================

    private static String formatPlayTime(long ticks) {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0)
            return hours + "h " + minutes + "m " + secs + "s";
        if (minutes > 0)
            return minutes + "m " + secs + "s";
        return secs + "s";
    }

    private static String formatDamage(long value) {
        return String.format(Locale.ROOT, "%,.1f", value / 10.0);
    }

    private static String formatDistance(long cm) {
        if (cm >= 100000)
            return String.format(Locale.ROOT, "%,.2f km", cm / 100000.0);
        if (cm >= 100)
            return String.format(Locale.ROOT, "%,.1f m", cm / 100.0);
        return cm + " cm";
    }

    private static String formatRaw(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    // ==================== 内部类型 ====================

    static final class MetricDef {
        final String key;
        final String label;
        final boolean isAggregate;
        final Identifier statId;
        final ToLongFunction<ServerPlayer> reader;
        final Function<Long, String> formatter;

        private MetricDef(String key, String label, Identifier statId,
                ToLongFunction<ServerPlayer> reader,
                Function<Long, String> formatter,
                boolean isAggregate) {
            this.key = key;
            this.label = label;
            this.statId = statId;
            this.reader = reader;
            this.formatter = formatter;
            this.isAggregate = isAggregate;
        }

        static MetricDef custom(String key, String label, Identifier statId,
                Function<Long, String> formatter) {
            ToLongFunction<ServerPlayer> reader = p -> p.getStats().getValue(Stats.CUSTOM.get(statId));
            return new MetricDef(key, label, statId, reader, formatter, false);
        }

        static MetricDef aggregate(String key, String label,
                Function<Long, String> formatter,
                ToLongFunction<ServerPlayer> reader) {
            return new MetricDef(key, label, null, reader, formatter, true);
        }
    }

    static final class PlayerStats {
        private UUID uuid;
        private String name;
        private long lastUpdated;
        private Map<String, Long> stats;

        PlayerStats() {
        }

        PlayerStats(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
            this.lastUpdated = System.currentTimeMillis();
            this.stats = new HashMap<>();
        }

        PlayerStats(UUID uuid, String name, long lastUpdated, Map<String, Long> stats) {
            this.uuid = uuid;
            this.name = name;
            this.lastUpdated = lastUpdated;
            this.stats = new HashMap<>(stats);
        }

        UUID getUuid() {
            return uuid;
        }

        String getName() {
            return name;
        }

        long getLastUpdated() {
            return lastUpdated;
        }

        Map<String, Long> getStats() {
            return stats;
        }

        void setLastUpdated(long lastUpdated) {
            this.lastUpdated = lastUpdated;
        }

        void setUuid(UUID uuid) {
            this.uuid = uuid;
        }

        void setName(String name) {
            this.name = name;
        }

        void setStats(Map<String, Long> stats) {
            this.stats = stats;
        }

        long get(String key) {
            return stats.getOrDefault(key, 0L);
        }

        void set(String key, long value) {
            stats.put(key, value);
        }
    }
}
