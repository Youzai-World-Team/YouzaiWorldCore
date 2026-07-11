package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隐身玩家容器动画屏蔽 Mixin。
 * <p>
 * 当隐身玩家与箱子等容器交互时（如打开箱子），禁止容器动画（箱盖开合）和声音
 * 传播给其他玩家。隐身玩家自身仍可以正常看到动画并与容器交互。
 * </p>
 * <h3>实现原理</h3>
 * <ul>
 *   <li>在 {@link ChestBlockEntity#startOpen} 中记录隐身玩家打开的容器位置</li>
 *   <li>在 {@link ChestBlockEntity#signalOpenCount} 中拦截 blockEvent 调用，阻止动画包广播</li>
 *   <li>在 {@link ChestBlockEntity#playSound} 中拦截声音播放，阻止声音广播</li>
 * </ul>
 */
@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityAnimationMixin {

    /** 记录被隐身玩家打开的容器位置 → 打开的隐身玩家 */
    @Unique
    private static final Map<BlockPos, ServerPlayer> INVIS_CHEST_OPENERS = new ConcurrentHashMap<>();

    // ==================== startOpen ====================

    /**
     * 在 {@code startOpen} 中记录隐身玩家与容器的交互关系。
     * 如果是隐身玩家打开：记录到映射表。如果是非隐身玩家打开：清除映射表中的记录。
     */
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void youzaiworldcore$onStartOpen(ContainerUser user, CallbackInfo ci) {
        ChestBlockEntity self = (ChestBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (user instanceof ServerPlayer player) {
            if (InvisibilityManager.isInvisible(player)) {
                INVIS_CHEST_OPENERS.put(pos, player);
            } else {
                // 如果有显形玩家打开了该容器，清除隐身标记，允许动画正常广播
                INVIS_CHEST_OPENERS.remove(pos);
            }
        }
    }

    // ==================== stopOpen ====================

    /**
     * 在 {@code stopOpen} 中清理容器跟踪记录。
     * 当最后一个开启者关闭容器时移除记录。
     */
    @Inject(method = "stopOpen", at = @At("HEAD"))
    private void youzaiworldcore$onStopOpen(ContainerUser user, CallbackInfo ci) {
        // 不直接移除——让 signalOpenCount 根据 newCount == 0 来清理
        // 这里仅用于标记，实际清理在 signalOpenCount 中完成
    }

    // ==================== signalOpenCount ====================

    /**
     * 在 {@code signalOpenCount} 中拦截 blockEvent 调用。
     * 如果容器是由隐身玩家打开的，取消 blockEvent 调用，
     * 从而阻止 {@link net.minecraft.network.protocol.game.ClientboundBlockEventPacket}
     * 广播给其他玩家。
     */
    @Inject(method = "signalOpenCount", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onSignalOpenCount(
            Level level,
            BlockPos pos,
            BlockState state,
            int oldCount,
            int newCount,
            CallbackInfo ci
    ) {
        // 如果此容器在隐身玩家打开记录中，阻止动画广播
        if (INVIS_CHEST_OPENERS.containsKey(pos)) {
            ci.cancel();
        }

        // 当容器完全关闭时，清理记录
        if (newCount == 0) {
            INVIS_CHEST_OPENERS.remove(pos);
        }
    }

    // ==================== playSound ====================

    /**
     * 在 {@code playSound} 中拦截声音播放。
     * 如果容器是由隐身玩家打开的，取消声音播放调用，
     * 从而阻止容器开合声音传播给其他玩家。
     * <p>
     * {@code playSound} 是私有静态方法，由 {@code ContainerOpenersCounter} 匿名内部类的
     * {@code onOpen} / {@code onClose} 调用。
     * </p>
     */
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private static void youzaiworldcore$onPlaySound(
            Level level,
            BlockPos pos,
            BlockState state,
            SoundEvent sound,
            CallbackInfo ci
    ) {
        if (INVIS_CHEST_OPENERS.containsKey(pos)) {
            ci.cancel();
        }
    }
}
