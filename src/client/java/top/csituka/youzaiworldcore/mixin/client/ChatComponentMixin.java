/*
 * Adapted from Higher Chat (https://github.com/MDLC01/higher-chat-mc)
 * Original author: MDLC01
 * Original license: Unlicense (Public Domain)
 *
 * This file is part of YouzaiWorldCore.
 * Licensed under Apache-2.0.
 */
package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import top.csituka.youzaiworldcore.client.higherchat.SharedStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Adjusts the chat bottom margin so the chat sits above the armor bar.
 *
 * <p>The default bottom margin of 40 pixels is replaced with a dynamic value
 * that accounts for the current HUD icon positions.</p>
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @ModifyConstant(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            constant = @Constant(intValue = 40)
    )
    private int adjustBottomMarginInRender(int bottomMargin) {
        return Math.max(bottomMargin, SharedStorage.getOptimalChatMargin());
    }
}
