package top.csituka.youzaiworldcore.mail;

/**
 * 奖励附件类型枚举。
 * <ul>
 *   <li>{@link #ITEM} — 物品（NBT 序列化）</li>
 *   <li>{@link #COMMAND} — 指令（支持 %player%/%uuid% 占位符）</li>
 *   <li>{@link #VANILLA_EXP} — 原版经验值</li>
 *   <li>{@link #VANILLA_LEVEL} — 原版等级</li>
 *   <li>{@link #ADVENTURE_EXP} — 本项目冒险经验</li>
 * </ul>
 */
public enum AttachmentType {
    ITEM,
    COMMAND,
    VANILLA_EXP,
    VANILLA_LEVEL,
    ADVENTURE_EXP
}
