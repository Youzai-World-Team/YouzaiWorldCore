package top.csituka.youzaiworldcore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 末地传送门相关功能（末地传送门框可合成 / 可搬运 / 末影龙额外龙蛋）的配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/end_portal_settings.json}
 * <p>
 * 当前支持设置：
 * <ul>
 *   <li>{@code mustHaveSilkTouchToBreakPortal} — 是否必须使用精准采集镐才能破坏末地传送门框（默认 true）</li>
 *   <li>{@code addBrokenPortalFramesToInventory} — 破坏后是否直接放入背包（默认 true；false 则以掉落物形式扔出）</li>
 *   <li>{@code sendMessageOnExtraDragonEggDrop} — 击杀末影龙获得额外龙蛋时是否发送提示消息（默认 true）</li>
 * </ul>
 */
@SuppressWarnings({"null", "unused"})
public final class EndPortalConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/EndPortalConfig");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("end_portal_settings.json");

    private static boolean mustHaveSilkTouchToBreakPortal = true;
    private static boolean addBrokenPortalFramesToInventory = true;
    private static boolean sendMessageOnExtraDragonEggDrop = true;

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

    /** 从文件加载配置（不存在则使用默认值并创建文件） */
    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            if (root.has("mustHaveSilkTouchToBreakPortal") && !root.get("mustHaveSilkTouchToBreakPortal").isJsonNull())
                mustHaveSilkTouchToBreakPortal = root.get("mustHaveSilkTouchToBreakPortal").getAsBoolean();

            if (root.has("addBrokenPortalFramesToInventory") && !root.get("addBrokenPortalFramesToInventory").isJsonNull())
                addBrokenPortalFramesToInventory = root.get("addBrokenPortalFramesToInventory").getAsBoolean();

            if (root.has("sendMessageOnExtraDragonEggDrop") && !root.get("sendMessageOnExtraDragonEggDrop").isJsonNull())
                sendMessageOnExtraDragonEggDrop = root.get("sendMessageOnExtraDragonEggDrop").getAsBoolean();
        } catch (Exception e) {
            LOGGER.error("加载末地传送门设置失败: {}", e.getMessage());
        }
    }

    /** 保存配置到文件（含默认值） */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("mustHaveSilkTouchToBreakPortal", mustHaveSilkTouchToBreakPortal);
            root.addProperty("addBrokenPortalFramesToInventory", addBrokenPortalFramesToInventory);
            root.addProperty("sendMessageOnExtraDragonEggDrop", sendMessageOnExtraDragonEggDrop);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存末地传送门设置失败: {}", e.getMessage());
        }
    }
}
