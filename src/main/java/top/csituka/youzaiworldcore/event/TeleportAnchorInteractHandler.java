package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.util.LoadedChunks;

/**
 * 传送锚点上方的交互转发及防放置处理器。
 * <p>
 * 功能一 — 交互转发：石柱高 2 格，实体方块仅占底座 1 格。<br>
 * 玩家右键柱身上半部分时，将交互转发至底座方块实体。
 * <p>
 * 功能二 — 防放置：阻止玩家在锚点正上方（y+1）放置方块。
 */
@SuppressWarnings("null")
public class TeleportAnchorInteractHandler implements UseBlockCallback, UseItemCallback {

    private static final TeleportAnchorInteractHandler INSTANCE = new TeleportAnchorInteractHandler();

    private TeleportAnchorInteractHandler() {}

    public static void register() {
        UseBlockCallback.EVENT.register(INSTANCE);
        UseItemCallback.EVENT.register(INSTANCE);
    }

    // ---- UseBlockCallback: 防上方放置 + 点击锚点上方实体方块时转发 ----

    @Override
    @NonNull
    public InteractionResult interact(Player player, @NonNull Level level, @NonNull InteractionHand hand,
                                       @NonNull BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        BlockPos clickedPos = hitResult.getBlockPos();
        var clickedChunk = LoadedChunks.get(serverLevel, clickedPos);
        if (clickedChunk == null) return InteractionResult.PASS;
        BlockState clickedState = clickedChunk.getBlockState(clickedPos);

        // 对传送锚点本身的交互放行
        if (clickedState.getBlock() instanceof TeleportAnchorBlock) {
            return InteractionResult.PASS;
        }

        // 阻止在锚点正上方放置方块
        BlockPos placementPos = clickedPos.relative(hitResult.getDirection());
        BlockPos belowPlacement = placementPos.below();
        var placementChunk = LoadedChunks.get(serverLevel, belowPlacement);
        if (placementChunk != null && placementChunk.getBlockState(belowPlacement).getBlock() instanceof TeleportAnchorBlock) {
            return InteractionResult.FAIL;
        }

        // 常规转发：点击锚点上方实体方块
        return redirectToAnchorBelow(serverLevel, player, clickedPos, hitResult);
    }

    // ---- UseItemCallback: 点击锚点正上方的空气时转发 ----

    @Override
    @NonNull
    public InteractionResult interact(Player player, @NonNull Level level, @NonNull InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) return InteractionResult.PASS;

        // 沿射线步进采样
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        for (double d = 0.5; d <= 5.0; d += 0.25) {
            Vec3 sample = eye.add(look.scale(d));
            BlockPos clickedPos = BlockPos.containing(sample.x, sample.y, sample.z);
            BlockPos below = clickedPos.below();

            var chunk = LoadedChunks.get(serverLevel, below);
            if (chunk == null) break;
            BlockState belowState = chunk.getBlockState(below);
            if (!(belowState.getBlock() instanceof TeleportAnchorBlock)) {
                continue;
            }

            double relX = sample.x - below.getX();
            double relZ = sample.z - below.getZ();
            if (relX >= -0.1 && relX <= 1.1 && relZ >= -0.1 && relZ <= 1.1) {
                BlockHitResult hit = new BlockHitResult(sample, Direction.UP, below, false);
                InteractionResult result = belowState.useWithoutItem(level, player, hit);
                if (result.consumesAction()) {
                    return result;
                }
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * 检查目标位置下方是否为传送锚点，若是则将交互转发至锚点。
     */
    private static InteractionResult redirectToAnchorBelow(ServerLevel level, Player player,
                                                            BlockPos clickedPos, BlockHitResult hitResult) {
        BlockPos anchorPos = clickedPos.below();
        var chunk = LoadedChunks.get(level, anchorPos);
        if (chunk == null) return InteractionResult.PASS;
        BlockState anchorState = chunk.getBlockState(anchorPos);

        if (anchorState.getBlock() instanceof TeleportAnchorBlock) {
            BlockHitResult redirectedHit = new BlockHitResult(
                    hitResult.getLocation(),
                    hitResult.getDirection(),
                    anchorPos,
                    hitResult.isInside()
            );
            return anchorState.useWithoutItem(level, player, redirectedHit);
        }

        return InteractionResult.PASS;
    }
}
