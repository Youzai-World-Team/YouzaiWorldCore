package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.config.EventSettings;

/**
 * 三叉戟虚空保护事件处理器。
 * <p>
 * 当三叉戟落入虚空（Y 坐标低于世界最低高度）时，
 * 自动将其转换为掉落物返回给投掷者附近。
 * 监听 {@link ServerTickEvents#START_SERVER_TICK} 进行周期性检测。
 * </p>
 */
@SuppressWarnings("null")
public class TridentVoidHandler {

    private static final TridentVoidHandler INSTANCE = new TridentVoidHandler();
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/TridentVoid");

    /** 扫描间隔（tick），避免每tick全实体遍历。三叉戟是低频事件，20tick（1秒）足够。 */
    private static final int SCAN_INTERVAL = 20;
    private static final EntityTypeTest<Entity, ThrownTrident> TRIDENT_TEST =
            EntityTypeTest.forClass(ThrownTrident.class);
    private int tickCounter = 0;

    private TridentVoidHandler() {
    }

    private void onServerTick(ServerLevel level) {
        if (!EventSettings.isTridentVoidProtectEnabled()) return;

        // 直接从实体索引取三叉戟，避免每秒遍历本维度的全部实体。
        for (ThrownTrident trident : level.getEntities(TRIDENT_TEST, entity -> true)) {
            Entity entity = trident;
            // 检查是否低于世界最低高度
            if (entity.getY() > level.getMinY() - 10) {
                continue;
            }

            // 获取三叉戟的物品堆
            ItemStack tridentStack = trident.getPickupItemStackOrigin();

            // 在投掷者或世界出生点生成掉落物
            Entity owner = trident.getOwner();
            double spawnX, spawnY, spawnZ;

            if (owner instanceof Player player) {
                spawnX = player.getX();
                spawnY = player.getY() + 1.0;
                spawnZ = player.getZ();
                LOGGER.info("三叉戟落入虚空，返还给投掷者 {} at [{}, {}, {}]",
                        player.getName().getString(), spawnX, spawnY, spawnZ);
            } else {
                var respawnData = level.getRespawnData();
                spawnX = respawnData.pos().getX() + 0.5;
                spawnY = respawnData.pos().getY() + 1.0;
                spawnZ = respawnData.pos().getZ() + 0.5;
                LOGGER.info("三叉戟落入虚空，掉落于世界出生点");
            }

            ItemEntity itemEntity = new ItemEntity(level,
                    spawnX, spawnY, spawnZ, tridentStack.copy());
            itemEntity.setDefaultPickUpDelay();
            itemEntity.setNoGravity(false);
            level.addFreshEntity(itemEntity);

            // 移除原三叉戟实体
            trident.discard();

            LOGGER.debug("三叉戟已从虚空回收");
        }
    }

    /**
     * 注册事件处理器。
     */
    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            // 节流：每 SCAN_INTERVAL tick 扫描一次，而非每 tick 全维度遍历
            if (++INSTANCE.tickCounter < SCAN_INTERVAL) {
                return;
            }
            INSTANCE.tickCounter = 0;
            for (ServerLevel level : server.getAllLevels()) {
                INSTANCE.onServerTick(level);
            }
        });
        LOGGER.info("三叉戟虚空保护事件处理器已注册 (scanInterval={}tick)", SCAN_INTERVAL);
    }
}
