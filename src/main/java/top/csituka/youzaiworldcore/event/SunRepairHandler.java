package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.levelgen.Heightmap;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;

import java.util.Random;

/**
 * 阳光修复附魔的 tick 处理器。
 * 每 5~10 秒随机间隔检查在线玩家，在阳光下时修复 1 耐久。
 */
@SuppressWarnings("null")
public class SunRepairHandler {

    private static int tickCounter = 0;
    private static int currentInterval = 150; // 初始 7.5s
    private static final int MIN_TICK = 100;  // 5s
    private static final int MAX_TICK = 200;  // 10s
    private static final Random RANDOM = new Random();
    private static Holder<Enchantment> cachedEnchantment;

    /**
     * 阳光判定的复用游标。
     * <p>
     * 仅在服务端主线程的 {@code START_SERVER_TICK} 回调中使用，无并发访问。
     * </p>
     */
    private static final BlockPos.MutableBlockPos SCAN_CURSOR = new BlockPos.MutableBlockPos();

    /** 人形护甲槽位（预筛选，避免每次 {@code EquipmentSlot.values()} 克隆数组） */
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS =
            java.util.Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values())
                    .filter(s -> s.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR)
                    .toArray(net.minecraft.world.entity.EquipmentSlot[]::new);

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < currentInterval) return;
            tickCounter = 0;
            currentInterval = MIN_TICK + RANDOM.nextInt(MAX_TICK - MIN_TICK + 1);

            if (cachedEnchantment == null) {
                var reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                cachedEnchantment = reg.getOrThrow(ModEnchantments.SUN_REPAIR_KEY);
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!isInSunlight(player)) continue;
                repairPlayerItems(player);
            }
        });
    }

    private static void repairPlayerItems(ServerPlayer player) {
        // 手持
        checkStack(player.getMainHandItem());
        checkStack(player.getOffhandItem());
        // 盔甲栏（ARMOR_SLOTS 为预筛选常量：enum values() 每次调用都会克隆一份数组）
        for (var slot : ARMOR_SLOTS) {
            checkStack(player.getItemBySlot(slot));
        }
        // 物品栏
        var inv = player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            checkStack(inv.getItem(i));
        }
    }

    /** 带阳光修复附魔的物品恢复耐久，修复量随等级提升（每级 +1 耐久/次） */
    private static void checkStack(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        if (!stack.isDamaged()) return;
        int level = stack.getEnchantments().getLevel(cachedEnchantment);
        if (level > 0) {
            int repaired = Math.min(level, stack.getDamageValue());
            stack.setDamageValue(stack.getDamageValue() - repaired);
        }
    }

    /**
     * 玩家是否暴露在阳光下。
     * <p>
     * 判定逻辑（头顶是否存在完整碰撞方块）与原实现<b>完全一致</b>，只优化扫描方式：
     * </p>
     * <ul>
     *   <li><b>扫描上界</b>：原先一路扫到世界顶（主世界 ~256 次迭代）。改用
     *       {@code MOTION_BLOCKING} 高度图给出的地形顶端作为上界——完整碰撞方块
     *       必然阻挡移动，因此高度图之上不可能存在完整碰撞方块，截断不改变结论。
     *       地表玩家的循环次数因此降到 0~1 次。</li>
     *   <li><b>对象分配</b>：原先每次迭代 {@code new BlockPos(...)} 两次（单次判定
     *       ~512 次分配）。改用可复用的 {@link BlockPos.MutableBlockPos}，零分配。</li>
     * </ul>
     */
    private static boolean isInSunlight(ServerPlayer player) {
        var level = (ServerLevel) player.level();
        if (!level.dimensionType().hasSkyLight()) return false;
        if (level.isRaining() || level.isThundering()) return false;
        if (level.isDarkOutside()) return false;

        BlockPos pos = player.blockPosition();
        int x = pos.getX();
        int z = pos.getZ();
        // 高度图返回「最高阻挡方块之上一格」，其上不可能有完整碰撞方块
        int topY = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z),
                level.getHeight());

        BlockPos.MutableBlockPos cursor = SCAN_CURSOR;
        for (int y = pos.getY() + 1; y < topY; y++) {
            cursor.set(x, y, z);
            var state = level.getBlockState(cursor);
            if (!state.isAir() && state.isCollisionShapeFullBlock(level, cursor)) {
                return false;
            }
        }
        return true;
    }
}
