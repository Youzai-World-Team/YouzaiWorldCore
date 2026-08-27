package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * S2C：向客户端广播已核验的 Mojang 纹理属性。
 * <p>客户端把属性交给原版 SkinManager，因此皮肤、披风和鞘翅继续使用原版下载与缓存逻辑。</p>
 */
@SuppressWarnings("null")
public record MojangSkinPayload(
        UUID ownerUuid,
        UUID profileId,
        String profileName,
        String textureValue,
        String textureSignature) implements CustomPacketPayload {

    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    private static final int MAX_TEXTURE_PROPERTY_LENGTH = 8192;

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mojang_skin");
    public static final Type<MojangSkinPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, MojangSkinPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.ownerUuid());
                        buf.writeUUID(payload.profileId());
                        buf.writeUtf(payload.profileName(), 64);
                        buf.writeUtf(payload.textureValue(), MAX_TEXTURE_PROPERTY_LENGTH);
                        buf.writeUtf(payload.textureSignature(), MAX_TEXTURE_PROPERTY_LENGTH);
                    },
                    buf -> new MojangSkinPayload(
                            buf.readUUID(), buf.readUUID(), buf.readUtf(64),
                            buf.readUtf(MAX_TEXTURE_PROPERTY_LENGTH),
                            buf.readUtf(MAX_TEXTURE_PROPERTY_LENGTH)));

    public MojangSkinPayload {
        ownerUuid = ownerUuid == null ? EMPTY_UUID : ownerUuid;
        profileId = profileId == null ? EMPTY_UUID : profileId;
        profileName = profileName == null ? "" : profileName;
        textureValue = textureValue == null ? "" : textureValue;
        textureSignature = textureSignature == null ? "" : textureSignature;
    }

    /** 创建移除指定玩家正版外观缓存的通知。 */
    public static MojangSkinPayload disabled(UUID ownerUuid) {
        return new MojangSkinPayload(ownerUuid, EMPTY_UUID, "", "", "");
    }

    /** 是否携带 Mojang 会话服务返回的签名纹理属性。 */
    public boolean hasTextureProperty() {
        return !textureValue.isBlank();
    }

    /** 是否表示一个已通过服务端核验的正版档案。 */
    public boolean verified() {
        return !EMPTY_UUID.equals(profileId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
