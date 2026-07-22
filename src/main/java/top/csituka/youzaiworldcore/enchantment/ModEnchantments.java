package top.csituka.youzaiworldcore.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * 附魔 ResourceKey 注册中心（数据驱动，定义在 data 包 JSON 中）。
 */
public class ModEnchantments {

    public static final ResourceKey<Enchantment> SUN_REPAIR_KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "sun_repair")
    );

    public static final ResourceKey<Enchantment> SPIRIT_TURBO_KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "spirit_turbo")
    );
}
