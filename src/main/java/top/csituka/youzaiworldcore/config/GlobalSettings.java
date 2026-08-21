package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 全局配置（与世界无关，任何存档下都生效）。
 * <p>
 * 唯一文件：{@code <gameDir>/yzwc/server/config/global_settings.json}，
 * 内部先按<b>功能模块</b>分节，再写各模块自己的配置项：
 * </p>
 *
 * <pre>
 * {
 *   "core_module":   { "dev_mode_enabled": false, "log_to_file": false },
 *   "api_module":    { "base_url": "https://api.mcyzw.top", ... },
 *   "afk_module":    { "enabled": true, "threshold_seconds": 300, ... },
 *   ...
 * }
 * </pre>
 *
 * <p>
 * 玩家<b>个人</b>配置不放这里，见 {@link UserSettings}。
 * </p>
 * <p>
 * 使用方式：{@link #load()} 必须在任何模块 {@code load()} 之前调用一次
 * （已在 {@code YouzaiworldCore.onInitialize()} 最开头完成）。
 * 文件不存在（新开服）时会直接写出一份<b>包含全部模块默认值</b>的完整配置；
 * 各模块随后取自己的分节 {@code GlobalSettings.section(GlobalSettings.AFK_MODULE)} 读写，
 * 改完调用 {@link #save()} 整体落盘。
 * </p>
 */
public final class GlobalSettings {

    private static final String MODULE = "GlobalSettings";

    // ===== 模块分节名（新增模块时在此登记，保持与 data/ backup/ temp/ 下的文件夹名一致）=====

    /** 模组核心（开发者模式 / 日志开关） */
    public static final String CORE_MODULE = "core_module";
    /** AFK 挂机检测 */
    public static final String AFK_MODULE = "afk_module";
    /** 全局事件开关 */
    public static final String EVENT_MODULE = "event_module";
    /** 天然带电苦力怕 */
    public static final String CHARGED_CREEPER_MODULE = "charged_creeper_module";
    /** 末地传送门相关功能 */
    public static final String END_PORTAL_MODULE = "end_portal_module";
    /** 老吴贴贴事件 */
    public static final String LAOWU_MEME_MODULE = "laowu_meme_module";
    /** 试炼宝库无限领奖 */
    public static final String TRIAL_VAULT_MODULE = "trial_vault_module";
    /** 更新检查器 */
    public static final String UPDATE_MODULE = "update_module";
    /** 原地重生 */
    public static final String RESPAWN_MODULE = "respawn_module";
    /** 邮件系统 */
    public static final String MAIL_MODULE = "mail_module";
    /** 宠物模块 */
    public static final String PET_MODULE = "pet_module";
    /** 自定义皮肤与披风 */
    public static final String COSMETIC_MODULE = "cosmetic_module";
    /** 维度池（跨维度独立背包） */
    public static final String DIMENSIONAL_INVENTORIES_MODULE = "dimensional_inventories_module";
    /** 玩法统计与排行榜 */
    public static final String STATUS_MODULE = "status_module";
    /** 冒险等级与属性加点 */
    public static final String SKILL_MODULE = "skill_module";
    /** 双开门（玩家个人配置，见 {@link UserSettings}） */
    public static final String DOUBLE_DOORS_MODULE = "double_doors_module";
    /** 单玩家功能开关（玩家个人配置，见 {@link UserSettings}） */
    public static final String FUNCTION_MODULE = "function_module";
    /** 聊天消息格式化（仿 Styled Chat 精简版：模板 + %papi% 占位符） */
    public static final String CHAT_FORMAT_MODULE = "chat_format_module";
    /** Tab 列表抬头 / 页脚定制（仿 Styled Player List 精简版，仅 Header/Footer） */
    public static final String TABLIST_MODULE = "tablist_module";
    /** 侧边栏定制（仿 Styled Sidebars 精简版，计分板驱动，单一全局样式） */
    public static final String SIDEBAR_MODULE = "sidebar_module";
    /** Api 服务端账户与装扮网桥配置 */
    public static final String API_MODULE = "api_module";

    private static final JsonFileStore STORE = new JsonFileStore(ModPaths.globalSettingsFile());

    static {
        STORE.setDefaultsWriter(DefaultSettingsWriter::writeAllDefaults);
    }

    /**
     * 批量写入模式：为 true 时各模块的 {@link #save()} 只改内存不落盘，
     * 由批量操作结束时统一写一次，避免生成默认文件时反复写盘。
     */
    private static boolean batching = false;

    private GlobalSettings() {
    }

    /**
     * 从磁盘读取全局配置；文件不存在时（新开服）写出一份包含全部模块默认值的完整配置。
     * <p>
     * 必须在任何模块 {@code load()} 之前调用；重复调用等同于重新加载。
     * </p>
     */
    public static void load() {
        DebugLogger.entering(MODULE, "load", "file=" + STORE.file());
        boolean created = STORE.loadOrCreateDefaults();
        if (created) {
            DebugLogger.info(MODULE, "未检测到全局配置，已写出默认配置：%s", STORE.file());
        }
        DebugLogger.info(MODULE, "全局配置已加载：%s（模块分节 %d 个）",
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
            // 兜底：极少数早于 onInitialize 触发类加载的路径（如 Mixin）也能拿到正确数据
            load();
        }
        return STORE.section(moduleName);
    }

    /** 把内存中的全局配置整体写回磁盘（批量写入模式下只改内存）。 */
    public static void save() {
        if (batching) {
            return;
        }
        STORE.save();
    }

    /**
     * 清空全部内容、写入全部模块的默认值并落盘。
     * <p>供新开服首次生成与坏文件恢复使用。</p>
     */
    public static void resetToDefaults() {
        STORE.writeDefaults();
    }

    /** @return 全局配置文件路径 */
    public static java.nio.file.Path file() {
        return STORE.file();
    }

    /** 供 {@link DefaultSettingsWriter} 在批量模式下写默认值 */
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
