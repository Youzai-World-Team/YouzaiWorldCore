package top.csituka.youzaiworldcore.map;

import java.util.Locale;

/** 地图采样层；功能参考 Conflux Map 的公开说明，采样与同步均为本项目独立实现。 */
public enum MapLayer {
    AUTO, SURFACE, CAVE, FIXED, ROOF;

    /** @return 当前图层的语言键 */
    public String translationKey() {
        return "map.youzaiworldcore.layer." + name().toLowerCase(Locale.ROOT);
    }

    /** @return 是否按指定高度分别缓存 */
    public boolean hasHeight() { return this == CAVE || this == FIXED; }
}
