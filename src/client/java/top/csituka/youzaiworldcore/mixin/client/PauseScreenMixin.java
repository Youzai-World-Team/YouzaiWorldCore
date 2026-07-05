package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 修改 ESC 暂停菜单（{@link PauseScreen}）：
 * <ol>
 *   <li>保留以下按钮，其余全部移除（含模组注册的）：
 *     <ul>
 *       <li>{@code menu.returnToGame} — 回到游戏</li>
 *       <li>{@code gui.advancements} — 进度</li>
 *       <li>{@code gui.stats} — 统计信息</li>
 *       <li>{@code menu.options} — 选项</li>
 *       <li>{@code menu.multiplayerOptions.button} — 对局域网开放</li>
 *       <li>{@code menu.returnToMenu} / {@code menu.disconnect} — 保存并退回到标题屏幕</li>
 *     </ul>
 *   </li>
 *   <li>将剩余按钮紧凑居中排列，消除移除按钮后产生的空隙。</li>
 * </ol>
 * <p>
 * 布局（3 行半高 + 1 行全高）：
 * <pre>
 *   [      回到游戏      ]  204px
 *   [进度 98][ 统计信息 98]
 *   [选项 98][对局域网开放98]
 *   [  保存并退回到标题屏幕  ]  204px
 * </pre>
 */
@Mixin(PauseScreen.class)
public class PauseScreenMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/PauseScreenMixin");

    /** 需要在暂停菜单中保留的按钮翻译键 */
    private static final Set<String> KEEP_KEYS = new HashSet<>(Set.of(
            "menu.returnToGame",
            "gui.advancements",
            "gui.stats",
            "menu.options",
            "menu.multiplayerOptions.button",
            "menu.returnToMenu",
            "menu.disconnect"
    ));

    /** 全宽按钮宽度（与原版保持一致） */
    private static final int FULL_W = 204;

    /** 半宽按钮宽度 */
    private static final int HALF_W = 98;

    /** 按钮高度 */
    private static final int BTN_H = 20;

    /** 按钮间距 */
    private static final int GAP = 4;

    @Inject(method = "init", at = @At("TAIL"))
    private void youzaiworldcore$restructurePauseButtons(CallbackInfo ci) {
        PauseScreen screen = (PauseScreen) (Object) this;
        ScreenAccessor accessor = (ScreenAccessor) screen;

        // ============ 1. 收集保留/移除的按钮 ============
        AbstractWidget returnBtn = null;
        AbstractWidget advBtn = null;
        AbstractWidget statsBtn = null;
        AbstractWidget optionsBtn = null;
        AbstractWidget mpOptionsBtn = null;
        AbstractWidget disconnectBtn = null;
        List<GuiEventListener> toRemove = new ArrayList<>();

        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button widget) {
                String key = extractTranslationKey(widget.getMessage());
                if (key == null) {
                    toRemove.add(child);
                } else {
                    switch (key) {
                        case "menu.returnToGame"          -> returnBtn = widget;
                        case "gui.advancements"           -> advBtn = widget;
                        case "gui.stats"                  -> statsBtn = widget;
                        case "menu.options"               -> optionsBtn = widget;
                        case "menu.multiplayerOptions.button" -> mpOptionsBtn = widget;
                        case "menu.returnToMenu", "menu.disconnect" -> disconnectBtn = widget;
                        default -> toRemove.add(child);
                    }
                }
            }
        }

        if (toRemove.isEmpty()) return;

        // ============ 2. 移除不需要的按钮 ============
        List<Renderable> renderables = accessor.youzaiworldcore$getRenderables();
        List<NarratableEntry> narratables = accessor.youzaiworldcore$getNarratables();
        List<GuiEventListener> childrenList = accessor.youzaiworldcore$getChildren();

        for (GuiEventListener child : toRemove) {
            childrenList.remove(child);
            renderables.remove((Object) child);
            narratables.remove((Object) child);
        }

        // ============ 3. 紧凑居中排列保留的按钮 ============
        int centerX = screen.width / 2;

        // 垂直起始位置：约 25% 高度（与原版一致）
        int totalHeight = 4 * BTN_H + 3 * GAP;
        int startY = Math.min(
                screen.height / 4,
                Math.max(60, (screen.height - totalHeight) / 2)
        );

        // 行 0：回到游戏（全宽）
        if (returnBtn != null) {
            returnBtn.setX(centerX - FULL_W / 2);
            returnBtn.setY(startY);
            returnBtn.setWidth(FULL_W);
            returnBtn.setHeight(BTN_H);
        }

        int row1Y = startY + BTN_H + GAP;

        // 行 1：进度 | 统计信息
        if (advBtn != null) {
            advBtn.setX(centerX - FULL_W / 2);
            advBtn.setY(row1Y);
            advBtn.setWidth(HALF_W);
            advBtn.setHeight(BTN_H);
        }
        if (statsBtn != null) {
            statsBtn.setX(centerX + FULL_W / 2 - HALF_W);
            statsBtn.setY(row1Y);
            statsBtn.setWidth(HALF_W);
            statsBtn.setHeight(BTN_H);
        }

        int row2Y = row1Y + BTN_H + GAP;

        // 行 2：选项 | 对局域网开放
        if (optionsBtn != null) {
            optionsBtn.setX(centerX - FULL_W / 2);
            optionsBtn.setY(row2Y);
            optionsBtn.setWidth(HALF_W);
            optionsBtn.setHeight(BTN_H);
        }
        if (mpOptionsBtn != null) {
            mpOptionsBtn.setX(centerX + FULL_W / 2 - HALF_W);
            mpOptionsBtn.setY(row2Y);
            mpOptionsBtn.setWidth(HALF_W);
            mpOptionsBtn.setHeight(BTN_H);
        }

        int row3Y = row2Y + BTN_H + GAP;

        // 行 3：保存并退回到标题屏幕（全宽）
        if (disconnectBtn != null) {
            disconnectBtn.setX(centerX - FULL_W / 2);
            disconnectBtn.setY(row3Y);
            disconnectBtn.setWidth(FULL_W);
            disconnectBtn.setHeight(BTN_H);
        }

        if (ClientExternalSettings.isLogToFile()) {
            LOGGER.debug("Re-paused pause menu: removed {} unwanted buttons, kept 6", toRemove.size());
        }
    }

    private static String extractTranslationKey(Component component) {
        if (component == null) return null;
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        for (Component sibling : component.getSiblings()) {
            String key = extractTranslationKey(sibling);
            if (key != null) return key;
        }
        return null;
    }
}
