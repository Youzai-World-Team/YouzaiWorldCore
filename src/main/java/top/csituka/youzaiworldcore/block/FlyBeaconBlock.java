package top.csituka.youzaiworldcore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import org.jetbrains.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

@SuppressWarnings("null")
public class FlyBeaconBlock extends BaseEntityBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public FlyBeaconBlock(BlockBehaviour.Properties properties) {
        super(properties);
        DebugLogger.SERVER.entering("FlyBeaconBlock", "constructor");
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
        DebugLogger.SERVER.exiting("FlyBeaconBlock", "constructor");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        DebugLogger.SERVER.entering("FlyBeaconBlock", "newBlockEntity", "pos=" + pos);
        FlyBeaconBlockEntity entity = new FlyBeaconBlockEntity(pos, state);
        DebugLogger.SERVER.exiting("FlyBeaconBlock", "newBlockEntity");
        return entity;
    }

    @Override
    @NonNull
    public RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> blockEntityType) {
        DebugLogger.SERVER.entering("FlyBeaconBlock", "getTicker");
        if (!level.isClientSide()) {
            DebugLogger.SERVER.branch("FlyBeaconBlock", "is server side", true);
            DebugLogger.SERVER.exiting("FlyBeaconBlock", "getTicker", "server ticker");
            return createTickerHelper(blockEntityType, ModBlockEntities.FLY_BEACON, FlyBeaconBlockEntity::serverTick);
        }
        DebugLogger.SERVER.branch("FlyBeaconBlock", "is client side, no ticker", false);
        DebugLogger.SERVER.exiting("FlyBeaconBlock", "getTicker", "null (client)");
        return null;
    }

    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        DebugLogger.SERVER.entering("FlyBeaconBlock", "useWithoutItem", "pos=" + pos + ", player=" + player.getName().getString());
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof FlyBeaconBlockEntity flyBeacon) {
                player.openMenu(flyBeacon);
            }
        }
        DebugLogger.SERVER.exiting("FlyBeaconBlock", "useWithoutItem", "SUCCESS");
        return InteractionResult.SUCCESS;
    }
}
