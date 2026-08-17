package top.csituka.youzaiworldcore.client.particle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.client.FunctionToggleClientState;

import java.util.function.Predicate;

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

    /** 目标方块状态筛选器：空区块段可直接跳过，不改变实际粒子判定。 */
    private static final Predicate<BlockState> ANIMATED_BLOCK_STATE = state -> {
        var block = state.getBlock();
        return block == Blocks.BEACON
                || block == Blocks.BREWING_STAND
                || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.DRAGON_EGG;
    };

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

        // 按区块段先做 palette 筛选：绝大多数没有目标方块的区块段无需逐格查询。
        BlockPos playerBP = BlockPos.containing(playerPos);
        int minX = playerBP.getX() - radius;
        int maxX = playerBP.getX() + radius;
        int minY = Math.max(level.getMinY(), playerBP.getY() - radius);
        int maxY = Math.min(level.getMaxY(), playerBP.getY() + radius);
        int minZ = playerBP.getZ() - radius;
        int maxZ = playerBP.getZ() + radius;

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        int minSection = level.getSectionIndex(minY);
        int maxSection = level.getSectionIndex(maxY);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] sections = chunk.getSections();
                int sectionFrom = Math.max(0, minSection);
                int sectionTo = Math.min(sections.length - 1, maxSection);
                for (int sectionIndex = sectionFrom; sectionIndex <= sectionTo; sectionIndex++) {
                    LevelChunkSection section = sections[sectionIndex];
                    if (!section.maybeHas(ANIMATED_BLOCK_STATE)) {
                        continue;
                    }

                    int sectionMinY = level.getSectionYFromSectionIndex(sectionIndex) << 4;
                    int fromX = Math.max(minX, chunkX << 4);
                    int toX = Math.min(maxX, (chunkX << 4) + 15);
                    int fromY = Math.max(minY, sectionMinY);
                    int toY = Math.min(maxY, sectionMinY + 15);
                    int fromZ = Math.max(minZ, chunkZ << 4);
                    int toZ = Math.min(maxZ, (chunkZ << 4) + 15);

                    for (int x = fromX; x <= toX; x++) {
                        for (int y = fromY; y <= toY; y++) {
                            for (int z = fromZ; z <= toZ; z++) {
                                double dx = x + 0.5 - playerPos.x;
                                double dy = y + 0.5 - playerPos.y;
                                double dz = z + 0.5 - playerPos.z;
                                if (dx * dx + dy * dy + dz * dz > RENDER_RADIUS_SQ) {
                                    continue;
                                }

                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                var block = state.getBlock();
                                if (block == Blocks.BEACON) {
                                    // 信标：小型火焰粒子环绕
                                    if (level.getRandom().nextFloat() < 0.3f) {
                                        double angle = level.getRandom().nextDouble() * Math.PI * 2;
                                        double r = 0.8;
                                        level.addParticle(ParticleTypes.FLAME,
                                                x + 0.5 + Math.cos(angle) * r,
                                                y + 0.2 + level.getRandom().nextDouble() * 0.3,
                                                z + 0.5 + Math.sin(angle) * r,
                                                0, 0.02, 0);
                                    }
                                } else if (block == Blocks.BREWING_STAND) {
                                    // 酿造台：女巫药水粒子
                                    if (level.getRandom().nextFloat() < 0.5f) {
                                        level.addParticle(ParticleTypes.WITCH,
                                                x + 0.2 + level.getRandom().nextDouble() * 0.6,
                                                y + 0.6 + level.getRandom().nextDouble() * 0.3,
                                                z + 0.2 + level.getRandom().nextDouble() * 0.6,
                                                0, 0.01, 0);
                                    }
                                } else if (block == Blocks.ENCHANTING_TABLE) {
                                    // 附魔台：附魔字符粒子
                                    if (level.getRandom().nextFloat() < 0.4f) {
                                        level.addParticle(ParticleTypes.ENCHANT,
                                                x + 0.2 + level.getRandom().nextDouble() * 0.6,
                                                y + 0.9 + level.getRandom().nextDouble() * 0.3,
                                                z + 0.2 + level.getRandom().nextDouble() * 0.6,
                                                0, 0.03, 0);
                                    }
                                } else if (block == Blocks.DRAGON_EGG) {
                                    // 龙蛋：传送门粒子
                                    if (level.getRandom().nextFloat() < 0.6f) {
                                        level.addParticle(ParticleTypes.PORTAL,
                                                x + 0.1 + level.getRandom().nextDouble() * 0.8,
                                                y + 0.1 + level.getRandom().nextDouble() * 0.7,
                                                z + 0.1 + level.getRandom().nextDouble() * 0.8,
                                                (level.getRandom().nextDouble() - 0.5) * 0.1,
                                                (level.getRandom().nextDouble() - 0.5) * 0.1,
                                                (level.getRandom().nextDouble() - 0.5) * 0.1);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
