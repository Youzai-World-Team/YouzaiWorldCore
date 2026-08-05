package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.function.Consumer;

/**
 * 返回卷轴：基于「就近传送」的便捷一次性物品。
 * <p>
 * 与传送卷轴共用蓄力框架（5 秒右键蓄力 + 弓动作 + 伤害打断），但完成蓄力后的动作不同：
 * <ul>
 * <li>直接寻址玩家当前维度中距离最近的「有效传送锚点」（参见
 * {@link TeleportAnchorManager#findNearestActiveAnchorInDimension}），并执行传送，
 * 传送成功后 <b>扣 1 张</b>（叠堆减 1，耗尽则该组物品销毁）+ 进入
 * {@value #COOLDOWN_TICKS} tick（60 秒）物品冷却。</li>
 * <li>玩家当前维度<b>没有任何可用锚点</b>时，不传送也不消耗物品，仅在动作栏显示
 * 「在此维度没有可用的传送锚点~」并附加 60 秒冷却。
 * 这一分支的存在是为了让玩家在「完全无锚点」的存档里依然能感知到物品生效了。</li>
 * <li>蓄力途中受伤害或松开右键时同样走 {@link TeleportStoneChargeHandler} 的中断路径，
 * 不进入冷却、不消耗物品。</li>
 * </ul>
 * <p>
 * 因为跳过 GUI、远端不需要选择锚点，本物品<b>不</b>涉及 {@code TeleportAnchorListPayload} /
 * {@code EntryType}，全流程在服务端 {@code finishUsingItem} 内闭环；
 * 这也是它与传送卷轴最大的行为差异——可以视为「为上一次传送石 / 传送锚点做反向操作」的快捷品。
 * <p>
 * 创造模式与传统传送一致：免冷却、免物品消耗，但仍完整执行传送动作（便于测试）。
 */
@SuppressWarnings("null")
public class ReturnScrollItem extends Item {

    /** 最大叠堆。 */
    public static final int MAX_STACK_SIZE = 16;

    /** 传送成功后的冷却时长（tick），1200 tick = 60 秒。 */
    public static final int COOLDOWN_TICKS = 1200;

    /** 蓄力时长（tick），与传送石 / 传送卷轴保持 5 秒。 */
    public static final int CHARGE_TICKS = TeleportStoneItem.CHARGE_TICKS;

    /** 单次传送卷轴的使用量。固定为 1，叠堆减 1，耗尽则该组物品销毁。 */
    public static final int SCROLL_CONSUME_AMOUNT = 1;

    private static final int CHARGE_SOUND_INTERVAL = TeleportStoneItem.getChargeSoundInterval();
    private static final int CHARGE_PARTICLE_COUNT = TeleportStoneItem.getChargeParticleCount();
    private static final double CHARGE_RING_START_RADIUS = TeleportStoneItem.getChargeRingStartRadius();
    private static final double CHARGE_RING_END_RADIUS = TeleportStoneItem.getChargeRingEndRadius();

    public ReturnScrollItem(Properties properties) {
        super(properties.stacksTo(MAX_STACK_SIZE).rarity(Rarity.UNCOMMON));
        DebugLogger.entering("ReturnScrollItem", "constructor");
        DebugLogger.exiting("ReturnScrollItem", "constructor");
    }

    /**
     * 取目标锚点的目标维度实例：因为本物品限定「同维度」，目标维度 = 玩家当前维度。
     * 提取为单独方法以便日后扩展跨维度需求时只改这一处。
     */
    private static ServerLevel targetLevel(ServerPlayer serverPlayer, TeleportAnchorData target) {
        return serverPlayer.level().getServer().getLevel(target.dimension());
    }

    /**
     * 右键：开始蓄力。真正执行回程传送在 {@link #finishUsingItem} 中进行。
     * <p>
     * 冷却期间原版根本不会调用本方法（见 {@code ServerPlayerGameMode#useItem}），
     * 冷却提示由 {@link #sendCooldownMessage} 发出。
     */
    @Override
    @NonNull
    public InteractionResult use(Level level, Player player, @NonNull InteractionHand usedHand) {
        DebugLogger.entering("ReturnScrollItem", "use",
                "player=" + player.getName().getString() + ", hand=" + usedHand);

        ItemStack stack = player.getItemInHand(usedHand);

        if (player.getCooldowns().isOnCooldown(stack)) {
            if (player instanceof ServerPlayer serverPlayer) {
                sendCooldownMessage(serverPlayer, stack);
            }
            DebugLogger.branch("ReturnScrollItem", "on cooldown", true);
            DebugLogger.exiting("ReturnScrollItem", "use", "FAIL (cooldown)");
            return InteractionResult.FAIL;
        }

        player.startUsingItem(usedHand);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
            DebugLogger.info("ReturnScrollItem", "玩家 %s 开始蓄力返回卷轴",
                    player.getName().getString());
        }

