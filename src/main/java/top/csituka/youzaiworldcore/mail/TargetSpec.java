package top.csituka.youzaiworldcore.mail;

import java.util.Collections;
import java.util.List;

/**
 * 接收范围规格。
 * <p>用于在 {@link Mail} 中存储原始接收范围，支持编辑时预填多选框与范围变更 diff。</p>
 *
 * @param scope 范围类型：0=ALL, 1=NONADMIN, 2=PLAYER, 3=ROLE
 * @param args  名称/节点列表（PLAYER/ROLE 时有效；ALL/NONADMIN 时忽略）
 */
public record TargetSpec(
        byte scope,
        List<String> args
) {
    /** 全体（含管理） */
    public static final byte SCOPE_ALL = 0;
    /** 全体非管理 */
    public static final byte SCOPE_NONADMIN = 1;
    /** 指定玩家 */
    public static final byte SCOPE_PLAYER = 2;
    /** 角色组/权限节点 */
    public static final byte SCOPE_ROLE = 3;

    public static TargetSpec forPlayers(List<String> playerNames) {
        return new TargetSpec(SCOPE_PLAYER, Collections.unmodifiableList(playerNames));
    }

    public static TargetSpec forRoles(List<String> roleNodes) {
        return new TargetSpec(SCOPE_ROLE, Collections.unmodifiableList(roleNodes));
    }

    public static TargetSpec all() {
        return new TargetSpec(SCOPE_ALL, List.of());
    }

    public static TargetSpec nonadmin() {
        return new TargetSpec(SCOPE_NONADMIN, List.of());
    }
}
