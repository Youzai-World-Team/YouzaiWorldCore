package top.csituka.youzaiworldcore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 全局事件开关配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/event_settings.json}
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class EventSettings {

    public static final String MODULE = "EventSettings";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/EventSettings");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("event_settings.json");

    private static boolean deathSound = true;
    private static boolean jukeboxLoop = true;
    private static boolean babyZombieWeak = true;
    private static boolean witherSkullDrop = true;
    private static boolean tridentVoidProtect = true;

    private EventSettings() {
    }

    public static boolean isDeathSoundEnabled() { return deathSound; }
    public static boolean isJukeboxLoopEnabled() { return jukeboxLoop; }
    public static boolean isBabyZombieWeakEnabled() { return babyZombieWeak; }
    public static boolean isWitherSkullDropEnabled() { return witherSkullDrop; }
    public static boolean isTridentVoidProtectEnabled() { return tridentVoidProtect; }

    public static void setDeathSound(boolean v) { if (deathSound != v) { deathSound = v; save(); } }
    public static void setJukeboxLoop(boolean v) { if (jukeboxLoop != v) { jukeboxLoop = v; save(); } }
    public static void setBabyZombieWeak(boolean v) { if (babyZombieWeak != v) { babyZombieWeak = v; save(); } }
    public static void setWitherSkullDrop(boolean v) { if (witherSkullDrop != v) { witherSkullDrop = v; save(); } }
    public static void setTridentVoidProtect(boolean v) { if (tridentVoidProtect != v) { tridentVoidProtect = v; save(); } }

    public static void load() {
        DebugLogger.entering(MODULE, "load");
        if (!Files.exists(CONFIG_FILE)) {
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null) {
                if (root.has("death_sound")) deathSound = root.get("death_sound").getAsBoolean();
                if (root.has("jukebox_loop")) jukeboxLoop = root.get("jukebox_loop").getAsBoolean();
                if (root.has("baby_zombie_weak")) babyZombieWeak = root.get("baby_zombie_weak").getAsBoolean();
                if (root.has("wither_skull_drop")) witherSkullDrop = root.get("wither_skull_drop").getAsBoolean();
                if (root.has("trident_void_protect")) tridentVoidProtect = root.get("trident_void_protect").getAsBoolean();
            }
            DebugLogger.info(MODULE, "已加载全局事件配置");
        } catch (Exception e) {
            LOGGER.error("加载事件配置失败: {}", e.getMessage());
        }
        DebugLogger.exiting(MODULE, "load");
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("death_sound", deathSound);
            root.addProperty("jukebox_loop", jukeboxLoop);
            root.addProperty("baby_zombie_weak", babyZombieWeak);
            root.addProperty("wither_skull_drop", witherSkullDrop);
            root.addProperty("trident_void_protect", tridentVoidProtect);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存事件配置失败: {}", e.getMessage());
        }
    }
}
