package top.csituka.youzaiworldcore.client.map;

import java.util.EnumMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.map.MapWaypointIcon;

/** 分组图标共用白色透明蒙版；每次绘制传入当前 YZUI 状态色，不缓存主题颜色。 */
public final class MapGroupIcons {
    @SuppressWarnings("null")
    private static final EnumMap<MapWaypointIcon, Identifier> TEXTURES = new EnumMap<>(MapWaypointIcon.class);
    static {
        for (var icon : MapWaypointIcon.ALL)
            TEXTURES.put(icon, Identifier.fromNamespaceAndPath(
                    "youzaiworldcore", "textures/gui/dot/" + icon.id() + ".png"));
    }

    private MapGroupIcons() {
    }

    /** 管理器、提示、搜索及辅助朗读共用名称；内置分组随客户端语言变化。 */
    @SuppressWarnings("null")
    public static Component label(MapWaypointGroup group) {
        return group.editable() ? Component.literal(group.name())
                : MapTexts.text(group.equals(MapWaypointGroup.DEATH) ? "category.death" : "ungrouped");
    }

    /** 始终采样完整 16×16 原图，缩放和透明度由界面控制。 */
    public static void render(GuiGraphicsExtractor g, MapWaypointGroup group, int x, int y, int size, int color) {
        render(g, group.icon(), x, y, size, color);
    }

    /** 图标库预览与地图使用同一纹理及主题着色。 */
    @SuppressWarnings("null")
    public static void render(GuiGraphicsExtractor g, MapWaypointIcon icon, int x, int y, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURES.get(icon), x, y, 0, 0,
                size, size, 16, 16, 16, 16, color);
    }
}
