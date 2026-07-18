package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端转发实验性功能命令（查询 / 自切换 / 全服 / 指定玩家）。
 * <p>
 * 与双开门、隐身命令同理，{@code /yzwc} 根命令在客户端被注册（用于 {@code /yzwc settings}），
 * 客户端在解析 {@code /yzwc experimental_feature ...} 时会因找不到子节点而失败。
 * 因此该命令在客户端仅做解析与转发，真正的权限校验与状态变更由服务端完成。
 * </p>
 *
 * @param id         实验性功能内部 ID（如 {@code chorus_fruit_drops}）
 * @param mode       作用域：{@code 0}=查询，{@code 1}=为自己切换，
 *                   {@code 2}=全服切换，{@code 3}=为指定玩家切换
 * @param enabled    目标开关（仅 set 类模式有意义）
 * @param targetName 目标玩家名（仅 mode=3 有意义，其余为 null）
 */
@SuppressWarnings("null")
public record ExperimentalFeaturePayload(
        String id,
        byte mode,
        boolean enabled,
        @Nullable String targetName
) implements CustomPacketPayload {

    public static final byte MODE_QUERY = 0;
    public static final byte MODE_SELF = 1;
    public static final byte MODE_ALL = 2;
    public static final byte MODE_ONLY = 3;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "experimental_feature");

    public static final Type<ExperimentalFeaturePayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExperimentalFeaturePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.id());
                        buf.writeByte(p.mode());
                        buf.writeBoolean(p.enabled());
                        buf.writeBoolean(p.targetName() != null);
                        if (p.targetName() != null) {
                            buf.writeUtf(p.targetName());
                        }
                    },
                    buf -> {
                        String id = buf.readUtf();
                        byte mode = buf.readByte();
                        boolean enabled = buf.readBoolean();
                        String targetName = buf.readBoolean() ? buf.readUtf() : null;
                        return new ExperimentalFeaturePayload(id, mode, enabled, targetName);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
