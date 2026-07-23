package top.csituka.youzaiworldcore.itemborder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品边框功能配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/item_borders.json}
 * <p>
 * 除 {@code manual_borders} 外所有配置项均为硬编码常量，不可在配置文件中修改。
 * 配置文件仅持久化 {@code manual_borders} 手动边框规则表。
 * </p>
 *
 * <h3>硬编码默认值</h3>
 * <table>
 *   <tr><td>enabled</td><td>true</td><td>功能总开关</td></tr>
 *   <tr><td>hotbar</td><td>true</td><td>HUD 快捷栏绘制边框</td></tr>
 *   <tr><td>showForCommon</td><td>false</td><td>普通物品不绘制</td></tr>
 *   <tr><td>squareCorners</td><td>false</td><td>圆角</td></tr>
 *   <tr><td>fullBorder</td><td>true</td><td>完整四边</td></tr>
 *   <tr><td>overItems</td><td>true</td><td>边框叠于物品之上</td></tr>
 *   <tr><td>extraGlow</td><td>true</td><td>辉光增强</td></tr>
 *   <tr><td>automaticBorders</td><td>true</td><td>稀有度自动着色</td></tr>
 * </table>
 *
 * <p>该功能参考了 ItemBorders（anthonyhilyard）的设计理念，为物品槽位绘制
 * 稀有度渐变色边框。本实现完全使用 26.2 的原生 API（GuiGraphicsExtractor 管线、
 * vanilla ItemStack.getRarity），无任何外部依赖。</p>
 */
@SuppressWarnings({ "null", "unused" })
public final class ItemBorderConfig {

    public static final String MODULE = "ItemBorderConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ItemBorderConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("item_borders.json");

    // ===== 硬编码常量（不可通过配置文件修改）=====

    /** 功能总开关 */
    public static final boolean ENABLED = true;

    /** 是否在快捷栏（HUD 热栏）也绘制边框 */
    public static final boolean HOTBAR = true;

    /** 是否为普通（白色）稀有度的物品也绘制边框 */
    public static final boolean SHOW_FOR_COMMON = false;

    /** 是否使用直角转角（false 时上下横条各向内缩 1px，产生圆角效果） */
    public static final boolean SQUARE_CORNERS = true;

    /** 是否绘制完整四边边框（false 时仅底部有渐变，上部透明） */
    public static final boolean FULL_BORDER = true;

    /** 是否让边框叠在物品图标之上（26.2 Extractor 管线总是绘制在物品之上，本项保留仅作兼容占位） */
    public static final boolean OVER_ITEMS = true;

    /** 是否在边框内侧再绘制一圈低透明度的辉光增强线 */
    public static final boolean EXTRA_GLOW = true;

    /** 是否根据物品稀有度自动着色（关闭时仅手动配置/NBT 生效） */
    public static final boolean AUTO_BORDERS = true;

    // ===== 配置文件可修改字段 =====

    /**
     * 手动边框配置表。
     * <p>
     * key   — 颜色值（支持 Minecraft 颜色名如 "gold"/"red"/"dark_purple" 或
     *          十六进制如 "#FF00AA" / "0xFFFF55" / "FF0000"）。
     * value — 匹配该颜色的物品 ID 列表（如 ["minecraft:diamond_sword", "minecraft:netherite_chestplate"]）。
     */
    private static Map<String, List<String>> manualBorders = new HashMap<>();

    // ===== 访问器 =====

    /**
     * @return 不可修改的手动边框配置映射（colorName → itemIds）
     */
    public static Map<String, List<String>> getManualBorders() {
        return Collections.unmodifiableMap(manualBorders);
    }

    // ===== 持久化（仅 manualBorders）=====

    /** 从文件加载配置（不存在则写入默认空规则表） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        if (!Files.exists(CONFIG_FILE)) {
            DebugLogger.info(MODULE, "配置文件不存在，写入默认空规则表");
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                DebugLogger.warn(MODULE, "配置文件为空，重置为默认");
                save();
                DebugLogger.exiting(MODULE, "load", "reset");
                return;
            }

            if (root.has("manual_borders") && !root.get("manual_borders").isJsonNull()) {
                JsonObject manualObj = root.getAsJsonObject("manual_borders");
                Type listType = new TypeToken<List<String>>() {}.getType();
                manualBorders.clear();
                for (String colorKey : manualObj.keySet()) {
                    JsonElement elem = manualObj.get(colorKey);
                    List<String> items = GSON.fromJson(elem, listType);
                    if (items != null && !items.isEmpty()) {
                        manualBorders.put(colorKey, items);
                    }
                }
            }

            DebugLogger.info(MODULE, "已加载配置 (%d 条手动规则)", manualBorders.size());
        } catch (Exception e) {
            LOGGER.error("加载物品边框配置失败: {}", e.getMessage());
        }

        DebugLogger.exiting(MODULE, "load");
    }

    /** 保存手动边框规则到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();

            JsonObject manualObj = new JsonObject();
            for (Map.Entry<String, List<String>> entry : manualBorders.entrySet()) {
                manualObj.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
            }
            root.add("manual_borders", manualObj);

            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存物品边框配置失败: {}", e.getMessage());
        }
    }
}
