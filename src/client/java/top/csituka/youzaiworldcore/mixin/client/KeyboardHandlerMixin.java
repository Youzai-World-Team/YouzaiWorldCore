package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long windowPointer, int keyOrAction, KeyEvent event, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }

        int key = event.key();
        int action = keyOrAction;

        if (key == InputConstants.KEY_F && action == InputConstants.PRESS) {
            boolean shiftPressed = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);

            if (shiftPressed) {
                ci.cancel();
            }
        }
    }
}
