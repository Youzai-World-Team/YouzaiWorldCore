package top.csituka.youzaiworldcore.map;

import java.util.List;

/** 路径点可选的八种图标分组；写入 SQLite 和网络包时使用固定英文 ID。 */
public enum MapWaypointGroup {
    MINERALS("minerals"), COMBAT("combat"), RUINS("ruins"), STARRED("starred"),
    COLLECTIBLES("collectibles"), BUILDINGS("buildings"), OTHER("other"), DEATH("death");

    public static final List<MapWaypointGroup> ALL = List.of(values());
    private final String id;

    MapWaypointGroup(String id) { this.id = id; }

    /** 分组资源名与持久化标识，不随玩家语言变化。 */
    public String id() { return id; }

    /** 新建和修改只接受固定 ID，不能借助数据包建立文本分组。 */
    public static boolean isValidId(String id) {
        return ALL.stream().anyMatch(group -> group.id.equals(id));
    }

    /** 仅解析显示，不改写旧记录；原内置中文分组对应图标，其余旧文本归入其他。 */
    public static MapWaypointGroup of(MapWaypoint point) {
        for (var group : ALL) if (group.id.equals(point.group())) return group;
        return switch (point.group()) {
            case "矿产" -> MINERALS;
            case "战斗" -> COMBAT;
            case "遗迹" -> RUINS;
            case "星标" -> STARRED;
            case "收集物" -> COLLECTIBLES;
            case "建筑" -> BUILDINGS;
            case "其他" -> OTHER;
            default -> switch (point.kind()) {
                case DEATH -> DEATH;
                case STRUCTURE -> RUINS;
                default -> OTHER;
            };
        };
    }
}
