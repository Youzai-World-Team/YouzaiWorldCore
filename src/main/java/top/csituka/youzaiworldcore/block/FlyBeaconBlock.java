package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
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
    public static final MapCodec<FlyBeaconBlock> CODEC = simpleCodec(FlyBeaconBlock::new);

    public FlyBeaconBlock(BlockBehaviour.Properties properties) {
        super(properties);
        DebugLogger.entering("FlyBeaconBlock", "constructor");
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
        DebugLogger.exiting("FlyBeaconBlock", "constructor");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    @NonNull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        DebugLogger.entering("FlyBeaconBlock", "newBlockEntity", "pos=" + pos);
        FlyBeaconBlockEntity entity = new FlyBeaconBlockEntity(pos, state);
        DebugLogger.exiting("FlyBeaconBlock", "newBlockEntity");
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
        DebugLogger.entering("FlyBeaconBlock", "getTicker");
        if (!level.isClientSide()) {
            DebugLogger.branch("FlyBeaconBlock", "is server side", true);
            DebugLogger.exiting("FlyBeaconBlock", "getTicker", "server ticker");
            return createTickerHelper(blockEntityType, ModBlockEntities.FLY_BEACON, FlyBeaconBlockEntity::serverTick);
        }
        DebugLogger.branch("FlyBeaconBlock", "is client side, no ticker", false);
        DebugLogger.exiting("FlyBeaconBlock", "getTicker", "null (client)");
        return null;
    }

    @Override
    @NonNull
    protected InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
        DebugLogger.entering("FlyBeaconBlock", "useWithoutItem", "pos=" + pos + ", player=" + player.getName().getString());
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof FlyBeaconBlockEntity flyBeacon) {
                player.openMenu(flyBeacon);
            }
        }
        DebugLogger.exiting("FlyBeaconBlock", "useWithoutItem", "SUCCESS");
        return InteractionResult.SUCCESS;
    }
}
