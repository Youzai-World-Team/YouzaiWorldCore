/*
 * Adapted from Higher Chat (https://github.com/MDLC01/higher-chat-mc)
 * Original author: MDLC01
 * Original license: Unlicense (Public Domain)
 *
 * This file is part of YouzaiWorldCore.
 * Licensed under Apache-2.0.
 */
package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.higherchat.SharedStorage;

/**
 * Intercepts HUD icon rendering to track their vertical positions,
 * enabling the chat to be positioned above the armor bar.
 *
 * <p>Adapted from Higher Chat by MDLC01.</p>
 */
@Mixin(Hud.class)
public abstract class HudMixin {

    /**
     * Resets icon position tracking at the start of each render frame.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        SharedStorage.resetData();
    }

    /**
     * Tracks the position of heart icons rendered by {@code extractHeart}.
     */
    @Redirect(method = "extractHeart",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void onExtractHeart(GuiGraphicsExtractor graphics, RenderPipeline renderPipeline,
                                Identifier icons, int x, int y, int width, int height) {
        SharedStorage.declareIconAt(x, y);
        graphics.blitSprite(renderPipeline, icons, x, y, width, height);
    }

    /**
     * Tracks the position of armor icons rendered by {@code extractArmor}.
     * This method is static because {@code extractArmor} is static in {@code Hud}.
     */
    @Redirect(method = "extractArmor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private static void onExtractArmorPiece(GuiGraphicsExtractor graphics, RenderPipeline renderPipeline,
                                             Identifier icons, int x, int y, int width, int height) {
        SharedStorage.declareIconAt(x, y);
        graphics.blitSprite(renderPipeline, icons, x, y, width, height);
    }

    /**
     * Tracks the position of food icons rendered by {@code extractFood}.
     */
    @Redirect(method = "extractFood",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void onExtractFoodIcon(GuiGraphicsExtractor graphics, RenderPipeline renderPipeline,
                                   Identifier icons, int x, int y, int width, int height) {
        SharedStorage.declareIconAt(x, y);
        graphics.blitSprite(renderPipeline, icons, x, y, width, height);
    }

    /**
     * Tracks the position of vehicle heart icons rendered by {@code extractVehicleHealth}.
     */
    @Redirect(method = "extractVehicleHealth",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void onExtractVehicleHeart(GuiGraphicsExtractor graphics, RenderPipeline renderPipeline,
                                       Identifier icons, int x, int y, int width, int height) {
        SharedStorage.declareIconAt(x, y);
        graphics.blitSprite(renderPipeline, icons, x, y, width, height);
    }
}
