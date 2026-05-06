package top.csituka.youzaiworldcore.screen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import top.csituka.youzaiworldcore.YouzaiworldCore;

public class ModMenuTypes {

    public static final MenuType<DecompositionTableMenu> DECOMPOSITION_TABLE = register(
            "decomposition_table",
            new MenuType<>(DecompositionTableMenu::new, FeatureFlagSet.of())
    );

    public static final MenuType<FlyBeaconMenu> FLY_BEACON = register(
            "fly_beacon",
            new MenuType<>(FlyBeaconMenu::new, FeatureFlagSet.of())
    );

    private static <T extends MenuType<?>> T register(String name, T menuType) {
        return Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name), menuType);
    }

    public static void initialize() {
    }
}
