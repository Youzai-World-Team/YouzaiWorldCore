package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家个人配置（每位注册玩家一个文件，强关联账户系统）。
 * <p>
 * 文件位置：{@code <gameDir>/yzwc/server/config/user_settings/<玩家UUID>.json}，
 * 其中的 UUID 与 {@code account_module/registerd_users_data.json} 里该玩家记录的 UUID 一一对应。
 * </p>
 * <p>
 * 文件内部同样先按<b>功能模块</b>分节：
 * </p>
 *
 * <pre>
 * {
 *   "double_doors_module": { "enabled": true },
 *   "function_module":     { "ladder_extend_downward": true, "damage_numbers": false }
 * }
 * </pre>
 *
 * <p>
 * <b>只放必须由服务端保存的玩家设置</b>；纯客户端功能的配置不在此处保存。
 * </p>
 * <p>
 * 生命周期：{@link #create(UUID)} 由账户注册流程调用，{@link #delete(UUID)} 由账户注销
 * / 管理员删除账户流程调用。未注册玩家读取时返回默认值，写入时按需惰性建档，
 * 保证离线服未开账户系统的场景也不会丢设置。
 * </p>
 */
public final class UserSettings {

    private static final String MODULE = "UserSettings";

    /** 玩家 UUID -> 该玩家的配置文件容器 */
    private static final Map<UUID, JsonFileStore> CACHE = new ConcurrentHashMap<>();

    private UserSettings() {
    }

    // ===== 生命周期 =====

    /**
     * 注册账户时创建该玩家的个人配置文件（已存在则不动）。
     * <p>新建的文件带有全部个人配置模块的默认分节，管理员打开就能看到能改什么。</p>
     *
     * @param playerUuid 玩家 UUID
     */
    public static void create(UUID playerUuid) {
        DebugLogger.entering(MODULE, "create", "uuid=" + playerUuid);
        Path file = ModPaths.userSettingsFile(playerUuid);
        if (Files.isRegularFile(file)) {
            DebugLogger.info(MODULE, "个人配置已存在，跳过创建: %s", file);
            DebugLogger.exiting(MODULE, "create", "already exists");
            return;
        }
        JsonFileStore store = store(playerUuid);
        store.writeDefaults();
        DebugLogger.info(MODULE, "已创建玩家个人配置: %s", file);
        DebugLogger.exiting(MODULE, "create");
    }

    /**
     * 注销账户时删除该玩家的个人配置文件。
     *
     * @param playerUuid 玩家 UUID
     */
    public static void delete(UUID playerUuid) {
        DebugLogger.entering(MODULE, "delete", "uuid=" + playerUuid);
        JsonFileStore store = CACHE.remove(playerUuid);
        if (store == null) {
            store = new JsonFileStore(ModPaths.userSettingsFile(playerUuid));
        }
        store.deleteFile();
        DebugLogger.info(MODULE, "已删除玩家个人配置: %s", store.file());
        DebugLogger.exiting(MODULE, "delete");
    }

    /** 清空内存缓存（{@code /yzwc reload} 用），下次访问会重新读盘。 */
    public static void invalidateAll() {
        DebugLogger.info(MODULE, "清空个人配置缓存（%d 份）", CACHE.size());
        CACHE.clear();
    }

    // ===== 读写 =====

    /**
     * 取某玩家某模块的配置分节。
     *
     * @param playerUuid 玩家 UUID
     * @param moduleName 模块名，用 {@link GlobalSettings} 里的常量
     */
    public static ConfigSection section(UUID playerUuid, String moduleName) {
        return store(playerUuid).section(moduleName);
    }

    /**
     * 把某玩家的个人配置写回磁盘。
     *
     * @param playerUuid 玩家 UUID
     */
    public static void save(UUID playerUuid) {
        store(playerUuid).save();
    }

    /**
     * @param playerUuid 玩家 UUID
     * @return 该玩家是否已有个人配置文件
     */
    public static boolean exists(UUID playerUuid) {
        return Files.isRegularFile(ModPaths.userSettingsFile(playerUuid));
    }

    /**
     * 遍历所有已存在的个人配置文件（用于启动期一次性汇总，如构建内存索引）。
     *
     * @param action 对每个 (UUID, 容器) 执行的动作
     */
    public static void forEachExisting(java.util.function.BiConsumer<UUID, JsonFileStore> action) {
        Path dir = ModPaths.userSettingsDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                String uuidText = name.substring(0, name.length() - ".json".length());
                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidText);
                } catch (IllegalArgumentException e) {
                    ConfigCrash.fail(file, "<文件名>",
                            "个人配置文件名必须是玩家 UUID，形如 "
                                    + "3f2504e0-4f89-11d3-9a0c-0305e82c3301.json", e);
                    return; // 不可达
                }
                action.accept(uuid, store(uuid));
            }
        } catch (IOException e) {
            ConfigCrash.fail(dir, "<目录>", "遍历个人配置目录失败：" + e.getMessage(), e);
        }
    }

    // ===== 内部 =====

    /** 取（并惰性加载）某玩家的配置容器 */
    private static JsonFileStore store(UUID playerUuid) {
        return CACHE.computeIfAbsent(playerUuid, uuid -> {
            JsonFileStore store = new JsonFileStore(ModPaths.userSettingsFile(uuid));
            store.setDefaultsWriter(() -> writeDefaultsInto(store));
            store.load();
            return store;
        });
    }

    /**
     * 把「玩家个人配置」的默认内容写进给定容器。
     * <p>
     * 新增需要服务端保存的个人设置时，在这里补一行默认值，
     * 注册新账户与坏文件恢复都会自动带上。
     * </p>
     */
    private static void writeDefaultsInto(JsonFileStore store) {
        // 双开门：默认开启
        store.section(GlobalSettings.DOUBLE_DOORS_MODULE).set("enabled", true);
        // 单玩家功能开关：默认全部开启
        ConfigSection function = store.section(GlobalSettings.FUNCTION_MODULE);
        for (String key : FunctionToggleManager.ALL_KEYS) {
            function.set(key, true);
        }
    }
}
