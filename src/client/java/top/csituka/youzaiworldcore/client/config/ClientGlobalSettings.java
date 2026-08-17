package top.csituka.youzaiworldcore.client.config;

import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.JsonFileStore;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.nio.file.Path;

/**
 * 客户端全局配置。
 * <p>
 * 唯一文件：{@code <gameDir>/yzwc/client/global_settings.json}。
 * 与服务端不同，客户端<b>只有这一个文件、没有任何子目录</b> ——
 * 客户端不存在需要长期保管的数据、备份或缓存，多套目录纯属负担。
 * </p>
 * <p>
 * 文件内部同样先按<b>功能模块</b>分节，再写各模块自己的配置项：
 * </p>
 *
 * <pre>
 * {
 *   "core_module":           { "dev_mode_enabled": false, "log_level": 0, "yzui_enabled": true, ... },
 *   "highlight_item_module": { "toggle": true, "color": 16755200, "comparator": "ITEM_ONLY", ... }
 * }
 * </pre>
 *
 * <p>
 * 使用方式与服务端的 {@code GlobalSettings} 完全一致：{@link #load()} 必须在任何模块
 * {@code load()} 之前调用一次（已在 {@code Client.onInitializeClient()} 最开头完成）；
 * 文件不存在（新装模组）时会直接写出一份<b>包含全部模块默认值</b>的完整配置；
 * 读到非法内容时把坏文件改名为 {@code .error}、在原路径重建默认配置，然后崩溃退出。
 * </p>
 */
public final class ClientGlobalSettings {

    private static final String MODULE = "ClientGlobalSettings";

    // ===== 模块分节名（新增客户端模块时在此登记）=====

    /** 客户端核心（开发者模式 / 日志级别 / 调试地址 / YZUI 开关等） */
    public static final String CORE_MODULE = "core_module";
    /** 物品高亮 */
    public static final String HIGHLIGHT_ITEM_MODULE = "highlight_item_module";
    /** YZHUD（位置与透明度） */
    public static final String YZHUD_MODULE = "yzhud_module";

    private static final JsonFileStore STORE = new JsonFileStore(ModPaths.clientSettingsFile());

    static {
        STORE.setDefaultsWriter(ClientDefaultSettingsWriter::writeAllDefaults);
    }

    /**
     * 批量写入模式：为 true 时各模块的 {@link #save()} 只改内存不落盘，
     * 由批量操作结束时统一写一次，避免生成默认文件时反复写盘。
     */
    private static boolean batching = false;

    private ClientGlobalSettings() {
    }

    /**
     * 从磁盘读取客户端配置；文件不存在时写出一份包含全部模块默认值的完整配置。
     * <p>必须在任何模块 {@code load()} 之前调用；重复调用等同于重新加载。</p>
     */
    public static void load() {
        DebugLogger.entering(MODULE, "load", "file=" + STORE.file());
        boolean created = STORE.loadOrCreateDefaults();
        if (created) {
            DebugLogger.info(MODULE, "未检测到客户端配置，已写出默认配置：%s", STORE.file());
        }
        DebugLogger.info(MODULE, "客户端配置已加载：%s（模块分节 %d 个）",
                STORE.file(), STORE.moduleNames().size());
        DebugLogger.exiting(MODULE, "load", created ? "created default" : "loaded");
    }

    /**
     * 取某个模块的配置分节；不存在时自动创建空分节。
     *
     * @param moduleName 模块名，用本类里的常量
     */
    public static ConfigSection section(String moduleName) {
        if (!STORE.isLoaded()) {
            // 兜底：极少数早于 onInitializeClient 触发类加载的路径（如 Mixin）也能拿到正确数据
            load();
        }
        return STORE.section(moduleName);
    }

    /** 把内存中的客户端配置整体写回磁盘（批量写入模式下只改内存）。 */
    public static void save() {
        if (batching) {
            return;
        }
        STORE.save();
    }

    /** @return 客户端配置文件路径 */
    public static Path file() {
        return STORE.file();
    }

    /** 供 {@link ClientDefaultSettingsWriter} 在批量模式下写默认值 */
    static void runBatched(Runnable action) {
        boolean outer = batching;
        batching = true;
        try {
            action.run();
        } finally {
            batching = outer;
        }
    }
}
