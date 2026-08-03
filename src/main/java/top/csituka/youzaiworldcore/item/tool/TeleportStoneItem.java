package top.csituka.youzaiworldcore.item.tool;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * 传送石：长按右键蓄力 {@value #CHARGE_TICKS} tick（5 秒）后打开玩家的传送锚点列表，
 * 无需走到某个传送锚点方块前。
 * <p>
 * 可用列表与右键传送锚点方块时完全一致，同样由
 * {@link TeleportAnchorManager#getValidPointsForPlayer(ServerPlayer, net.minecraft.resources.ResourceKey)}
 * 过滤失效锚点与跨维度池条目。
 * <p>
 * 由于不是通过某个具体锚点打开的，数据包中的「当前锚点」坐标与维度传 {@code null}，
 * 客户端界面因此不会显示当前锚点的定位图标，其余功能（重命名 / 删除 / 排序 / 复制坐标 / 搜索）保持一致。
 * 玩家没有任何可用锚点时同样打开界面，由界面显示空列表提示文本。
 * <p>
 * <b>蓄力机制</b>：右键按住进入蓄力，蓄满 5 秒才发送列表数据包打开界面。
 * <ul>
 *   <li>冷却期间无法开始蓄力（原版在 {@code ServerPlayerGameMode#useItem} 里就因冷却拦下了右键，
 *       剩余秒数提示由 {@code ServerPlayerGameModeCooldownMixin} 发到动作栏）</li>
 *   <li>提前松手或受到伤害都会打断蓄力（受伤打断见 {@code TeleportStoneChargeHandler}）</li>
 *   <li>蓄力被打断<b>不</b>进入冷却，也不消耗任何资源——冷却与耐久只在传送真正成功时结算</li>
 * </ul>
 * <p>
 * <b>代价机制</b>（均在传送真正成功时才结算，见
 * {@code ModNetworking} 的 {@code TeleportAnchorTeleportPayload} 处理器）：
 * <ul>
 *   <li>耐久：满耐久 {@value #MAX_DURABILITY}，同维度按直线距离每 {@value #BLOCKS_PER_DURABILITY}
 *       格扣 1 点（不足 1 点按 1 点算），跨维度固定扣 {@value #CROSS_DIMENSION_COST} 点</li>
 *   <li>冷却：传送成功后进入 {@value #COOLDOWN_TICKS} tick（60 秒）冷却，
 *       仅打开列表查看/管理锚点不触发冷却</li>
 *   <li>创造模式免除耐久与冷却</li>
 * </ul>
 */
@SuppressWarnings("null")
public class TeleportStoneItem extends Item {

    /** 满耐久值。 */
    public static final int MAX_DURABILITY = 1000;

    /** 同维度传送时，每多少格直线距离消耗 1 点耐久。 */
    public static final int BLOCKS_PER_DURABILITY = 100;

    /** 跨维度传送的固定耐久消耗（直线距离在不同尺度的维度间没有可比性，故取固定值）。 */
    public static final int CROSS_DIMENSION_COST = 50;

    /** 传送成功后的冷却时长（tick），1200 tick = 60 秒。 */
    public static final int COOLDOWN_TICKS = 1200;

    /** 打开传送列表前需要持续蓄力的时长（tick），100 tick = 5 秒。 */
    public static final int CHARGE_TICKS = 100;

    /** 蓄力期间每隔多少 tick 播放一次蓄力音效。 */
    private static final int CHARGE_SOUND_INTERVAL = 10;

    /** 蓄力期间每 tick 在玩家周围生成的粒子数量（沿圆周均匀分布）。 */
    private static final int CHARGE_PARTICLE_COUNT = 6;

    /** 蓄力粒子环的起始半径（格），随蓄力进度向玩家收拢。 */
    private static final double CHARGE_RING_START_RADIUS = 1.8;

    /** 蓄力粒子环的结束半径（格）。 */
    private static final double CHARGE_RING_END_RADIUS = 0.4;

    public TeleportStoneItem(Properties properties) {
        super(properties.stacksTo(1).durability(MAX_DURABILITY).rarity(Rarity.UNCOMMON));
        DebugLogger.entering("TeleportStoneItem", "constructor");
        DebugLogger.exiting("TeleportStoneItem", "constructor");
    }

    /**
     * 计算本次传送应消耗的耐久点数。
     * <p>
     * 同维度：{@code max(1, ceil(直线距离 / BLOCKS_PER_DURABILITY))}；跨维度：固定
     * {@value #CROSS_DIMENSION_COST} 点。距离取玩家当前位置到锚点落点（方块上表面中心）的欧氏距离，
     * 与客户端列表右侧显示的距离口径一致。
     *
     * @param player 发起传送的玩家（用当前位置计算）
     * @param target 目标传送锚点
     * @return 需要消耗的耐久点数，至少为 1
     */
    public static int computeDurabilityCost(ServerPlayer player, TeleportAnchorData target) {
        if (!target.dimension().equals(player.level().dimension())) {
            return CROSS_DIMENSION_COST;
        }
        BlockPos pos = target.pos();
        double distance = Math.sqrt(player.distanceToSqr(
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
        return Math.max(1, (int) Math.ceil(distance / BLOCKS_PER_DURABILITY));
    }

    // ===== 蓄力 =====

    /**
     * 右键：开始蓄力。真正打开传送列表在 {@link #finishUsingItem} 中进行。
     * <p>
     * 冷却期间原版根本不会调用到本方法（见方法内注释），冷却提示由
     * {@code ServerPlayerGameModeCooldownMixin} 负责。
     */
    @Override
    @NonNull
    public InteractionResult use(Level level, Player player, @NonNull InteractionHand usedHand) {
        DebugLogger.entering("TeleportStoneItem", "use",
                "player=" + player.getName().getString() + ", hand=" + usedHand);

        ItemStack stack = player.getItemInHand(usedHand);

        // 冷却检查：冷却期间连蓄力都不能开始
        // 注意原版 ServerPlayerGameMode#useItem / 客户端预测都会在冷却时直接返回 PASS，
        // 本方法在冷却期间其实到不了，提示文本由 ServerPlayerGameModeCooldownMixin 发出；
        // 这里保留一层兜底，防止其它途径直接调用到 use。
        if (player.getCooldowns().isOnCooldown(stack)) {
            if (player instanceof ServerPlayer serverPlayer) {
                sendCooldownMessage(serverPlayer, stack);
            }
            DebugLogger.branch("TeleportStoneItem", "on cooldown", true);
            DebugLogger.exiting("TeleportStoneItem", "use", "FAIL (cooldown)");
            return InteractionResult.FAIL;
        }

        player.startUsingItem(usedHand);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
            DebugLogger.info("TeleportStoneItem", "玩家 %s 开始蓄力传送石",
                    player.getName().getString());
        }

        DebugLogger.exiting("TeleportStoneItem", "use", "CONSUME (charging)");
        return InteractionResult.CONSUME;
    }

    /** 蓄力总时长；使用满该时长后由原版调用 {@link #finishUsingItem}。 */
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
     * 蓄力过程中的表现：一圈随进度向玩家收拢的传送门粒子，配合逐渐升调的音效。
     * <p>
     * 只在服务端生成，通过 {@link ServerLevel#sendParticles} 广播给附近所有玩家，
     * 保证第三人称视角下别的玩家也能看到蓄力效果。
     *
     * @param remainingTicks 剩余蓄力刻数（{@value #CHARGE_TICKS} → 0）
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
     * 蓄满 5 秒：打开传送锚点列表。
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
            DebugLogger.info("TeleportStoneItem", "蓄力完成但传送石处于冷却中，未打开列表");
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
     * 蓄力被中断（提前松手，或受伤时由 {@code TeleportStoneChargeHandler} 调用
     * {@code stopUsingItem}）：只播放一声中断音效，不进入冷却、不消耗任何资源。
     * <p>
     * 注意原版在 {@code completeUsingItem} 走完 {@link #finishUsingItem} 之后还会调用一次本方法，
     * 此时 {@code remainingTicks} 为 0，需要据此把「正常蓄满」与「中途打断」区分开。
     *
     * @param remainingTicks 中断时剩余的蓄力刻数；0 表示蓄力已正常完成
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
            DebugLogger.info("TeleportStoneItem", "玩家 %s 的传送石蓄力被打断，剩余 %d tick",
                    entity.getName().getString(), remainingTicks);
        }
        return false;
    }

    /**
     * 向玩家发送可用传送锚点列表并标记「本次列表由传送石打开」。
     * <p>
     * 该标记供传送处理器判定是否扣耐久 / 上冷却；
     * currentPos / currentDim 传 null：非经由锚点方块打开，界面不显示「当前锚点」标志。
     */
    private static void openTeleportList(ServerPlayer serverPlayer, InteractionHand hand) {
        TeleportAnchorManager manager = TeleportAnchorManager.get(serverPlayer.level().getServer());
        List<TeleportAnchorData> validPoints =
                manager.getValidPointsForPlayer(serverPlayer, serverPlayer.level().dimension());

        manager.markListOpenedByStone(serverPlayer, hand);

        DebugLogger.info("TeleportStoneItem", "玩家 %s 蓄力完成，打开传送列表，可用锚点 %d 个",
                serverPlayer.getName().getString(), validPoints.size());

        ServerPlayNetworking.send(serverPlayer, new TeleportAnchorListPayload(validPoints, null, null));
    }

    /**
     * 获取传送石剩余冷却秒数（向上取整），用于提示文本。
     * <p>
     * {@code getCooldownPercent} 返回剩余冷却占总时长的比例，乘回总时长即可换算成秒。
     */
    private static int getRemainingCooldownSeconds(ServerPlayer player, ItemStack stack) {
        float percent = player.getCooldowns().getCooldownPercent(stack, 0.0F);
        return Math.max(1, (int) Math.ceil(percent * COOLDOWN_TICKS / 20.0F));
    }

    /**
     * 在动作栏提示玩家传送石还剩多少秒冷却。
     * <p>
     * 由 {@code ServerPlayerGameModeCooldownMixin} 在冷却期间右键传送石时调用——原版会在
     * {@code Item#use} 之前就因冷却返回 PASS，提示只能从那里发出。
     */
    public static void sendCooldownMessage(ServerPlayer player, ItemStack stack) {
        int remaining = getRemainingCooldownSeconds(player, stack);
        sendActionBar(player,
                Component.translatable("message.youzaiworldcore.teleport_stone.cooldown", remaining));
    }

    /**
     * 向玩家发送动作栏消息（屏幕底部，与凭虚法杖的飞行开关提示同一位置）。
     */
    public static void sendActionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay display, Consumer<Component> tooltip,
                                @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip_charge",
                        CHARGE_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip_cost",
                        BLOCKS_PER_DURABILITY, CROSS_DIMENSION_COST)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip_cooldown",
                        COOLDOWN_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
