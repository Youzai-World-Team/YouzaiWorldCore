package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.block.entity.LargeSignBlockEntity;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.block.entity.WirelessRedstoneBlockEntity;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.item.tool.TeleportStoneItem;
import top.csituka.youzaiworldcore.item.tool.WarpScrollItem;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload.EntryType;
import top.csituka.youzaiworldcore.config.DoubleDoorsState;
import top.csituka.youzaiworldcore.afk.AfkManager;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.redstone.WirelessRedstoneChannel;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;
import top.csituka.youzaiworldcore.skill.AttributeManager;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnManager;
import top.csituka.youzaiworldcore.cosmetic.CosmeticManager;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.LargeSignTextRules;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.mail.Mail;
import top.csituka.youzaiworldcore.mail.MailApiClient;
import top.csituka.youzaiworldcore.mail.MailAttachment;
import top.csituka.youzaiworldcore.mail.MailManager;
import top.csituka.youzaiworldcore.mail.MailPermissionHelper;
import top.csituka.youzaiworldcore.mail.MailRef;
import top.csituka.youzaiworldcore.mail.MailSettings;
import top.csituka.youzaiworldcore.mail.TargetSpec;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.impl.TrinketUtilities;
import top.csituka.youzaiworldcore.util.TrinketHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({"null", "unused"})
public class ModNetworking {
    
