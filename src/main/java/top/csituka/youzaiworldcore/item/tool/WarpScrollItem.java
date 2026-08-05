package top.csituka.youzaiworldcore.item.tool;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;
import java.util.function.Consumer;

/**
 * 传送卷轴：一次性消耗品，等价于传送石的一次性版本。
 * <p>
 * 与传送石共用同一套「传送锚点列表 + 目标选择」流程：
 * <ul>
 *   <li>右键蓄力 {@value #CHARGE_TICKS} tick（5 秒）后打开玩家已点亮的传送锚点列表</li>
 *   <li>被伤害打断时同样停止蓄力、不进入冷却（共用 {@code TeleportStoneChargeHandler}）</li>
 * </ul>
 * <p>
 * <b>与传送石的差异</b>（区别仅在传送真正成功时结算，见
 * {@code ModNetworking} 的 {@code TeleportAnchorTeleportPayload} 处理器）：
 * <ul>
 *   <li><b>代价</b>：消耗整张卷轴（叠堆减 1，耗尽则物品销毁）；不检查经验等级、不涉及耐久</li>
 *   <li><b>冷却</b>：传送成功后进入 {@value #COOLDOWN_TICKS} tick（120 秒）冷却，仅打开列表不触发冷却</li>
 *   <li><b>最大叠堆</b>：{@value #MAX_STACK_SIZE} 个</li>
 *   <li><b>创造模式</b>：与传送石一致，仍免除消耗与冷却</li>
 * </ul>
 * <p>
 * 蓄力时长、粒子效果与传送石完全一致：沿圆周收拢的传送门粒子 + 紫晶块钟声音效，
 * 复用 {@link TeleportStoneItem#CHARGE_TICKS}、{@link TeleportStoneItem#CHARGE_SOUND_INTERVAL} 等常量，
 * 保证两条物品线在视觉与时长上完全一致；卷轴唯一的可见差别是贴图本身。
 */
@SuppressWarnings("null")
public class WarpScrollItem extends Item {

    /** 最大叠堆数量。 */
    public static final int MAX_STACK_SIZE = 16;

    /** 传送成功后的冷却时长（tick），2400 tick = 120 秒。 */
    public static final int COOLDOWN_TICKS = 2400;

    /** 蓄力时长（tick）—— 与传送石保持一致，由 {@link TeleportStoneItem#CHARGE_TICKS} 镜像而来。 */
    public static final int CHARGE_TICKS = TeleportStoneItem.CHARGE_TICKS;

    /** 蓄力期间每隔多少 tick 播放一次蓄力音效（与传送石一致）。 */
    private static final int CHARGE_SOUND_INTERVAL = TeleportStoneItem.getChargeSoundInterval();

    /** 蓄力期间每 tick 在玩家周围生成的粒子数量（与传送石一致）。 */
    private static final int CHARGE_PARTICLE_COUNT = TeleportStoneItem.getChargeParticleCount();

    /** 蓄力粒子环的起始半径（格，传送石公开字段）。 */
    private static final double CHARGE_RING_START_RADIUS = TeleportStoneItem.getChargeRingStartRadius();

    /** 蓄力粒子环的结束半径（格，传送石公开字段）。 */
    private static final double CHARGE_RING_END_RADIUS = TeleportStoneItem.getChargeRingEndRadius();

    public WarpScrollItem(Properties properties) {
        super(properties.stacksTo(MAX_STACK_SIZE).rarity(Rarity.UNCOMMON));
        DebugLogger.entering("WarpScrollItem", "constructor");
        DebugLogger.exiting("WarpScrollItem", "constructor");
    }

    /**
     * 单次传送卷轴的使用代价。固定为 1（叠堆减 1，耗尽则整组物品销毁）。
     * <p>
     * 与 {@link TeleportStoneItem#computeDurabilityCost(Player, TeleportAnchorData)} 不同：
     * 卷轴不涉及耐久 / 距离 / 维度，永远只扣 1 张。即便最后一张也能成功使用。
     */
    public static final int SCROLL_CONSUME_AMOUNT = 1;

