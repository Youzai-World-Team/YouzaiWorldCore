package top.csituka.youzaiworldcore.map;

import java.util.List;

/** 分组可共用的内置图标库；图标身份独立于分组，不限制玩家创建同类分组。 */
public enum MapWaypointIcon {
    MINERALS("minerals"), COMBAT("combat"), RUINS("ruins"), STARRED("starred"),
    COLLECTIBLES("collectibles"), BUILDINGS("buildings"), OTHER("other"), DEATH("death");

    public static final List<MapWaypointIcon> ALL = List.of(values());
    private final String id;
    MapWaypointIcon(String id) { this.id = id; }

    /** 纹理及持久化标识，不随名称或语言改变。 */
    public String id() { return id; }

    /** 读取图标库身份；未知图标视为损坏数据，不静默替换。 */
    public static MapWaypointIcon byId(String id) {
        return ALL.stream().filter(icon -> icon.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("地图分组图标无效：" + id));
    }
}
