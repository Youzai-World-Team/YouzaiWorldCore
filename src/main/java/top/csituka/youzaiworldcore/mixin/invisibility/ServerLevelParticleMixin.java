package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

import java.util.List;

/**
 * 隐身玩家粒子效果屏蔽 Mixin。
 * <p>
 * 拦截 {@link ServerLevel#sendParticles(ParticleOptions, boolean, boolean, double, double, double, int, double, double, double, double)}
 * 广播版本。当粒子产生位置附近存在隐身玩家时，将粒子数据包仅发送给该隐身玩家自身，
 * 而不广播给其他玩家，从而隐藏隐身玩家触发的粒子效果。
 * </p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelParticleMixin {

    /** 判定粒子是否属于隐身玩家的最大距离平方（2.0 格半径）。 */
    @Unique
    private static final double PARTICLE_PROXIMITY_THRESHOLD_SQ = 4.0; // 2.0^2

    @Shadow
    private List<ServerPlayer> players;

    @Shadow
    private boolean sendParticles(ServerPlayer player, boolean longDistance, double x, double y, double z, Packet<?> packet) {
        throw new AssertionError("Shadow method not implemented");
    }

    /**
     * 拦截 {@code sendParticles(ParticleOptions, boolean, boolean, double, double, double, int, double, double, double, double)}
     * —— 粒子广播方法。
     * <p>
     * 检查粒子位置附近是否存在隐身玩家，若存在则将粒子仅发送给该隐身玩家，
     * 从而对其他玩家隐藏粒子效果。
     * </p>
     */
    @Inject(
            method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends ParticleOptions> void youzaiworldcore$onSendParticles(
            T options,
            boolean longDistance,
            boolean something,
            double x,
            double y,
            double z,
            int count,
            double dx,
            double dy,
            double dz,
            double speed,
            CallbackInfoReturnable<Integer> cir
    ) {
        // 查找粒子位置附近的隐身玩家
        ServerPlayer invisiblePlayer = youzaiworldcore$findInvisiblePlayerAt(x, y, z);
        if (invisiblePlayer == null) {
            // 附近没有隐身玩家，让原逻辑正常广播
            return;
        }

        // 构造粒子数据包
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                options,
                longDistance,
                something,
                x, y, z,
                (float) dx, (float) dy, (float) dz,
                (float) speed,
                count
        );

        // 仅发送给隐身玩家自身，不广播给其他玩家
        boolean sent = this.sendParticles(invisiblePlayer, longDistance, x, y, z, packet);
        cir.setReturnValue(sent ? 1 : 0);
    }

    /**
     * 在粒子位置附近查找隐身玩家。
     *
     * @param x 粒子 X 坐标
     * @param y 粒子 Y 坐标
     * @param z 粒子 Z 坐标
     * @return 找到的隐身玩家，若不存在则返回 {@code null}
     */
    @Unique
    private ServerPlayer youzaiworldcore$findInvisiblePlayerAt(double x, double y, double z) {
        for (ServerPlayer player : this.players) {
            if (InvisibilityManager.isInvisible(player)) {
                double dx = player.getX() - x;
                double dy = player.getY() - y;
                double dz = player.getZ() - z;
                if (dx * dx + dy * dy + dz * dz <= PARTICLE_PROXIMITY_THRESHOLD_SQ) {
                    return player;
                }
            }
        }
        return null;
    }
}
