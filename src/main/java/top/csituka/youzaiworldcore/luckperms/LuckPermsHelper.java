package top.csituka.youzaiworldcore.luckperms;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LuckPerms 权限集成工具类。
 * <p>
 * 提供基于 LuckPerms API 的细粒度权限检查，同时包含必要的降级保障：</p>
 * <ul>
 *   <li>当 LuckPerms 模组未安装时 → 所有直接 LP 方法返回空值/ {@code false}，<b>不会</b>
 *       导致模组崩溃或抛出异常</li>
 *   <li>调用方（如命令注册处）需通过 {@link #isLuckPermsLoaded()} 判断当前模式，
 *       并在 LP 不可用时自行回退到原版 OP 等级检查（提供 {@link CommandSourceStack#hasPermission}）</li>
 *   <li>所有公共方法均为 null-safe 和线程安全</li>
 * </ul>
 *
 * <p>
 * 权限节点命名遵循 {@code domain.subdomain.action} 规范：</p>
 * <ul>
 *   <li>{@code youzaiworldcore.command.teleport_world} — 跨维度传送</li>
 *   <li>{@code youzaiworldcore.command.open_menu} — 打开管理菜单</li>
 *   <li>{@code youzaiworldcore.command.*} — 所有命令的通配符</li>
 *   <li>{@code youzaiworldcore.*} — 所有模组权限的通配符</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 在命令注册时：
 * .requires(source -> LuckPermsHelper.checkPermission(
 *     source, LuckPermsHelper.PERMISSION_TELEPORT_WORLD, Commands.LEVEL_ADMINS))
 *
 * // 或在需要时直接判断：
 * if (LuckPermsHelper.isLuckPermsLoaded()) {
 *     LuckPermsHelper.getApi().ifPresent(api -> { ... });
 * }
 * }</pre>
 */
public final class LuckPermsHelper {

    // ===== 权限节点常量 =====
    // 按照 LuckPerms 推荐的 域.子域.操作 三级命名规范

    /** 跨维度传送命令 {@code /yzwc teleport_world} */
    public static final String PERMISSION_TELEPORT_WORLD = "youzaiworldcore.command.teleport_world";

    /** 打开管理菜单命令 {@code /yzwc open_menu} */
    public static final String PERMISSION_OPEN_MENU = "youzaiworldcore.command.open_menu";

    /** 模组重载命令 {@code /yzwc reload} */
    public static final String PERMISSION_RELOAD = "youzaiworldcore.command.reload";

    /** 实验性功能切换命令 {@code /yzwc experimental_feature} */
    public static final String PERMISSION_EXPERIMENTAL_FEATURE = "youzaiworldcore.command.experimental_feature";

    /** 实验性功能查询 {@code /yzwc experimental_feature <id>} */
    public static final String PERMISSION_EXPERIMENTAL_FEATURE_QUERY = "youzaiworldcore.command.experimental_feature.query";

    /** 实验性功能自切换 {@code /yzwc experimental_feature <id> <bool>}（为自己切换） */
    public static final String PERMISSION_EXPERIMENTAL_FEATURE_SELF = "youzaiworldcore.command.experimental_feature.self";

    /** 实验性功能管理 {@code /yzwc experimental_feature <id> <bool> all/only} */
    public static final String PERMISSION_EXPERIMENTAL_FEATURE_ADMIN = "youzaiworldcore.command.experimental_feature.admin";

    /** 管理：创建离线玩家账户 */
    public static final String PERMISSION_ACCOUNT_MGR_CREATE = "youzaiworldcore.command.account.mgr.create";

    /** 管理：重置玩家密码 */
    public static final String PERMISSION_ACCOUNT_MGR_RESET_PASSWORD = "youzaiworldcore.command.account.mgr.reset_password";

    /** 管理：删除玩家账户 */
    public static final String PERMISSION_ACCOUNT_MGR_DELETE = "youzaiworldcore.command.account.mgr.delete";

    /** 管理：查看/设置会话超时时间 */
    public static final String PERMISSION_ACCOUNT_MGR_SESSION_TIMEOUT = "youzaiworldcore.command.account.mgr.session_timeout";

    /** 管理：查看/设置登录锁定冷却时间 */
    public static final String PERMISSION_ACCOUNT_MGR_LOGIN_COOLDOWN = "youzaiworldcore.command.account.mgr.login_cooldown";

    /** 管理：查询账户锁定状态 */
    public static final String PERMISSION_ACCOUNT_MGR_LOGIN_COOLDOWN_STATUS = "youzaiworldcore.command.account.mgr.login_cooldown.status";

    /** 管理：解锁玩家账户 */
    public static final String PERMISSION_ACCOUNT_MGR_LOGIN_COOLDOWN_UNLOCK = "youzaiworldcore.command.account.mgr.login_cooldown.unlock";

    /** 管理命令通配符（等价于所有 mgr 子命令的合集） */
    public static final String PERMISSION_ACCOUNT_MGR_WILDCARD = "youzaiworldcore.command.account.mgr.*";

    /** 所有命令的通配符 */
    public static final String PERMISSION_COMMAND_WILDCARD = "youzaiworldcore.command.*";

    /** 整个模组的通配符权限 */
    public static final String PERMISSION_WILDCARD = "youzaiworldcore.*";

    /** 存储 LuckPerms 是否已加载的判断结果，避免重复调用 FabricLoader */
    private static final boolean LUCKPERMS_LOADED;

    /** 懒加载的 LuckPerms API 实例 */
    private static LuckPerms luckPermsInstance = null;

    static {
        // 初始化时检查 LuckPerms 模组是否已加载
        LUCKPERMS_LOADED = FabricLoader.getInstance().isModLoaded("luckperms");
        if (LUCKPERMS_LOADED) {
            YouzaiworldCore.LOGGER.info("LuckPerms API 可用，将启用细粒度权限控制");
        } else {
            YouzaiworldCore.LOGGER.info("LuckPerms 未安装，将回退到原版 OP 等级权限检查");
        }
    }

    /**
     * 私有构造方法，防止实例化。
     */
    private LuckPermsHelper() {
    }

    // ==================== 基础状态检查 ====================

    /**
     * 检查 LuckPerms 模组是否已加载到服务端。
     *
     * @return true 如果 LuckPerms 模组已加载且 API 可用
     */
    public static boolean isLuckPermsLoaded() {
        return LUCKPERMS_LOADED;
    }

    /**
     * 获取 LuckPerms API 实例。
     * <p>
     * 仅在 {@link #isLuckPermsLoaded()} 返回 {@code true} 时调用此方法。
     * 如果 LuckPerms 未加载，返回 {@link Optional#empty()} 而不是抛出异常。
     * </p>
     *
     * @return 包含 {@link LuckPerms} 实例的 {@link Optional}；如果 LP 未加载则为空
     */
    public static Optional<LuckPerms> getApi() {
        if (!LUCKPERMS_LOADED) {
            return Optional.empty();
        }
        if (luckPermsInstance == null) {
            try {
                luckPermsInstance = LuckPermsProvider.get();
            } catch (Exception e) {
                YouzaiworldCore.LOGGER.error("获取 LuckPerms API 失败: {}", e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.of(luckPermsInstance);
    }

    // ==================== 权限检查核心方法 ====================

    /**
     * 检查命令执行源（玩家）是否拥有指定的 LuckPerms 权限节点。
     * <p>
     * 该方法配合 Brigadier 的 {@code .requires()} 使用：</p>
     * <ul>
     *   <li>如果 LuckPerms 已安装 → 通过 LuckPerms 原生 API 检查权限节点</li>
     *   <li>如果 LuckPerms 未安装 → 回退到 {@code fallbackPermission} 对应的原版 OP 等级检查</li>
     *   <li>如果执行者不是玩家（如控制台）→ 直接放行（{@code true}）</li>
     *   <li>如果发生任何异常 → 安全地返回 {@code false}</li>
     * </ul>
     *
     * @param source               命令执行源
     * @param permission           要检查的 LuckPerms 权限节点
     * @param fallbackPermission   当 LP 不可用时回退使用的原版 {@link PermissionCheck}
     *                             （通常为 {@code Commands.LEVEL_ADMINS} 或 {@code Commands.LEVEL_ALL}）
     * @return true 如果执行者拥有该权限
     */
    public static boolean checkPermission(CommandSourceStack source, String permission,
                                          PermissionCheck fallbackPermission) {
        if (source == null || permission == null || permission.isEmpty()) {
            return false;
        }

        // 非玩家（控制台/命令方块）始终放行
        if (!source.isPlayer()) {
            return true;
        }

        try {
            // 如果 LuckPerms 已加载，优先使用原生 LP API 检查
            if (LUCKPERMS_LOADED) {
                ServerPlayer player = source.getPlayerOrException();
                User user = getApi()
                        .map(api -> api.getUserManager().getUser(player.getUUID()))
                        .orElse(null);

                if (user != null) {
                    boolean hasPermission = user.getCachedData()
                            .getPermissionData()
                            .checkPermission(permission)
                            .asBoolean();
                    if (hasPermission) {
                        return true;
                    }
                }
                // LP 已加载但玩家没有明确设置该权限 → 回退到 OP 等级检查
            }
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.debug("LuckPerms 权限检查异常，回退到 OP 等级: {}", e.getMessage());
        }

        // 降级到原版 OP 等级检查
        return fallbackPermission.check(source.permissions());
    }

    // ==================== 直接 LuckPerms API 方法 ====================

    /**
     * 通过 LuckPerms 原生 API 检查玩家是否拥有指定权限节点。
     * <p>
     * 与 {@link #checkPermission(CommandSourceStack, String, PermissionCheck)}
     * 的区别：此方法<b>仅</b>查询 LuckPerms 中的权限设置，不会回退到 OP 等级检查。
     * 如果 LuckPerms 未安装，直接返回 {@code false}。
     * </p>
     *
     * @param player     玩家 UUID
     * @param permission 权限节点
     * @return true 如果 LuckPerms 已安装且玩家拥有该权限
     */
    public static boolean checkLuckPermsOnly(UUID player, String permission) {
        if (!LUCKPERMS_LOADED || player == null || permission == null || permission.isEmpty()) {
            return false;
        }

        try {
            LuckPerms api = getApi().orElse(null);
            if (api == null) {
                return false;
            }

            User user = api.getUserManager().getUser(player);
            if (user == null) {
                return false;
            }
            return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("LuckPerms 权限检查失败 [{}]: {}", player, e.getMessage());
            return false;
        }
    }

    /**
     * 通过 LuckPerms 原生 API 获取玩家所属的所有用户组名称。
     * <p>
     * 如果 LuckPerms 未安装或玩家数据不可用，返回空集合。
     * </p>
     *
     * @param player 玩家 UUID
     * @return 玩家所属的用户组名称集合；永远不会为 {@code null}
     */
    public static Set<String> getPlayerGroups(UUID player) {
        if (!LUCKPERMS_LOADED || player == null) {
            return Set.of();
        }

        try {
            LuckPerms api = getApi().orElse(null);
            if (api == null) {
                return Set.of();
            }

            User user = api.getUserManager().getUser(player);
            if (user == null) {
                // 尝试从存储加载用户数据
                user = api.getUserManager().loadUser(player).getNow(null);
                if (user == null) {
                    return Set.of();
                }
            }

            return user.getNodes(NodeType.INHERITANCE).stream()
                    .map(InheritanceNode::getGroupName)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("获取 LuckPerms 用户组失败 [{}]: {}", player, e.getMessage());
            return Set.of();
        }
    }

    /**
     * 获取玩家的主用户组名称。
     *
     * @param player 玩家 UUID
     * @return 主用户组名称；如果不可用则返回 {@code "default"}
     */
    public static String getPrimaryGroup(UUID player) {
        if (!LUCKPERMS_LOADED || player == null) {
            return "default";
        }

        try {
            LuckPerms api = getApi().orElse(null);
            if (api == null) {
                return "default";
            }

            User user = api.getUserManager().getUser(player);
            if (user == null) {
                return "default";
            }
            return user.getPrimaryGroup();
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("获取 LuckPerms 主用户组失败 [{}]: {}", player, e.getMessage());
            return "default";
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 构建权限节点字符串。
     * <p>
     * 使用 {@code .} 连接各段，自动处理空段和 null。
     * 示例：{@code buildNode("youzaiworldcore", "command", "teleport_world")}
     * 返回 {@code "youzaiworldcore.command.teleport_world"}
     * </p>
     *
     * @param segments 权限节点各段
     * @return 拼接后的完整权限节点
     */
    public static String buildNode(String... segments) {
        if (segments == null || segments.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            if (segment != null && !segment.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(segment);
            }
        }
        return sb.toString();
    }

    /**
     * 判断权限系统当前是否处于"增强模式"。
     * <p>
     * 如果 LuckPerms 已安装，服主可以分配细粒度的权限节点给特定玩家或用户组；
     * 如果未安装，所有权限检查将依赖原版 OP 等级系统。
     * </p>
     *
     * @return true 如果权限系统处于"增强模式"（即 LuckPerms 已加载）
     */
    public static boolean isEnhancedMode() {
        return LUCKPERMS_LOADED;
    }
}
