package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.mail.AttachmentType;

/**
 * 网络传输用的附件数据结构（MailAdminSendPayload / MailAdminEditPayload 使用）。
 * <p>
 * 与 {@link top.csituka.youzaiworldcore.mail.MailAttachment} 不同：
 * ITEM 类型直接用 {@link ItemStack} 编码（{@code ItemStack.OPTIONAL_STREAM_CODEC}）而非 NBT 字符串。
 * </p>
 */
@SuppressWarnings("null")
public record AttachmentData(
        AttachmentType type,
        String data,
        int amount,
        @Nullable ItemStack itemStack
) {

    public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeEnum(p.type());
                        buf.writeUtf(p.data() != null ? p.data() : "");
                        buf.writeVarInt(p.amount());
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,
                                p.itemStack() != null ? p.itemStack() : ItemStack.EMPTY);
                    },
                    buf -> {
                        AttachmentType type = buf.readEnum(AttachmentType.class);
                        String data = buf.readUtf();
                        int amount = buf.readVarInt();
                        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        return new AttachmentData(type, data, amount,
                                stack.isEmpty() ? null : stack);
                    }
            );
}
