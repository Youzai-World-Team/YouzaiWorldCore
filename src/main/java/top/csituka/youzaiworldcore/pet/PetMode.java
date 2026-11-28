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

    /** 狩猎：支援主人或信任玩家在 10 格内发起的攻击，也会自卫。 */
    HUNTING,

    /** 陪伴：完全被动，不攻击任何生物，只跟随并在附近随机走动 */
    COMPANIONSHIP,

    /** 攻击：支援主人或信任玩家的进攻与反击，也会自卫。 */
    ATTACK,

    /** 守卫：保护受击的主人或信任玩家，也会自卫。 */
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
