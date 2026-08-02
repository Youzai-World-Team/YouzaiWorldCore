package top.csituka.youzaiworldcore.item.tool;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * 传送石：右键直接打开玩家的传送锚点列表，无需走到某个传送锚点方块前。
 * <p>
 * 可用列表与右键传送锚点方块时完全一致，同样由
 * {@link TeleportAnchorManager#getValidPointsForPlayer(ServerPlayer, net.minecraft.resources.ResourceKey)}
 * 过滤失效锚点与跨维度池条目。
 * <p>
 * 由于不是通过某个具体锚点打开的，数据包中的「当前锚点」坐标与维度传 {@code null}，
 * 客户端界面因此不会显示当前锚点的定位图标，其余功能（重命名 / 删除 / 排序 / 复制坐标 / 搜索）保持一致。
 * 玩家没有任何可用锚点时同样打开界面，由界面显示空列表提示文本。
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

    @Override
    @NonNull
    public InteractionResult use(Level level, Player player, @NonNull InteractionHand usedHand) {
        DebugLogger.entering("TeleportStoneItem", "use",
                "player=" + player.getName().getString() + ", hand=" + usedHand);

        ItemStack stack = player.getItemInHand(usedHand);

        // 权威逻辑只在服务端执行；客户端直接返回 SUCCESS 以播放挥手动画
        if (level.isClientSide()) {
            DebugLogger.branch("TeleportStoneItem", "client side", true);
            DebugLogger.exiting("TeleportStoneItem", "use", "SUCCESS (client)");
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || level.getServer() == null) {
            DebugLogger.exiting("TeleportStoneItem", "use", "PASS (not a server player)");
            return InteractionResult.PASS;
        }

        // 冷却检查：冷却期间连列表都打不开
        if (serverPlayer.getCooldowns().isOnCooldown(stack)) {
            int remaining = getRemainingCooldownSeconds(serverPlayer, stack);
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.youzaiworldcore.teleport_stone.cooldown", remaining));
            DebugLogger.branch("TeleportStoneItem", "on cooldown", true);
            DebugLogger.exiting("TeleportStoneItem", "use", "FAIL (cooldown, " + remaining + "s left)");
            return InteractionResult.FAIL;
        }

        TeleportAnchorManager manager = TeleportAnchorManager.get(level.getServer());
        List<TeleportAnchorData> validPoints =
                manager.getValidPointsForPlayer(serverPlayer, level.dimension());

        // 标记本次列表由传送石打开，供传送处理器判定是否扣耐久 / 上冷却
        manager.markListOpenedByStone(serverPlayer, usedHand);

        DebugLogger.info("TeleportStoneItem", "玩家 %s 使用传送石打开传送列表，可用锚点 %d 个",
                serverPlayer.getName().getString(), validPoints.size());

        // currentPos / currentDim 传 null：非经由锚点方块打开，界面不显示「当前锚点」标志
        ServerPlayNetworking.send(serverPlayer, new TeleportAnchorListPayload(validPoints, null, null));

        DebugLogger.exiting("TeleportStoneItem", "use", "SUCCESS");
        return InteractionResult.SUCCESS;
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

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay display, Consumer<Component> tooltip,
                                @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip_cost",
                        BLOCKS_PER_DURABILITY, CROSS_DIMENSION_COST)
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip_cooldown",
                        COOLDOWN_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