    public static void initialize() {
        DebugLogger.entering("ModNetworking", "initialize");

        // ===== 服务端接收处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(AuthRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (((top.csituka.youzaiworldcore.account.data.PlayerAuthAccess) (Object) player)
                        .yzwc$canSkipAuth()) {
                    return;
                }
                if (payload.action() == AuthRequestPayload.Action.LOGIN) {
                    top.csituka.youzaiworldcore.account.command.AccountCommands.executeLoginPayload(
                            player, payload.password());
                } else {
                    top.csituka.youzaiworldcore.account.command.AccountCommands.executeRegisterPayload(
                            player, payload.password(), payload.confirmation());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RegistrationEmailRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                var authPlayer = (top.csituka.youzaiworldcore.account.data.PlayerAuthAccess) (Object) player;
                if (authPlayer.yzwc$canSkipAuth() || authPlayer.yzwc$isAuthenticated()) return;
                if (payload.action() == RegistrationEmailRequestPayload.Action.SEND_CODE) {
                    top.csituka.youzaiworldcore.account.command.AccountCommands
                            .executeRegistrationEmailSendPayload(player, payload.sessionId(), payload.value());
                } else {
                    top.csituka.youzaiworldcore.account.command.AccountCommands
                            .executeRegistrationEmailVerifyPayload(player, payload.sessionId(), payload.value());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PasswordResetRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                var authPlayer = (top.csituka.youzaiworldcore.account.data.PlayerAuthAccess) (Object) player;
                if (authPlayer.yzwc$canSkipAuth() || authPlayer.yzwc$isAuthenticated()) return;
                if (payload.action() == PasswordResetRequestPayload.Action.SEND_CODE) {
                    top.csituka.youzaiworldcore.account.command.AccountCommands
                            .executePasswordResetSendPayload(player, payload.email());
                } else {
                    top.csituka.youzaiworldcore.account.command.AccountCommands
                            .executePasswordResetVerifyPayload(
                                    player, payload.sessionId(), payload.code(), payload.newPassword());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AccountManagementRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> top.csituka.youzaiworldcore.account.command.AccountCommands
                    .executeAccountManagementPayload(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(CosmeticUploadPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> CosmeticManager.applySnapshot(player, payload));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(CosmeticRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> CosmeticManager.handleRequest(player, payload.targetUuid()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MojangProfileRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> CosmeticManager.handleMojangProfileRequest(player, payload));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(TitleStateRequestPayload.ID, (payload, context) -> {
            var player = context.player();
            player.level().getServer().execute(() ->
                    top.csituka.youzaiworldcore.title.TitleManager.refreshPlayer(player, true));
        });

        ServerPlayNetworking.registerGlobalReceiver(TitleEquipPayload.ID, (payload, context) -> {
            var player = context.player();
            player.level().getServer().execute(() ->
                    top.csituka.youzaiworldcore.title.TitleManager.requestEquip(player, payload.titleId()));
        });

        ServerPlayNetworking.registerGlobalReceiver(DecomposeItemPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "DecomposeItemPayload handler");
            boolean isDecompositionTable = context.player().containerMenu instanceof DecompositionTableMenu;
            DebugLogger.branch("ModNetworking", "containerMenu instanceof DecompositionTableMenu", isDecompositionTable);
            if (isDecompositionTable) {
                DecompositionTableMenu menu = (DecompositionTableMenu) context.player().containerMenu;
                menu.performDecomposition();
            }
            DebugLogger.exiting("ModNetworking", "DecomposeItemPayload handler");
        });
        
        ServerPlayNetworking.registerGlobalReceiver(FlyBeaconActivePayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "FlyBeaconActivePayload handler");
            boolean isFlyBeaconMenu = context.player().containerMenu instanceof FlyBeaconMenu;
            DebugLogger.branch("ModNetworking", "containerMenu instanceof FlyBeaconMenu", isFlyBeaconMenu);
            if (isFlyBeaconMenu) {
                FlyBeaconMenu menu = (FlyBeaconMenu) context.player().containerMenu;
                boolean isFlyBeaconBlockEntity = menu.getContainer() instanceof FlyBeaconBlockEntity;
                DebugLogger.branch("ModNetworking", "getContainer() instanceof FlyBeaconBlockEntity", isFlyBeaconBlockEntity);
                if (isFlyBeaconBlockEntity) {
                    FlyBeaconBlockEntity blockEntity = (FlyBeaconBlockEntity) menu.getContainer();
                    blockEntity.setActive(payload.active());
                }
            }
            DebugLogger.exiting("ModNetworking", "FlyBeaconActivePayload handler");
        });

        // ===== 原地重生请求处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(InPlaceRespawnRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "InPlaceRespawnRequestPayload handler");
            var player = context.player();
            player.level().getServer().execute(() -> InPlaceRespawnManager.handleRequest(player));
            DebugLogger.exiting("ModNetworking", "InPlaceRespawnRequestPayload handler");
        });

        // ===== 维度池传送处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(WorldPoolTeleportPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "WorldPoolTeleportPayload handler");
            DebugLogger.info("ModNetworking", "Teleporting player to pool: " + payload.poolId());
            context.player().level().getServer().execute(() -> {
                DimensionPoolManager.teleportToPool(context.player(), payload.poolId());
            });
            DebugLogger.exiting("ModNetworking", "WorldPoolTeleportPayload handler");
        });

        // ===== 传送锚点传送处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorTeleportPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "TeleportAnchorTeleportPayload handler");
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "TeleportAnchorTeleportPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;

                // 按坐标查找传送点（不再依赖索引，避免过滤列表与原始列表的索引错位）
                TeleportAnchorData target =
                        manager.findPoint(serverPlayer, payload.pos(), payload.dimension());
                if (target == null) {
                    DebugLogger.info("ModNetworking", "Teleport point not found at " + payload.pos());
                    return;
                }

                var targetLevel = server.getLevel(target.dimension());
                if (targetLevel == null) {
                    DebugLogger.info("ModNetworking", "Target dimension not loaded: " + target.dimension().identifier());
                    return;
                }

                // 重新校验目标方块仍存在且处于激活状态
                BlockState anchorState = targetLevel.getBlockState(target.pos());
                if (!(anchorState.getBlock() instanceof TeleportAnchorBlock)
                        || !anchorState.getValue(TeleportAnchorBlock.ACTIVE)) {
                    // 目标锚点已失效，从玩家列表中清理
                    manager.removePointByPos(serverPlayer, target.pos(), target.dimension());
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.youzaiworldcore.teleport_anchor.invalid"));
                    DebugLogger.info("ModNetworking", "Target anchor invalid at " + target.pos());
                    return;
                }

                // 维度池隔离校验：源维度与目标维度必须同池，或至少一方未加入任何池
                String sourceDim = serverPlayer.level().dimension().identifier().toString();
                String targetDim = target.dimension().identifier().toString();
                if (!sourceDim.equals(targetDim)) {
                    boolean samePool = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                            .dimensionsInSamePool(sourceDim, targetDim);
                    var sourcePool = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                            .getPoolByDimension(sourceDim);
                    var targetPool = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                            .getPoolByDimension(targetDim);
                    // 仅当双方都有池但不同池时拒绝；任一方无池则放行
                    if (sourcePool.isPresent() && targetPool.isPresent() && !samePool) {
                        serverPlayer.sendSystemMessage(
                                Component.translatable("message.youzaiworldcore.teleport_anchor.pool_mismatch"));
                        DebugLogger.info("ModNetworking", "Cross-pool teleport blocked: " + sourceDim + " -> " + targetDim);
                        return;
                    }
                }

                // 冷却检查
                long gameTime = serverPlayer.level().getGameTime();
                if (!manager.canTeleport(serverPlayer, gameTime)) {
                    int remaining = manager.getRemainingCooldownSeconds(serverPlayer, gameTime);
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.youzaiworldcore.teleport_anchor.cooldown", remaining));
                    DebugLogger.info("ModNetworking", "Player on cooldown: " + remaining + "s remaining");
                    return;
                }

                // 经验等级消耗：同维度 1 级，跨维度 2 级；创造模式免费
                // 仅传送锚点方块 / 传送石入口需要经验，传送卷轴一次性使用故免经验
                TeleportAnchorManager.TeleportOpenerSource source = manager.consumeTeleportSourceMark(serverPlayer);
                EntryType entryType = source != null ? source.type() : EntryType.ANCHOR;
                InteractionHand entryHand = source != null ? source.hand() : null;
                boolean isScrollEntry = entryType == EntryType.SCROLL;
                boolean itemEntry = source != null;

                boolean isCreative = serverPlayer.getAbilities().instabuild;
                int cost = target.dimension().equals(serverPlayer.level().dimension()) ? 1 : 2;
                if (!isScrollEntry && !isCreative && serverPlayer.experienceLevel < cost) {
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.youzaiworldcore.teleport_anchor.no_xp", cost));
                    DebugLogger.info("ModNetworking", "Player lacks XP: needs " + cost + " but has " + serverPlayer.experienceLevel);
                    return;
                }

                // ===== 传送物品入口结算（耐久 / 卷轴均在传送真正成功时扣，避免先扣后拒） =====
                // 标记无论成败都在此消费；校验放在扣经验之前，避免校验失败却已扣掉经验。
                ItemStack itemStack = null;
                InteractionHand itemHand = null;
                int durabilityCost = 0;
                if (itemEntry && !isCreative && entryHand != null) {
                    itemStack = serverPlayer.getItemInHand(entryHand);
                    var entryItem = itemStack.getItem();
                    if (entryType == EntryType.STONE && !(entryItem instanceof TeleportStoneItem)) {
                        // 兜底：玩家在开着界面时换了手，另一只手再找一次传送石
                        InteractionHand otherHand = entryHand == InteractionHand.MAIN_HAND
                                ? InteractionHand.OFF_HAND
                                : InteractionHand.MAIN_HAND;
                        ItemStack alternative = serverPlayer.getItemInHand(otherHand);
                        if (alternative.getItem() instanceof TeleportStoneItem) {
                            itemStack = alternative;
                            itemHand = otherHand;
                            entryHand = otherHand;
                        } else {
                            // 传送石已不在手上：拒绝传送，避免丢弃传送石白嫖免耐久传送
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.teleport_stone.not_held"));
                            DebugLogger.info("ModNetworking", "Teleport stone no longer held, teleport cancelled");
                            return;
                        }
                    } else if (entryType == EntryType.SCROLL && !(entryItem instanceof WarpScrollItem)) {
                        InteractionHand otherHand = entryHand == InteractionHand.MAIN_HAND
                                ? InteractionHand.OFF_HAND
                                : InteractionHand.MAIN_HAND;
                        ItemStack alternative = serverPlayer.getItemInHand(otherHand);
                        if (alternative.getItem() instanceof WarpScrollItem) {
                            itemStack = alternative;
                            itemHand = otherHand;
                            entryHand = otherHand;
                        } else {
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.warp_scroll.not_held"));
                            DebugLogger.info("ModNetworking", "Warp scroll no longer held, teleport cancelled");
                            return;
                        }
                    } else {
                        itemHand = entryHand;
                    }

                    // 传送石：按距离计算耐久消耗并校验
                    if (entryType == EntryType.STONE) {
                        durabilityCost = TeleportStoneItem.computeDurabilityCost(serverPlayer, target);
                        int remainingDurability = itemStack.getMaxDamage() - itemStack.getDamageValue();
                        if (remainingDurability < durabilityCost) {
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.teleport_stone.no_durability",
                                            durabilityCost, remainingDurability));
                            DebugLogger.info("ModNetworking", "Teleport stone durability insufficient: needs "
                                    + durabilityCost + " but has " + remainingDurability);
                            return;
                        }
                    } else if (entryType == EntryType.SCROLL) {
                        // 卷轴：始终消耗 1 张（叠堆减 1，耗尽则该组物品销毁），不涉及耐久
                        if (itemStack.getCount() < WarpScrollItem.SCROLL_CONSUME_AMOUNT) {
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.youzaiworldcore.warp_scroll.not_held"));
                            DebugLogger.info("ModNetworking", "Warp scroll count insufficient, teleport cancelled");
                            return;
                        }
                    }
                }

                if (!isScrollEntry && !isCreative) {
                    serverPlayer.giveExperienceLevels(-cost);
                }

                // 记录传送锚点自身的 3 秒入门冷却
                manager.recordTeleport(serverPlayer, gameTime);

                // 传送物品结算：先上冷却（堆对象还在），再扣耐久 / 缩卷轴
                if (itemStack != null && itemHand != null) {
                    if (entryType == EntryType.STONE) {
                        serverPlayer.getCooldowns().addCooldown(itemStack, TeleportStoneItem.COOLDOWN_TICKS);
                        itemStack.hurtAndBreak(durabilityCost, serverPlayer, itemHand);
                        DebugLogger.info("ModNetworking", "Teleport stone consumed %d durability (hand=%s)",
                                durabilityCost, itemHand);
                    } else if (entryType == EntryType.SCROLL) {
                        serverPlayer.getCooldowns().addCooldown(itemStack, WarpScrollItem.COOLDOWN_TICKS);
                        itemStack.shrink(WarpScrollItem.SCROLL_CONSUME_AMOUNT);
                        DebugLogger.info("ModNetworking", "Warp scroll consumed 1 (hand=%s, remaining=%s)",
                                itemHand, itemStack.isEmpty() ? "destroyed" : itemStack.getCount());
                    }
                }

                serverPlayer.teleportTo(targetLevel,
                        target.pos().getX() + 0.5,
                        target.pos().getY() + 1.0,
                        target.pos().getZ() + 0.5,
                        java.util.Set.of(),
                        serverPlayer.getYRot(),
                        serverPlayer.getXRot(),
                        true);
                DebugLogger.info("ModNetworking", "Teleported player to " + target.pos() + " in " + target.dimension().identifier());
            });
            DebugLogger.exiting("ModNetworking", "TeleportAnchorTeleportPayload handler");
        });

        // ===== 传送锚点删除处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorDeletePayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                    manager.removePointByPos((net.minecraft.server.level.ServerPlayer) player, payload.pos(), payload.dimension());
                });
            }
        });

        // ===== 大字牌文本提交处理器 =====
        // 客户端只能「请求」写入，是否放行完全由这里判定：
        // 方块仍是大字牌 → 未涂蜡 → 该玩家确实是本次被授权的编辑者（含 8 格距离校验）
        // → 文本符合 LargeSignTextRules。任一条不满足即静默丢弃。
        ServerPlayNetworking.registerGlobalReceiver(LargeSignSetTextPayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                BlockPos pos = payload.pos();
                String text = payload.text();

                if (!(player.level().getBlockEntity(pos) instanceof LargeSignBlockEntity sign)) {
                    DebugLogger.branch("ModNetworking", "大字牌文本提交：目标不是大字牌", false);
                    return;
                }
                if (!player.mayBuild() || !sign.mayEdit(player)) {
                    DebugLogger.warn("ModNetworking",
                            "拒绝大字牌文本提交（无权限 / 非授权编辑者 / 距离过远）：player=%s, pos=%s",
                            player.getName().getString(), pos.toShortString());
                    return;
                }
                if (!LargeSignTextRules.isValid(text)) {
                    DebugLogger.warn("ModNetworking",
                            "拒绝大字牌文本提交（内容不合法）：player=%s, pos=%s, text=%s",
                            player.getName().getString(), pos.toShortString(), text);
                    return;
                }

                sign.setText(text);
                // 一次授权只用一次，提交后立即失效
                sign.setAllowedPlayerEditor(null);
                DebugLogger.info("ModNetworking", "玩家 %s 写入大字牌文本：pos=%s, text=%s",
                        player.getName().getString(), pos.toShortString(), text);
            });
        });

        // ===== 无线红石频道提交处理器 =====
        // 客户端只能「请求」写入，是否放行完全由这里判定：
        // 目标仍是无线红石元件 → 该玩家确实是本次被授权的设置者（含 8 格距离校验）
        // → 频道号在合法区间内。任一条不满足即静默丢弃。
        ServerPlayNetworking.registerGlobalReceiver(WirelessRedstoneSetChannelPayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                BlockPos pos = payload.pos();
                int channel = payload.channel();

                if (!(player.level().getBlockEntity(pos) instanceof WirelessRedstoneBlockEntity component)) {
                    DebugLogger.branch("ModNetworking", "无线红石频道提交：目标不是无线红石元件", false);
                    return;
                }
                if (!player.mayBuild() || !component.mayEdit(player)) {
                    DebugLogger.warn("ModNetworking",
                            "拒绝无线红石频道提交（无权限 / 非授权设置者 / 距离过远）：player=%s, pos=%s",
                            player.getName().getString(), pos.toShortString());
                    return;
                }
                if (!WirelessRedstoneChannel.isValid(channel)) {
                    DebugLogger.warn("ModNetworking",
                            "拒绝无线红石频道提交（频道号越界）：player=%s, pos=%s, channel=%d",
                            player.getName().getString(), pos.toShortString(), channel);
                    return;
                }

                component.setChannel(channel);
                // 一次授权只用一次，提交后立即失效
                component.setAllowedPlayerEditor(null);
                DebugLogger.info("ModNetworking", "玩家 %s 设置无线红石%s频道：pos=%s, channel=%d",
                        player.getName().getString(), component.isTransmitter() ? "发射器" : "接收器",
                        pos.toShortString(), channel);
            });
        });

        // ===== 传送锚点重命名处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorRenamePayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                    manager.renamePointByPos((net.minecraft.server.level.ServerPlayer) player,
                            payload.pos(), payload.dimension(), payload.newName());
                });
            }
        });

        // ===== 传送锚点激活处理器（玩家从命名界面确认后） =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorActivatePayload.TYPE, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                BlockPos pos = payload.pos();
                net.minecraft.world.level.Level level = player.level();
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof TeleportAnchorBlock)) return;
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof TeleportAnchorBlockEntity anchorBE)) return;

                UUID playerUuid = player.getUUID();
                if (anchorBE.isActivatedBy(playerUuid)) return; // 已激活过

                boolean wasEmpty = anchorBE.addActivator(playerUuid);
                if (wasEmpty) {
                    level.setBlock(pos, state.setValue(TeleportAnchorBlock.ACTIVE, true), 3);
                    level.sendBlockUpdated(pos, state, state.setValue(TeleportAnchorBlock.ACTIVE, true), 3);
                }

                // 粒子效果（仅本人）
                {
                    var particle = net.minecraft.core.particles.ParticleTypes.END_ROD;
                    var pkt = new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(
                            particle, false, false,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            0.6f, 0.6f, 0.6f, 0.08f, 40
                    );
                    player.connection.send(pkt);
                }

                // 音效（在锚点位置播放，激活者能听见）
                level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

                // 添加到玩家传送列表（附加维度池标识）
                TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                String finalName = payload.name().isEmpty()
                        ? Component.translatable("screen.youzaiworldcore.teleport_anchor_name.default").getString()
                        : payload.name();
                String poolId = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                        .getPoolByDimension(level.dimension().identifier().toString())
                        .map(p -> p.id())
                        .orElse(null);
                manager.addPointWithName(player, pos, level.dimension(), finalName, poolId);

                player.sendSystemMessage(
                        Component.translatable("message.youzaiworldcore.teleport_anchor.activated")
                );
            });
        });

        // ===== 传送锚点排序处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(TeleportAnchorReorderPayload.TYPE, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    TeleportAnchorManager manager = TeleportAnchorManager.get(server);
                    manager.movePoint((net.minecraft.server.level.ServerPlayer) player,
                            payload.fromIndex(), payload.toIndex());
                });
            }
        });

        // ===== 双开门功能切换 / 查询处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(DoubleDoorsTogglePayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "DoubleDoorsTogglePayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "DoubleDoorsTogglePayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                UUID uuid = player.getUUID();
                Boolean enabled = payload.enabled();
                if (enabled != null) {
                    DoubleDoorsState.setEnabled(uuid, enabled);
                }
                boolean cur = DoubleDoorsState.isEnabled(uuid);
                if (enabled != null) {
                    // 设置结果反馈
                    player.sendSystemMessage(Component.translatable(
                            enabled
                                    ? "youzaiworldcore.message.command.function.double_doors.set_enabled"
                                    : "youzaiworldcore.message.command.function.double_doors.set_disabled",
                            player.getName().getString()));
                } else {
                    // 查询反馈（区分默认启用 / 显式启用 / 已禁用）
                    player.sendSystemMessage(Component.translatable(
                            DoubleDoorsState.isExplicitlySet(uuid)
                                    ? "youzaiworldcore.message.command.function.double_doors.query_enabled"
                                    : (cur
                                            ? "youzaiworldcore.message.command.function.double_doors.query_default"
                                            : "youzaiworldcore.message.command.function.double_doors.query_disabled")));
                }
            });
            DebugLogger.exiting("ModNetworking", "DoubleDoorsTogglePayload handler");
        });

        // ===== 隐身功能切换处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(InvisibilityPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "InvisibilityPayload handler");
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                // 权限检查
                if (!InvisibilityManager.hasPermission(player)) {
                    player.sendSystemMessage(Component.literal("§c你没有权限使用隐身功能"));
                    DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "no permission");
                    return;
                }
                Boolean enabled = payload.enabled();
                if (enabled == null) {
                    // 查询当前状态
                    boolean cur = InvisibilityManager.isInvisible(player);
                    player.sendSystemMessage(Component.translatable(
                            cur
                                    ? "youzaiworldcore.message.command.function.invisibility.query_enabled"
                                    : "youzaiworldcore.message.command.function.invisibility.query_disabled"));
                    return;
                }
                // 创造模式检查（设置隐身才需要）
                if (player.gameMode() != GameType.CREATIVE) {
                    player.sendSystemMessage(Component.literal(
                            "§c只有创造模式才能使用隐身功能（请先切换到创造模式）"));
                    DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler", "not creative");
                    return;
                }
                if (enabled) {
                    DebugLogger.branch("ModNetworking", "InvisibilityPayload", true, "enabling");
                    InvisibilityManager.enable(player);
                } else {
                    DebugLogger.branch("ModNetworking", "InvisibilityPayload", false, "disabling");
                    InvisibilityManager.disable(player);
                }
                player.sendSystemMessage(Component.translatable(enabled
                        ? "youzaiworldcore.message.command.function.invisibility.set_enabled"
                        : "youzaiworldcore.message.command.function.invisibility.set_disabled"));
            });
            DebugLogger.exiting("ModNetworking", "InvisibilityPayload handler");
        });

        // ===== 属性加点 C2S 处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(AttributeUpgradePayload.TYPE, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            player.level().getServer().execute(() -> {
                AttributeManager.handleUpgrade(player, payload.attributeKey());
            });
        });

        // ===== 宠物管理命令 C2S 转发处理器 =====
        ServerPlayNetworking.registerGlobalReceiver(PetCommandPayload.ID, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            DebugLogger.entering("ModNetworking", "PetCommandPayload handler", "args=" + payload.args());
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "PetCommandPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                try {
                    // 重建完整命令字符串：yzwc pet <args>
                    String fullCommand = "yzwc pet " + payload.args();
                    DebugLogger.info("ModNetworking", "执行宠物命令: /%s", fullCommand);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), fullCommand);
                } catch (Exception e) {
                    DebugLogger.error("ModNetworking", "执行宠物命令失败: %s", e.getMessage());
                }
            });
            DebugLogger.exiting("ModNetworking", "PetCommandPayload handler");
        });

        // ===== AFK 客户端心跳处理器（服务端权威：更新客户端通道活动时间） =====
        ServerPlayNetworking.registerGlobalReceiver(AfkHeartbeatPayload.ID, (payload, context) -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            DebugLogger.trace("ModNetworking", "AfkHeartbeatPayload handler: player=%s, idleTicks=%d",
                    player.getName().getString(), payload.idleTicks());
            var server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                AfkManager.onHeartbeat(player, server.getTickCount(), payload.idleTicks());
            });
        });
        DebugLogger.info("ModNetworking", "Registered receiver: AfkHeartbeatPayload");

        // ===== YZUI 生存物品栏左键拖拽收集（服务端权威操作） =====
        ServerPlayNetworking.registerGlobalReceiver(InventoryCollectPayload.ID, (payload, context) -> {
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                var menu = player.containerMenu;
                boolean validMenu = menu == player.inventoryMenu
                        && menu.containerId == payload.containerId()
                        && !player.hasInfiniteMaterials();
                if (!validMenu || payload.slotIndex() < 0 || payload.slotIndex() >= menu.slots.size()) {
                    DebugLogger.info("InventoryCollect", "拒绝无效收集请求：container=%d, slot=%d",
                            payload.containerId(), payload.slotIndex());
                    menu.broadcastFullState();
                    return;
                }

                var slot = menu.getSlot(payload.slotIndex());
                ItemStack carried = menu.getCarried();
                ItemStack slotStack = slot.getItem();
                int capacity = carried.isEmpty() ? 0 : carried.getMaxStackSize() - carried.getCount();
                if (!slot.isActive() || !slot.mayPickup(player) || slotStack.isEmpty() || capacity <= 0
                        || !ItemStack.isSameItemSameComponents(carried, slotStack)) {
                    DebugLogger.info("InventoryCollect", "忽略不满足条件的收集请求：slot=%d",
                            payload.slotIndex());
                    menu.broadcastFullState();
                    return;
                }

                int requested = Math.min(capacity, slotStack.getCount());
                ItemStack taken = slot.safeTake(requested, requested, player);
                if (taken.isEmpty() || !ItemStack.isSameItemSameComponents(carried, taken)) {
                    DebugLogger.info("InventoryCollect", "槽位拒绝取出物品：slot=%d", payload.slotIndex());
                    menu.broadcastFullState();
                    return;
                }

                ItemStack updatedCarried = carried.copy();
                updatedCarried.grow(taken.getCount());
                menu.setCarried(updatedCarried);
                menu.broadcastChanges();
                DebugLogger.info("InventoryCollect", "已从 slot %d 收集 %d 个 %s",
                        payload.slotIndex(), taken.getCount(), taken.getHoverName().getString());
            });
        });
        DebugLogger.info("ModNetworking", "Registered receiver: InventoryCollectPayload");

        // ===== Trinkets 饰品槽交互处理器（服务端权威操作） =====
        ServerPlayNetworking.registerGlobalReceiver(TrinketInteractPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "TrinketInteractPayload handler");
            var player = context.player();
            var server = player.level().getServer();
            if (server == null) {
                DebugLogger.exiting("ModNetworking", "TrinketInteractPayload handler", "server is null");
                return;
            }
            server.execute(() -> {
                try {
                    eu.pb4.trinkets.api.TrinketAttachment attachment = eu.pb4.trinkets.api.TrinketsApi.getAttachment(player);
                    if (attachment == null) {
                        DebugLogger.warn("ModNetworking", "TrinketAttachment is null for player %s", player.getName().getString());
                        return;
                    }
                    eu.pb4.trinkets.api.TrinketInventory inv = attachment.getInventories().get(payload.groupKey());
                    if (inv == null) {
                        DebugLogger.warn("ModNetworking", "TrinketInventory '%s' not found", payload.groupKey());
                        return;
                    }
                    eu.pb4.trinkets.api.TrinketSlotAccess access = inv.getSlotAccess(payload.slotIndex());
                    if (access == null) {
                        DebugLogger.warn("ModNetworking", "TrinketSlotAccess at %s[%d] not found", payload.groupKey(), payload.slotIndex());
                        return;
                    }

                    net.minecraft.world.item.ItemStack carried = player.containerMenu.getCarried();
                    net.minecraft.world.item.ItemStack reported = payload.cursor() != null ? payload.cursor() : net.minecraft.world.item.ItemStack.EMPTY;
                    // 生存模式不得信任客户端上报的任意 ItemStack；只有创造模式允许把本地创造栏
                    // 生成的虚拟光标物品作为兜底。标准生存物品栏点击会先同步服务端 carried。
                    boolean creativeCursor = player.hasInfiniteMaterials();
                    net.minecraft.world.item.ItemStack cursor = creativeCursor ? reported : carried;
                    net.minecraft.world.item.ItemStack slotStack = access.get();

                    DebugLogger.info("ModNetworking", "TrinketReq group=%s[%d] action=%d serverCarried=%s cursor=%s(%d) slot=%s",
                            payload.groupKey(), payload.slotIndex(), payload.action(),
                            carried.isEmpty() ? "empty" : carried.getHoverName().getString(),
                            cursor.isEmpty() ? "empty" : cursor.getHoverName().getString(), cursor.getCount(),
                            slotStack.isEmpty() ? "empty" : slotStack.getHoverName().getString());

                    switch (payload.action()) {
                        case TrinketInteractPayload.ACTION_PLACE:
                            if (cursor.isEmpty()) {
                                DebugLogger.info("ModNetworking", "PLACE skipped: no item to place (serverCarried empty, clientCursor empty)");
                                break;
                            }
                            if (!slotStack.isEmpty()) {
                                DebugLogger.info("ModNetworking", "PLACE rejected: slot %s[%d] is not empty",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            if (!access.slotType().validatorCheck(cursor, access, player)) {
                                DebugLogger.info("ModNetworking", "Validator rejected %s -> %s[%d]",
                                        cursor.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            int placeCount = Math.min(cursor.getCount(), access.maxStackSize(cursor));
                            if (placeCount <= 0) {
                                DebugLogger.info("ModNetworking", "PLACE rejected: slot %s[%d] has no capacity",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            net.minecraft.world.item.ItemStack placed = cursor.copyWithCount(placeCount);
                            if (!access.set(placed)) {
                                DebugLogger.info("ModNetworking", "PLACE rejected by slot access: %s[%d]",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            inv.setChanged();
                            TrinketUtilities.callTrinketEquipmentChange(slotStack, placed, access, player);
                            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), placed);
                            net.minecraft.world.item.ItemStack remainder = cursor.copy();
                            remainder.shrink(placeCount);
                            player.containerMenu.setCarried(creativeCursor || remainder.isEmpty()
                                    ? net.minecraft.world.item.ItemStack.EMPTY : remainder);
                            DebugLogger.info("ModNetworking", "Trinket PLACE: %s x%d -> %s[%d], remainder=%d",
                                    placed.getHoverName().getString(), placed.getCount(), payload.groupKey(),
                                    payload.slotIndex(), remainder.getCount());
                            break;
                        case TrinketInteractPayload.ACTION_TAKE:
                            if (slotStack.isEmpty()) {
                                DebugLogger.info("ModNetworking", "TAKE skipped: slot %s[%d] is empty",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            player.containerMenu.setCarried(creativeCursor
                                    ? net.minecraft.world.item.ItemStack.EMPTY : slotStack.copy());
                            access.set(net.minecraft.world.item.ItemStack.EMPTY);
                            inv.setChanged();
                            TrinketUtilities.callTrinketEquipmentChange(slotStack, net.minecraft.world.item.ItemStack.EMPTY, access, player);
                            DebugLogger.info("ModNetworking", "Trinket TAKE: %s[%d] -> cursor", payload.groupKey(), payload.slotIndex());
                            break;
                        case TrinketInteractPayload.ACTION_SWAP:
                            if (cursor.isEmpty()) {
                                DebugLogger.info("ModNetworking", "SWAP skipped: no item to swap (serverCarried empty, clientCursor empty)");
                                break;
                            }
                            if (!access.slotType().validatorCheck(cursor, access, player)) {
                                DebugLogger.info("ModNetworking", "Validator rejected %s -> %s[%d] (SWAP)",
                                        cursor.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            if (cursor.getCount() > access.maxStackSize(cursor)) {
                                DebugLogger.info("ModNetworking", "SWAP rejected: %s x%d exceeds slot limit %d",
                                        cursor.getHoverName().getString(), cursor.getCount(), access.maxStackSize(cursor));
                                break;
                            }
                            if (!access.set(cursor.copy())) {
                                DebugLogger.info("ModNetworking", "SWAP rejected by slot access: %s[%d]",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            player.containerMenu.setCarried(creativeCursor
                                    ? net.minecraft.world.item.ItemStack.EMPTY : slotStack.copy());
                            inv.setChanged();
                            TrinketUtilities.callTrinketEquipmentChange(slotStack, cursor, access, player);
                            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), cursor);
                            DebugLogger.info("ModNetworking", "Trinket SWAP: cursor <-> %s[%d]", payload.groupKey(), payload.slotIndex());
                            break;
                        case TrinketInteractPayload.ACTION_QUICK_MOVE:
                            // 快捷移动：饰品槽物品 → 主物品栏/快捷栏（玩家背包 0-35）。
                            // 手动转移不依赖菜单类型（CreativeModeMenu 无 Trinkets 注入槽，
                            // 不能用标准 QUICK_MOVE 点击）；物品栏由 menu 广播同步，
                            // 饰品槽由 Trinkets 的 tick 级脏检查自动 SYNC_INVENTORY 同步。
                            if (slotStack.isEmpty()) {
                                DebugLogger.info("ModNetworking", "QUICK_MOVE skipped: slot %s[%d] is empty",
                                        payload.groupKey(), payload.slotIndex());
                                break;
                            }
                            {
                                net.minecraft.world.item.ItemStack toMove = slotStack.copy();
                                net.minecraft.world.entity.player.Inventory playerInv = player.getInventory();
                                for (int i = 0; i < playerInv.getContainerSize(); i++) {
                                    net.minecraft.world.item.ItemStack existing = playerInv.getItem(i);
                                    if (existing.isEmpty()) {
                                        playerInv.setItem(i, toMove);
                                        toMove = net.minecraft.world.item.ItemStack.EMPTY;
                                        break;
                                    } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(existing, toMove)
                                            && existing.getCount() < existing.getMaxStackSize()) {
                                        int space = existing.getMaxStackSize() - existing.getCount();
                                        int take = Math.min(space, toMove.getCount());
                                        existing.grow(take);
                                        toMove.shrink(take);
                                        if (toMove.isEmpty()) {
                                            break;
                                        }
                                    }
                                }
                                if (toMove.isEmpty()) {
                                    access.set(net.minecraft.world.item.ItemStack.EMPTY);
                                    inv.setChanged();
                                    TrinketUtilities.callTrinketEquipmentChange(slotStack, net.minecraft.world.item.ItemStack.EMPTY, access, player);
                                    DebugLogger.info("ModNetworking", "Trinket QUICK_MOVE: %s -> %s[%d] -> inventory",
                                            slotStack.getHoverName().getString(), payload.groupKey(), payload.slotIndex());
                                } else {
                                    access.set(toMove);
                                    inv.setChanged();
                                    TrinketUtilities.callTrinketEquipmentChange(slotStack, toMove, access, player);
                                    DebugLogger.info("ModNetworking", "Trinket QUICK_MOVE partial: %s -> %s[%d] (inventory full, %d left)",
                                            slotStack.getHoverName().getString(), payload.groupKey(), payload.slotIndex(), toMove.getCount());
                                }
                            }
                            break;
                        default:
                            DebugLogger.warn("ModNetworking", "Unknown trinket action: %d", payload.action());
                            break;
                    }
                    player.containerMenu.broadcastChanges();
                } catch (Exception e) {
                    DebugLogger.error("ModNetworking", "Trinket interact handler error: %s", e.getMessage());
                }
            });
            DebugLogger.exiting("ModNetworking", "TrinketInteractPayload handler");
        });

        // ======================================================================
        // 邮件系统（Mail）—— 服务端接收处理器
        // ----------------------------------------------------------------------
        // 邮件正文与每玩家收件箱由 Api 服务端权威保存（见 MailApiClient）。
        // 这里的职责固定为三步：主线程判权限 → 异步调 Api → 回主线程推 S2C。
        // 所有 Api 调用都必须走 mailApi(...)，绝不能在主线程里同步等 HTTP。
        // ======================================================================

        // 1. 打开发布 GUI（纯权限判定，不需要 Api）
        ServerPlayNetworking.registerGlobalReceiver(MailComposeOpenPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailComposeOpenPayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, new OpenMailComposePayload());
                    DebugLogger.info("ModNetworking", "Opened mail compose GUI for %s", player.getScoreboardName());
                } else {
                    player.sendSystemMessage(Component.literal("§c你没有权限发布邮件"));
                }
            });
            DebugLogger.exiting("ModNetworking", "MailComposeOpenPayload handler");
        });

        // 2. 打开收件箱
        ServerPlayNetworking.registerGlobalReceiver(MailOpenPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailOpenPayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                UUID uuid = player.getUUID();
                mailApi(player, "MailOpenPayload", () -> MailApiClient.fetchInbox(uuid), result -> {
                    if (!result.success()) {
                        ServerPlayNetworking.send(player,
                                MailOpResultPayload.failure(null, mailApiFailure(result.message())));
                        return;
                    }
                    ServerPlayNetworking.send(player, new MailListPayload(result.entries()));
                    MailManager.sendUnread(player, result.unread());
                    DebugLogger.info("ModNetworking", "Sent inbox for %s: %d entries",
                            player.getScoreboardName(), result.entries().size());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailOpenPayload handler");
        });

        // 3. 请求已发送列表
        ServerPlayNetworking.registerGlobalReceiver(MailSentListRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailSentListRequestPayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailSentListRequestPayload handler", "no permission");
                    return;
                }
                mailApi(player, "MailSentListRequestPayload", MailApiClient::fetchSentList, result -> {
                    if (!result.success()) {
                        ServerPlayNetworking.send(player,
                                MailOpResultPayload.failure(null, mailApiFailure(result.message())));
                        return;
                    }
                    ServerPlayNetworking.send(player, new MailSentListPayload(result.summaries()));
                    DebugLogger.info("ModNetworking", "Sent mail sent list for %s: %d entries",
                            player.getScoreboardName(), result.summaries().size());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailSentListRequestPayload handler");
        });

        // 4. 撤回邮件
        ServerPlayNetworking.registerGlobalReceiver(MailRecallPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailRecallPayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailRecallPayload handler", "no permission");
                    return;
                }
                UUID mailId = payload.mailId();
                mailApi(player, "MailRecallPayload", () -> MailApiClient.recall(mailId), result -> {
                    if (!result.success()) {
                        ServerPlayNetworking.send(player, MailOpResultPayload.failure(mailId,
                                result.message().isBlank() ? "邮件不存在或已撤回" : result.message()));
                        return;
                    }
                    // Api 已删除全部收件箱引用，这里只把移除推给在线接收者
                    for (UUID recipient : result.recipients()) {
                        ServerPlayer online = server.getPlayerList().getPlayer(recipient);
                        if (online != null) {
                            ServerPlayNetworking.send(online, MailUpdatePayload.createRemove(mailId));
                        }
                    }
                    MailManager.refreshUnreadFor(server, result.recipients());
                    ServerPlayNetworking.send(player, MailOpResultPayload.success(mailId, "已撤回"));
                    DebugLogger.info("ModNetworking", "Mail recalled: mailId=%s, recipients=%d",
                            mailId, result.recipients().size());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailRecallPayload handler");
        });

        // 5. 清理过期邮件
        ServerPlayNetworking.registerGlobalReceiver(MailPurgePayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailPurgePayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailPurgePayload handler", "no permission");
                    return;
                }
                boolean keepStarred = MailSettings.get().isKeepStarredAfterExpire();
                mailApi(player, "MailPurgePayload", () -> MailApiClient.purge(keepStarred), result -> {
                    if (!result.success()) {
                        ServerPlayNetworking.send(player,
                                MailOpResultPayload.failure(null, mailApiFailure(result.message())));
                        return;
                    }
                    MailManager.refreshUnreadFor(server, result.affected());
                    ServerPlayNetworking.send(player, MailOpResultPayload.success(null, "已清理过期邮件"));
                    DebugLogger.info("ModNetworking", "Mail purge by %s: removed=%d",
                            player.getScoreboardName(), result.removed());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailPurgePayload handler");
        });

        // 6. 查看指定玩家信箱
        ServerPlayNetworking.registerGlobalReceiver(MailListRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailListRequestPayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailListRequestPayload handler", "no permission");
                    return;
                }
                String targetName = payload.targetPlayer();
                UUID targetUuid;
                if (targetName.isEmpty()) {
                    targetUuid = player.getUUID();
                } else {
                    targetUuid = accountUuid(AccountDataStorage.get(targetName));
                    if (targetUuid == null) {
                        ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "找不到玩家: " + targetName));
                        DebugLogger.exiting("ModNetworking", "MailListRequestPayload handler", "player not found");
                        return;
                    }
                }
                mailApi(player, "MailListRequestPayload", () -> MailApiClient.fetchInbox(targetUuid), result -> {
                    if (!result.success()) {
                        ServerPlayNetworking.send(player,
                                MailOpResultPayload.failure(null, mailApiFailure(result.message())));
                        return;
                    }
                    ServerPlayNetworking.send(player, new MailListPayload(result.entries()));
                    DebugLogger.info("ModNetworking", "Sent inbox for target %s: %d entries",
                            targetName.isEmpty() ? "self" : targetName, result.entries().size());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailListRequestPayload handler");
        });

        // 7. 编辑预填
        ServerPlayNetworking.registerGlobalReceiver(MailFetchPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailFetchPayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    DebugLogger.exiting("ModNetworking", "MailFetchPayload handler", "no permission");
                    return;
                }
                UUID mailId = payload.mailId();
                UUID viewer = player.getUUID();
                mailApi(player, "MailFetchPayload", () -> {
                    MailApiClient.DetailResult detail = MailApiClient.fetchDetail(mailId, viewer);
                    // 有附件且无人领取时，编辑期间必须先隐藏，避免接收者看到半成品
                    if (detail.success() && detail.canEdit() && detail.needHidden()) {
                        return new MailEditPrefill(detail, MailApiClient.setHidden(mailId, true));
                    }
                    return new MailEditPrefill(detail, null);
                }, prefill -> {
                    MailApiClient.DetailResult detail = prefill.detail();
                    if (!detail.success() || detail.mail() == null) {
                        ServerPlayNetworking.send(player, MailOpResultPayload.failure(mailId,
                                detail.statusCode() == 404 ? "邮件不存在" : mailApiFailure(detail.message())));
                        DebugLogger.exiting("ModNetworking", "MailFetchPayload handler", "mail not found");
                        return;
                    }
                    Mail mail = detail.mail();
                    MailApiClient.HiddenResult hidden = prefill.hidden();
                    if (hidden != null && hidden.success()) {
                        mail.setHidden(true);
                        for (UUID recipient : hidden.recipients()) {
                            ServerPlayer online = server.getPlayerList().getPlayer(recipient);
                            if (online != null) {
                                ServerPlayNetworking.send(online, MailUpdatePayload.createRemove(mailId));
                            }
                        }
                    }
                    MailRef ref = detail.ref() != null ? detail.ref() : new MailRef(mailId);
                    ServerPlayNetworking.send(player, MailUpdatePayload.createEditPrefill(ref, mail, detail.canEdit()));
                    DebugLogger.info("ModNetworking", "Fetched mail %s for edit (canEdit=%s)",
                            mailId, detail.canEdit());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailFetchPayload handler");
        });

        // 8. 邮件操作（OPEN/READ/STAR/UNSTAR/CLAIM/DELETE）
        ServerPlayNetworking.registerGlobalReceiver(MailActionPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailActionPayload handler", "action=" + payload.action());
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                UUID mailId = payload.mailId();
                switch (payload.action()) {
                    case MailActionPayload.ACTION_OPEN, MailActionPayload.ACTION_READ ->
                        mailStateAction(player, mailId, "read", false);
                    case MailActionPayload.ACTION_STAR -> mailStateAction(player, mailId, "star", false);
                    case MailActionPayload.ACTION_UNSTAR -> mailStateAction(player, mailId, "unstar", false);
                    case MailActionPayload.ACTION_DELETE -> mailStateAction(player, mailId, "delete", true);
                    case MailActionPayload.ACTION_CLAIM -> mailClaim(player, mailId);
                    default -> DebugLogger.warn("ModNetworking", "未知邮件操作: %s", payload.action());
                }
            });
            DebugLogger.exiting("ModNetworking", "MailActionPayload handler");
        });

        // 9. 发布邮件
        ServerPlayNetworking.registerGlobalReceiver(MailAdminSendPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailAdminSendPayload handler", "title=" + payload.title());
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "没有权限"));
                    DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler", "no permission");
                    return;
                }
                // 校验必填项
                if (payload.targets().isEmpty()) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "请至少选择一个接收范围"));
                    DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler", "no targets");
                    return;
                }
                if (payload.title().isEmpty()) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "主题不能为空"));
                    DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler", "empty title");
                    return;
                }
                // 物品附件的 NBT 序列化需要 registryAccess，只能在服务端线程做
                List<MailAttachment> attachments = convertAttachments(payload.attachments(), server);
                List<TargetSpec> targets = payload.targets();
                String sender = player.getScoreboardName();
                String scopeSummary = MailManager.generateScopeSummary(targets);
                Long expireTime = MailManager.computeExpireTime(payload.expireOption());
                mailApi(player, "MailAdminSendPayload", () -> {
                    // 接收范围解析要查 LuckPerms，放在异步线程里做
                    Set<UUID> recipients = MailManager.resolveTargets(targets);
                    return MailApiClient.send(sender, payload.mailType(), targets, scopeSummary,
                            payload.title(), payload.body(), expireTime, attachments, recipients);
                }, result -> {
                    if (!result.success() || result.mail() == null) {
                        ServerPlayNetworking.send(player,
                                MailOpResultPayload.failure(null, mailApiFailure(result.message())));
                        return;
                    }
                    Mail mail = result.mail();
                    for (UUID recipient : result.recipients()) {
                        ServerPlayer online = server.getPlayerList().getPlayer(recipient);
                        if (online != null) {
                            ServerPlayNetworking.send(online,
                                    MailUpdatePayload.createUpdate(new MailRef(mail.getId()), mail));
                        }
                    }
                    MailManager.refreshUnreadFor(server, result.recipients());
                    ServerPlayNetworking.send(player, MailOpResultPayload.success(mail.getId(), "已发布"));
                    DebugLogger.info("ModNetworking", "Mail published: id=%s, title=%s, recipients=%d",
                            mail.getId(), mail.getTitle(), result.recipients().size());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailAdminSendPayload handler");
        });

        // 10. 编辑/取消编辑邮件
        ServerPlayNetworking.registerGlobalReceiver(MailAdminEditPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailAdminEditPayload handler", "mailId=" + payload.mailId());
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                UUID mailId = payload.mailId();
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(mailId, "没有权限"));
                    DebugLogger.exiting("ModNetworking", "MailAdminEditPayload handler", "no permission");
                    return;
                }
                if (payload.cancel()) {
                    // 取消编辑：恢复 hidden=false，并把带各自状态的条目推回在线接收者
                    Set<UUID> onlineSnapshot = onlineUuids(server);
                    mailApi(player, "MailAdminEditPayload:cancel", () -> {
                        MailApiClient.HiddenResult hidden = MailApiClient.setHidden(mailId, false);
                        return new MailEditRestore(hidden, hidden.success()
                                ? MailApiClient.fetchRefs(mailId,
                                        intersectOnline(hidden.recipients(), onlineSnapshot))
                                : Map.of());
                    }, restore -> {
                        MailApiClient.HiddenResult hidden = restore.hidden();
                        if (!hidden.success()) {
                            ServerPlayNetworking.send(player,
                                    MailOpResultPayload.failure(mailId, mailApiFailure(hidden.message())));
                            return;
                        }
                        pushMailToOnline(server, hidden.mail(), restore.refs());
                        ServerPlayNetworking.send(player, MailOpResultPayload.success(mailId, "已取消编辑"));
                        DebugLogger.info("ModNetworking", "Mail edit cancelled: mailId=%s", mailId);
                    });
                    return;
                }
                List<MailAttachment> attachments = convertAttachments(payload.attachments(), server);
                List<TargetSpec> targets = payload.targets();
                String sender = player.getScoreboardName();
                String scopeSummary = MailManager.generateScopeSummary(targets);
                Long expireTime = MailManager.computeExpireTime(payload.expireOption());
                Set<UUID> onlineSnapshot = onlineUuids(server);
                mailApi(player, "MailAdminEditPayload", () -> {
                    Set<UUID> recipients = MailManager.resolveTargets(targets);
                    // hidden=false：保存修改的同时结束编辑期间的隐藏
                    MailApiClient.EditResult edit = MailApiClient.edit(mailId, sender, payload.mailType(), targets,
                            scopeSummary, payload.title(), payload.body(), expireTime, attachments,
                            recipients, false);
                    return new MailEditOutcome(edit, edit.success()
                            ? MailApiClient.fetchRefs(mailId, intersectOnline(edit.recipients(), onlineSnapshot))
                            : Map.of());
                }, outcome -> {
                    MailApiClient.EditResult edit = outcome.edit();
                    if (!edit.success()) {
                        ServerPlayNetworking.send(player, MailOpResultPayload.failure(mailId,
                                edit.message().isBlank() ? "编辑失败，邮件可能已被撤回" : edit.message()));
                        DebugLogger.exiting("ModNetworking", "MailAdminEditPayload handler", "edit rejected");
                        return;
                    }
                    ServerPlayNetworking.send(player, MailOpResultPayload.success(mailId, "已保存修改"));
                    pushMailToOnline(server, edit.mail(), outcome.refs());
                    // 接收范围收窄后已不再持有引用的玩家，通知其移除
                    for (UUID removed : edit.removed()) {
                        ServerPlayer online = server.getPlayerList().getPlayer(removed);
                        if (online != null) {
                            ServerPlayNetworking.send(online, MailUpdatePayload.createRemove(mailId));
                        }
                    }
                    List<UUID> affected = new ArrayList<>(edit.recipients());
                    affected.addAll(edit.removed());
                    MailManager.refreshUnreadFor(server, affected);
                    DebugLogger.info("ModNetworking", "Mail edited: mailId=%s, recipients=%d, removed=%d",
                            mailId, edit.recipients().size(), edit.removed().size());
                });
            });
            DebugLogger.exiting("ModNetworking", "MailAdminEditPayload handler");
        });

        // 11. 请求已注册玩家名单（发布页「选取玩家」弹窗）
        ServerPlayNetworking.registerGlobalReceiver(MailPlayerListRequestPayload.ID, (payload, context) -> {
            DebugLogger.entering("ModNetworking", "MailPlayerListRequestPayload handler");
            var player = (ServerPlayer) context.player();
            var server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!MailPermissionHelper.hasMailPermission(player)) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "没有权限"));
                    DebugLogger.exiting("ModNetworking", "MailPlayerListRequestPayload handler", "no permission");
                    return;
                }
                List<String> names = AccountDataStorage.getAll()
                        .values().stream()
                        .map(account -> account.username)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                ServerPlayNetworking.send(player, new MailPlayerListPayload(names));
                DebugLogger.info("ModNetworking", "Sent registered player list to %s: %d entries",
                        player.getScoreboardName(), names.size());
            });
            DebugLogger.exiting("ModNetworking", "MailPlayerListRequestPayload handler");
        });

        DebugLogger.exiting("ModNetworking", "initialize");
    }

    // ==========================================================================
    // 邮件系统辅助方法
    // ==========================================================================

    /** 编辑预填的两步结果：详情 + （必要时）隐藏操作。 */
    private record MailEditPrefill(MailApiClient.DetailResult detail, MailApiClient.HiddenResult hidden) {
    }

    /** 取消编辑的两步结果：恢复显示 + 各在线接收者的引用。 */
    private record MailEditRestore(MailApiClient.HiddenResult hidden, Map<UUID, MailRef> refs) {
    }

    /** 保存编辑的两步结果：编辑结果 + 编辑后各接收者的引用。 */
    private record MailEditOutcome(MailApiClient.EditResult edit, Map<UUID, MailRef> refs) {
    }

    /** 领取奖励的结果：成功时 detail 为 null，失败时用它把权威条目推回客户端。 */
    private record MailClaimOutcome(MailApiClient.ClaimResult claim, MailApiClient.DetailResult detail) {
    }

    /**
     * 异步执行一次邮件 Api 调用，并把结果交回服务端主线程处理。
     * <p>
     * 邮件数据在 Api 服务端，每个操作都要走 HTTP；同步等待会直接卡住 tick，
     * 所以统一「异步请求 + {@code server.execute} 回主线程」（与账户系统一致）。
     * 结果到达时玩家已下线则直接丢弃。
     * </p>
     *
     * @param player  发起操作的玩家
     * @param label   日志标签
     * @param call    在异步线程执行的 Api 调用
     * @param handler 在主线程执行的结果处理
     */
    private static <T> void mailApi(net.minecraft.server.level.ServerPlayer player, String label,
            java.util.function.Supplier<T> call, java.util.function.Consumer<T> handler) {
        mailApi(player, label, call, handler, true);
    }

    /**
     * 同上，但可指定玩家下线后是否仍然执行结果处理。
     *
     * @param skipWhenOffline true 时玩家下线即丢弃结果；领取奖励需要传 false 以便记录补发线索
     */
    private static <T> void mailApi(net.minecraft.server.level.ServerPlayer player, String label,
            java.util.function.Supplier<T> call, java.util.function.Consumer<T> handler, boolean skipWhenOffline) {
        var server = player.level().getServer();
        if (server == null) return;
        CompletableFuture.supplyAsync(call).whenComplete((result, error) -> server.execute(() -> {
            if (error != null) {
                DebugLogger.exception("ModNetworking", label, error);
                if (!player.hasDisconnected()) {
                    ServerPlayNetworking.send(player, MailOpResultPayload.failure(null, "邮件服务请求失败"));
                }
                return;
            }
            if (result == null) {
                DebugLogger.warn("ModNetworking", "%s 未取得 Api 结果", label);
                return;
            }
            if (skipWhenOffline && player.hasDisconnected()) {
                DebugLogger.debug("ModNetworking", "%s 结果到达时玩家已下线，丢弃", label);
                return;
            }
            handler.accept(result);
        }));
    }

    /** 统一的 Api 失败提示文案。 */
    private static String mailApiFailure(String message) {
        return message == null || message.isBlank() ? "邮件服务不可用" : message;
    }

    /**
     * 在主线程快照当前在线玩家 UUID，供异步阶段收窄批量查询范围。
     * <p>玩家列表不是线程安全的，异步线程里不能直接遍历。</p>
     */
    private static Set<UUID> onlineUuids(net.minecraft.server.MinecraftServer server) {
        Set<UUID> uuids = new HashSet<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            uuids.add(online.getUUID());
        }
        return uuids;
    }

    /**
     * 取交集：群发邮件的接收者可能上千，而批量查询只需要覆盖当前在线的那部分。
     * <p>快照可能略微过期无妨——真正发包前还会再查一次玩家列表。</p>
     */
    private static List<UUID> intersectOnline(Collection<UUID> recipients, Set<UUID> online) {
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : recipients) {
            if (online.contains(uuid)) {
                result.add(uuid);
            }
        }
        return result;
    }

    /**
     * 玩家侧状态变更（已读 / 星标 / 取消星标 / 删除）。
     * <p>客户端已做乐观更新，这里只需回推权威未读数；删除额外推一条移除。</p>
     */
    private static void mailStateAction(net.minecraft.server.level.ServerPlayer player, UUID mailId,
            String action, boolean removeFromClient) {
        UUID uuid = player.getUUID();
        mailApi(player, "MailActionPayload:" + action, () -> MailApiClient.action(uuid, mailId, action), result -> {
            if (!result.success()) {
                DebugLogger.warn("ModNetworking", "邮件操作 %s 失败: mailId=%s, %s",
                        action, mailId, result.message());
                return;
            }
            if (removeFromClient) {
                ServerPlayNetworking.send(player, MailUpdatePayload.createRemove(mailId));
            }
            // 任何操作都可能改变未读数，统一回推权威值（修复红点不消失 / 空信箱仍显示数量）
            MailManager.sendUnread(player, result.unread());
            DebugLogger.info("ModNetworking", "Mail action %s processed for %s",
                    action, player.getScoreboardName());
        });
    }

    /**
     * 领取奖励。
     * <p>
     * Api 先原子写入 claimed 并返回附件，再由 {@link MailManager#applyAttachments}
     * 在主线程实际发放：宁可在极端情况下丢一次奖励，也不能让同一封邮件被领两次。
     * 因此玩家恰好在这一次 HTTP 往返期间下线时，必须留下可补发的日志。
     * </p>
     */
    private static void mailClaim(net.minecraft.server.level.ServerPlayer player, UUID mailId) {
        UUID uuid = player.getUUID();
        mailApi(player, "MailActionPayload:CLAIM", () -> {
            MailApiClient.ClaimResult claim = MailApiClient.claim(uuid, mailId);
            // 失败时顺手取回权威条目，用来撤销客户端的乐观更新
            return claim.success()
                    ? new MailClaimOutcome(claim, null)
                    : new MailClaimOutcome(claim, MailApiClient.fetchDetail(mailId, uuid));
        }, outcome -> {
            MailApiClient.ClaimResult claim = outcome.claim();
            if (!claim.success()) {
                if (player.hasDisconnected()) return;
                ServerPlayNetworking.send(player, MailOpResultPayload.failure(mailId,
                        claim.message().isBlank() ? "领取失败" : claim.message()));
                MailApiClient.DetailResult detail = outcome.detail();
                if (detail != null && detail.success() && detail.mail() != null) {
                    MailRef ref = detail.ref() != null ? detail.ref() : new MailRef(mailId);
                    ServerPlayNetworking.send(player, MailUpdatePayload.createUpdate(ref, detail.mail()));
                }
                return;
            }
            if (player.hasDisconnected()) {
                top.csituka.youzaiworldcore.YouzaiworldCore.LOGGER.warn(
                        "玩家 {} 在领取邮件 {} 期间下线：Api 已记为已领取但奖励未发放，需要管理员补发",
                        player.getScoreboardName(), mailId);
                return;
            }
            // Api 已记账，这里在主线程实际发放
            MailManager.applyAttachments(player, claim.mail());
            MailRef ref = claim.ref() != null ? claim.ref() : new MailRef(mailId, true, false, true);
            ServerPlayNetworking.send(player, MailUpdatePayload.createUpdate(ref, claim.mail()));
            ServerPlayNetworking.send(player, MailOpResultPayload.success(mailId, "已领取奖励"));
            MailManager.sendUnread(player, claim.unread());
        }, false);
    }

    /** 把一封邮件按各接收者自己的引用状态推给在线玩家。 */
    private static void pushMailToOnline(net.minecraft.server.MinecraftServer server, Mail mail,
            Map<UUID, MailRef> refs) {
        if (mail == null) return;
        for (Map.Entry<UUID, MailRef> entry : refs.entrySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());
            if (online != null) {
                ServerPlayNetworking.send(online, MailUpdatePayload.createUpdate(entry.getValue(), mail));
            }
        }
    }

    /** 安全解析账户 UUID；账户缺失或格式非法时返回 null。 */
    private static UUID accountUuid(top.csituka.youzaiworldcore.account.data.PlayerAccount account) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(account.uuid);
        } catch (IllegalArgumentException ignored) {
            DebugLogger.warn("ModNetworking", "账户 UUID 格式无效：%s", account.username);
            return null;
        }
    }

    /**
     * 将网络传输的 AttachmentData（含 ItemStack）转换为存储用的 MailAttachment（NBT 字符串）。
     * <p>需要 {@code registryAccess()}，必须在服务端线程调用。</p>
     */
    private static List<MailAttachment> convertAttachments(
            List<AttachmentData> dataList, net.minecraft.server.MinecraftServer server) {
        if (dataList == null) return List.of();
        var lookup = server.registryAccess();
        return dataList.stream().map(ad -> {
            String itemNbt = null;
            if (ad.itemStack() != null && !ad.itemStack().isEmpty()) {
                try {
                    net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag)
                            net.minecraft.world.item.ItemStack.CODEC.encodeStart(
                                    lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                                    ad.itemStack()).getOrThrow();
                    itemNbt = tag.toString();
                } catch (Exception e) {
                    DebugLogger.error("ModNetworking", "序列化物品附件失败: %s", e.getMessage());
                }
            }
            return new MailAttachment(ad.type(), ad.data(), ad.amount(), itemNbt);
        }).collect(java.util.stream.Collectors.toList());
    }
}
