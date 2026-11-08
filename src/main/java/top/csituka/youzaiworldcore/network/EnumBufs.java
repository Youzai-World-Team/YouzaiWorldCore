package top.csituka.youzaiworldcore.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 网络包中枚举值的读写工具。
 * <p>
 * 线格式与原版已过时的 {@code FriendlyByteBuf#writeEnum / readEnum} 完全一致（VarInt 序号）；
 * 读取时额外校验序号范围，畸形数据包会以 {@link DecoderException} 拒绝，而不是抛出数组越界。
 * 收发双方始终是同一版本的模组，按序号编码是安全的；调整枚举常量顺序时需同时升级客户端与服务端。
 */
public final class EnumBufs {

    private EnumBufs() {
    }

    /** 写出枚举序号。 */
    public static void write(FriendlyByteBuf buf, Enum<?> value) {
        buf.writeVarInt(value.ordinal());
    }

    /** 读取枚举序号并转换为常量，序号越界时拒绝该数据包。 */
    public static <T extends Enum<T>> T read(FriendlyByteBuf buf, Class<T> type) {
        T[] values = type.getEnumConstants();
        int ordinal = buf.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("无效的 " + type.getSimpleName() + " 序号：" + ordinal);
        }
        return values[ordinal];
    }
}
