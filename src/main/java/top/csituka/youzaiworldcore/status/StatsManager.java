package top.csituka.youzaiworldcore.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.api.ApiServiceClient;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 玩家统计管理器。
 * <p>
 * 从原版统计系统读取累计值，按 UUID 绑定游戏账户并定期上传到 Api。
 * 本地保留最新副本，并在月度重置期间保留一个待上传批次；不再维护排行榜导出文件。
 * </p>
 */
@SuppressWarnings("null")
public final class StatsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/StatsManager");
    private static final String MODULE = "StatsManager";
    /** 统计自动上传周期：6 小时（20 tick/秒）。 */
    private static final int REFRESH_INTERVAL_TICKS = 432000;
    private static final int UPLOAD_BATCH_SIZE = 400;

    /** 本地快照写盘线程；Api 上传使用 CompletableFuture 的异步线程。 */
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "YZWC-Stats-IO");
        thread.setDaemon(true);
        return thread;
    });

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentHashMap<String, PlayerStats> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean UPLOAD_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean MONTHLY_RESET_UPLOAD_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile String lastResetMonth;
    private static volatile PendingMonthlyReset pendingMonthlyReset;

    /** 仅统计原版红石相关物品，避免同路径模组物品被误计入。 */
    private static final Set<String> REDSTONE_COMPONENTS = Set.of(
            "redstone", "redstone_torch", "repeater", "comparator", "observer",
            "piston", "sticky_piston", "dispenser", "dropper", "hopper", "lever",
            "tripwire_hook", "target", "daylight_detector", "note_block",
            "redstone_block", "sculk_sensor", "calibrated_sculk_sensor",
            "lightning_rod", "trapped_chest", "powered_rail", "detector_rail",
            "activator_rail", "rail", "redstone_lamp", "tnt", "crafter");

    private static volatile Set<Item> REDSTONE_ITEMS;
    private static int tickCounter;
    private static volatile boolean initialized;

    private static final List<MetricDef> METRICS = List.of(
            MetricDef.custom("play_time", "在线时间", Stats.PLAY_TIME, StatsManager::formatPlayTime),
            MetricDef.custom("total_world_time", "世界时间", Stats.TOTAL_WORLD_TIME, StatsManager::formatPlayTime),
            MetricDef.custom("time_since_death", "距上次死亡", Stats.TIME_SINCE_DEATH, StatsManager::formatPlayTime),
            MetricDef.custom("time_since_rest", "距上次休息", Stats.TIME_SINCE_REST, StatsManager::formatPlayTime),
            MetricDef.custom("sneak_time", "潜行时间", Stats.CROUCH_TIME, StatsManager::formatPlayTime),
            MetricDef.custom("jumps", "跳跃次数", Stats.JUMP, StatsManager::formatRaw),
            MetricDef.custom("deaths", "死亡次数", Stats.DEATHS, StatsManager::formatRaw),
            MetricDef.custom("mob_kills", "击杀怪物", Stats.MOB_KILLS, StatsManager::formatRaw),
            MetricDef.custom("player_kills", "击杀玩家", Stats.PLAYER_KILLS, StatsManager::formatRaw),
            MetricDef.custom("damage_dealt", "造成伤害", Stats.DAMAGE_DEALT, StatsManager::formatDamage),
            MetricDef.custom("damage_dealt_absorbed", "造成且被吸收的伤害", Stats.DAMAGE_DEALT_ABSORBED,
                    StatsManager::formatDamage),
            MetricDef.custom("damage_dealt_resisted", "造成且被抵抗的伤害", Stats.DAMAGE_DEALT_RESISTED,
                    StatsManager::formatDamage),
            MetricDef.custom("damage_blocked_by_shield", "盾牌格挡伤害", Stats.DAMAGE_BLOCKED_BY_SHIELD,
                    StatsManager::formatDamage),
            MetricDef.custom("damage_absorbed", "吸收伤害", Stats.DAMAGE_ABSORBED, StatsManager::formatDamage),
            MetricDef.custom("damage_resisted", "抵抗伤害", Stats.DAMAGE_RESISTED, StatsManager::formatDamage),
            MetricDef.custom("damage_taken", "受到伤害", Stats.DAMAGE_TAKEN, StatsManager::formatDamage),
            MetricDef.custom("walk_cm", "步行距离", Stats.WALK_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("crouch_cm", "潜行距离", Stats.CROUCH_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("sprint_cm", "疾跑距离", Stats.SPRINT_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("walk_on_water_cm", "水面行走距离", Stats.WALK_ON_WATER_ONE_CM,
                    StatsManager::formatDistance),
            MetricDef.custom("walk_under_water_cm", "水下行走距离", Stats.WALK_UNDER_WATER_ONE_CM,
                    StatsManager::formatDistance),
            MetricDef.custom("climb_cm", "攀爬距离", Stats.CLIMB_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("fly_cm", "飞行距离", Stats.FLY_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("swim_cm", "游泳距离", Stats.SWIM_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("aviate_cm", "鞘翅飞行距离", Stats.AVIATE_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("minecart_cm", "矿车移动距离", Stats.MINECART_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("boat_cm", "船移动距离", Stats.BOAT_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("pig_cm", "骑猪移动距离", Stats.PIG_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("happy_ghast_cm", "骑快乐恶魂移动距离", Stats.HAPPY_GHAST_ONE_CM,
                    StatsManager::formatDistance),
            MetricDef.custom("horse_cm", "骑马移动距离", Stats.HORSE_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("strider_cm", "骑炽足兽移动距离", Stats.STRIDER_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("nautilus_cm", "骑鹦鹉螺移动距离", Stats.NAUTILUS_ONE_CM,
                    StatsManager::formatDistance),
            MetricDef.custom("fall_cm", "坠落距离", Stats.FALL_ONE_CM, StatsManager::formatDistance),
            MetricDef.custom("fish_caught", "钓鱼数量", Stats.FISH_CAUGHT, StatsManager::formatRaw),
            MetricDef.custom("talked_to_villager", "与村民交谈", Stats.TALKED_TO_VILLAGER, StatsManager::formatRaw),
            MetricDef.custom("traded", "村民交易次数", Stats.TRADED_WITH_VILLAGER, StatsManager::formatRaw),
            MetricDef.custom("items_dropped", "丢弃物品", Stats.DROP, StatsManager::formatRaw),
            MetricDef.custom("sleep_in_bed", "睡觉次数", Stats.SLEEP_IN_BED, StatsManager::formatRaw),
            MetricDef.custom("enchanted", "附魔次数", Stats.ENCHANT_ITEM, StatsManager::formatRaw),
            MetricDef.custom("fill_cauldron", "填充炼药锅", Stats.FILL_CAULDRON, StatsManager::formatRaw),
            MetricDef.custom("use_cauldron", "使用炼药锅", Stats.USE_CAULDRON, StatsManager::formatRaw),
            MetricDef.custom("clean_armor", "清洗盔甲", Stats.CLEAN_ARMOR, StatsManager::formatRaw),
            MetricDef.custom("clean_banner", "清洗旗帜", Stats.CLEAN_BANNER, StatsManager::formatRaw),
            MetricDef.custom("clean_shulker_box", "清洗潜影盒", Stats.CLEAN_SHULKER_BOX, StatsManager::formatRaw),
            MetricDef.custom("interact_with_brewingstand", "使用酿造台", Stats.INTERACT_WITH_BREWINGSTAND,
                    StatsManager::formatRaw),
            MetricDef.custom("interact_with_beacon", "使用信标", Stats.INTERACT_WITH_BEACON, StatsManager::formatRaw),
            MetricDef.custom("inspect_dropper", "检查投掷器", Stats.INSPECT_DROPPER, StatsManager::formatRaw),
            MetricDef.custom("inspect_hopper", "检查漏斗", Stats.INSPECT_HOPPER, StatsManager::formatRaw),
            MetricDef.custom("inspect_dispenser", "检查发射器", Stats.INSPECT_DISPENSER, StatsManager::formatRaw),
            MetricDef.custom("play_noteblock", "播放音符盒", Stats.PLAY_NOTEBLOCK, StatsManager::formatRaw),
            MetricDef.custom("tune_noteblock", "调整音符盒音调", Stats.TUNE_NOTEBLOCK, StatsManager::formatRaw),
            MetricDef.custom("pot_flower", "给花盆放置植物", Stats.POT_FLOWER, StatsManager::formatRaw),
            MetricDef.custom("trigger_trapped_chest", "触发陷阱箱", Stats.TRIGGER_TRAPPED_CHEST,
                    StatsManager::formatRaw),
            MetricDef.custom("open_enderchest", "打开末影箱", Stats.OPEN_ENDERCHEST, StatsManager::formatRaw),
            MetricDef.custom("open_shulker_box", "打开潜影盒", Stats.OPEN_SHULKER_BOX, StatsManager::formatRaw),
            MetricDef.custom("open_barrel", "打开木桶", Stats.OPEN_BARREL, StatsManager::formatRaw),
            MetricDef.custom("play_record", "播放唱片", Stats.PLAY_RECORD, StatsManager::formatRaw),
            MetricDef.custom("interact_with_furnace", "使用熔炉", Stats.INTERACT_WITH_FURNACE, StatsManager::formatRaw),
            MetricDef.custom("interact_with_crafting_table", "使用工作台", Stats.INTERACT_WITH_CRAFTING_TABLE,
                    StatsManager::formatRaw),
            MetricDef.custom("open_chest", "打开普通箱子", Stats.OPEN_CHEST, StatsManager::formatRaw),
            MetricDef.custom("interact_with_blast_furnace", "使用高炉", Stats.INTERACT_WITH_BLAST_FURNACE,
                    StatsManager::formatRaw),
            MetricDef.custom("interact_with_smoker", "使用烟熏炉", Stats.INTERACT_WITH_SMOKER, StatsManager::formatRaw),
            MetricDef.custom("interact_with_lectern", "使用讲台", Stats.INTERACT_WITH_LECTERN, StatsManager::formatRaw),
            MetricDef.custom("interact_with_campfire", "使用营火", Stats.INTERACT_WITH_CAMPFIRE, StatsManager::formatRaw),
            MetricDef.custom("interact_with_cartography_table", "使用制图台", Stats.INTERACT_WITH_CARTOGRAPHY_TABLE,
                    StatsManager::formatRaw),
            MetricDef.custom("interact_with_loom", "使用织布机", Stats.INTERACT_WITH_LOOM, StatsManager::formatRaw),
            MetricDef.custom("interact_with_stonecutter", "使用切石机", Stats.INTERACT_WITH_STONECUTTER,
                    StatsManager::formatRaw),
            MetricDef.custom("raid_trigger", "触发袭击", Stats.RAID_TRIGGER, StatsManager::formatRaw),
            MetricDef.custom("target_hit", "命中靶子", Stats.TARGET_HIT, StatsManager::formatRaw),
            MetricDef.custom("raid_wins", "袭击胜利", Stats.RAID_WIN, StatsManager::formatRaw),
            MetricDef.custom("animals_bred", "繁殖动物", Stats.ANIMALS_BRED, StatsManager::formatRaw),
            MetricDef.custom("bell_ring", "敲钟次数", Stats.BELL_RING, StatsManager::formatRaw),
            MetricDef.custom("cake_eaten", "吃蛋糕", Stats.EAT_CAKE_SLICE, StatsManager::formatRaw),
            MetricDef.custom("interact_with_anvil", "使用铁砧", Stats.INTERACT_WITH_ANVIL, StatsManager::formatRaw),
            MetricDef.custom("interact_with_grindstone", "使用砂轮", Stats.INTERACT_WITH_GRINDSTONE,
                    StatsManager::formatRaw),
            MetricDef.custom("interact_with_smithing_table", "使用锻造台", Stats.INTERACT_WITH_SMITHING_TABLE,
                    StatsManager::formatRaw),
            MetricDef.aggregate("redstone_placed", "红石相关物品使用", StatsManager::formatRaw,
                    StatsManager::readRedstonePlaced));

    private StatsManager() {
    }

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
            initializeMonthlyReset(server);
            DebugLogger.info(MODULE, "状态数据加载完成，共 %s 位玩家，%s 个红石相关物品",
                    CACHE.size(), REDSTONE_ITEMS == null ? 0 : REDSTONE_ITEMS.size());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DebugLogger.info(MODULE, "服务器停止，保存最新状态副本...");
            refreshAllOnline(server);
            flushIoExecutor();
            save(server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer player) {
                refreshPlayer(server, player);
                saveAsync(server);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            checkMonthlyReset(server);
            if (++tickCounter >= REFRESH_INTERVAL_TICKS) {
                tickCounter = 0;
                if (pendingMonthlyReset != null) return;
                refreshAllOnline(server);
                saveAsync(server);
                uploadAsync(server);
            }
        });

        CommandRegistrationCallback.EVENT.register(StatsManager::registerCommands);
        initialized = true;
        DebugLogger.exiting(MODULE, "initialize");
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext ignored,
            Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("status")
                        .then(Commands.literal("upload")
                                // MCSM 通过服务器后台控制台执行；玩家不能手动触发上传。
                                .requires(src -> !src.isPlayer())
                                .executes(StatsManager::executeUpload))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("list")
                                        .requires(src -> LuckPermsHelper.checkPermission(
                                                src, LuckPermsHelper.PERMISSION_STATUS_QUERY, Commands.LEVEL_ADMINS))
                                        .executes(ctx -> executeList(ctx, EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int executeUpload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (source.isPlayer()) {
            source.sendFailure(Component.literal("统计上传命令仅可由服务器后台执行。"));
            return 0;
        }

        MinecraftServer server = source.getServer();
        // 手动触发也要先处理跨月状态，确保重置快照优先上传且不会与增量批次并发。
        checkMonthlyReset(server);
        if (pendingMonthlyReset == null) {
            refreshAllOnline(server);
            saveAsync(server);
            uploadAsync(server);
        } else {
            uploadPendingMonthlyReset(server);
        }
        source.sendSuccess(() -> Component.literal("已触发统计上传；没有变化的数据不会重复发送。"), false);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        refreshPlayer(server, target);
        PlayerStats stats = CACHE.get(target.getUUID().toString());
        if (stats == null) {
            source.sendFailure(Component.literal("该玩家暂无统计数据。"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== " + target.getName().getString() + " 的统计 ==="), false);
        for (MetricDef metric : METRICS) {
            String formatted = metric.formatter.apply(stats.get(metric.key));
            source.sendSuccess(() -> Component.literal("  " + metric.label + ": " + formatted), false);
        }
        return METRICS.size();
    }

    // ===== Api 上传 =====

    private static void initializeMonthlyReset(MinecraftServer server) {
        if (pendingMonthlyReset != null) {
            uploadPendingMonthlyReset(server);
            return;
        }
        String currentMonth = YearMonth.now().toString();
        if (lastResetMonth == null || lastResetMonth.isBlank()) {
            // 新安装或旧版数据首次升级时建立基线，不立即清除已有统计。
            lastResetMonth = currentMonth;
            save(server);
            return;
        }
        if (!lastResetMonth.equals(currentMonth)) beginMonthlyReset(server);
    }

    private static void checkMonthlyReset(MinecraftServer server) {
        if (pendingMonthlyReset != null) {
            uploadPendingMonthlyReset(server);
            return;
        }
        if (lastResetMonth == null || lastResetMonth.isBlank()) {
            lastResetMonth = YearMonth.now().toString();
            return;
        }
        if (!lastResetMonth.equals(YearMonth.now().toString())) beginMonthlyReset(server);
    }

    private static void beginMonthlyReset(MinecraftServer server) {
        if (pendingMonthlyReset != null || MONTHLY_RESET_UPLOAD_IN_FLIGHT.get() || UPLOAD_IN_FLIGHT.get()) return;
        refreshAllOnline(server);
        String month = YearMonth.now().toString();
        String resetId = month + "-" + UUID.randomUUID();
        pendingMonthlyReset = new PendingMonthlyReset(resetId, month, snapshotForMonthlyReset());
        resetLocalStatistics(server);
        // 先持久化待上传批次，再开始网络请求；崩溃后可用同一 resetId 安全重试。
        save(server);
        uploadPendingMonthlyReset(server);
    }

    private static void uploadPendingMonthlyReset(MinecraftServer server) {
        PendingMonthlyReset pending = pendingMonthlyReset;
        if (pending == null || !MONTHLY_RESET_UPLOAD_IN_FLIGHT.compareAndSet(false, true)) return;
        CompletableFuture.supplyAsync(() -> uploadBatches(pending.entries(), "reset", pending.resetId()))
                .whenComplete((success, error) -> {
                    MONTHLY_RESET_UPLOAD_IN_FLIGHT.set(false);
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        DebugLogger.warn(MODULE, "月度统计重置批次上传失败，将重试: %s",
                                error == null ? "Api 返回失败" : error.getMessage());
                        return;
                    }
                    server.execute(() -> {
                        if (pendingMonthlyReset != pending) return;
                        pendingMonthlyReset = null;
                        lastResetMonth = pending.month();
                        save(server);
                        DebugLogger.info(MODULE, "月度统计已重置并完成云端累计，月份 %s", pending.month());
                    });
                });
    }

    private static void uploadAsync(MinecraftServer server) {
        if (!UPLOAD_IN_FLIGHT.compareAndSet(false, true)) {
            DebugLogger.debug(MODULE, "上一次统计上传尚未完成，跳过本轮");
            return;
        }
        List<PendingStatsUpload> pending = snapshotForUpload();
        if (pending.isEmpty()) {
            UPLOAD_IN_FLIGHT.set(false);
            return;
        }
        List<ApiServiceClient.StatsUploadEntry> entries = pending.stream()
                .map(PendingStatsUpload::entry)
                .toList();

        CompletableFuture.supplyAsync(() -> uploadBatches(entries))
                .whenComplete((success, error) -> {
                    UPLOAD_IN_FLIGHT.set(false);
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        DebugLogger.warn(MODULE, "统计上传失败，将在下一周期重试: %s",
                                error == null ? "Api 返回失败" : error.getMessage());
                        return;
                    }
                    DebugLogger.info(MODULE, "统计增量上传成功，共 %s 位玩家", entries.size());
                    server.execute(() -> {
                        markUploaded(pending);
                        saveAsync(server);
                    });
                });
    }

    private static boolean uploadBatches(List<ApiServiceClient.StatsUploadEntry> entries) {
        return uploadBatches(entries, "delta", null);
    }

    private static boolean uploadBatches(List<ApiServiceClient.StatsUploadEntry> entries, String mode, String resetId) {
        for (int start = 0; start < entries.size(); start += UPLOAD_BATCH_SIZE) {
            int end = Math.min(entries.size(), start + UPLOAD_BATCH_SIZE);
            ApiServiceClient.StatsUploadResult result = ApiServiceClient.uploadStats(
                    entries.subList(start, end), mode, resetId);
            if (!result.success() || result.accepted() != end - start) {
                DebugLogger.warn(MODULE, "统计批次上传失败: HTTP %s, %s", result.statusCode(), result.message());
                return false;
            }
        }
        return true;
    }

    private static List<PendingStatsUpload> snapshotForUpload() {
        List<PendingStatsUpload> result = new ArrayList<>();
        for (PlayerStats player : CACHE.values()) {
            Map<String, Long> delta = player.getUploadDelta();
            if (delta == null) continue;
            result.add(new PendingStatsUpload(new ApiServiceClient.StatsUploadEntry(
                    player.getUuid(), player.getName(), player.getLastUpdated(), delta)));
        }
        return result;
    }

    private static List<ApiServiceClient.StatsUploadEntry> snapshotForMonthlyReset() {
        List<ApiServiceClient.StatsUploadEntry> result = new ArrayList<>();
        for (PlayerStats player : CACHE.values()) {
            result.add(new ApiServiceClient.StatsUploadEntry(
                    player.getUuid(), player.getName(), player.getLastUpdated(),
                    new LinkedHashMap<>(player.getStats())));
        }
        return result;
    }

    private static void markUploaded(List<PendingStatsUpload> pending) {
        for (PendingStatsUpload upload : pending) {
            ApiServiceClient.StatsUploadEntry entry = upload.entry();
            PlayerStats player = CACHE.get(entry.uuid().toString());
            if (player != null) player.markUploaded(entry.username(), entry.stats());
        }
    }

    // ===== 红石统计 =====

    private static void buildRedstoneItems() {
        if (REDSTONE_ITEMS != null) return;
        Set<Item> items = BuiltInRegistries.ITEM.stream()
                .filter(StatsManager::isRedstoneComponent)
                .collect(Collectors.toSet());
        REDSTONE_ITEMS = Collections.unmodifiableSet(items);
    }

    static boolean isRedstoneComponent(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (!"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return REDSTONE_COMPONENTS.contains(path)
                || path.endsWith("_button")
                || path.endsWith("_pressure_plate");
    }

    private static long readRedstonePlaced(ServerPlayer player) {
        Set<Item> items = REDSTONE_ITEMS;
        if (items == null) return 0L;
        long total = 0L;
        for (Item item : items) {
            total += player.getStats().getValue(Stats.ITEM_USED.get(item));
        }
        return total;
    }

    private static void resetLocalStatistics(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            resetVanillaStats(player);
            try {
                player.getStats().save();
            } catch (RuntimeException e) {
                DebugLogger.warn(MODULE, "保存玩家 %s 的原版统计失败: %s", player.getName().getString(), e.getMessage());
            }
        }
        resetOfflineStatsFiles(server);
        for (PlayerStats player : CACHE.values()) player.resetForNewMonth();
    }

    private static void resetVanillaStats(ServerPlayer player) {
        resetStatType(player, Stats.BLOCK_MINED);
        resetStatType(player, Stats.ITEM_CRAFTED);
        resetStatType(player, Stats.ITEM_USED);
        resetStatType(player, Stats.ITEM_BROKEN);
        resetStatType(player, Stats.ITEM_PICKED_UP);
        resetStatType(player, Stats.ITEM_DROPPED);
        resetStatType(player, Stats.ENTITY_KILLED);
        resetStatType(player, Stats.ENTITY_KILLED_BY);
        resetStatType(player, Stats.CUSTOM);
    }

    private static <T> void resetStatType(ServerPlayer player, StatType<T> type) {
        type.getRegistry().stream().forEach(value -> player.resetStat(type.get(value)));
    }

    private static void resetOfflineStatsFiles(MinecraftServer server) {
        Path statsDir = server.getWorldPath(LevelResource.ROOT).resolve("stats");
        if (!Files.isDirectory(statsDir)) return;
        try (Stream<Path> files = Files.list(statsDir)) {
            for (Path statFile : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                try (Reader reader = Files.newBufferedReader(statFile)) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (!root.isJsonObject()) continue;
                    JsonObject statsRoot = root.getAsJsonObject().getAsJsonObject("stats");
                    if (statsRoot == null) continue;
                    zeroJsonNumbers(statsRoot);
                    Path temporary = statFile.resolveSibling(statFile.getFileName() + ".tmp");
                    Files.writeString(temporary, GSON.toJson(root));
                    try {
                        Files.move(temporary, statFile, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(temporary, statFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    DebugLogger.warn(MODULE, "重置原版统计文件失败: %s - %s", statFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("扫描原版统计目录以执行月度重置失败", e);
        }
    }

    private static void zeroJsonNumbers(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    element.getAsJsonObject().addProperty(entry.getKey(), 0);
                } else {
                    zeroJsonNumbers(entry.getValue());
                }
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) zeroJsonNumbers(child);
        }
    }

    // ===== 本地数据 =====

    static Path getDataRoot(MinecraftServer server) {
        return top.csituka.youzaiworldcore.config.ModPaths.worldData(
                server, top.csituka.youzaiworldcore.config.GlobalSettings.STATUS_MODULE);
    }

    private static void createDataDir(MinecraftServer server) {
        try {
            Files.createDirectories(getDataRoot(server));
        } catch (IOException e) {
            LOGGER.error("创建状态数据目录失败", e);
        }
    }

    private static Path getDataFile(MinecraftServer server) {
        return getDataRoot(server).resolve("data.json");
    }

    @SuppressWarnings("unchecked")
    static void load(MinecraftServer server) {
        Path file = getDataFile(server);
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> raw = GSON.fromJson(reader, Map.class);
            if (raw == null) return;
            Object resetMonth = raw.get("lastResetMonth");
            lastResetMonth = resetMonth == null ? null : String.valueOf(resetMonth);
            pendingMonthlyReset = parsePendingMonthlyReset(raw.get("pendingMonthlyReset"));
            Object playersObj = raw.get("players");
            Map<String, Map<String, Object>> players = new HashMap<>();
            if (playersObj instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> value) {
                        players.put(entry.getKey().toString(), (Map<String, Object>) value);
                    }
                }
            } else {
                // 兼容旧版以 UUID 为顶层键的文件格式；旧 snapshots 字段会被忽略。
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    if (!"version".equals(entry.getKey()) && !"snapshots".equals(entry.getKey())
                            && entry.getValue() instanceof Map<?, ?> value) {
                        players.put(entry.getKey(), (Map<String, Object>) value);
                    }
                }
            }
            for (Map.Entry<String, Map<String, Object>> entry : players.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    Map<String, Object> data = entry.getValue();
                    String name = String.valueOf(data.getOrDefault("name", uuid.toString().substring(0, 8)));
                    long lastUpdated = numberValue(data.get("lastUpdated"), 0L);
                    Map<String, Long> stats = new HashMap<>();
                    if (data.get("stats") instanceof Map<?, ?> rawStats) {
                        for (Map.Entry<?, ?> stat : rawStats.entrySet()) {
                            stats.put(stat.getKey().toString(), numberValue(stat.getValue(), 0L));
                        }
                    }
                    Map<String, Long> uploadedStats = new HashMap<>();
                    if (data.get("uploadedStats") instanceof Map<?, ?> rawUploadedStats) {
                        for (Map.Entry<?, ?> stat : rawUploadedStats.entrySet()) {
                            uploadedStats.put(stat.getKey().toString(), numberValue(stat.getValue(), 0L));
                        }
                    }
                    boolean uploadInitialized = Boolean.TRUE.equals(data.get("uploadInitialized"));
                    String uploadedName = data.get("uploadedName") == null
                            ? null : String.valueOf(data.get("uploadedName"));
                    CACHE.put(entry.getKey(), new PlayerStats(uuid, name, lastUpdated, stats,
                            uploadedStats, uploadedName, uploadInitialized));
                } catch (RuntimeException e) {
                    DebugLogger.warn(MODULE, "解析玩家 %s 数据失败: %s", entry.getKey(), e.getMessage());
                }
            }
            DebugLogger.info(MODULE, "已加载 %s 位玩家状态", CACHE.size());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("读取状态数据文件失败: {}", file, e);
            backupCorruptFile(server, file);
        }
    }

    private static long numberValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    /** 扫描并以原版 stats 文件校准离线缓存，避免旧缓存遮蔽权威数据。 */
    static void scanOfflineStats(MinecraftServer server) {
        Path statsDir = server.getWorldPath(LevelResource.ROOT).resolve("stats");
        if (!Files.isDirectory(statsDir)) return;
        try (Stream<Path> files = Files.list(statsDir)) {
            for (Path statFile : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                String fileName = statFile.getFileName().toString();
                String uuidText = fileName.substring(0, fileName.length() - 5);
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidText);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                PlayerStats previous = CACHE.get(uuidText);
                PlayerStats parsed = previous == null
                        ? new PlayerStats(uuid, uuidText.substring(0, 8))
                        : new PlayerStats(uuid, previous.getName(), previous.getLastUpdated(), new HashMap<>(),
                                previous.getUploadedStats(), previous.getUploadedName(), previous.isUploadInitialized());
                try (Reader reader = Files.newBufferedReader(statFile)) {
                    var root = JsonParser.parseReader(reader).getAsJsonObject();
                    var statsObject = root.getAsJsonObject("stats");
                    if (statsObject == null) continue;
                    var custom = statsObject.getAsJsonObject("minecraft:custom");
                    if (custom != null) {
                        for (MetricDef metric : METRICS) {
                            if (metric.isAggregate) continue;
                            JsonElement value = custom.get("minecraft:" + metric.statId.getPath());
                            if (value != null && value.isJsonPrimitive()) parsed.set(metric.key, value.getAsLong());
                        }
                    }
                    var used = statsObject.getAsJsonObject("minecraft:used");
                    if (used != null) {
                        long redstone = 0L;
                        for (Map.Entry<String, JsonElement> entry : used.entrySet()) {
                            if (isRedstoneItemId(entry.getKey())) redstone += entry.getValue().getAsLong();
                        }
                        parsed.set("redstone_placed", redstone);
                    }
                    parsed.setLastUpdated(Files.getLastModifiedTime(statFile).toMillis());
                    CACHE.put(uuidText, parsed);
                } catch (Exception e) {
                    DebugLogger.warn(MODULE, "读取玩家统计文件失败: %s - %s", statFile, e.getMessage());
                    if (previous == null) CACHE.put(uuidText, parsed);
                }
            }
        } catch (IOException e) {
            LOGGER.error("扫描 stats 目录失败", e);
        }
    }

    private static boolean isRedstoneItemId(String itemId) {
        int colon = itemId.indexOf(':');
        if (colon >= 0 && !"minecraft".equals(itemId.substring(0, colon))) return false;
        String path = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        return REDSTONE_COMPONENTS.contains(path)
                || path.endsWith("_button")
                || path.endsWith("_pressure_plate");
    }

    static void refreshPlayer(MinecraftServer server, ServerPlayer player) {
        String uuidText = player.getUUID().toString();
        PlayerStats stats = CACHE.computeIfAbsent(uuidText,
                ignored -> new PlayerStats(player.getUUID(), player.getName().getString()));
        stats.setLastUpdated(System.currentTimeMillis());
        stats.setName(player.getName().getString());
        for (MetricDef metric : METRICS) {
            try {
                stats.set(metric.key, metric.reader.applyAsLong(player));
            } catch (Exception e) {
                DebugLogger.warn(MODULE, "读取统计 %s 失败: %s", metric.key, e.getMessage());
            }
        }
    }

    private static void refreshAllOnline(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) refreshPlayer(server, player);
    }

    private static void save(MinecraftServer server) {
        StatsSnapshot snapshot = createSnapshot();
        writeJson(getDataFile(server), serializeRoot(snapshot), snapshot.players().size());
    }

    private static void saveAsync(MinecraftServer server) {
        StatsSnapshot snapshot = createSnapshot();
        Path target = getDataFile(server);
        IO_EXECUTOR.execute(() -> writeJson(target, serializeRoot(snapshot), snapshot.players().size()));
    }

    private static StatsSnapshot createSnapshot() {
        Map<String, PlayerStats> players = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerStats> entry : CACHE.entrySet()) {
            PlayerStats value = entry.getValue();
            players.put(entry.getKey(), new PlayerStats(
                    value.getUuid(), value.getName(), value.getLastUpdated(), value.getStats(),
                    value.getUploadedStats(), value.getUploadedName(), value.isUploadInitialized()));
        }
        return new StatsSnapshot(players);
    }

    private static String serializeRoot(StatsSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 5);
        if (lastResetMonth != null) root.put("lastResetMonth", lastResetMonth);
        PendingMonthlyReset pending = pendingMonthlyReset;
        if (pending != null) root.put("pendingMonthlyReset", serializePendingMonthlyReset(pending));
        root.put("players", snapshot.players());
        return GSON.toJson(root);
    }

    @SuppressWarnings("unchecked")
    private static PendingMonthlyReset parsePendingMonthlyReset(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return null;
        String resetId = raw.get("resetId") == null ? "" : String.valueOf(raw.get("resetId"));
        String month = raw.get("month") == null ? "" : String.valueOf(raw.get("month"));
        if (resetId.isBlank() || month.isBlank() || !(raw.get("entries") instanceof List<?> rawEntries)) return null;
        List<ApiServiceClient.StatsUploadEntry> entries = new ArrayList<>();
        for (Object rawEntry : rawEntries) {
            if (!(rawEntry instanceof Map<?, ?> entry)) continue;
            try {
                UUID uuid = UUID.fromString(String.valueOf(entry.get("uuid")));
                String username = entry.get("username") == null ? "" : String.valueOf(entry.get("username"));
                long lastUpdated = numberValue(entry.get("lastUpdated"), 0L);
                Map<String, Long> stats = new LinkedHashMap<>();
                if (entry.get("stats") instanceof Map<?, ?> rawStats) {
                    for (Map.Entry<?, ?> stat : rawStats.entrySet()) {
                        stats.put(String.valueOf(stat.getKey()), numberValue(stat.getValue(), 0L));
                    }
                }
                entries.add(new ApiServiceClient.StatsUploadEntry(uuid, username, lastUpdated, stats));
            } catch (RuntimeException ignored) {
                DebugLogger.warn(MODULE, "忽略损坏的月度统计重置条目");
            }
        }
        return new PendingMonthlyReset(resetId, month, entries);
    }

    private static Map<String, Object> serializePendingMonthlyReset(PendingMonthlyReset pending) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resetId", pending.resetId());
        result.put("month", pending.month());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ApiServiceClient.StatsUploadEntry entry : pending.entries()) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("uuid", entry.uuid().toString());
            serialized.put("username", entry.username());
            serialized.put("lastUpdated", entry.lastUpdated());
            serialized.put("stats", new LinkedHashMap<>(entry.stats()));
            entries.add(serialized);
        }
        result.put("entries", entries);
        return result;
    }

    private static void writeJson(Path target, String json, int playerCount) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, json);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            DebugLogger.info(MODULE, "状态数据已保存，共 %s 位玩家", playerCount);
        } catch (IOException e) {
            LOGGER.error("保存状态数据失败", e);
        }
    }

    private static void flushIoExecutor() {
        try {
            Future<?> barrier = IO_EXECUTOR.submit(() -> { });
            barrier.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("等待状态异步保存完成失败", e);
        }
    }

    private static void backupCorruptFile(MinecraftServer server, Path file) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupDir = top.csituka.youzaiworldcore.config.ModPaths.ensureDir(
                    top.csituka.youzaiworldcore.config.ModPaths.worldBackup(
                            server, top.csituka.youzaiworldcore.config.GlobalSettings.STATUS_MODULE));
            Path backup = backupDir.resolve("status_corrupt_" + stamp + ".zip");
            top.csituka.youzaiworldcore.util.BackupArchive.writeFile(backup, file, "data.json");
        } catch (IOException e) {
            LOGGER.error("备份损坏的状态数据失败", e);
        }
    }

    private static String formatPlayTime(long ticks) {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m " + secs + "s";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    private static String formatDamage(long value) {
        return String.format(Locale.ROOT, "%,.1f", value / 10.0);
    }

    private static String formatDistance(long cm) {
        if (cm >= 100000) return String.format(Locale.ROOT, "%,.2f km", cm / 100000.0);
        if (cm >= 100) return String.format(Locale.ROOT, "%,.1f m", cm / 100.0);
        return cm + " cm";
    }

    private static String formatRaw(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private record StatsSnapshot(Map<String, PlayerStats> players) {
    }

    private record PendingStatsUpload(ApiServiceClient.StatsUploadEntry entry) {
    }

    private record PendingMonthlyReset(
            String resetId,
            String month,
            List<ApiServiceClient.StatsUploadEntry> entries) {
    }

    static final class MetricDef {
        final String key;
        final String label;
        final boolean isAggregate;
        final Identifier statId;
        final ToLongFunction<ServerPlayer> reader;
        final Function<Long, String> formatter;

        private MetricDef(String key, String label, Identifier statId,
                ToLongFunction<ServerPlayer> reader, Function<Long, String> formatter, boolean isAggregate) {
            this.key = key;
            this.label = label;
            this.statId = statId;
            this.reader = reader;
            this.formatter = formatter;
            this.isAggregate = isAggregate;
        }

        static MetricDef custom(String key, String label, Identifier statId, Function<Long, String> formatter) {
            return new MetricDef(key, label, statId,
                    player -> player.getStats().getValue(Stats.CUSTOM.get(statId)), formatter, false);
        }

        static MetricDef aggregate(String key, String label, Function<Long, String> formatter,
                ToLongFunction<ServerPlayer> reader) {
            return new MetricDef(key, label, null, reader, formatter, true);
        }
    }

    static final class PlayerStats {
        private UUID uuid;
        private String name;
        private long lastUpdated;
        private Map<String, Long> stats;
        private Map<String, Long> uploadedStats;
        private String uploadedName;
        private boolean uploadInitialized;

        PlayerStats() {
            this.stats = new HashMap<>();
            this.uploadedStats = new HashMap<>();
        }

        PlayerStats(UUID uuid, String name) {
            this(uuid, name, System.currentTimeMillis(), new HashMap<>(), new HashMap<>(), null, false);
        }

        PlayerStats(UUID uuid, String name, long lastUpdated, Map<String, Long> stats) {
            this(uuid, name, lastUpdated, stats, new HashMap<>(), null, false);
        }

        PlayerStats(UUID uuid, String name, long lastUpdated, Map<String, Long> stats,
                Map<String, Long> uploadedStats, String uploadedName, boolean uploadInitialized) {
            this.uuid = uuid;
            this.name = name;
            this.lastUpdated = lastUpdated;
            this.stats = new HashMap<>(stats);
            this.uploadedStats = new HashMap<>(uploadedStats == null ? Map.of() : uploadedStats);
            this.uploadedName = uploadedName;
            this.uploadInitialized = uploadInitialized;
        }

        UUID getUuid() { return uuid; }
        String getName() { return name; }
        long getLastUpdated() { return lastUpdated; }
        Map<String, Long> getStats() { return stats; }
        Map<String, Long> getUploadedStats() { return uploadedStats; }
        String getUploadedName() { return uploadedName; }
        boolean isUploadInitialized() { return uploadInitialized; }
        void setLastUpdated(long value) { lastUpdated = value; }
        void setName(String value) { name = value; }
        long get(String key) { return stats.getOrDefault(key, 0L); }
        void set(String key, long value) { stats.put(key, value); }

        Map<String, Long> getUploadDelta() {
            Map<String, Long> current = new LinkedHashMap<>(stats);
            Map<String, Long> delta = new LinkedHashMap<>();
            Map<String, Long> uploaded = uploadedStats == null ? Map.of() : uploadedStats;
            if (!uploadInitialized) {
                delta.putAll(current);
            } else {
                for (Map.Entry<String, Long> entry : current.entrySet()) {
                    if (!Objects.equals(uploaded.get(entry.getKey()), entry.getValue())) {
                        delta.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            boolean nameChanged = !uploadInitialized || !Objects.equals(name, uploadedName);
            return delta.isEmpty() && !nameChanged ? null : delta;
        }

        void markUploaded(String username, Map<String, Long> delta) {
            if (uploadedStats == null) uploadedStats = new HashMap<>();
            if (delta != null) uploadedStats.putAll(delta);
            uploadedName = username;
            uploadInitialized = true;
        }

        void resetForNewMonth() {
            stats.clear();
            for (MetricDef metric : METRICS) stats.put(metric.key, 0L);
            uploadedStats = new HashMap<>(stats);
            uploadedName = name;
            uploadInitialized = true;
            lastUpdated = System.currentTimeMillis();
        }
    }
}
