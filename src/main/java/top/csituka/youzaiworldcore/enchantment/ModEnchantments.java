package top.csituka.youzaiworldcore.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.List;

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

    /**
     * 本项目注册的全部附魔键，供创造模式分类等统一遍历使用。
     */
    public static final List<ResourceKey<Enchantment>> ALL = List.of(
            SUN_REPAIR_KEY,
            SPIRIT_TURBO_KEY
    );
}
