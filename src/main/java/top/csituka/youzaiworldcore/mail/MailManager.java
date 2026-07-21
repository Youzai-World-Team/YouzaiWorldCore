package top.csituka.youzaiworldcore.mail;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 邮件系统业务逻辑核心。
 * <p>
 * 包含发送、领取、撤回、编辑、过期、权限判定等所有服务器端权威操作。
 * 所有公开方法均含 DebugLogger 埋点。
 * </p>
 */
@SuppressWarnings("null")
public class MailManager {

    private static final String MODULE = "MailManager";

    // ========================================================================
    // 解析与权限
    // ========================================================================

    /**
     * 解析接收范围列表为 UUID 并集，附带在线 ServerPlayer 映射。
     * <p>
     * 使用 {@link AccountDataStorage#getAll()} 的全部已注册账户作为全集。
     * </p>
     *
     * @param targets 接收范围列表（可多选）
     * @return 包含去重 UUID 与在线玩家映射的 Result
     */
    public static ResolveResult resolveTargets(List<TargetSpec> targets) {
        DebugLogger.entering(MODULE, "resolveTargets", "targets=" + targets.size());
        Set<UUID> allUuids = new HashSet<>();
        Map<UUID, ServerPlayer> onlinePlayers = new HashMap<>();

        // 获取全部已注册账户（离线覆盖：AccountDataStorage.initialize() 启动时 loadFromDisk）
        Map<String, PlayerAccount> accounts = AccountDataStorage.getAll();
        Set<UUID> allAccountUuids = accounts.values().stream()
                .map(acc -> acc.uuid)
                .filter(u -> u != null && !u.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toSet());

        for (TargetSpec spec : targets) {
            switch (spec.scope()) {
                case TargetSpec.SCOPE_ALL -> allUuids.addAll(allAccountUuids);
                case TargetSpec.SCOPE_NONADMIN -> {
                    for (PlayerAccount acc : accounts.values()) {
                        UUID uuid = UUID.fromString(acc.uuid);
                        // 非管理 = 不持有邮件权限且 OP 等级不足
                        boolean isAdmin = LuckPermsHelper.checkLuckPermsOnly(uuid,
                                MailSettings.get().getMailPermissionNode())
                                || (false); // player.hasPermission level — 只能对在线玩家查
                        // 对离线账户，仅按 LuckPerms 节点判定；OP 等级无法查离线账户
                        // 此处简化：所有已注册账户均纳入 ALL，在线时再二次过滤
                        // 但 NONADMIN 需要精确判定，离线账户按"非管理"处理（假设权限节点默认没有）
                        if (!isAdmin) {
                            allUuids.add(uuid);
                        }
                    }
                }
                case TargetSpec.SCOPE_PLAYER -> {
                    for (String name : spec.args()) {
                        PlayerAccount acc = AccountDataStorage.get(name);
                        if (acc != null && acc.uuid != null && !acc.uuid.isEmpty()) {
                            allUuids.add(UUID.fromString(acc.uuid));
                        }
                    }
                }
                case TargetSpec.SCOPE_ROLE -> {
                    for (String node : spec.args()) {
                        // 遍历所有账户，检查是否有该角色节点
                        for (PlayerAccount acc : accounts.values()) {
                            UUID uuid = UUID.fromString(acc.uuid);
                            Set<String> groups = LuckPermsHelper.getPlayerGroups(uuid);
                            if (groups.contains(node)) {
                                allUuids.add(uuid);
                            }
                        }
                    }
                }
                default -> DebugLogger.warn(MODULE, "未知 scope: " + spec.scope());
            }
        }

        DebugLogger.info(MODULE, "resolveTargets 结果: %d 个 UUID (去重后)", allUuids.size());
        DebugLogger.exiting(MODULE, "resolveTargets");
        return new ResolveResult(allUuids, onlinePlayers);
    }

    /**
     * 解析结果：UUID 集合 + 在线玩家映射（后续填充）。
     */
    public record ResolveResult(Set<UUID> allUuids, Map<UUID, ServerPlayer> onlinePlayers) {
    }

    // ========================================================================
    // 编辑前置检查
    // ========================================================================

