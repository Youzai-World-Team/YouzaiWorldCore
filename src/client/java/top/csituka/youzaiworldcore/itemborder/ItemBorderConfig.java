package top.csituka.youzaiworldcore.itemborder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品边框功能配置。
 * <p>
 * 所有配置项均为硬编码常量，无可修改配置文件。包括：
 * </p>
 *
 * <table>
 *   <tr><td>enabled</td><td>true</td><td>功能总开关</td></tr>
 *   <tr><td>hotbar</td><td>true</td><td>HUD 快捷栏绘制边框</td></tr>
 *   <tr><td>showForCommon</td><td>false</td><td>普通物品不绘制</td></tr>
 *   <tr><td>squareCorners</td><td>true</td><td>直角</td></tr>
 *   <tr><td>fullBorder</td><td>true</td><td>完整四边</td></tr>
 *   <tr><td>overItems</td><td>true</td><td>边框叠于物品之上</td></tr>
 *   <tr><td>extraGlow</td><td>true</td><td>辉光增强</td></tr>
 *   <tr><td>automaticBorders</td><td>true</td><td>稀有度自动着色</td></tr>
 *   <tr><td>manualBorders</td><td>硬编码</td><td>三组预设稀有度分配（~60 项）</td></tr>
 * </table>
 *
 * <p>该功能参考了 ItemBorders（anthonyhilyard）的设计理念，为物品槽位绘制
 * 稀有度渐变色边框。本实现完全使用 26.2 的原生 API（GuiGraphicsExtractor 管线、
 * vanilla ItemStack.getRarity），无任何外部依赖。</p>
 */
@SuppressWarnings("unused")
public final class ItemBorderConfig {

    public static final String MODULE = "ItemBorderConfig";

    // ===== 功能开关 =====

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

    // ===== 硬编码手动边框规则 =====

    /**
     * 手动边框规则表（不可修改）。
     * <p>
     * key   — 颜色值（支持 Minecraft 颜色名如 "gold"/"red"/"dark_purple" 或
     *          十六进制如 "#FF00AA" / "0xFFFF55" / "FF0000"）。
     * value — 匹配该颜色的物品 ID 列表（如 {@code ["minecraft:diamond_sword", "minecraft:netherite_chestplate"]}）。
     * </p>
     * <p>
     * 内置 yzwc 预设稀有度分配：UNCOMMON(yellow) 18 项、RARE(aqua) 19 项、
     * EPIC(light_purple) 22 项（含本模组物品）。完全硬编码，不可被任何配置文件覆盖。
     * </p>
     */
    public static final Map<String, List<String>> MANUAL_BORDERS;

    static {
        Map<String, List<String>> map = new HashMap<>();

        // === UNCOMMON (yellow #FFFF55) ===
        map.put("yellow", List.of(
            "minecraft:turtle_egg",
            "minecraft:chorus_flower",
            "minecraft:parched_spawn_egg",
            "minecraft:frogspawn",
            "minecraft:name_tag",
            "minecraft:saddle",
            "minecraft:turtle_helmet",
            "minecraft:golden_apple",
            "minecraft:golden_carrot",
            "minecraft:golden_dandelion",
            "minecraft:blaze_rod",
            "minecraft:breeze_rod",
            "minecraft:phantom_membrane",
            "minecraft:field_masoned_banner_pattern",
            "minecraft:globe_banner_pattern",
            "minecraft:bordure_indented_banner_pattern",
            "minecraft:flower_banner_pattern",
            "minecraft:creaking_heart"
        ));

        // === RARE (aqua #55FFFF) ===
        map.put("aqua", List.of(
            "minecraft:end_crystal",
            "minecraft:diamond",
            "minecraft:diamond_axe",
            "minecraft:diamond_boots",
            "minecraft:diamond_chestplate",
            "minecraft:diamond_helmet",
            "minecraft:diamond_hoe",
            "minecraft:diamond_horse_armor",
            "minecraft:diamond_leggings",
            "minecraft:diamond_nautilus_armor",
            "minecraft:diamond_pickaxe",
            "minecraft:diamond_shovel",
            "minecraft:diamond_spear",
            "minecraft:diamond_sword",
            "minecraft:ender_eye",
            "minecraft:netherite_scrap",
            "minecraft:ancient_debris",
            "minecraft:trial_key",
            "minecraft:ominous_trial_key"
        ));

        // === EPIC (light_purple #FF55FF) ===
        map.put("light_purple", List.of(
            "minecraft:netherite_ingot",
            "minecraft:netherite_axe",
            "minecraft:netherite_boots",
            "minecraft:netherite_chestplate",
            "minecraft:netherite_helmet",
            "minecraft:netherite_hoe",
            "minecraft:netherite_horse_armor",
            "minecraft:netherite_leggings",
            "minecraft:netherite_nautilus_armor",
            "minecraft:netherite_pickaxe",
            "minecraft:netherite_shovel",
            "minecraft:netherite_spear",
            "minecraft:netherite_sword",
            "minecraft:respawn_anchor",
            "minecraft:end_portal_frame",
            "youzaiworldcore:yz_ingot",
            "youzaiworldcore:yz_shovel",
            "youzaiworldcore:yz_pickaxe",
            "youzaiworldcore:yz_hoe",
            "youzaiworldcore:yz_sword",
            "youzaiworldcore:yz_axe",
            "youzaiworldcore:heart_of_guardianship"
        ));

        MANUAL_BORDERS = Collections.unmodifiableMap(map);
    }
}
