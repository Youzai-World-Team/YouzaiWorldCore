package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 传送锚点方块。
 * <ul>
 *   <li>右键未激活的锚点 → 激活并添加到玩家传送列表</li>
 *   <li>右键已激活的锚点 → 打开传送选择 GUI</li>
 * </ul>
 */
public class TeleportAnchorBlock extends Block {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final MapCodec<TeleportAnchorBlock> CODEC = simpleCodec(TeleportAnchorBlock::new);

    public TeleportAnchorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos,
                                                @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        boolean active = state.getValue(ACTIVE);

        if (!active) {
            // 激活锚点
            level.setBlock(pos, state.setValue(ACTIVE, true), 3);
            level.sendBlockUpdated(pos, state, state.setValue(ACTIVE, true), 3);

            // 添加到玩家传送列表
            TeleportAnchorManager manager = TeleportAnchorManager.get(level.getServer());
            manager.addPoint(serverPlayer, pos, level.dimension());

            player.sendSystemMessage(
                    Component.translatable("message.youzaiworldcore.teleport_anchor.activated")
            );
        } else {
            // 已激活 → 发送传送点列表给客户端，打开 GUI
            TeleportAnchorManager manager = TeleportAnchorManager.get(level.getServer());
            var points = manager.getPointsForPlayer(serverPlayer);

            // 筛选有效锚点（方块仍在原处且仍处于激活状态）
            var validPoints = points.stream()
                    .filter(p -> {
                        var targetLevel = serverPlayer.level().getServer().getLevel(p.dimension());
                        if (targetLevel == null) return false;
                        BlockState anchorState = targetLevel.getBlockState(p.pos());
                        return anchorState.is(this) && anchorState.getValue(ACTIVE);
                    })
                    .toList();

            ServerPlayNetworking.send(serverPlayer, new TeleportAnchorListPayload(validPoints));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(ACTIVE) && level instanceof Level lvl && lvl.getServer() != null) {
            TeleportAnchorManager.get(lvl.getServer()).removeAnchorAt(pos, lvl.dimension());
        }
        super.destroy(level, pos, state);
    }
}
