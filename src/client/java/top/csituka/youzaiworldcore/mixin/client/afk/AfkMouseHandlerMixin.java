package top.csituka.youzaiworldcore.mixin.client.afk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.afk.AfkInputTracker;

/**
 * AFK 输入检测：鼠标通道（语义化活动判定）。
 * <p>
 * <ul>
 *   <li>{@link MouseHandler#onButton}（点击）：<b>一律算活动</b>——攻击 / 放置 /
 *       使用 / 拿取物品 / 操作容器 / 合成 / 界面按钮，都是真实操作；</li>
 *   <li>{@link MouseHandler#onScroll}（滚轮）：聊天框滚动记录不算，其余（热栏
 *       切换、容器滚动）算活动；</li>
 *   <li>{@link MouseHandler#onMove}（移动）：<b>不算活动</b>——「打开物品栏但
 *       未操作」时鼠标悬停 / 晃动不应恢复 AFK。</li>
 * </ul>
 * 全部在窗口聚焦时判定。
 * </p>
 */
@Mixin(MouseHandler.class)
public class AfkMouseHandlerMixin {

    @Inject(
            method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onButton(long window, MouseButtonInfo button,
            int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getWindow().isFocused()) {
            return;
        }
        // 游戏外屏幕（暂停菜单 / 设置 / ModMenu 等）点击不算活动；
        // 游戏内点击（攻击/放置/拿取/容器/合成/本项目屏幕按钮）一律算
        if (!AfkInputTracker.isGameActivity(mc.gui.screen())) {
            return;
        }
        AfkInputTracker.markInput();
    }

    @Inject(
            method = "onScroll(JDD)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onScroll(long window, double horizontal,
            double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getWindow().isFocused()) {
            return;
        }
        if (mc.gui.screen() instanceof ChatScreen) {
            // 滚动聊天记录不算活动
            return;
        }
        if (!AfkInputTracker.isGameActivity(mc.gui.screen())) {
            return;
        }
        AfkInputTracker.markInput();
    }
    // 注意：onMove（鼠标移动）不注入——移动鼠标不算操作活动，
    // 「打开物品栏但未操作」时悬停/晃动鼠标不应恢复 AFK。
}
