package top.csituka.youzaiworldcore.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 从近到远循环处理已加载区块，避免地图首次打开集中扫描整个视距。 */
public final class MapScanPattern {
    public record Offset(int x, int z) { }
    private static final Map<Integer, List<Offset>> CACHE = new HashMap<>();
    private MapScanPattern() { }

    /** @return 指定半径内按距离排序的固定偏移表 */
    public static synchronized List<Offset> offsets(int radius) {
        int bounded = Math.clamp(radius, 1, 32);
        return CACHE.computeIfAbsent(bounded, value -> {
            List<Offset> offsets = new ArrayList<>();
            for (int z = -value; z <= value; z++) for (int x = -value; x <= value; x++) offsets.add(new Offset(x, z));
            offsets.sort(Comparator.comparingInt(offset -> offset.x * offset.x + offset.z * offset.z));
            return List.copyOf(offsets);
        });
    }
}
