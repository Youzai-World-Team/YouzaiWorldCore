package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：将玩家当前属性数据同步到客户端（升级时、加入时发送）。
 */
@SuppressWarnings("null")
public record AttributeSyncPayload(
        int skillPointsAvailable,
        int maxHealth,
        int healingAmplification,
        int miningSpeed,
        int movementSpeed,
        int jumpAmplitude,
        int luck,
        int meleeDamage,
        int rangedDamage,
        int damageResistance,
        int playerLevel
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "attribute_sync");

    public static final Type<AttributeSyncPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, AttributeSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.skillPointsAvailable);
                        buf.writeVarInt(p.maxHealth);
                        buf.writeVarInt(p.healingAmplification);
                        buf.writeVarInt(p.miningSpeed);
                        buf.writeVarInt(p.movementSpeed);
                        buf.writeVarInt(p.jumpAmplitude);
                        buf.writeVarInt(p.luck);
                        buf.writeVarInt(p.meleeDamage);
                        buf.writeVarInt(p.rangedDamage);
                        buf.writeVarInt(p.damageResistance);
                        buf.writeVarInt(p.playerLevel);
                    },
                    buf -> new AttributeSyncPayload(
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
