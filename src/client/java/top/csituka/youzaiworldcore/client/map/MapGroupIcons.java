package top.csituka.youzaiworldcore.client.map;

import java.util.EnumMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;

/** 分组图标共用白色透明蒙版；每次绘制传入当前 YZUI 状态色，不缓存主题颜色。 */
public final class MapGroupIcons {
    private static final EnumMap<MapWaypointGroup, Identifier> TEXTURES = new EnumMap<>(MapWaypointGroup.class);
    static {
        for (var group : MapWaypointGroup.ALL) TEXTURES.put(group, Identifier.fromNamespaceAndPath(
                "youzaiworldcore", "textures/gui/dot/" + group.id() + ".png"));
    }

    private MapGroupIcons() { }

    /** 管理器名称、悬停提示、搜索及辅助朗读共用私人名称，未重命名时使用本地化默认值。 */
    public static Component label(MapWaypointGroup group) {
        String name = MapPersonalData.groupName(group);
        return name.isEmpty() ? MapTexts.text("category." + group.id()) : Component.literal(name);
    }

    /** 始终采样完整 16×16 原图，缩放和透明度由界面控制。 */
    public static void render(GuiGraphicsExtractor g, MapWaypointGroup group, int x, int y, int size, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURES.get(group), x, y, 0, 0,
                size, size, 16, 16, 16, 16, color);
    }
}
