package top.csituka.youzaiworldcore.mixin.client.itemborder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.itemborder.ItemBorderConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 修改 {@link ItemStack#getRarity()}，对 {@link ItemBorderConfig#MANUAL_BORDERS}
 * 中的物品返回对应的稀有度等级，使其名称文字显示为相应颜色。
 * <p>
 * 不影响边框渲染的拾取判定——边框渲染走 {@code matchManualBorder()} 优先于
 * {@code getRarity()}，不存在递归冲突。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
@Mixin(ItemStack.class)
public abstract class ItemStackRarityMixin {

    @Unique
    private static final String MODULE = "ItemStackRarityMixin";

    /**
     * 物品 ID → Rarity 的 O(1) 查询映射（从 MANUAL_BORDERS 构建）。
     * <p>
     * 用 {@link Identifier} 而非 {@code String} 作键：{@code getRarity()} 是全局热路径
     * （物品名、tooltip、边框渲染都会走），原先每次调用都要
     * {@code getKey().toString()} 拼一个新字符串。Identifier 自身可直接比较，
     * hashCode 走 String 内部缓存，零分配。
     */
    @Unique
    private static final Map<Identifier, Rarity> ITEM_RARITY_OVERRIDES;

    static {
        DebugLogger.info(MODULE, "构建物品稀有度覆写映射表...");
        Map<Identifier, Rarity> map = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : ItemBorderConfig.MANUAL_BORDERS.entrySet()) {
            Rarity rarity = colorNameToRarity(entry.getKey());
            if (rarity == null) {
                DebugLogger.warn(MODULE, "无法将颜色键 '%s' 映射为 Rarity", entry.getKey());
                continue;
            }
            for (String itemId : entry.getValue()) {
                Identifier parsed = Identifier.tryParse(itemId);
                if (parsed == null) {
                    DebugLogger.warn(MODULE, "无法解析物品 ID '%s'", itemId);
                    continue;
                }
                map.put(parsed, rarity);
            }
        }
        ITEM_RARITY_OVERRIDES = Collections.unmodifiableMap(map);
        DebugLogger.info(MODULE, "已加载 %d 条稀有度覆写", ITEM_RARITY_OVERRIDES.size());
    }

    /**
     * 将 MANUAL_BORDERS 的颜色键名转换为对应的 {@link Rarity} 枚举值。
     */
    @Unique
    private static Rarity colorNameToRarity(String color) {
        return switch (color.toLowerCase()) {
            case "yellow"       -> Rarity.UNCOMMON;   // name: §e (yellow)
            case "aqua"         -> Rarity.RARE;        // name: §b (aqua)
            case "light_purple" -> Rarity.EPIC;        // name: §d (light purple)
            default -> null;
        };
    }

    @Inject(method = "getRarity", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$modifyRarity(CallbackInfoReturnable<Rarity> cir) {
        ItemStack self = (ItemStack) (Object) this;

        // 用注册表 ID 查映射（getKey 返回缓存的 Identifier 实例，不产生分配）
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem());
        if (itemId == null) return;
        Rarity override = ITEM_RARITY_OVERRIDES.get(itemId);
        if (override != null) {
            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
                DebugLogger.trace(MODULE, "覆写稀有度: %s → %s", itemId, override);
            }
            cir.setReturnValue(override);
        }
    }
}
