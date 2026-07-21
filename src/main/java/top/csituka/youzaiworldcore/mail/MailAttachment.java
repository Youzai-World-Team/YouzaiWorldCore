package top.csituka.youzaiworldcore.mail;

import org.jetbrains.annotations.Nullable;

/**
 * 邮件奖励附件。
 *
 * @param type    附件类型
 * @param data    COMMAND: 命令字符串；其余类型: 数值字符串
 * @param amount  数量 / 点数 / 等级
 * @param itemNbt 仅 {@link AttachmentType#ITEM} 使用：ItemStack 的 NBT 字符串序列化
 */
public record MailAttachment(
        AttachmentType type,
        String data,
        int amount,
        @Nullable String itemNbt
) {
}
