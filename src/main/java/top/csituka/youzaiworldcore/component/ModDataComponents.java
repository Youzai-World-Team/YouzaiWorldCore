package top.csituka.youzaiworldcore.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponentType;
import top.csituka.youzaiworldcore.YouzaiworldCore;

public class ModDataComponents {

    public static final DataComponentType<Boolean> VOID_STAFF_ACTIVE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "void_staff_active"),
            DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build()
    );

    public static void initialize() {
    }
}
