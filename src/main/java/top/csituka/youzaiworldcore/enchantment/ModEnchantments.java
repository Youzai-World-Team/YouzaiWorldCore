package top.csituka.youzaiworldcore.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * 阳光修复附魔（数据驱动，定义在 data 包 JSON 中）。
 * 玩家在阳光下时，打上此附魔的耐久工具会缓慢恢复。
 */
public class ModEnchantments {

    public static final ResourceKey<Enchantment> SUN_REPAIR_KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "sun_repair")
    );
}
