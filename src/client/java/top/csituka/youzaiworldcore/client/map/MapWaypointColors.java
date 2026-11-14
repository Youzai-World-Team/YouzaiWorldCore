package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapWaypoint;

import java.util.Locale;

/** 路径点颜色自动分配及编辑共用格式；颜色属于地点，不随分组或界面主题改变。 */
public final class MapWaypointColors {
    private static final int[] PALETTE = {
            0xFF4DA6FF, 0xFFFF8C42, 0xFF8DD35F, 0xFFC792EA,
            0xFFF6C85F, 0xFF4DC9B1, 0xFFEF6F9B, 0xFF8B9FEF,
            0xFFD4A373, 0xFF70D6FF, 0xFFFF70A6, 0xFFE9ED8A
    };

    private MapWaypointColors() { }

    /** 优先选择当前玩家私人普通路径点使用最少的预设色，取消创建不占用颜色。 */
    public static int nextColor() {
        var player = Minecraft.getInstance().player;
        int[] counts = new int[PALETTE.length];
        for (var point : MapPersonalData.points()) {
            if (point.shared() || point.kind() != MapWaypoint.Kind.NORMAL
                    || player == null || !point.owner().equals(player.getUUID())) continue;
            for (int index = 0; index < PALETTE.length; index++)
                if (point.color() == PALETTE[index]) counts[index]++;
        }
        int selected = 0;
        for (int index = 1; index < PALETTE.length; index++)
            if (counts[index] < counts[selected]) selected = index;
        return PALETTE[selected];
    }

    /** 编辑器共用六位 RGB 文本，不含透明度。 */
    public static String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0xFFFFFF);
    }

    /** 接受六位 RGB 或带 # 的 RGB，非法输入保留草稿而不保存。 */
    public static int parse(String value) {
        if (value == null || !value.matches("#?[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("颜色必须为六位十六进制");
        return 0xFF000000 | Integer.parseInt(value.replace("#", ""), 16);
    }

    /** 地图、世界内标点和颜色预览共用圆点，黑白轮廓在不同背景上保持可辨识。 */
    public static void render(GuiGraphicsExtractor g, int color, int x, int y, float opacity) {
        RoundedRect.fill(g, x - 6, y - 6, 12, 12, 6, YzuiTheme.alpha(0xFF000000, opacity * 0.85F));
        RoundedRect.fill(g, x - 5, y - 5, 10, 10, 5, YzuiTheme.alpha(0xFFFFFFFF, opacity));
        RoundedRect.fill(g, x - 4, y - 4, 8, 8, 4, YzuiTheme.alpha(color, opacity));
    }
}