    /**
     * 检查玩家是否能用卷轴传送：只要手持数量大于等于 1 即可，与距离、维度无关。
     * <p>
     * 创造模式直接放行；蓄力前的客户端预判也走这个入口——和 {@link TeleportStoneItem#computeDurabilityCost}
     * 一样，参数取 {@link Player}，客户端与服务端算出的结果必须一致。
     */
    public static boolean canAffordScroll(Player player, ItemStack scrollStack) {
        if (scrollStack.isEmpty()) {
            return false;
        }
        return scrollStack.getCount() >= SCROLL_CONSUME_AMOUNT;
    }

    // ===== 蓄力 =====

    /**
     * 右键：开始蓄力。真正打开传送列表在 {@link #finishUsingItem} 中进行。
     * <p>
     * 冷却期间原版根本不会调用到本方法（见 {@code ServerPlayerGameMode#useItem}），
     * 提示由 {@link TeleportStoneItem#sendActionBar} 配合卷轴专用翻译键发出。
     * <p>
     * 与传送石共用 {@link TeleportStoneChargeHandler} 监听伤害打断，因此本方法不重复实现。
     */
    @Override
    @NonNull
    public InteractionResult use(Level level, Player player, @NonNull InteractionHand usedHand) {
        DebugLogger.entering("WarpScrollItem", "use",
                "player=" + player.getName().getString() + ", hand=" + usedHand);

        ItemStack stack = player.getItemInHand(usedHand);

        // 冷却检查：卷轴冷却期间不能再次蓄力
        if (player.getCooldowns().isOnCooldown(stack)) {
            if (player instanceof ServerPlayer serverPlayer) {
                sendCooldownMessage(serverPlayer, stack);
            }
            DebugLogger.branch("WarpScrollItem", "on cooldown", true);
            DebugLogger.exiting("WarpScrollItem", "use", "FAIL (cooldown)");
            return InteractionResult.FAIL;
        }

        player.startUsingItem(usedHand);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
            DebugLogger.info("WarpScrollItem", "玩家 %s 开始蓄力传送卷轴",
                    player.getName().getString());
        }

