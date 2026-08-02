package top.csituka.youzaiworldcore.item.tool;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
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
 */
@SuppressWarnings("null")
public class TeleportStoneItem extends Item {

    public TeleportStoneItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
        DebugLogger.entering("TeleportStoneItem", "constructor");
        DebugLogger.exiting("TeleportStoneItem", "constructor");
    }

    @Override
    @NonNull
    public InteractionResult use(Level level, Player player, @NonNull InteractionHand usedHand) {
        DebugLogger.entering("TeleportStoneItem", "use",
                "player=" + player.getName().getString() + ", hand=" + usedHand);

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

        TeleportAnchorManager manager = TeleportAnchorManager.get(level.getServer());
        List<TeleportAnchorData> validPoints =
                manager.getValidPointsForPlayer(serverPlayer, level.dimension());

        DebugLogger.info("TeleportStoneItem", "玩家 %s 使用传送石打开传送列表，可用锚点 %d 个",
                serverPlayer.getName().getString(), validPoints.size());

        // currentPos / currentDim 传 null：非经由锚点方块打开，界面不显示「当前锚点」标志
        ServerPlayNetworking.send(serverPlayer, new TeleportAnchorListPayload(validPoints, null, null));

        DebugLogger.exiting("TeleportStoneItem", "use", "SUCCESS");
        return InteractionResult.SUCCESS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay display, Consumer<Component> tooltip,
                                @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.teleport_stone.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
