package top.csituka.youzaiworldcore.pet;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 宠物战斗模式枚举。
 * <p>
 * 定义狼在宠物系统下的四种行为模式，影响其攻击逻辑与索敌行为。
 * </p>
 */
public enum PetMode implements StringRepresentable {

    /** 狩猎：只攻击主人或信任玩家附近（10格内）主动攻击的生物，不主动索敌 */
    HUNTING,

    /** 陪伴：完全被动，不攻击任何生物，只跟随并在附近随机走动 */
    COMPANIONSHIP,

    /** 攻击：激进模式。攻击攻击主人/信任者的生物 + 主人/信任者主动攻击的生物（并集） */
    ATTACK,

    /** 守卫：仅攻击攻击主人/信任玩家的生物（被动反击），不响应主动攻击命令 */
    GUARD;

    @SuppressWarnings("null")
    public static final Codec<PetMode> CODEC = StringRepresentable.fromEnum(PetMode::values);

    /**
     * 将小写字符串解析为 PetMode。
     *
     * @param name 小写模式名称
     * @return 对应的 PetMode
     * @throws IllegalArgumentException 若无法识别
     */
    public static PetMode fromString(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }

    @Override
    @SuppressWarnings("null")
    public @NotNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
