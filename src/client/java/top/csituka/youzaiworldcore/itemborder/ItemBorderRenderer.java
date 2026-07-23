package top.csituka.youzaiworldcore.itemborder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 物品边框渲染器。
 * <p>
 * 在容器界面与 HUD 中，为每个物品槽位绘制稀有度渐变色边框。
 * 颜色优先级：手动配置 {@literal >} NBT 标签 {@literal >} 稀有度自动色。
 * 使用 26.2 的 {@link GuiGraphicsExtractor#fillGradient} 和
 * {@link GuiGraphicsExtractor#fill} 实现边框绘制，无外部依赖。
 * </p>
 *
 * <h3>与原 ItemBorders (anthonyhilyard) 的关键差异</h3>
 * <ul>
 *   <li>使用 vanilla {@code ItemStack.getRarity()} 替代 Prism 的 {@code ItemColors}</li>
 *   <li>手动规则使用简单 item ID 匹配，替代 Prism {@code Selectors} 选择器引擎</li>
 *   <li>绘制使用 26.2 Extractor API，替代 {@code PoseStack} + {@code GuiHelper.drawGradientRect}</li>
 *   <li>配置使用本项目的 Gson JSON 模式，替代 Iceberg 配置框架</li>
 * </ul>
 */
public final class ItemBorderRenderer {

    public static final String MODULE = "ItemBorderRenderer";

    /** 稀有度对应 RGB 颜色值（不含 alpha） */
    private static final int COLOR_WHITE       = 0xFFFFFF;
    private static final int COLOR_YELLOW      = 0xFFFF55;
    private static final int COLOR_AQUA        = 0x55FFFF;
    private static final int COLOR_LIGHT_PURPLE = 0xFF55FF;

    /** 全框与辉光的 alpha 值 */
    private static final int BORDER_ALPHA = 0xEE;
    private static final int GLOW_ALPHA = BORDER_ALPHA / 3;  // ~0x4F

    /** 标准 Minecraft 颜色名 → RGB 映射（不含 alpha） */
    private static final Map<String, Integer> COLOR_NAME_MAP = new HashMap<>();
    static {
        COLOR_NAME_MAP.put("black",        0x000000);
        COLOR_NAME_MAP.put("dark_blue",    0x0000AA);
        COLOR_NAME_MAP.put("dark_green",   0x00AA00);
        COLOR_NAME_MAP.put("dark_aqua",    0x00AAAA);
        COLOR_NAME_MAP.put("dark_red",     0xAA0000);
        COLOR_NAME_MAP.put("dark_purple",  0xAA00AA);
        COLOR_NAME_MAP.put("gold",         0xFFAA00);
        COLOR_NAME_MAP.put("gray",         0xAAAAAA);
        COLOR_NAME_MAP.put("dark_gray",    0x555555);
        COLOR_NAME_MAP.put("blue",         0x5555FF);
        COLOR_NAME_MAP.put("green",        0x55FF55);
        COLOR_NAME_MAP.put("aqua",         0x55FFFF);
        COLOR_NAME_MAP.put("red",          0xFF5555);
        COLOR_NAME_MAP.put("light_purple", 0xFF55FF);
        COLOR_NAME_MAP.put("yellow",       0xFFFF55);
        COLOR_NAME_MAP.put("white",        0xFFFFFF);
        // 别名
        COLOR_NAME_MAP.put("dark_grey",    0x555555);
        COLOR_NAME_MAP.put("grey",         0xAAAAAA);
    }

    private ItemBorderRenderer() {}

    // ============================================================
    //  公开渲染入口（容器界面）
    // ============================================================

    /**
     * 为 {@link Slot} 绘制边框（容器界面调用）。
     *
     * @param gui  当前 {@link GuiGraphicsExtractor} 实例
     * @param slot 目标槽位
     */
    public static void renderBorder(GuiGraphicsExtractor gui, Slot slot) {
        if (!isRenderable(slot.getItem())) return;

        int startColor = resolveBorderColor(slot.getItem());
        if (startColor == 0) return;

        drawBorder(gui, slot.x, slot.y, startColor, startColor);
    }

    // ============================================================
    //  公开渲染入口（HUD 热栏）
    // ============================================================

    /**
     * 为 HUD 快捷栏中的物品绘制边框。
     *
     * @param gui  当前 {@link GuiGraphicsExtractor} 实例
     * @param x    槽位屏幕 X 坐标
     * @param y    槽位屏幕 Y 坐标
     * @param item 目标物品
     */
    public static void renderBorder(GuiGraphicsExtractor gui, int x, int y, ItemStack item) {
        if (!isRenderable(item)) return;
        if (!ItemBorderConfig.HOTBAR) return;

        int startColor = resolveBorderColor(item);
        if (startColor == 0) return;

        drawBorder(gui, x, y, startColor, startColor);
    }

    // ============================================================
    //  渲染条件判断
    // ============================================================

    /**
     * 检查物品是否可以绘制边框。
     * 规则：功能开启、物品非空。
     */
    private static boolean isRenderable(ItemStack item) {
        if (!ItemBorderConfig.ENABLED) return false;
        if (item.isEmpty()) return false;
        return true;
    }

    // ============================================================
    //  颜色决议（三级优先级）
    // ============================================================

    /**
     * 解析物品的边框颜色。
     *
     * @return ARGB 颜色值（alpha 在 0x00–0xFF 之间），若不应绘制则返回 0
     */
    private static int resolveBorderColor(ItemStack item) {
        // 第一级：手动配置
        Integer manualColor = matchManualBorder(item);
        if (manualColor != null) {
            DebugLogger.trace(MODULE, "手动匹配颜色: %08X for %s", manualColor,
                    BuiltInRegistries.ITEM.getKey(item.getItem()));
            return manualColor | 0xEE000000;
        }

        // 第二级：NBT 标签覆写
        Integer nbtColor = readNbtBorderColor(item);
        if (nbtColor != null) {
            return nbtColor | 0xEE000000;
        }

        // 第三级：自动稀有度色（需开启自动边框）
        if (!ItemBorderConfig.AUTO_BORDERS) {
            return 0;
        }

        int color = fromRarity(item.getRarity());

        // 检查是否有自定义名称颜色
        int nameColor = getCustomNameColor(item);
        if (nameColor != 0) {
            color = nameColor;
        }

        // 白色稀有度：仅当 showForCommon 开启时绘制
        if (color == COLOR_WHITE) {
            if (!ItemBorderConfig.SHOW_FOR_COMMON) {
                return 0;
            }
        }

        return color | 0xEE000000;
    }

    // ============================================================
    //  手动规则匹配
    // ============================================================

    /**
     * 在手动配置中查找匹配当前物品的颜色。
     *
     * @return RGB 颜色值（不含 alpha），或 null
     */
    private static Integer matchManualBorder(ItemStack item) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        String itemIdStr = itemId.toString();

        for (Map.Entry<String, List<String>> entry : ItemBorderConfig.getManualBorders().entrySet()) {
            String colorKey = entry.getKey();
            List<String> itemIds = entry.getValue();

            if (itemIds.contains(itemIdStr)) {
                return parseColorSimple(colorKey);
            }
        }
        return null;
    }

    // ============================================================
    //  NBT 颜色覆写读取
    // ============================================================

    /**
     * 从物品的 CUSTOM_DATA 组件中读取 yzwc_border_colors 标签。
     * <p>
     * 数据包格式：{@code yzwc_border_colors: { "top": "#FF0000", "bottom": "#00FF00" }}
     * 若提供了 top 和 bottom，仅取 top 作为单色。
     * </p>
     *
     * @return RGB 颜色值（不含 alpha），或 null
     */
    private static Integer readNbtBorderColor(ItemStack item) {
        if (!item.has(DataComponents.CUSTOM_DATA)) return null;

        CompoundTag customTag = item.get(DataComponents.CUSTOM_DATA).copyTag();
        if (!customTag.contains("yzwc_border_colors")) return null;

        CompoundTag colorsTag = customTag.getCompound("yzwc_border_colors").orElse(null);
        if (colorsTag == null) return null;

        String topStr = colorsTag.getString("top").orElse(null);
        String bottomStr = colorsTag.getString("bottom").orElse(null);

        // 取 top，若没有则取 bottom
        String colorStr = (topStr != null) ? topStr : bottomStr;
        if (colorStr == null) return null;

        return parseColorSimple(colorStr);
    }

    // ============================================================
    //  稀有度→颜色映射（硬编码，避免依赖 ChatFormatting 变更）
    // ============================================================

    /**
     * 将 Minecraft 稀有度枚举映射为 RGB 边框颜色值。
     * COMMON→白色, UNCOMMON→黄色, RARE→青色, EPIC→浅紫。
     *
     * @param rarity 物品稀有度
     * @return RGB 颜色值（不含 alpha）
     */
    private static int fromRarity(Rarity rarity) {
        return switch (rarity) {
            case COMMON   -> COLOR_WHITE;
            case UNCOMMON -> COLOR_YELLOW;
            case RARE     -> COLOR_AQUA;
            case EPIC     -> COLOR_LIGHT_PURPLE;
        };
    }

    // ============================================================
    //  自定义名称颜色检测
    // ============================================================

    /**
     * 检查物品是否有自定义名称，且该名称使用了特定的颜色。
     *
     * @return RGB 颜色值（不含 alpha），若无效则返回 0
     */
    private static int getCustomNameColor(ItemStack item) {
        Component name = item.getHoverName();
        if (name == null) return 0;

        TextColor textColor = extractTextColor(name);
        if (textColor == null) return 0;

        int color = textColor.getValue() & 0x00FFFFFF;

        // 忽略白色（与默认名同色）
        if (color == COLOR_WHITE) return 0;

        return color;
    }

    /**
     * 递归提取组件的 TextColor（优先查找显式设置的颜色）。
     */
    private static TextColor extractTextColor(Component component) {
        Style style = component.getStyle();
        if (style != null && style.getColor() != null) {
            return style.getColor();
        }
        for (Component sibling : component.getSiblings()) {
            TextColor color = extractTextColor(sibling);
            if (color != null) return color;
        }
        return null;
    }

    // ============================================================
    //  颜色解析工具
    // ============================================================

    /**
     * 从字符串解析颜色值。
     * <p>
     * 支持：
     * <ul>
     *   <li>Minecraft 标准颜色名：{@code "red"}, {@code "gold"}, {@code "dark_purple"} 等</li>
     *   <li>十六进制 {@code "#RRGGBB"} 格式</li>
     *   <li>十六进制 {@code "0xRRGGBB"} 格式</li>
     *   <li>裸十六进制 {@code "RRGGBB"} 格式</li>
     * </ul>
     *
     * @param input 颜色字符串
     * @return RGB 颜色值（不含 alpha），或 null
     */
    private static Integer parseColorSimple(String input) {
        if (input == null || input.isBlank()) return null;

        String trimmed = input.trim();

        // 尝试通过 TextColor.parseColor 解析（返回 DataResult）
        var parseResult = TextColor.parseColor(trimmed);
        Optional<TextColor> parsed = parseResult.result();
        if (parsed.isPresent()) {
            return parsed.get().getValue() & 0x00FFFFFF;
        }

        // 尝试标准颜色名映射
        Integer named = COLOR_NAME_MAP.get(trimmed.toLowerCase());
        if (named != null) return named;

        // 手动处理裸十六进制（无 # 前缀）或 0x 前缀
        String hex = trimmed;
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }

        // 尝试作为 hex 解析
        if (hex.matches("[0-9A-Fa-f]{6}")) {
            try {
                return Integer.parseInt(hex, 16) & 0x00FFFFFF;
            } catch (NumberFormatException ignored) {}
        }

        DebugLogger.warn(MODULE, "无法解析颜色值: \"{}\"", trimmed);
        return null;
    }

    // ============================================================
    //  边框绘制核心
    // ============================================================

    /**
     * 绘制 16×16 槽位边框。
     * <p>
     * 绘制四段 1px 宽的矩形条：左竖、右竖、顶横、底横，
     * 左/右为渐变，顶/底为纯色。支持圆角（squareCorners=false 时横条缩进 1px）
     * 和辉光增强。
     * </p>
     *
     * @param gui        绘制上下文
     * @param x          槽位左上角 X
     * @param y          槽位左上角 Y
     * @param startColor 起始 ARGB 色（用于竖条上端/顶条）
     * @param endColor   结束 ARGB 色（用于竖条下端/底条）
     */
    private static void drawBorder(GuiGraphicsExtractor gui,
                                   int x, int y,
                                   int startColor, int endColor) {
        boolean fullBorder = ItemBorderConfig.FULL_BORDER;
        boolean squareCorners = ItemBorderConfig.SQUARE_CORNERS;
        boolean extraGlow = ItemBorderConfig.EXTRA_GLOW;

        // 颜色 alpha 处理：全框时所有边都有 alpha，非全框时顶部透明
        int topColor, bottomColor;
        topColor = fullBorder ? (startColor & 0x00FFFFFF) | (BORDER_ALPHA << 24)
                              : (startColor & 0x00FFFFFF);  // alpha=0 → 透明
        bottomColor = (endColor & 0x00FFFFFF) | (BORDER_ALPHA << 24);

        int xo = squareCorners ? 0 : 1;

        // --- 左右竖条（渐变色）---
        gui.fillGradient(x,      y + 1, x + 1,  y + 15, topColor, bottomColor);
        gui.fillGradient(x + 15, y + 1, x + 16, y + 15, topColor, bottomColor);

        // --- 顶横条（纯色）---
        gui.fill(x + xo, y, x + 16 - xo, y + 1, topColor);

        // --- 底横条（纯色）---
        gui.fill(x + xo, y + 15, x + 16 - xo, y + 16, bottomColor);

        // --- 辉光增强（内层 1px，alpha 降至 1/3）---
        if (extraGlow) {
            int glowColor = (GLOW_ALPHA << 24) | (endColor & 0x00FFFFFF);

            gui.fillGradient(x + 1,   y + 1, x + 2,   y + 15, glowColor, glowColor);
            gui.fillGradient(x + 14,  y + 1, x + 15,  y + 15, glowColor, glowColor);
            gui.fill(x + 1,  y + 1, x + 15, y + 2,   glowColor);
            gui.fill(x + 1,  y + 14, x + 15, y + 15,  glowColor);
        }
    }
}
