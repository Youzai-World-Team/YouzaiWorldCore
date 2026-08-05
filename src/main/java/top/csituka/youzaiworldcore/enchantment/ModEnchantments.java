package top.csituka.youzaiworldcore.enchantment;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.List;

/**
 * 附魔 ResourceKey 注册中心（数据驱动，定义在 data 包 JSON 中）。
 * <p>
 * 共 17 个附魔：sun_repair / spirit_turbo（原有）+ 15 个新附魔（源自 Raiyon's More Enchantments 移植）。
 */
public class ModEnchantments {

    // ========== 原有附魔 ==========

    public static final ResourceKey<Enchantment> SUN_REPAIR_KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "sun_repair")
    );

    public static final ResourceKey<Enchantment> SPIRIT_TURBO_KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "spirit_turbo")
    );

    // ========== 新增附魔 (Raiyon's More Enchantments 移植) ==========

    // -- 武器 --
    public static final ResourceKey<Enchantment> LEECHING_KEY = rk("leeching");
    public static final ResourceKey<Enchantment> POISON_PUFF_KEY = rk("poison_puff");

    // -- 弩 --
    public static final ResourceKey<Enchantment> FIRE_CHARGE_KEY = rk("fire_charge");
    public static final ResourceKey<Enchantment> SONIC_CHARGE_KEY = rk("sonic_charge");

    // -- 护甲 --
    public static final ResourceKey<Enchantment> GLOWING_AURA_KEY = rk("glowing_aura");
    public static final ResourceKey<Enchantment> COWARDICE_KEY = rk("cowardice");
    public static final ResourceKey<Enchantment> WIND_CHARGE_KEY = rk("wind_charge");

    // -- 盾牌 --
    public static final ResourceKey<Enchantment> SPIKES_KEY = rk("spikes");
    public static final ResourceKey<Enchantment> BOUNCE_KEY = rk("bounce");

    // -- 工具 --
    public static final ResourceKey<Enchantment> SMELTING_KEY = rk("smelting");

    // -- 重锤 --
    public static final ResourceKey<Enchantment> METEOR_SMASH_KEY = rk("meteor_smash");

    /**
     * 本项目注册的全部附魔键，供创造模式分类等统一遍历使用。
     */
    public static final List<ResourceKey<Enchantment>> ALL = List.of(
            // 原有
            SUN_REPAIR_KEY,
            SPIRIT_TURBO_KEY,
            // 新增
            LEECHING_KEY,
            POISON_PUFF_KEY,
            FIRE_CHARGE_KEY,
            SONIC_CHARGE_KEY,
            GLOWING_AURA_KEY,
            COWARDICE_KEY,
            WIND_CHARGE_KEY,
            SPIKES_KEY,
            BOUNCE_KEY,
            SMELTING_KEY,
            METEOR_SMASH_KEY
    );

    private static ResourceKey<Enchantment> rk(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
    }
}
