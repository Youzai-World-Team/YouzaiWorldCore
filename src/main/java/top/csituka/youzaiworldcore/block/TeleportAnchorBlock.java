package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload;
import top.csituka.youzaiworldcore.network.TeleportAnchorOpenNamePayload;

import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 传送锚点方块。
 * <ul>
 *   <li>右键未激活的锚点 → 激活并添加到玩家传送列表</li>
 *   <li>右键已激活的锚点 → 打开传送选择 GUI</li>
 * </ul>
 * 激活状态通过 BlockState 的 ACTIVE 属性和 BlockEntity 中的玩家 UUID 集合共同管理。
 * 当至少一个玩家激活了此锚点时，ACTIVE=true 使方块发光。
 */
@SuppressWarnings("null")
public class TeleportAnchorBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final MapCodec<TeleportAnchorBlock> CODEC = simpleCodec(TeleportAnchorBlock::new);

    public TeleportAnchorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TeleportAnchorBlockEntity(pos, state);
    }

    @Override
    @NonNull
    public RenderShape getRenderShape(@NonNull BlockState state) {
        // 26.2 中已没有 ENTITYBLOCK_ANIMATED，改为 INVISIBLE 以阻止默认方块模型渲染，
        // 由 TeleportAnchorBlockEntityRenderer 接管整个方块的渲染（实现逐玩家纹理）。
        return RenderShape.INVISIBLE;
    }

    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos,
                                                @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof TeleportAnchorBlockEntity anchorBE)) {
            return InteractionResult.SUCCESS;
        }

        UUID playerUuid = player.getUUID();

        if (!anchorBE.isActivatedBy(playerUuid)) {
            // 玩家尚未激活此锚点 → 打开命名界面
            ServerPlayNetworking.send(serverPlayer, new TeleportAnchorOpenNamePayload(pos, level.dimension()));
        } else {
            // 已激活 → 发送传送点列表给客户端
            TeleportAnchorManager manager = TeleportAnchorManager.get(level.getServer());
            var points = manager.getPointsForPlayer(serverPlayer);
            String currentPoolId = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                    .getPoolByDimension(level.dimension().identifier().toString())
                    .map(p -> p.id())
                    .orElse(null);

            var validPoints = points.stream()
                    .filter(p -> {
                        var targetLevel = serverPlayer.level().getServer().getLevel(p.dimension());
                        if (targetLevel == null) return false;
                        BlockState anchorState = targetLevel.getBlockState(p.pos());
                        if (!anchorState.is(this) || !anchorState.getValue(ACTIVE)) return false;
                        // 维度池隔离过滤：同池或至少一方未加入任何池时可通过
                        if (p.poolId() == null || currentPoolId == null) return true;
                        return p.poolId().equals(currentPoolId);
                    })
                    .toList();

            ServerPlayNetworking.send(serverPlayer, new TeleportAnchorListPayload(validPoints, pos, level.dimension()));
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
