package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
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
        // 盔甲栏
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                checkStack(player.getItemBySlot(slot));
            }
        }
        // 物品栏
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
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

    /** 玩家是否暴露在阳光下 */
    private static boolean isInSunlight(ServerPlayer player) {
        var level = (ServerLevel) player.level();
        if (!level.dimensionType().hasSkyLight()) return false;
        if (level.isRaining() || level.isThundering()) return false;
        if (level.isDarkOutside()) return false;
        BlockPos pos = player.blockPosition();
        int maxY = level.getHeight();
        for (int y = pos.getY() + 1; y < maxY; y++) {
            var state = level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
            if (!state.isAir() && state.isCollisionShapeFullBlock(level, new BlockPos(pos.getX(), y, pos.getZ()))) {
                return false;
            }
        }
        return true;
    }
}
