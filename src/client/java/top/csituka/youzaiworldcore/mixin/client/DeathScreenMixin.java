package top.csituka.youzaiworldcore.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.InPlaceRespawnClientState;
import top.csituka.youzaiworldcore.network.InPlaceRespawnRequestPayload;

import java.util.List;

/** 在原版死亡界面的重生按钮右半侧加入“原地重生”按钮。 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {

    private static final int BUTTON_GAP = 4;
    private static final int HALF_BUTTON_WIDTH = 98;

    @Shadow
    @Final
    private List<Button> exitButtons;

    @Shadow
    private int delayTicker;

    @Unique
    private Button youzaiworldcore$inPlaceRespawnButton;

    protected DeathScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void youzaiworldcore$addInPlaceRespawnButton(CallbackInfo ci) {
        if (!InPlaceRespawnClientState.isEnabled() || exitButtons.isEmpty()) {
            return;
        }

        Button vanillaRespawnButton = exitButtons.getFirst();
        vanillaRespawnButton.setWidth(HALF_BUTTON_WIDTH);
        youzaiworldcore$inPlaceRespawnButton = Button.builder(
                        Component.translatable("youzaiworldcore.respawn.in_place.button"),
                        button -> youzaiworldcore$requestInPlaceRespawn())
                .bounds(vanillaRespawnButton.getX() + HALF_BUTTON_WIDTH + BUTTON_GAP,
                        vanillaRespawnButton.getY(), HALF_BUTTON_WIDTH, 20)
                .build();
        youzaiworldcore$inPlaceRespawnButton.active = false;
        addRenderableWidget(youzaiworldcore$inPlaceRespawnButton);
        exitButtons.add(youzaiworldcore$inPlaceRespawnButton);
    }

    @Unique
    private void youzaiworldcore$requestInPlaceRespawn() {
        if (!InPlaceRespawnClientState.beginRequest()) {
            return;
        }
        exitButtons.forEach(button -> button.active = false);
        ClientPlayNetworking.send(new InPlaceRespawnRequestPayload());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void youzaiworldcore$updatePendingButtons(CallbackInfo ci) {
        if (youzaiworldcore$inPlaceRespawnButton == null || delayTicker < 20) {
            return;
        }
        if (InPlaceRespawnClientState.isPending()) {
            exitButtons.forEach(button -> button.active = false);
        } else if (InPlaceRespawnClientState.consumeButtonReactivation()) {
            exitButtons.forEach(button -> button.active = true);
        }
    }

    @Inject(method = "visitText", at = @At("TAIL"))
    private void youzaiworldcore$renderRequiredLevel(ActiveTextCollector collector, CallbackInfo ci) {
        if (youzaiworldcore$inPlaceRespawnButton == null) {
            return;
        }
        collector.accept(TextAlignment.CENTER,
                youzaiworldcore$inPlaceRespawnButton.getX()
                        + youzaiworldcore$inPlaceRespawnButton.getWidth() / 2,
                youzaiworldcore$inPlaceRespawnButton.getY() - 12,
                InPlaceRespawnClientState.getStatusText());
    }
}