        DebugLogger.exiting("ReturnScrollItem", "use", "CONSUME (charging)");
        return InteractionResult.CONSUME;
    }

    /** 蓄力总时长；与传送石 / 传送卷轴保持完全一致。 */
    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return CHARGE_TICKS;
    }

    /** 借用弓的蓄力动作，让玩家在蓄力期间保持举手姿势。 */
    @Override
    @NonNull
    public ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    /**
     * 蓄力过程表现：圆周粒子环与逐 tick 升调音效，与传送石 / 传送卷轴完全一致。
     */
    @Override
    public void onUseTick(@NonNull Level level, @NonNull LivingEntity entity,
            @NonNull ItemStack stack, int remainingTicks) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int elapsed = CHARGE_TICKS - remainingTicks;
        float progress = Math.min(1.0F, Math.max(0.0F, elapsed / (float) CHARGE_TICKS));

        double radius = CHARGE_RING_START_RADIUS
                - (CHARGE_RING_START_RADIUS - CHARGE_RING_END_RADIUS) * progress;
        double height = 0.1 + progress * 1.6;
        for (int i = 0; i < CHARGE_PARTICLE_COUNT; i++) {
            double angle = elapsed * 0.25 + i * (Math.PI * 2 / CHARGE_PARTICLE_COUNT);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    entity.getX() + Math.cos(angle) * radius,
                    entity.getY() + height,
                    entity.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.02);
        }

        if (elapsed % CHARGE_SOUND_INTERVAL == 0) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.4F, 0.8F + progress * 1.0F);
        }
    }

    /**
     * 蓄满 5 秒：执行回程传送。
     * <p>
     * 三种结果：
     * <ol>
     * <li>找到最近锚点 → 传送 + 扣 1 张 + 60s 冷却（创造模式：只传送、免扣免冷）</li>
     * <li>同维度无任何可用锚点 → 动作栏提示 + 60s 冷却（<b>不消耗</b>卷轴）</li>
     * <li>蓄力途中进入冷却 → 动作栏提示，<b>不</b>扣物品、不进新冷却（兜底）</li>
     * </ol>
     */
    @Override
    @NonNull
    public ItemStack finishUsingItem(@NonNull ItemStack stack, @NonNull Level level,
            @NonNull LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)
                || level.getServer() == null) {
            return stack;
        }

        // 蓄力途中可能因为别的途径进入冷却，先校验一次
        if (serverPlayer.getCooldowns().isOnCooldown(stack)) {
            sendCooldownMessage(serverPlayer, stack);
            DebugLogger.info("ReturnScrollItem", "蓄力完成但返回卷轴处于冷却中，未执行回程");
            return stack;
        }

        TeleportAnchorManager manager = TeleportAnchorManager.get(level.getServer());
        var nearestOpt = manager.findNearestActiveAnchorInDimension(
                serverPlayer, serverPlayer.level().dimension());

        if (nearestOpt.isEmpty()) {
            // 当前维度无任何可用锚点 → 提示 + 冷却，不扣卷轴
            handleNoAnchor(serverPlayer, stack, level);
            return stack;
        }

        TeleportAnchorData target = nearestOpt.get();
        executeTeleport(serverPlayer, target, stack, level);
        return stack;
    }

    /**
     * 蓄力被中断（提前松手 / 受伤触发 {@code stopUsingItem}）：只播放中断音效，
     * 不进入冷却、不消耗卷轴——冷却与卷轴消耗只在传送真正成功时结算。
     */
    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level,
            @NonNull LivingEntity entity, int remainingTicks) {
        if (remainingTicks <= 0) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
            DebugLogger.info("ReturnScrollItem", "玩家 %s 的返回卷轴蓄力被打断，剩余 %d tick",
                    entity.getName().getString(), remainingTicks);
        }
        return false;
    }

    /**
     * 当前维度内没有可用锚点：不扣卷轴，但进入 60 秒冷却，避免玩家反复右键刷屏。
     * <p>
     * 同时广播「传送门」风格粒子给附近所有玩家，便于群体玩法中其他人意识到该玩家意图回程失败。
     */
    private void handleNoAnchor(ServerPlayer serverPlayer, ItemStack stack, Level level) {
        boolean isCreative = serverPlayer.getAbilities().instabuild;
        if (!isCreative) {
            serverPlayer.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        }
        serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.youzaiworldcore.return_scroll.no_anchor")
                        .withStyle(ChatFormatting.YELLOW)));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                    20, 0.4, 0.4, 0.4, 0.05);
        }
        DebugLogger.info("ReturnScrollItem",
                "玩家 %s 当前维度无任何可用锚点，已进入冷却但未消耗卷轴",
                serverPlayer.getName().getString());
    }

    /**
     * 执行回程传送：扣 1 张卷轴 + 进入 60s 冷却，使用玩家当前位置 → 锚点上表面中心的精确传送。
     * <p>
     * 不走 {@code TeleportAnchorTeleportPayload}（那是 GUI 路径），改为服务端直接调用
     * {@link ServerPlayer#teleportTo}：
     * <ul>
     * <li>玩家维度与锚点维度一致——直接站内传送</li>
     * <li>传送成功后在玩家所在维度公告一次回程结果（仅输出到本玩家的动作栏）</li>
     * <li>创造模式：只传送但不进冷却、不扣卷轴，便于快速测试</li>
     * </ul>
     */
    private void executeTeleport(ServerPlayer serverPlayer, TeleportAnchorData target,
            ItemStack stack, Level level) {
        boolean isCreative = serverPlayer.getAbilities().instabuild;

        // 真正传送之前先扣卷轴与上冷却：若扣后被客观失败（如目标维度被卸载）则玩家仍会损失 1 张。
        // 这里目标维度与玩家同维度所以不会失败，但保持「先扣、再传」的顺序与传送石一致，便于
        // 服务端审计流程统一；传送失败也只是简单 return。
        if (!isCreative) {
            serverPlayer.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            stack.shrink(SCROLL_CONSUME_AMOUNT);
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.4F);
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                    40, 0.4, 0.8, 0.4, 0.3);
        }

        serverPlayer.teleportTo(ReturnScrollItem.targetLevel(serverPlayer, target),
                target.pos().getX() + 0.5,
                target.pos().getY() + 1.0,
                target.pos().getZ() + 0.5,
                java.util.Set.of(),
                serverPlayer.getYRot(),
                serverPlayer.getXRot(),
                true);

        serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.youzaiworldcore.return_scroll.teleported", target.name())
                        .withStyle(ChatFormatting.GREEN)));

        DebugLogger.info("ReturnScrollItem",
                "玩家 %s 通过返回卷轴传送到最近锚点 %s (次元=%s, 消耗 %d 张, 冷却 %d tick)",
                serverPlayer.getName().getString(),
                target.name(),
                target.dimension().identifier(),
                isCreative ? 0 : SCROLL_CONSUME_AMOUNT,
                isCreative ? 0 : COOLDOWN_TICKS);
    }

    /**
     * 获取返回卷轴剩余冷却秒数（向上取整），用于提示文本。
     */
    private static int getRemainingCooldownSeconds(ServerPlayer player, ItemStack stack) {
        float percent = player.getCooldowns().getCooldownPercent(stack, 0.0F);
        return Math.max(1, (int) Math.ceil(percent * COOLDOWN_TICKS / 20.0F));
    }

    /**
     * 在动作栏提示玩家返回卷轴还剩多少秒冷却。
     * <p>
     * 被 {@code ServerPlayerGameModeCooldownMixin} 在冷却期间右键时调用——原版会在
     * {@code Item#use} 之前就因冷却返回 PASS，提示只能从那里发出。
     */
    public static void sendCooldownMessage(ServerPlayer player, ItemStack stack) {
        int remaining = getRemainingCooldownSeconds(player, stack);
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.youzaiworldcore.return_scroll.cooldown", remaining)));
    }

    /** 向玩家发送动作栏消息（屏幕底部，与传送物品的冷却提示同一位置）。 */
    public static void sendActionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
            @NonNull TooltipDisplay display, Consumer<Component> tooltip,
            @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.return_scroll.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.return_scroll.tooltip_charge",
                CHARGE_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.return_scroll.tooltip_consume")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.return_scroll.tooltip_cooldown",
                COOLDOWN_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