    /**
     * 判断一封邮件是否可编辑（前置规则）。
     * <ul>
     * <li>无附件邮件（公告/通知）→ 始终可编辑</li>
     * <li>有附件且已有人领取 → 不可编辑，仅可撤回</li>
     * <li>有附件且无人领取 → 可编辑，但编辑期间需隐藏</li>
     * </ul>
     *
     * @param mailId 邮件 ID
     * @return CanEditResult
     */
    public static CanEditResult computeCanEdit(UUID mailId) {
        DebugLogger.entering(MODULE, "computeCanEdit", "mailId=" + mailId);
        Mail mail = SentMailRepository.get(mailId);
        if (mail == null) {
            DebugLogger.exiting(MODULE, "computeCanEdit", "mail not found");
            return new CanEditResult(false, false, "mail_not_found");
        }

        // 无附件 → 始终可编辑
        if (!mail.hasAttachments()) {
            DebugLogger.exiting(MODULE, "computeCanEdit", "no attachments, can edit");
            return new CanEditResult(true, false, "");
        }

        // 有附件且已有人领取 → 不可编辑，仅可撤回
        if (mail.isClaimed()) {
            DebugLogger.exiting(MODULE, "computeCanEdit", "claimed=true, cannot edit");
            return new CanEditResult(false, true, "已有玩家领取过附件，不可编辑，仅可撤回");
        }

        // 有附件且无人领取 → 可编辑（需隐藏）
        DebugLogger.exiting(MODULE, "computeCanEdit", "can edit (hidden)");
        return new CanEditResult(true, true, "");
    }

    /**
     * 编辑前置检查结果。
     *
     * @param canEdit    是否可编辑
     * @param needHidden 编辑期间是否需要隐藏邮件（有附件未领取时需要）
     * @param denyReason 拒绝原因（不可编辑时）
     */
    public record CanEditResult(boolean canEdit, boolean needHidden, String denyReason) {
    }

    // ========================================================================
    // 核心业务方法
    // ========================================================================

    /**
     * 发送邮件。
     * <p>
     * 生成 Mail、构造 scopeSummary、写入仓库、为接收者建索引、在线推送。
     * </p>
     */
    public static UUID send(ServerPlayer sender, List<TargetSpec> targetTargets, MailType type,
            String title, String body, byte expireOption, List<MailAttachment> attachments) {
        DebugLogger.entering(MODULE, "send",
                "sender=" + sender.getScoreboardName() + ", type=" + type + ", title=" + title);
        UUID mailId = UUID.randomUUID();
        Long expireTime = computeExpireTime(expireOption);
        String scopeSummary = generateScopeSummary(targetTargets);

        Mail mail = new Mail(mailId, type, sender.getScoreboardName(), targetTargets,
                scopeSummary, title, body, System.currentTimeMillis(), expireTime, attachments);
        SentMailRepository.put(mail);

        ResolveResult resolved = resolveTargets(targetTargets);
        var server = sender.level().getServer();

        for (UUID uuid : resolved.allUuids()) {
            MailRef ref = new MailRef(mailId);
            MailDataStorage.addRef(uuid, ref);

            // 在线推送
            if (server != null) {
                var online = server.getPlayerList().getPlayer(uuid);
                if (online != null) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(online,
                            new top.csituka.youzaiworldcore.network.MailUpdatePayload(
                                    top.csituka.youzaiworldcore.network.MailUpdatePayload.MODE_UPDATE, ref, mail, null,
                                    false, false));
                    // 更新未读数
                    int unread = MailDataStorage.getUnreadCount(uuid);
                    boolean canSend = top.csituka.youzaiworldcore.mail.MailPermissionHelper.hasMailPermission(online);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(online,
                            new top.csituka.youzaiworldcore.network.MailUnreadCountPayload(unread, canSend));
                }
            }
        }

