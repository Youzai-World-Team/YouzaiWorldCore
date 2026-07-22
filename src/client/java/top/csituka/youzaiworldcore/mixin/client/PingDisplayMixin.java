package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.render.PingDisplayRender;

/**
 * 替换 Tab 列表中原版 ping 信号格图标为彩色文字显示。
 * <p>
 * 两个注入点：
 * <ul>
 *   <li>{@code @ModifyConstant} — 扩大原版预留的 ping 图标宽度 (13 → 36) 以便容纳文字。</li>
 *   <li>{@code @Inject(HEAD, cancellable)} — 取消原版画信号格的逻辑，改画彩色 ping 文字。</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("null")
@Mixin(PlayerTabOverlay.class)
public abstract class PingDisplayMixin {

    /**
     * 扩大 Tab 列表右侧预留的 ping 宽度（原版 13px 信号格 → 36px 文字区域）。
     * 对应的原版常量是 {@code extractRenderState} 中用来计算 slot 宽度的字面量 13。
     */
    @ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = 13))
    private int yzwc$modifyPingAreaWidth(int original) {
        return original + 23;
    }

    /**
     * 在 {@code extractPingIcon} 开头拦截，取消原版画信号格，代之以彩色 ping 文字。
     *
     * <p>26.2 方法签名已在真实 jar 中验证：</p>
     * <pre>
     *     extractPingIcon(GuiGraphicsExtractor, int slotWidth, int xOffset, int yOffset, PlayerInfo)
     * </pre>
     */
    @Inject(method = "extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void yzwc$replacePingIconWithText(GuiGraphicsExtractor context,
                                               int slotWidth,
                                               int xOffset,
                                               int yOffset,
                                               PlayerInfo info,
                                               CallbackInfo ci) {
        PingDisplayRender.renderPingText(Minecraft.getInstance(), context,
                slotWidth, xOffset, yOffset, info);
        ci.cancel();
    }
}
