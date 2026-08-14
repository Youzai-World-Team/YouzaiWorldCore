package top.csituka.youzaiworldcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 末地传送门相关功能（末地传送门框可合成 / 可搬运 / 末影龙额外龙蛋）的配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code end_portal_module} 分节。
 * <p>
 * 当前支持设置：
 * <ul>
 *   <li>{@code must_have_silk_touch_to_break_portal} — 是否必须使用精准采集镐才能破坏末地传送门框（默认 true）</li>
 *   <li>{@code add_broken_portal_frames_to_inventory} — 破坏后是否直接放入背包（默认 true；false 则以掉落物形式扔出）</li>
 *   <li>{@code send_message_on_extra_dragon_egg_drop} — 击杀末影龙获得额外龙蛋时是否发送提示消息（默认 true）</li>
 * </ul>
 */
@SuppressWarnings({"null", "unused"})
public final class EndPortalConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/EndPortalConfig");

    /** 默认值：破坏末地传送门框需要精准采集 */
    private static final boolean DEFAULT_MUST_HAVE_SILK_TOUCH = true;
    /** 默认值：破坏后直接放入背包 */
    private static final boolean DEFAULT_ADD_TO_INVENTORY = true;
    /** 默认值：额外龙蛋时发送提示 */
    private static final boolean DEFAULT_SEND_DRAGON_EGG_MESSAGE = true;

    private static boolean mustHaveSilkTouchToBreakPortal = DEFAULT_MUST_HAVE_SILK_TOUCH;
    private static boolean addBrokenPortalFramesToInventory = DEFAULT_ADD_TO_INVENTORY;
    private static boolean sendMessageOnExtraDragonEggDrop = DEFAULT_SEND_DRAGON_EGG_MESSAGE;

    private EndPortalConfig() {}

    // ===== 读取 =====

    public static boolean isMustHaveSilkTouchToBreakPortal() {
        return mustHaveSilkTouchToBreakPortal;
    }

    public static boolean isAddBrokenPortalFramesToInventory() {
        return addBrokenPortalFramesToInventory;
    }

    public static boolean isSendMessageOnExtraDragonEggDrop() {
        return sendMessageOnExtraDragonEggDrop;
    }

    // ===== 持久化 =====

    /** 从全局配置的 {@code end_portal_module} 分节加载（分节缺失则写入默认值） */
    public static void load() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.END_PORTAL_MODULE);
        if (section.isEmpty()) {
            save();
            return;
        }
        mustHaveSilkTouchToBreakPortal =
                section.getBoolean("must_have_silk_touch_to_break_portal", mustHaveSilkTouchToBreakPortal);
        addBrokenPortalFramesToInventory =
                section.getBoolean("add_broken_portal_frames_to_inventory", addBrokenPortalFramesToInventory);
        sendMessageOnExtraDragonEggDrop =
                section.getBoolean("send_message_on_extra_dragon_egg_drop", sendMessageOnExtraDragonEggDrop);
        LOGGER.debug("末地传送门配置已加载");
    }

    /** 重置为默认值并写入 {@code end_portal_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        mustHaveSilkTouchToBreakPortal = DEFAULT_MUST_HAVE_SILK_TOUCH;
        addBrokenPortalFramesToInventory = DEFAULT_ADD_TO_INVENTORY;
        sendMessageOnExtraDragonEggDrop = DEFAULT_SEND_DRAGON_EGG_MESSAGE;
        save();
    }

    /** 保存配置到全局配置文件的 {@code end_portal_module} 分节（含默认值） */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.END_PORTAL_MODULE);
        section.set("must_have_silk_touch_to_break_portal", mustHaveSilkTouchToBreakPortal);
        section.set("add_broken_portal_frames_to_inventory", addBrokenPortalFramesToInventory);
        section.set("send_message_on_extra_dragon_egg_drop", sendMessageOnExtraDragonEggDrop);
        GlobalSettings.save();
    }
}