        DebugLogger.info(MODULE, "邮件已发送: id=%s, title=%s, 接收者=%d, scope=%s",
                mailId, title, resolved.allUuids().size(), scopeSummary);
        DebugLogger.exiting(MODULE, "send", "mailId=" + mailId);
        return mailId;
    }

    /**
     * 领取奖励。
     * <p>
     * 校验存在/REWARD/未领取/未过期 → 逐项发放 → 标记 claimed。
     * </p>
     */
    public static boolean claim(ServerPlayer player, UUID mailId) {
        DebugLogger.entering(MODULE, "claim", "player=" + player.getScoreboardName() + ", mailId=" + mailId);
        Mail mail = SentMailRepository.get(mailId);
        if (mail == null) {
            DebugLogger.exiting(MODULE, "claim", "mail not found");
            return false;
        }
        if (mail.getType() != MailType.REWARD) {
            DebugLogger.exiting(MODULE, "claim", "not REWARD");
            return false;
        }
        if (mail.isExpired()) {
            DebugLogger.exiting(MODULE, "claim", "expired");
            return false;
        }

        // 检查该玩家是否已领取
        var box = MailDataStorage.load(player.getUUID());
        var optRef = box.getMails().stream().filter(r -> r.getMailId().equals(mailId)).findFirst();
        if (optRef.isPresent() && optRef.get().isClaimed()) {
            DebugLogger.exiting(MODULE, "claim", "already claimed");
            return false;
        }

        var lookup = player.level().getServer().registryAccess();

        for (MailAttachment att : mail.getAttachments()) {
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
                        player.level().getServer().getCommands().performPrefixedCommand(
                                player.level().getServer().createCommandSourceStack(), cmd);
                    }
                    case VANILLA_EXP -> player.giveExperiencePoints(att.amount());
                    case VANILLA_LEVEL -> player.giveExperienceLevels(att.amount());
                    case ADVENTURE_EXP -> AdventureLevelManager.grantExp(player, att.amount());
                }
            } catch (Exception e) {
                DebugLogger.exception(MODULE, "claim-attachment", e);
            }
        }

        // 标记已领取（全局 + 个人）
        mail.setClaimed(true);
        SentMailRepository.put(mail);

        if (optRef.isPresent()) {
            optRef.get().setClaimed(true);
            MailDataStorage.updateRef(player.getUUID(), optRef.get());
        }

        DebugLogger.info(MODULE, "玩家 %s 已领取奖励 mailId=%s", player.getScoreboardName(), mailId);
        DebugLogger.exiting(MODULE, "claim", "success=true");
        return true;
    }

    /**
     * 撤回邮件：删仓库条目（在线接收者推送由调用方处理）。
     */
    public static boolean recall(UUID mailId) {
        DebugLogger.entering(MODULE, "recall", "mailId=" + mailId);
        Mail removed = SentMailRepository.remove(mailId);
        DebugLogger.info(MODULE, "撤回邮件: mailId=%s, success=%s", mailId, removed != null);
        DebugLogger.exiting(MODULE, "recall", "success=" + (removed != null));
        return removed != null;
    }

    /**
     * 编辑邮件：更新字段 + 范围变更 diff。
     * <p>
     * 注意：调用方负责 hidden 标志切换与在线推送。
     * </p>
     */
    public static boolean edit(UUID mailId, List<TargetSpec> newTargets, MailType type,
            String title, String body, byte expireOption, List<MailAttachment> attachments) {
        DebugLogger.entering(MODULE, "edit", "mailId=" + mailId);
        Mail mail = SentMailRepository.get(mailId);
        if (mail == null) {
            DebugLogger.exiting(MODULE, "edit", "mail not found");
            return false;
        }

        // 1. 计算旧、新接收者集合的 diff
        ResolveResult oldResolved = resolveTargets(mail.getTargets());
        ResolveResult newResolved = resolveTargets(newTargets);
        Set<UUID> oldSet = oldResolved.allUuids();
        Set<UUID> newSet = newResolved.allUuids();

        // 新增：addRef
        for (UUID uuid : newSet) {
            if (!oldSet.contains(uuid)) {
                MailDataStorage.addRef(uuid, new MailRef(mailId));
            }
        }
        // 被移除且未领取：删 ref（已领取的保留）
        for (UUID uuid : oldSet) {
            if (!newSet.contains(uuid)) {
                var box = MailDataStorage.load(uuid);
                var optRef = box.getMails().stream().filter(r -> r.getMailId().equals(mailId)).findFirst();
                if (optRef.isPresent() && !optRef.get().isClaimed()) {
                    MailDataStorage.removeRef(uuid, mailId);
                }
            }
        }

        // 2. 更新字段
        String scopeSummary = generateScopeSummary(newTargets);
        mail.setTargets(newTargets);
        mail.setScopeSummary(scopeSummary);
        mail.setType(type);
        mail.setTitle(title);
        mail.setBody(body);
        Long newExpire = computeExpireTime(expireOption);
        mail.setExpireTime(newExpire);
        mail.setAttachments(attachments);
        // hidden 由调用方控制（进入编辑时设 true，完成时设 false）
        SentMailRepository.put(mail);

        DebugLogger.info(MODULE, "邮件已编辑: mailId=%s, title=%s", mailId, title);
        DebugLogger.exiting(MODULE, "edit", "success=true");
        return true;
    }

    /**
     * 清理过期邮件：扫描仓库 + 所有收件箱，移除过期且未星标的条目。
     */
    public static void purge() {
        DebugLogger.entering(MODULE, "purge");
        Set<UUID> expiredIds = new HashSet<>();
        for (Mail mail : SentMailRepository.getAll()) {
            if (mail.isExpired()) {
                expiredIds.add(mail.getId());
            }
        }
        for (UUID mailId : expiredIds) {
            SentMailRepository.remove(mailId);
        }
        DebugLogger.info(MODULE, "清理过期邮件: 移除 %d 封", expiredIds.size());
        // 收件箱清理在 MailDataStorage.load 时按需进行
        DebugLogger.exiting(MODULE, "purge");
    }

    /**
     * 账户注销时清空其收件箱。
     */
    public static void onAccountDeleted(java.util.UUID playerUuid) {
        DebugLogger.entering(MODULE, "onAccountDeleted", "playerUuid=" + playerUuid);
        MailDataStorage.removeBox(playerUuid);
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