package top.csituka.youzaiworldcore.mail;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.network.MailUnreadCountPayload;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.skill.PlayerLevelData;
import top.csituka.youzaiworldcore.skill.PlayerLevelStorage;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 邮件系统业务逻辑核心。
 * <p>
 * 邮件正文与每玩家收件箱的<b>权威存储在 Api 服务端</b>（见 {@link MailApiClient}），
 * 本类负责三件仍然只能在 Minecraft 服务端做的事：
 * </p>
 * <ol>
 * <li><b>接收范围解析</b> —— NONADMIN / ROLE 需要 LuckPerms，Api 拿不到权限数据；</li>
 * <li><b>奖励发放</b> —— 物品 / 指令 / 经验都要落到在线玩家身上；</li>
 * <li><b>在线推送</b> —— 未读徽标与列表更新走 S2C 数据包。</li>
 * </ol>
 * <p>
 * 所有 Api 调用一律 {@code CompletableFuture.supplyAsync} 后
 * 再 {@code server.execute(...)} 回主线程，避免 HTTP 往返卡住主线程。
 * </p>
 */
@SuppressWarnings("null")
public class MailManager {

    private static final String MODULE = "MailManager";

    // ========================================================================
    // 接收范围解析（依赖 LuckPerms，必须留在模组侧）
    // ========================================================================

    /**
     * 解析接收范围列表为收件人 UUID 并集。
     * <p>
     * 以 {@link AccountDataStorage#getAll()} 的全部已注册账户（含离线）为全集。
     * 解析结果随发布 / 编辑请求一起发给 Api，由 Api 建立收件箱引用。
     * </p>
     *
     * @param targets 接收范围列表（可多选）
     * @return 去重后的收件人 UUID 集合
     */
    public static Set<UUID> resolveTargets(List<TargetSpec> targets) {
        DebugLogger.entering(MODULE, "resolveTargets", "targets=" + (targets == null ? 0 : targets.size()));
        Set<UUID> recipients = new HashSet<>();
        if (targets == null || targets.isEmpty()) {
            DebugLogger.exiting(MODULE, "resolveTargets", "empty targets");
            return recipients;
        }

        Map<String, PlayerAccount> accounts = AccountDataStorage.getAll();
        String permissionNode = MailSettings.get().getMailPermissionNode();

        for (TargetSpec spec : targets) {
            switch (spec.scope()) {
                case TargetSpec.SCOPE_ALL -> {
                    for (PlayerAccount account : accounts.values()) {
                        addAccount(recipients, account);
                    }
                }
                case TargetSpec.SCOPE_NONADMIN -> {
                    // 离线账户查不到原版 OP 等级，只能按 LuckPerms 节点判定：
                    // 未持有邮件权限节点的账户都算「非管理」。
                    for (PlayerAccount account : accounts.values()) {
                        UUID uuid = parseUuid(account);
                        if (uuid == null || LuckPermsHelper.checkLuckPermsOnly(uuid, permissionNode)) {
                            continue;
                        }
                        recipients.add(uuid);
                    }
                }
                case TargetSpec.SCOPE_PLAYER -> {
                    for (String name : spec.args()) {
                        addAccount(recipients, AccountDataStorage.get(name));
                    }
                }
                case TargetSpec.SCOPE_ROLE -> {
                    Set<String> wanted = new HashSet<>(spec.args());
                    if (wanted.isEmpty()) {
                        continue;
                    }
                    for (PlayerAccount account : accounts.values()) {
                        UUID uuid = parseUuid(account);
                        if (uuid == null) {
                            continue;
                        }
                        // 每个账户只查一次权限组，再与所选角色求交集。
                        Set<String> groups = LuckPermsHelper.getPlayerGroups(uuid);
                        if (groups.stream().anyMatch(wanted::contains)) {
                            recipients.add(uuid);
                        }
                    }
                }
                default -> DebugLogger.warn(MODULE, "未知 scope: %d", spec.scope());
            }
        }

        DebugLogger.info(MODULE, "resolveTargets 结果: %d 个 UUID (去重后)", recipients.size());
        DebugLogger.exiting(MODULE, "resolveTargets");
        return recipients;
    }

    private static void addAccount(Set<UUID> recipients, PlayerAccount account) {
        UUID uuid = parseUuid(account);
        if (uuid != null) {
            recipients.add(uuid);
        }
    }

