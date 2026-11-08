package top.csituka.youzaiworldcore.util;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.References;

/**
 * 模组自行持久化的物品 NBT 的版本升级工具。
 * <p>
 * 维度池背包、邮件附件等数据由模组以 SNBT 字符串保存，不经过原版存档的 DataFixer。
 * Minecraft 更新时物品组件的格式可能变化（例如 26.3 移除了 {@code map_color}、
 * 调整了陶罐纹饰结构），未升级的旧数据会解析失败。本类使用原版 DataFixer，
 * 把物品 NBT 从写出时的数据版本升级到当前版本后再解析。
 */
public final class ItemStackNbtUpgrader {

    /**
     * Minecraft 26.2 的数据版本。
     * <p>
     * 引入版本记录之前保存的物品数据都由 26.2 构建写出，没有版本号时按此版本升级。
     */
    public static final int LEGACY_DATA_VERSION = 4903;

    private ItemStackNbtUpgrader() {
    }

    /** 当前游戏的数据版本，写出物品数据时一并保存。 */
    public static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    /**
     * 把物品 NBT 从 {@code fromVersion} 升级到当前数据版本；已是当前版本或更新时原样返回。
     */
    public static CompoundTag upgrade(DataFixer fixer, CompoundTag tag, int fromVersion) {
        int current = currentDataVersion();
        if (tag.isEmpty() || fromVersion >= current) {
            return tag;
        }
        Tag upgraded = fixer.update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag), fromVersion, current)
                .getValue();
        return upgraded instanceof CompoundTag compound ? compound : tag;
    }
}
