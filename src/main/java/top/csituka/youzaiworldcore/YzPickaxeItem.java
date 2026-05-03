package top.csituka.youzaiworldcore;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public class YzPickaxeItem extends Item {

    public YzPickaxeItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Properties settings) {
        super(settings.pickaxe(material, attackDamageBaseline, attackSpeedBaseline));
    }
}