    private static UUID parseUuid(PlayerAccount account) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(account.uuid);
        } catch (IllegalArgumentException ignored) {
            DebugLogger.warn(MODULE, "账户 UUID 格式无效，已跳过：%s", account.username);
            return null;
        }
    }

    // ========================================================================
    // 奖励发放（Api 已记账，这里只负责给到玩家身上）
    // ========================================================================

    /**
     * 把邮件附件实际发放给玩家。<b>必须在服务端主线程调用。</b>
     * <p>
     * 领取的记账（{@code claimed} 标记与重复领取拦截）已由
     * {@code POST /api/game/mail/claim} 原子完成，本方法只做发放：
     * 单个附件失败只记日志，不影响其余附件。
     * </p>
     *
     * @param player 领取玩家
     * @param mail   Api 返回的权威邮件（含附件）
     */
    public static void applyAttachments(ServerPlayer player, Mail mail) {
        DebugLogger.entering(MODULE, "applyAttachments",
                "player=" + player.getScoreboardName() + ", mailId=" + mail.getId());
        MinecraftServer server = player.level().getServer();
        var lookup = server.registryAccess();
        List<MailAttachment> attachments = mail.getAttachments();
        if (attachments == null) {
            DebugLogger.exiting(MODULE, "applyAttachments", "no attachments");
            return;
        }

        for (MailAttachment att : attachments) {
            try {
                switch (att.type()) {
                    case ITEM -> {
                        if (att.itemNbt() == null || att.itemNbt().isEmpty())
                            break;
                        var tag = net.minecraft.nbt.TagParser.parseCompoundFully(att.itemNbt());
                        com.mojang.serialization.DataResult<ItemStack> result = ItemStack.CODEC.parse(
                                lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                                (net.minecraft.nbt.Tag) tag);
                        ItemStack stack = result.result().orElse(ItemStack.EMPTY);
                        if (!stack.isEmpty()) {
                            stack.setCount(att.amount() > 0 ? Math.min(att.amount(), stack.getMaxStackSize()) : 1);
                            if (!player.getInventory().add(stack)) {
                                // 背包满则掉落
                                var itemEntity = new ItemEntity(player.level(),
                                        player.getX(), player.getY(), player.getZ(), (ItemStack) stack);
                                player.level().addFreshEntity(itemEntity);
                                DebugLogger.info(MODULE, "背包满，物品掉落: %s x%d", stack.getDisplayName().getString(),
                                        stack.getCount());
                            }
                        }
                    }
                    case COMMAND -> {
                        String cmd = att.data()
                                .replace("%player%", player.getScoreboardName())
                                .replace("%uuid%", player.getUUID().toString());
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
                    }
                    case VANILLA_EXP -> player.giveExperiencePoints(att.amount());
                    case VANILLA_LEVEL -> player.giveExperienceLevels(att.amount());
                    case ADVENTURE_EXP -> AdventureLevelManager.grantExp(player, att.amount());
                    case ADVENTURE_LEVEL -> grantAdventureLevels(player, att.amount());
                }
            } catch (Exception e) {
                DebugLogger.exception(MODULE, "applyAttachments", e);
            }
        }
        DebugLogger.info(MODULE, "玩家 %s 已领取奖励 mailId=%s", player.getScoreboardName(), mail.getId());
        DebugLogger.exiting(MODULE, "applyAttachments");
    }

    /**
     * 发放本项目冒险等级：把「升 N 级所需的总经验差值」折算成经验后调用
     * {@link AdventureLevelManager#grantExp}，从而复用其升级发技能点、属性同步与 HUD 推送。
     *
     * @param player 接收玩家
     * @param levels 要提升的等级数（正整数）
     */
    private static void grantAdventureLevels(ServerPlayer player, int levels) {
        if (levels <= 0) {
            return;
        }
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
        int currentLevel = data.getLevel();
        long targetTotal = AdventureLevelManager.totalExpForLevel(currentLevel + levels);
        int delta = (int) Math.max(1L, targetTotal - data.totalExp);
        DebugLogger.info(MODULE, "发放冒险等级: player=%s, levels=%d, Lv.%d → Lv.%d, exp=+%d",
                player.getScoreboardName(), levels, currentLevel, currentLevel + levels, delta);
        AdventureLevelManager.grantExp(player, delta);
    }

    // ========================================================================
    // 未读徽标推送
    // ========================================================================

    /**
     * 向单个在线玩家回推未读数与发布权限。
     * <p>
     * 读 / 领取 / 删除 / 星标 / 撤回 / 编辑之后都要调用：客户端徽标与界面红点完全依赖这个权威值，
     * 少推一次就会出现「已读但红点还在」「信箱空了仍显示数量」。
     * </p>
     *
     * @param player 目标玩家
     * @param unread Api 返回的权威未读数
     */
    public static void sendUnread(ServerPlayer player, int unread) {
        boolean canSend = MailPermissionHelper.hasMailPermission(player);
        ServerPlayNetworking.send(player, new MailUnreadCountPayload(unread, canSend));
        DebugLogger.trace(MODULE, "同步未读数: player=%s, unread=%d", player.getScoreboardName(), unread);
    }

    /** 异步拉取并回推单个玩家的未读数（玩家加入服务器时使用）。 */
    public static void refreshUnread(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID uuid = player.getUUID();
        CompletableFuture.supplyAsync(() -> MailApiClient.fetchUnread(uuid))
                .whenComplete((result, error) -> server.execute(() -> {
                    if (error != null) {
                        DebugLogger.exception(MODULE, "refreshUnread", error);
                        return;
                    }
                    if (result == null || !result.success()) {
                        DebugLogger.warn(MODULE, "拉取未读数失败: player=%s, %s",
                                player.getScoreboardName(), result == null ? "无响应" : result.message());
                        return;
                    }
                    if (player.hasDisconnected()) {
                        return;
                    }
                    sendUnread(player, result.unread());
                }));
    }

    /**
     * 异步批量刷新一批收件人中「当前在线」玩家的未读徽标。
     * <p>
     * 群发、撤回、编辑与过期清理之后调用，一次 Api 请求覆盖全部在线玩家。
     * </p>
     *
     * @param server     服务端实例
     * @param recipients 受影响的收件人（离线的会被忽略）
     */
    public static void refreshUnreadFor(MinecraftServer server, Collection<UUID> recipients) {
        if (server == null || recipients.isEmpty()) {
            return;
        }
        List<UUID> online = new ArrayList<>();
        for (UUID uuid : recipients) {
            if (server.getPlayerList().getPlayer(uuid) != null) {
                online.add(uuid);
            }
        }
        if (online.isEmpty()) {
            return;
        }
        CompletableFuture.supplyAsync(() -> MailApiClient.fetchUnreadBatch(online))
                .whenComplete((counts, error) -> server.execute(() -> {
                    if (error != null) {
                        DebugLogger.exception(MODULE, "refreshUnreadFor", error);
                        return;
                    }
                    if (counts == null || counts.isEmpty()) {
                        return;
                    }
                    for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
                        ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
                        if (target != null) {
                            sendUnread(target, entry.getValue());
                        }
                    }
                }));
    }

    /**
     * 异步刷新<b>全部在线玩家</b>的未读徽标。
     * <p>
     * 游戏内的发布 / 领取 / 撤回都会即时回推未读数，但后台管理页
     * （{@code POST /api/admin/mails}）发布的邮件没有 S2C 触发点 —— Api 无法主动
     * 通知模组。因此按 {@link MailSettings#getUnreadRefreshIntervalTicks()} 周期性
     * 批量拉一次，让红点最迟在一个周期内自动点亮。
     * </p>
     * <p>
     * 整批玩家只发一次 Api 请求；没有在线玩家时直接返回，不产生任何请求。
     * </p>
     *
     * @param server 服务端实例
     */
    public static void refreshUnreadForOnline(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<UUID> online = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
        }
        if (online.isEmpty()) {
            return;
        }
        DebugLogger.trace(MODULE, "周期刷新未读徽标: online=%d", online.size());
        refreshUnreadFor(server, online);
    }

    // ========================================================================
    // 过期清理
    // ========================================================================

    /**
     * 周期性清理过期邮件（异步）。
     * <p>
     * 是否保留被星标的过期邮件由 {@link MailSettings#isKeepStarredAfterExpire()} 决定，
     * 与界面提示「已收藏的过期邮件将保留」以及收件箱加载时的剔除规则保持一致。
     * </p>
     *
     * @param server 服务端实例，用于清理后刷新在线玩家徽标
     */
    public static void purgeAsync(MinecraftServer server) {
        boolean keepStarred = MailSettings.get().isKeepStarredAfterExpire();
        CompletableFuture.supplyAsync(() -> MailApiClient.purge(keepStarred))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        DebugLogger.exception(MODULE, "purgeAsync", error);
                        return;
                    }
                    if (result == null || !result.success()) {
                        DebugLogger.warn(MODULE, "清理过期邮件失败：%s",
                                result == null ? "无响应" : result.message());
                        return;
                    }
                    if (result.removed() > 0) {
                        DebugLogger.info(MODULE, "清理过期邮件: 移除 %d 封, 整理引用 %d 条",
                                result.removed(), result.prunedRefs());
                    }
                    if (server != null && !result.affected().isEmpty()) {
                        server.execute(() -> refreshUnreadFor(server, result.affected()));
                    }
                });
    }

    /**
     * 服务端启动清理：删除「已过期」且「没有任何玩家星标过」的邮件，
     * 并顺带剔除指向已删除邮件的悬空收件箱引用。
     * <p>
     * 由 {@code YouzaiworldCore} 在 {@code ServerLifecycleEvents.SERVER_STARTED} 调用。
     * 与 {@link #purgeAsync} 的区别：这里无视配置，一律保留被星标的过期邮件。
     * </p>
     *
     * @param server 服务端实例
     */
    public static void purgeOnServerStart(MinecraftServer server) {
        DebugLogger.entering(MODULE, "purgeOnServerStart");
        CompletableFuture.supplyAsync(() -> MailApiClient.purge(true))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        DebugLogger.exception(MODULE, "purgeOnServerStart", error);
                        return;
                    }
                    if (result == null || !result.success()) {
                        YouzaiworldCore.LOGGER.warn("邮件启动清理失败：{}",
                                result == null ? "Api 无响应" : result.message());
                        return;
                    }
                    YouzaiworldCore.LOGGER.info("邮件启动清理：删除过期且无人星标的邮件 {} 封，整理收件箱引用 {} 条",
                            result.removed(), result.prunedRefs());
                    DebugLogger.info(MODULE, "启动清理完成: removed=%d, prunedRefs=%d",
                            result.removed(), result.prunedRefs());
                });
        DebugLogger.exiting(MODULE, "purgeOnServerStart");
    }

    /**
     * 账户注销时清空其收件箱（异步）。邮件正文保留，其他收件人不受影响。
     *
     * @param playerUuid 被注销账户的玩家 UUID
     */
    public static void onAccountDeleted(UUID playerUuid) {
        DebugLogger.entering(MODULE, "onAccountDeleted", "playerUuid=" + playerUuid);
        CompletableFuture.runAsync(() -> MailApiClient.deleteBox(playerUuid))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        DebugLogger.exception(MODULE, "onAccountDeleted", error);
                    }
                });
        DebugLogger.exiting(MODULE, "onAccountDeleted");
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /**
     * 生成展示用范围摘要。
     */
    public static String generateScopeSummary(List<TargetSpec> targets) {
        if (targets == null || targets.isEmpty())
            return "";
        return targets.stream().map(spec -> switch (spec.scope()) {
            case TargetSpec.SCOPE_ALL -> "全体";
            case TargetSpec.SCOPE_NONADMIN -> "非管理";
            case TargetSpec.SCOPE_PLAYER -> "指定:" + String.join(",", spec.args());
            case TargetSpec.SCOPE_ROLE -> "角色:" + String.join(",", spec.args());
            default -> "未知";
        }).collect(Collectors.joining("+"));
    }

    /**
     * 根据过期选项编码计算过期时间戳。
     *
     * @param expireOption 0=1 天、1=7 天、2=30 天、3=永久
     * @return 过期时间戳（毫秒）；永久返回 null
     */
    public static Long computeExpireTime(byte expireOption) {
        long now = System.currentTimeMillis();
        return switch (expireOption) {
            case 0 -> now + 24L * 60 * 60 * 1000;
            case 1 -> now + 7L * 24 * 60 * 60 * 1000;
            case 2 -> now + 30L * 24 * 60 * 60 * 1000;
            case 3 -> null;
            default -> now + 30L * 24 * 60 * 60 * 1000;
        };
    }
}