        DebugLogger.exiting("WarpScrollItem", "use", "CONSUME (charging)");
        return InteractionResult.CONSUME;
    }

    /** 蓄力总时长；与传送石保持完全一致。 */
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
     * 蓄力过程表现：圆周粒子环与逐 tick 升调音效，与传送石完全一致。
     * <p>
     * 数值与 {@link TeleportStoneItem#onUseTick} 取自同一组常量，保证两条物品的视觉时长完全相同——
     * 玩家从传送石切到卷轴不会有任何手感差异。
     *
     * @param remainingTicks 剩余蓄力刻数（{@link #CHARGE_TICKS} → 0）
     */
    @Override
    public void onUseTick(@NonNull Level level, @NonNull LivingEntity entity,
                          @NonNull ItemStack stack, int remainingTicks) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int elapsed = CHARGE_TICKS - remainingTicks;
        float progress = Math.min(1.0F, Math.max(0.0F, elapsed / (float) CHARGE_TICKS));

        // 粒子环：半径随进度收拢，高度随进度上升，整体绕玩家旋转
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

        // 音效：每 CHARGE_SOUND_INTERVAL tick 一声，音调随进度升高
        if (elapsed % CHARGE_SOUND_INTERVAL == 0) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.4F, 0.8F + progress * 1.0F);
        }
    }

    /**
     * 蓄满 5 秒：打开传送锚点列表，并标记本次列表由传送卷轴打开（用于传送处理器结算代价）。
     * <p>
     * 与传送石的区别：本方法额外调用 {@link TeleportAnchorManager#markListOpenedByScroll}，
     * 由传送处理器识别后扣除整张卷轴 + 进入 120 秒冷却；其它验证与传送石一致。
     */
    @Override
    @NonNull
    public ItemStack finishUsingItem(@NonNull ItemStack stack, @NonNull Level level,
                                     @NonNull LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)
                || level.getServer() == null) {
            return stack;
        }

        // 蓄力途中可能因为别的途径进入冷却，打开列表前再校验一次
        if (serverPlayer.getCooldowns().isOnCooldown(stack)) {
            sendCooldownMessage(serverPlayer, stack);
            DebugLogger.info("WarpScrollItem", "蓄力完成但传送卷轴处于冷却中，未打开列表");
            return stack;
        }

        openTeleportList(serverPlayer, entity.getUsedItemHand());

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.4F);
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(),
                    40, 0.4, 0.8, 0.4, 0.3);
        }
        return stack;
    }

    /**
     * 蓄力被中断（提前松手 / 受伤触发 {@code stopUsingItem}）：只播放一声中断音效，
     * 不进入冷却、不消耗任何卷轴——冷却与卷轴消耗只在传送真正成功时结算。
     * <p>
     * 与传送石的判定逻辑一致：{@code remainingTicks} 为 0 时是正常蓄满，不算中断。
     */
    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level,
                                @NonNull LivingEntity entity, int remainingTicks) {
        if (remainingTicks <= 0) {
            // 蓄力已正常完成，不是中断
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
            DebugLogger.info("WarpScrollItem", "玩家 %s 的传送卷轴蓄力被打断，剩余 %d tick",
                    entity.getName().getString(), remainingTicks);
        }
        return false;
    }

    /**
     * 向玩家发送可用传送锚点列表并标记「本次列表由传送卷轴打开」。
     * <p>
     * 与 {@link TeleportStoneItem#openTeleportList} 的区别：标记类型为 SCROLL，
     * 传送处理器据此判断一次性消耗卷轴、附加 120 秒冷却。
     */
    private static void openTeleportList(ServerPlayer serverPlayer, InteractionHand hand) {
        TeleportAnchorManager manager = TeleportAnchorManager.get(serverPlayer.level().getServer());
        List<TeleportAnchorData> validPoints =
                manager.getValidPointsForPlayer(serverPlayer, serverPlayer.level().dimension());

        manager.markListOpenedByScroll(serverPlayer, hand);

        DebugLogger.info("WarpScrollItem", "玩家 %s 蓄力完成，打开传送列表（卷轴入口），可用锚点 %d 个",
                serverPlayer.getName().getString(), validPoints.size());

        ServerPlayNetworking.send(serverPlayer,
                new TeleportAnchorListPayload(validPoints,
                        null, null,
                        TeleportAnchorListPayload.EntryType.SCROLL, hand));
    }

    /**
     * 获取传送卷轴剩余冷却秒数（向上取整），用于提示文本。
     * <p>
     * 与 {@link TeleportStoneItem#getRemainingCooldownSeconds} 同口径，只换常量。
     */
    private static int getRemainingCooldownSeconds(ServerPlayer player, ItemStack stack) {
        float percent = player.getCooldowns().getCooldownPercent(stack, 0.0F);
        return Math.max(1, (int) Math.ceil(percent * COOLDOWN_TICKS / 20.0F));
    }

    /**
     * 在动作栏提示玩家传送卷轴还剩多少秒冷却。
     * <p>
     * 与 {@link TeleportStoneItem#sendCooldownMessage} 同模式：被
     * {@code ServerPlayerGameModeCooldownMixin} 在卷轴冷却期间右键时调用——原版会在
     * {@code Item#use} 之前就因冷却返回 PASS，提示只能从那里发出。
     */
    public static void sendCooldownMessage(ServerPlayer player, ItemStack stack) {
        int remaining = getRemainingCooldownSeconds(player, stack);
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.youzaiworldcore.warp_scroll.cooldown", remaining)));
    }

    /** 向玩家发送动作栏消息（屏幕底部，与传送石的冷却提示同一位置）。 */
    public static void sendActionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay display, Consumer<Component> tooltip,
                                @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.warp_scroll.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.warp_scroll.tooltip_charge",
                        CHARGE_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.warp_scroll.tooltip_consume")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.warp_scroll.tooltip_cooldown",
                        COOLDOWN_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
