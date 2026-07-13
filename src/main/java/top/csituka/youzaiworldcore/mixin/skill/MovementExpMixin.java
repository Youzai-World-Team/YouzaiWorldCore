package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 玩家移动距离追踪 → 每 1km +50
 * 通过 tick 中比较位置变化累计步行距离（非骑乘/飞行时）
 */
@Mixin(ServerPlayer.class)
public class MovementExpMixin {

    @Unique
    private double yzwc$lastMoveX = Double.NaN;
    @Unique
    private double yzwc$lastMoveZ = Double.NaN;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickMove(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.getVehicle() != null || self.isFallFlying()) return; // 非步行不计

        double x = self.getX();
        double z = self.getZ();
        if (Double.isNaN(yzwc$lastMoveX)) {
            yzwc$lastMoveX = x;
            yzwc$lastMoveZ = z;
            return;
        }

        double dx = x - yzwc$lastMoveX;
        double dz = z - yzwc$lastMoveZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist > 0 && dist < 10) { // 忽略传送（距离突变 > 10）
            AdventureLevelManager.addWalkDistance(self, dist);
        }

        yzwc$lastMoveX = x;
        yzwc$lastMoveZ = z;
    }
}
