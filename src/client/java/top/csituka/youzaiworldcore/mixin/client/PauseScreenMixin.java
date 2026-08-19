package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

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
 *   <li><b>按钮列整体左移</b>，为右侧玩家模型区让位。</li>
 *   <li><b>右侧渲染玩家模型</b>：复用原版物品栏 {@code InventoryScreen#extractEntityInInventoryFollowsMouse}
 *       的渲染管线（缩放比 30 与原版一致）。</li>
 *   <li><b>模型上方居中绘制玩家名字</b>：白色带阴影；「名字 + 模型」组合整体垂直
 *       居中于按钮列高度内，不超出左侧菜单高度。</li>
 * </ol>
 * <p>
 * 布局（3 行半高 + 1 行全高，整列左移 {@value #MODEL_SHIFT}px）：
 * <pre>
 *   [      回到游戏      ] 204px        ┌ 玩家名字 ┐
 *   [进度 98][ 统计信息 98]             │ 玩家模型 │
 *   [选项 98][对局域网开放98]            └──────────┘
 *   [  保存并退回到标题屏幕  ] 204px
 *   └──── 左移 38px ────┘  └─ 16px ─┘ └── 60px ──┘
 * </pre>
 * 「名字 + 模型」组合高度 = 行高(9) + 间距(2) + 模型区高(58) = 69px，垂直居中于
 * 按钮列高度（92px）内，即组合顶部/底部均不超出 {@code startY .. startY+92}。
 * 玩家名字底部距模型区上缘 2px。
 */
@Mixin(PauseScreen.class)
@SuppressWarnings("null")
public class PauseScreenMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/PauseScreenMixin");

    /** 全宽按钮宽度（与原版保持一致） */
    private static final int FULL_W = 204;

    /** 半宽按钮宽度 */
    private static final int HALF_W = 98;

    /** 按钮高度 */
    private static final int BTN_H = 20;

    /** 按钮间距 */
    private static final int GAP = 4;

    // ========== 玩家模型常量 ==========

    /** 模型缩放比（与原版物品栏玩家模型一致：30 px/块，模型约 54px 高） */
    private static final int MODEL_SCALE = 30;

    /** 模型离地偏移（原版物品栏 {@code extractEntityInInventoryFollowsMouse} 数值） */
    private static final float MODEL_LIFT = 0.0625F;

    /** 模型区宽度 */
    private static final int MODEL_RECT_W = 60;

    /** 模型区高度（容纳模型本体 54px + 上下余量；组合布局时与按钮列高度无关） */
    private static final int MODEL_RECT_H = 58;

    /** 按钮列与模型区之间的水平间距 */
    private static final int MODEL_GAP = 16;

    /** 按钮列整体左移量 = (模型区宽 + 间距) / 2，使「按钮列 + 间距 + 模型区」整体居中 */
    private static final int MODEL_SHIFT = (MODEL_RECT_W + MODEL_GAP) / 2;

    /** 屏幕逻辑宽度低于该值时禁用玩家模型（避免挤压按钮） */
    private static final int MIN_SCREEN_W = 360;

    /** 玩家名字颜色（白色，带阴影） */
    private static final int NAME_COLOR = 0xFFFFFFFF;

    /** 玩家名字底部与模型区上缘的间距 */
    private static final int NAME_GAP_ABOVE = 2;

    // ========== 模型渲染状态（每次 init 重新计算） ==========

    /** 模型是否可用（暂停菜单按钮存在且屏幕足够宽） */
    @Unique
    private boolean youzaiworldcore$modelReady = false;

    /** 模型区左上角 x（绝对坐标） */
    @Unique
    private int youzaiworldcore$modelX0;

    /** 模型区左上角 y（绝对坐标） */
    @Unique
    private int youzaiworldcore$modelY0;

    /** 模型区右下角 x（绝对坐标） */
    @Unique
    private int youzaiworldcore$modelX1;

    /** 模型区右下角 y（绝对坐标） */
    @Unique
    private int youzaiworldcore$modelY1;

    /** 玩家名字（init 时缓存，渲染时直接使用） */
    @Unique
    private String youzaiworldcore$playerName;

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

        boolean hasMenuButtons = returnBtn != null || advBtn != null || statsBtn != null
                || optionsBtn != null || mpOptionsBtn != null || disconnectBtn != null;
        if (!hasMenuButtons) {
            // 暂停菜单无按钮（showPauseMenu=false 场景，如「已暂停」纯标题屏）→ 不调整布局、不渲染模型
            this.youzaiworldcore$modelReady = false;
            return;
        }

        // ============ 2. 移除不需要的按钮 ============
        if (!toRemove.isEmpty()) {
            List<Renderable> renderables = accessor.youzaiworldcore$getRenderables();
            List<NarratableEntry> narratables = accessor.youzaiworldcore$getNarratables();
            List<GuiEventListener> childrenList = accessor.youzaiworldcore$getChildren();

            for (GuiEventListener child : toRemove) {
                childrenList.remove(child);
                renderables.remove((Object) child);
                narratables.remove((Object) child);
            }
        }

        // ============ 3. 紧凑居中排列保留的按钮（整列左移，为右侧模型区让位） ============
        // 屏幕过窄时不左移（模型区禁用），按钮保持居中
        boolean modelAllowed = screen.width >= MIN_SCREEN_W;
        int centerX = screen.width / 2 - (modelAllowed ? MODEL_SHIFT : 0);

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

        // ============ 4. 计算右侧玩家模型区 ============
        if (modelAllowed) {
            // 「名字 + 模型」组合垂直居中于按钮列高度内（startY .. startY+totalHeight），
            // 确保组合（文字+模型）整体不超出左侧菜单高度
            int nameAreaH = accessor.youzaiworldcore$getFont().lineHeight + NAME_GAP_ABOVE;
            int combH = nameAreaH + MODEL_RECT_H;
            int combTop = startY + (totalHeight - combH) / 2;

            // 模型区紧贴按钮列右缘之后；模型区上缘 = 名字底部 + 间距
            this.youzaiworldcore$modelX0 = centerX + FULL_W / 2 + MODEL_GAP;
            this.youzaiworldcore$modelY0 = combTop + nameAreaH;
            this.youzaiworldcore$modelX1 = this.youzaiworldcore$modelX0 + MODEL_RECT_W;
            this.youzaiworldcore$modelY1 = this.youzaiworldcore$modelY0 + MODEL_RECT_H;
            this.youzaiworldcore$modelReady = true;

            // 缓存玩家名字（模型上方显示；若玩家缺失则名字为 null，仅渲染模型）
            // 注意：26.2 中 authlib 的 GameProfile 无 getName()，改用 Entity.getName()（Component）
            Minecraft mc = Minecraft.getInstance();
            this.youzaiworldcore$playerName =
                    mc.player != null ? mc.player.getName().getString() : null;

            DebugLogger.info("PauseScreen",
                    "暂停菜单布局: 按钮左移 %dpx, 模型区=(%d,%d)-(%d,%d) 尺寸=%dx%d 缩放比=%d, 名字+模型组合=%dpx(名字行高%d+间距%d+模型区%d) 居中于按钮列高%dpx, 玩家名=%s",
                    MODEL_SHIFT, this.youzaiworldcore$modelX0, this.youzaiworldcore$modelY0,
                    this.youzaiworldcore$modelX1, this.youzaiworldcore$modelY1,
                    MODEL_RECT_W, MODEL_RECT_H, MODEL_SCALE,
                    combH, accessor.youzaiworldcore$getFont().lineHeight, NAME_GAP_ABOVE,
                    MODEL_RECT_H, totalHeight, this.youzaiworldcore$playerName);
        } else {
            this.youzaiworldcore$modelReady = false;
            this.youzaiworldcore$playerName = null;
            DebugLogger.warn("PauseScreen",
                    "屏幕过窄 (%dpx < %dpx)，禁用玩家模型，按钮列不左移", screen.width, MIN_SCREEN_W);
        }

        if (ClientExternalSettings.getLogLevel() > 0) {
            LOGGER.debug("Re-paused pause menu: removed {} unwanted buttons, kept 6, shift={}px, model={}",
                    toRemove.size(), MODEL_SHIFT, this.youzaiworldcore$modelReady);
        }
    }

    /**
     * 在暂停菜单右上侧渲染玩家模型（跟随鼠标旋转，行为与原版物品栏一致），
     * 并在模型上方居中绘制玩家名字。
     * <p>
     * 复用 {@link InventoryScreen#extractEntityInInventoryFollowsMouse}：内部通过
     * {@code EntityRenderer#createRenderState} 提取玩家渲染状态（皮肤/装备/姿态），
     * 再由 picture-in-picture 渲染器离屏渲染后贴回 GUI，缩放比与原版物品栏完全一致。
     * <p>
     * 注入 {@code extractRenderState} TAIL：模型绘制在按钮等组件之上（右侧无重叠，
     * 不遮挡任何交互元素）。
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void youzaiworldcore$renderPlayerModel(GuiGraphicsExtractor g, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!this.youzaiworldcore$modelReady) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                this.youzaiworldcore$modelX0, this.youzaiworldcore$modelY0,
                this.youzaiworldcore$modelX1, this.youzaiworldcore$modelY1,
                MODEL_SCALE, MODEL_LIFT, mouseX, mouseY, mc.player);

        // 玩家名字：模型区上方居中，底部与模型区上缘留 2px 间距（白字带阴影，可读性优先）
        if (this.youzaiworldcore$playerName != null && !this.youzaiworldcore$playerName.isEmpty()) {
            Font font = ((ScreenAccessor) this).youzaiworldcore$getFont();
            int nameW = font.width(this.youzaiworldcore$playerName);
            int nx = this.youzaiworldcore$modelX0 + (MODEL_RECT_W - nameW) / 2;
            int ny = this.youzaiworldcore$modelY0 - NAME_GAP_ABOVE - font.lineHeight;
            g.text(font, this.youzaiworldcore$playerName, nx, ny, NAME_COLOR, true);
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
