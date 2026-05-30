package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.entity.DecompositionTableBlockEntity;

/**
 * 分解台方块。
 * <p>
 * 该方块是一个具有方块实体（BlockEntity）的容器类方块，玩家可以通过右键点击打开其 GUI，
 * 进行物品分解操作。继承自 {@link BaseEntityBlock}，因此需要实现 {@link #newBlockEntity} 方法。
 * </p>
 */
public class DecompositionTableBlock extends BaseEntityBlock {

    public static final MapCodec<DecompositionTableBlock> CODEC = simpleCodec(DecompositionTableBlock::new);

    public DecompositionTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    @NonNull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new DecompositionTableBlockEntity(pos, state);
    }

    @Override
    @NonNull
    public RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos,
                                               @NonNull Player player, @NonNull BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DecompositionTableBlockEntity decompositionTable) {
                player.openMenu(decompositionTable);
                if (player instanceof ServerPlayer serverPlayer) {
                    MinecraftServer server = level.getServer();
                    if (server != null) {
                        grantUsedDecompositionTableAdvancement(serverPlayer, server);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 通过执行服务器指令授予玩家“使用分解台”成就。
     *
     * @param player 目标玩家
     * @param server Minecraft 服务器实例
     */
    private void grantUsedDecompositionTableAdvancement(ServerPlayer player, MinecraftServer server) {
        String command = "advancement grant " + player.getName().getString() + " only youzaiworldcore:youzaiworld/used_decomposition_table";
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(),
                command
        );
    }
}