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
import top.csituka.youzaiworldcore.client.hud.ManaHudRenderer;
import top.csituka.youzaiworldcore.client.hud.AdventureLevelHudRenderer;
import top.csituka.youzaiworldcore.client.pickup.DrawEntriesHandler;

/**
 * Intercepts HUD icon rendering to track their vertical positions,
 * enabling the chat to be positioned above the armor bar.
 *
 * <p>Adapted from Higher Chat by MDLC01.</p>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class HudMixin {

    /**
     * 在每帧 HUD 提取开始时重置图标位置，并优先提交拾取信息与声音字幕。
     *
     * <p>拾取提示在原版及 YZUI HUD 之前进入渲染队列，因此始终位于所有 HUD
     * 组件下方，不会遮挡热键栏、状态栏、记分板或其他叠加信息。</p>
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        SharedStorage.resetData();
        DrawEntriesHandler.INSTANCE.render(graphics);
    }

    /**
     * 在 HUD 渲染结束时绘制魔力条。
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void onRenderManaBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ManaHudRenderer.render(graphics);
        AdventureLevelHudRenderer.render(graphics);
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
