package top.csituka.youzaiworldcore.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import top.csituka.youzaiworldcore.map.MapWaypoint;

/** 仅提供人类可读的坐标复制和聊天草稿，不提供地图数据导入、导出。 */
public final class MapCoordinates {
    private MapCoordinates() { }

    public static String coordinate(MapWaypoint point) {
        return "[YZMAP " + point.x() + " " + point.y() + " " + point.z() + " " + point.dimension() + "] "
                + point.name().replace('\n', ' ').replace('\r', ' ');
    }

    /** 玩家自行发送草稿。 */
    public static void share(MapWaypoint point) {
        Minecraft.getInstance().gui.openChatAndAddText(ChatComponent.ChatMethod.MESSAGE, coordinate(point));
    }
}
