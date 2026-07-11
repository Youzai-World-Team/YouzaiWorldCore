package top.csituka.youzaiworldcore.dimensionalinventories;

import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.*;

/**
 * 维度池 — 一组维度的集合，池内玩家共享独立的状态（背包、生命、经验等）。
 * 当玩家在不同池之间穿越时，自动完成状态保存/加载。
 */
public final class DimensionPool {

    /** 池的唯一标识符，如 "survival_world_pool" */
    private String id;

    /** 显示名称 */
    private String displayName;

    /** 此池包含的维度 ID 集合 */
    private TreeSet<String> dimensions;

    /** 在此池中强制使用的游戏模式 */
    private GameType gameMode;

    /** 是否允许在此池中推进进度（成就） */
    private boolean progressAdvancements;

    /** 是否允许在此池中增加统计信息 */
    private boolean incrementStatistics;

    /** 玩家死亡后首次传送回的默认出生点；为 null 则使用已保存位置 */
    @Nullable
    private DefaultSpawn defaultSpawn;

    public DimensionPool() {
        this.dimensions = new TreeSet<>();
    }

    public DimensionPool(String id, String displayName, GameType gameMode,
                         boolean progressAdvancements, boolean incrementStatistics) {
        this.id = id;
        this.displayName = displayName;
        this.dimensions = new TreeSet<>();
        this.gameMode = gameMode;
        this.progressAdvancements = progressAdvancements;
        this.incrementStatistics = incrementStatistics;
    }

    // ===== Getters =====

    public String id() { return id; }
    public String displayName() { return displayName; }
    public TreeSet<String> dimensions() { return dimensions; }
    public GameType gameMode() { return gameMode; }
    public boolean progressAdvancements() { return progressAdvancements; }
    public boolean incrementStatistics() { return incrementStatistics; }
    @Nullable
    public DefaultSpawn defaultSpawn() { return defaultSpawn; }

    // ===== Setters =====

    public void setId(String id) { this.id = id; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setDimensions(TreeSet<String> dimensions) { this.dimensions = dimensions; }
    public void setGameMode(GameType gameMode) { this.gameMode = gameMode; }
    public void setProgressAdvancements(boolean progressAdvancements) { this.progressAdvancements = progressAdvancements; }
    public void setIncrementStatistics(boolean incrementStatistics) { this.incrementStatistics = incrementStatistics; }
    public void setDefaultSpawn(@Nullable DefaultSpawn defaultSpawn) { this.defaultSpawn = defaultSpawn; }

    // ===== 操作 =====

    public void addDimension(String dimension) {
        DebugLogger.entering("DimPool", "addDimension", "poolId=" + this.id + ", dimension=" + dimension);
        this.dimensions.add(dimension);
        DebugLogger.stateChange("DimPool", this.id != null ? this.id : "null", "dimensions", dimension + " added, total=" + this.dimensions.size());
        DebugLogger.exiting("DimPool", "addDimension");
    }

    public void removeDimension(String dimension) {
        this.dimensions.remove(dimension);
    }

    public boolean containsDimension(String dimension) {
        DebugLogger.entering("DimPool", "containsDimension", "poolId=" + this.id + ", dimension=" + dimension);
        boolean result = this.dimensions.contains(dimension);
        DebugLogger.exiting("DimPool", "containsDimension", String.valueOf(result));
        return result;
    }

    public boolean isEmpty() {
        DebugLogger.entering("DimPool", "isEmpty", "poolId=" + this.id);
        boolean result = this.dimensions.isEmpty();
        DebugLogger.exiting("DimPool", "isEmpty", String.valueOf(result));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DimensionPool that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "DimensionPool{id='" + id + "', dimensions=" + dimensions + ", gameMode=" + gameMode + "}";
    }

    // ===== 默认出生点 =====

    /**
     * 玩家从其他池死亡复活后，首次传送回此池时的默认降落位置。
     * <p>
     * 配置示例：{@code "defaultSpawn": {"dimension": "youzaiworld:mc", "x": 0, "y": 64, "z": 0, "yaw": 0, "pitch": 0}}
     */
    public static final class DefaultSpawn {
        private String dimension;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        public DefaultSpawn() {
            this.y = 64.0;
        }

        public DefaultSpawn(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }
        public float getYaw() { return yaw; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public float getPitch() { return pitch; }
        public void setPitch(float pitch) { this.pitch = pitch; }
    }
}
