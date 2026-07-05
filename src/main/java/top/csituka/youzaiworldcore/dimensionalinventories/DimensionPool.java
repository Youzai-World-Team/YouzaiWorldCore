package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.annotations.SerializedName;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 维度池 — 一组维度的集合，池内玩家共享独立的状态（背包、生命、经验等）。
 * 当玩家在不同池之间穿越时，自动完成状态保存/加载。
 */
public final class DimensionPool {

    /** 池的唯一标识符，如 "youzaiworldcore:survival_world_pool" */
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

    // ===== Setters =====

    public void setId(String id) { this.id = id; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setDimensions(TreeSet<String> dimensions) { this.dimensions = dimensions; }
    public void setGameMode(GameType gameMode) { this.gameMode = gameMode; }
    public void setProgressAdvancements(boolean progressAdvancements) { this.progressAdvancements = progressAdvancements; }
    public void setIncrementStatistics(boolean incrementStatistics) { this.incrementStatistics = incrementStatistics; }

    // ===== 操作 =====

    public void addDimension(String dimension) {
        this.dimensions.add(dimension);
    }

    public void removeDimension(String dimension) {
        this.dimensions.remove(dimension);
    }

    public boolean containsDimension(String dimension) {
        return this.dimensions.contains(dimension);
    }

    public boolean isEmpty() {
        return this.dimensions.isEmpty();
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
}
