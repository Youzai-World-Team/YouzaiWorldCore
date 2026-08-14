package top.csituka.youzaiworldcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 全局事件开关配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code event_module} 分节。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class EventSettings {

    public static final String MODULE = "EventSettings";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/EventSettings");

    /** 各事件的默认值：全部启用 */
    private static final boolean DEFAULT_ENABLED = true;

    private static boolean deathSound = DEFAULT_ENABLED;
    private static boolean jukeboxLoop = DEFAULT_ENABLED;
    private static boolean babyZombieWeak = DEFAULT_ENABLED;
    private static boolean witherSkullDrop = DEFAULT_ENABLED;
    private static boolean tridentVoidProtect = DEFAULT_ENABLED;
    private static boolean cropXpDrop = DEFAULT_ENABLED;

    private EventSettings() {
    }

    public static boolean isDeathSoundEnabled() { return deathSound; }
    public static boolean isJukeboxLoopEnabled() { return jukeboxLoop; }
    public static boolean isBabyZombieWeakEnabled() { return babyZombieWeak; }
    public static boolean isWitherSkullDropEnabled() { return witherSkullDrop; }
    public static boolean isTridentVoidProtectEnabled() { return tridentVoidProtect; }
    public static boolean isCropXpDropEnabled() { return cropXpDrop; }

    public static void setDeathSound(boolean v) { if (deathSound != v) { deathSound = v; save(); } }
    public static void setJukeboxLoop(boolean v) { if (jukeboxLoop != v) { jukeboxLoop = v; save(); } }
    public static void setBabyZombieWeak(boolean v) { if (babyZombieWeak != v) { babyZombieWeak = v; save(); } }
    public static void setWitherSkullDrop(boolean v) { if (witherSkullDrop != v) { witherSkullDrop = v; save(); } }
    public static void setTridentVoidProtect(boolean v) { if (tridentVoidProtect != v) { tridentVoidProtect = v; save(); } }
    public static void setCropXpDrop(boolean v) { if (cropXpDrop != v) { cropXpDrop = v; save(); } }

    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.EVENT_MODULE);
        if (section.isEmpty()) {
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        deathSound = section.getBoolean("death_sound", deathSound);
        jukeboxLoop = section.getBoolean("jukebox_loop", jukeboxLoop);
        babyZombieWeak = section.getBoolean("baby_zombie_weak", babyZombieWeak);
        witherSkullDrop = section.getBoolean("wither_skull_drop", witherSkullDrop);
        tridentVoidProtect = section.getBoolean("trident_void_protect", tridentVoidProtect);
        cropXpDrop = section.getBoolean("crop_xp_drop", cropXpDrop);
        DebugLogger.info(MODULE, "已加载全局事件配置");
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code event_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        deathSound = DEFAULT_ENABLED;
        jukeboxLoop = DEFAULT_ENABLED;
        babyZombieWeak = DEFAULT_ENABLED;
        witherSkullDrop = DEFAULT_ENABLED;
        tridentVoidProtect = DEFAULT_ENABLED;
        cropXpDrop = DEFAULT_ENABLED;
        save();
    }

    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.EVENT_MODULE);
        section.set("death_sound", deathSound);
        section.set("jukebox_loop", jukeboxLoop);
        section.set("baby_zombie_weak", babyZombieWeak);
        section.set("wither_skull_drop", witherSkullDrop);
        section.set("trident_void_protect", tridentVoidProtect);
        section.set("crop_xp_drop", cropXpDrop);
        GlobalSettings.save();
    }
}
