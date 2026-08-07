package top.csituka.youzaiworldcore.client.particle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.FunctionToggleClientState;

/**
 * 方块动画粒子渲染器。
 * <p>
 * 为特定方块添加环境粒子效果：
 * <ul>
 * <li><b>信标</b>：光束粒子增强</li>
 * <li><b>酿造台</b>：药水泡泡粒子</li>
 * <li><b>附魔台</b>：附魔字符粒子</li>
 * <li><b>龙蛋</b>：传送门粒子</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("null")
public class BlockAnimationRenderer {

    private static final String MODULE = "BlockAnimationRenderer";
    private static final int TICK_INTERVAL = 4;  // 每 4 tick 生成一次粒子
    private static final double RENDER_RADIUS = 48.0;
    private static final double RENDER_RADIUS_SQ = RENDER_RADIUS * RENDER_RADIUS;

    private static boolean registered = false;
    private static int tickCounter = 0;

    private BlockAnimationRenderer() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(BlockAnimationRenderer::onClientTick);
        DebugLogger.info(MODULE, "方块动画粒子渲染器已注册");
    }

    private static void onClientTick(Minecraft client) {
        tickCounter++;
        if (tickCounter % TICK_INTERVAL != 0) {
            return;
        }

        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            return;
        }

        if (!FunctionToggleClientState.isEnabled("block_animation")) return;

        Vec3 playerPos = player.position();
        int radius = 24; // 搜索半径（方块）

        // 遍历玩家附近的方块
        BlockPos playerBP = BlockPos.containing(playerPos);
        for (BlockPos pos : BlockPos.betweenClosed(
                playerBP.offset(-radius, -radius, -radius),
                playerBP.offset(radius, radius, radius))) {

            // 距离检测
            double dx = pos.getX() + 0.5 - playerPos.x;
            double dy = pos.getY() + 0.5 - playerPos.y;
            double dz = pos.getZ() + 0.5 - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > RENDER_RADIUS_SQ) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            var block = state.getBlock();

            if (block == Blocks.BEACON) {
                // 信标：小型火焰粒子环绕
                if (level.getRandom().nextFloat() < 0.3f) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2;
                    double r = 0.8;
                    level.addParticle(ParticleTypes.FLAME,
                            pos.getX() + 0.5 + Math.cos(angle) * r,
                            pos.getY() + 0.2 + level.getRandom().nextDouble() * 0.3,
                            pos.getZ() + 0.5 + Math.sin(angle) * r,
                            0, 0.02, 0);
                }
            } else if (block == Blocks.BREWING_STAND) {
                // 酿造台：女巫药水粒子
                if (level.getRandom().nextFloat() < 0.5f) {
                    level.addParticle(ParticleTypes.WITCH,
                            pos.getX() + 0.2 + level.getRandom().nextDouble() * 0.6,
                            pos.getY() + 0.6 + level.getRandom().nextDouble() * 0.3,
                            pos.getZ() + 0.2 + level.getRandom().nextDouble() * 0.6,
                            0, 0.01, 0);
                }
            } else if (block == Blocks.ENCHANTING_TABLE) {
                // 附魔台：附魔字符粒子
                if (level.getRandom().nextFloat() < 0.4f) {
                    level.addParticle(ParticleTypes.ENCHANT,
                            pos.getX() + 0.2 + level.getRandom().nextDouble() * 0.6,
                            pos.getY() + 0.9 + level.getRandom().nextDouble() * 0.3,
                            pos.getZ() + 0.2 + level.getRandom().nextDouble() * 0.6,
                            0, 0.03, 0);
                }
            } else if (block == Blocks.DRAGON_EGG) {
                // 龙蛋：传送门粒子
                if (level.getRandom().nextFloat() < 0.6f) {
                    level.addParticle(ParticleTypes.PORTAL,
                            pos.getX() + 0.1 + level.getRandom().nextDouble() * 0.8,
                            pos.getY() + 0.1 + level.getRandom().nextDouble() * 0.7,
                            pos.getZ() + 0.1 + level.getRandom().nextDouble() * 0.8,
                            (level.getRandom().nextDouble() - 0.5) * 0.1,
                            (level.getRandom().nextDouble() - 0.5) * 0.1,
                            (level.getRandom().nextDouble() - 0.5) * 0.1);
                }
            }
        }
    }
}
