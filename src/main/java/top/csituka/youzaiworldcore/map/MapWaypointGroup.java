package top.csituka.youzaiworldcore.map;

/** 玩家自建的路径点分组；名称与图标存入当前存档的客户端地图 SQLite，不公开给其他玩家。 */
public record MapWaypointGroup(String id, String name, MapWaypointIcon icon) {
    public static final MapWaypointGroup UNGROUPED = new MapWaypointGroup("", "", MapWaypointIcon.OTHER);
    public static final MapWaypointGroup DEATH = new MapWaypointGroup("death", "", MapWaypointIcon.DEATH);

    public MapWaypointGroup {
        if (id == null || name == null || icon == null) throw new IllegalArgumentException("地图分组无效");
        if (id.isEmpty() || id.equals("death")) {
            if (!name.isEmpty() || icon != (id.isEmpty() ? MapWaypointIcon.OTHER : MapWaypointIcon.DEATH))
                throw new IllegalArgumentException("内置地图分组不可修改");
        } else if (!validCustomId(id) || !validName(name)) {
            throw new IllegalArgumentException("地图分组名称或身份无效");
        }
    }

    /** 自建分组使用无连字符 UUID，符合路径点磁盘及网络字段的 32 字符上限。 */
    public static boolean validCustomId(String id) { return id != null && id.matches("[0-9a-f]{32}"); }

    /** 玩家输入的分组名称最多 32 字符，禁止空白名称及控制字符。 */
    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= 32 && name.equals(name.strip())
                && name.codePoints().noneMatch(Character::isISOControl);
    }

    /** 死亡点分组和未分组入口属于内置项，不提供编辑或删除。 */
    public boolean editable() { return validCustomId(id); }
}
