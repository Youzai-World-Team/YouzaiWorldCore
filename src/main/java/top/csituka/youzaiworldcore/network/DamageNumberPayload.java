package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：在受伤实体所在位置显示实际损失生命值的跳字。
 *
 * @param x 实体受伤时的世界 X 坐标
 * @param y 实体受伤时的世界 Y 坐标
 * @param z 实体受伤时的世界 Z 坐标
 * @param entityHeight 实体碰撞箱高度
 * @param damage 实际损失的生命值与吸收生命值总量
 */
@SuppressWarnings("null")
public record DamageNumberPayload(double x, double y, double z, float entityHeight, float damage)
        implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "damage_number");
    public static final Type<DamageNumberPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, DamageNumberPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeDouble(payload.x());
                        buf.writeDouble(payload.y());
                        buf.writeDouble(payload.z());
                        buf.writeFloat(payload.entityHeight());
                        buf.writeFloat(payload.damage());
                    },
                    buf -> new DamageNumberPayload(
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readFloat(),
                            buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
