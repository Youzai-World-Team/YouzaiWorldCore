package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端转发宠物管理命令至服务端。
 * <p>
 * {@code /yzwc} 根命令在客户端已被注册（用于 {@code /yzwc settings}），
 * 导致客户端无法识别 {@code pet} 子命令，会将 {@code pet} 误解析为
 * {@code experimental_feature} 的参数。故 {@code /yzwc pet ...} 必须在客户端
 * 仅做解析与转发，由服务端通过此数据包接收命令参数字符串并执行。
 * </p>
 *
 * @param args 命令参数字符串（如 {@code list} 或 {@code set DOGAB3F9 mode hunting}）
 */
public record PetCommandPayload(String args) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "pet_command");

    public static final Type<PetCommandPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, PetCommandPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.args),
                    buf -> new PetCommandPayload(buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
