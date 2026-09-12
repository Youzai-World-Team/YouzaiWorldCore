package top.csituka.youzaiworldcore.mixin.client.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.screen.InventoryItemGroupsScreen;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

/** 为客户端视觉设置追加分组入口，同时支持完整设置页和嵌入设置页。 */
@SuppressWarnings("null")
@Mixin(value = YouzaiWorldCoreSettingsScreen.class, remap = false)
public abstract class ItemGroupsSettingsEntryMixin extends Screen {

    @Shadow private int contentLeft;
    @Shadow private int contentWidth;
    @Shadow private int maxContentY;

    protected ItemGroupsSettingsEntryMixin(Component title) {
        super(title);
    }

    @Inject(method = "buildVisualSection", at = @At("TAIL"))
    private void youzaiworldcore$addItemGroupsEntry(CallbackInfo ci) {
        addRenderableWidget(new TransparentButton(contentLeft, maxContentY + 4, contentWidth, 22,
                Component.translatable("screen.youzaiworldcore.item_groups.title"), () -> {
                    Minecraft client = Minecraft.getInstance();
                    client.setScreenAndShow(new InventoryItemGroupsScreen(client.gui.screen()));
                }));
        maxContentY += 32;
    }
}
