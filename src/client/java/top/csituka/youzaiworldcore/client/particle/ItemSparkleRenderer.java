package top.csituka.youzaiworldcore.client.particle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 物品闪烁粒子渲染器。
 * <p>
 * 为掉落物（ItemEntity）添加随机电火花粒子效果，
 * 使其在地面上更显眼。
 * </p>
 */
@SuppressWarnings("null")
public class ItemSparkleRenderer {

    private static final String MODULE = "ItemSparkleRenderer";
    private static final int TICK_INTERVAL = 5;  // 每 5 tick（0.25 秒）检查一次
    private static final double RENDER_RADIUS = 32.0;

    private static boolean registered = false;
    private static int tickCounter = 0;

    private ItemSparkleRenderer() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(ItemSparkleRenderer::onClientTick);
        DebugLogger.info(MODULE, "物品闪烁粒子渲染器已注册");
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

        // 搜索玩家附近的掉落物
        AABB searchBox = player.getBoundingBox().inflate(RENDER_RADIUS);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
            if (itemEntity.isRemoved()) {
                continue;
            }

            // 每个物品有 40% 概率产生粒子
            if (level.getRandom().nextFloat() < 0.4f) {
                double x = itemEntity.getX() + (level.getRandom().nextDouble() - 0.5) * 0.5;
                double y = itemEntity.getY() + 0.1 + level.getRandom().nextDouble() * 0.3;
                double z = itemEntity.getZ() + (level.getRandom().nextDouble() - 0.5) * 0.5;

                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        x, y, z,
                        0, 0.01, 0);
            }
        }
    }
}
