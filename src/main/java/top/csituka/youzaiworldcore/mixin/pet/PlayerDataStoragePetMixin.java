package top.csituka.youzaiworldcore.mixin.pet;

import com.llamalad7.mixinextras.sugar.Local;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolPets;

/** 仅在原版玩家档案替换成功后，结算已写盘的肩载宠物快照。 */
@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStoragePetMixin {
    @SuppressWarnings("null")
    @Redirect(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;safeReplaceFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;)V"))
    private void yzwc$confirmShoulderWrite(Path current, Path replacement, Path backup, Player player,
            @Local CompoundTag data) {
        // 原版 void 包装会丢弃成功标志；复用其相同的替换、重试和回滚流程。
        if (Util.safeReplaceOrMoveFile(current, replacement, backup, false)
                && player instanceof ServerPlayer serverPlayer) {
            PoolPets.afterPlayerWrite(serverPlayer.level().getServer(), data);
        }
    }
}
