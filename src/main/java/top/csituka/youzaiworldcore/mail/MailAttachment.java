package top.csituka.youzaiworldcore.mail;

import org.jetbrains.annotations.Nullable;

/**
 * 邮件奖励附件。
 *
 * @param type    附件类型
 * @param data    COMMAND: 命令字符串；其余类型: 数值字符串
 * @param amount  数量 / 点数 / 等级
 * @param itemNbt 仅 {@link AttachmentType#ITEM} 使用：ItemStack 的 NBT 字符串序列化
 * @param dataVersion 写出 {@code itemNbt} 时的 Minecraft 数据版本；0 表示未知（按 26.2 旧数据处理），
 *                    读取时据此用原版 DataFixer 把物品升级到当前版本
 */
public record MailAttachment(
        AttachmentType type,
        String data,
        int amount,
        @Nullable String itemNbt,
        int dataVersion
) {

    /** 物品数据的有效来源版本：未记录时视为 26.2 写出的旧数据。 */
    public int effectiveDataVersion() {
        return dataVersion > 0 ? dataVersion : top.csituka.youzaiworldcore.util.ItemStackNbtUpgrader.LEGACY_DATA_VERSION;
    }
}
