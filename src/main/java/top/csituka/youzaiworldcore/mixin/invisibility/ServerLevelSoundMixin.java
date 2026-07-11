package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

/**
 * 隐身玩家声音屏蔽 Mixin。
 * <p>
 * 拦截 {@link ServerLevel#playSeededSound} 的两个重载方法，
 * 当声音来源实体是隐身玩家时，取消广播，从而阻止声音传播到其他玩家。
 * 隐身玩家自身仍能听到自己触发的音效（由客户端本地播放）。
 * </p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSoundMixin {

    /**
     * 拦截 {@code playSeededSound(Entity, double, double, double, Holder, SoundSource, float, float, long)}
     * —— 定点音效广播（如玩家饮用药水产生的声音）。
     */
    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void youzaiworldcore$onPlaySeededSoundPositional(
            Entity source,
            double x,
            double y,
            double z,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci
    ) {
        // 如果声音来源是隐身玩家，取消广播（其他玩家听不到，隐身玩家由自身客户端播放）
        if (source instanceof ServerPlayer player && InvisibilityManager.isInvisible(player)) {
            ci.cancel();
        }
    }

    /**
     * 拦截 {@code playSeededSound(Entity, Entity, Holder, SoundSource, float, float, long)}
     * —— 实体绑定音效广播（如玩家自身的脚步声、受伤声等）。
     */
    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void youzaiworldcore$onPlaySeededSoundEntity(
            Entity source,
            Entity target,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci
    ) {
        // 如果声音来源是隐身玩家，取消广播
        if (source instanceof ServerPlayer player && InvisibilityManager.isInvisible(player)) {
            ci.cancel();
        }
    }
}
