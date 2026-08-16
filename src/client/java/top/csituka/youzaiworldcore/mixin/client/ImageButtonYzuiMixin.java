package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.RecipeBookType;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin 为 YZUI 物品栏屏幕中的 {@link ImageButton} 添加 YZUI 样式。
 * <p>
 * YZUI 物品栏屏幕中的配方书开关按钮（20×18，由 AbstractRecipeBookScreen.init 创建）
 * 使用自定义 20×20 贴图（绿勾/红 X）替换原版纹理。
 * </p>
 * <p>
 * ⚠️ 注意：配方书翻页按钮（{@code RecipeBookPage.forwardButton/backButton}，12×17）
 * 同样继承 {@link ImageButton}，不能走原版贴图渲染，须绘制 YZUI 风格按钮
 * （半透明圆角矩形背景 + 居中 {@code '<'}/{@code '>'} 文本，与创造屏翻页按钮同一视觉语言）。
 * 判定依据为尺寸（12×17 与开关按钮 20×18 互斥），前后翻页方向
 * 通过按钮 message（原版构造时传入的 {@code gui.recipebook.next_page/previous_page}）区分。
 * </p>
 */
@Mixin(ImageButton.class)
public class ImageButtonYzuiMixin {

    @Unique
    private static final @NonNull Identifier YZWC_RECIPE_BOOK_SHOW = Identifier.fromNamespaceAndPath("youzaiworldcore",
            "textures/gui/recipe_book_show.png");
    @Unique
    private static final @NonNull Identifier YZWC_RECIPE_BOOK_HIDE = Identifier.fromNamespaceAndPath("youzaiworldcore",
            "textures/gui/recipe_book_hide.png");
    /** 配方书翻页按钮尺寸（RecipeBookPage.init 中 forwardButton/backButton 为 12×17） */
    @Unique
    private static final int YZWC_PAGE_BTN_W = 12;
    @Unique
    private static final int YZWC_PAGE_BTN_H = 17;
    /** 翻页按钮圆角半径 */
    @Unique
    private static final int YZWC_PAGE_BTN_RADIUS = 4;
    /** 翻页按钮背景色（常态 / 悬浮） */
    @Unique
    private static final int YZWC_PAGE_BTN_BG = 0x60FFFFFF;
    @Unique
    private static final int YZWC_PAGE_BTN_BG_HOVER = 0x80FFFFFF;
    /** 翻页箭头文本色 */
    @Unique
    private static final int YZWC_PAGE_BTN_TEXT = 0xCCFFFFFF;
    /** Debug 模块名 */
    @Unique
    private static final String YZWC_PAGE_BTN_DBG = "ImageButtonYzui";
    /**
     * 「下一页」按钮的 message，用于区分翻页方向。
     * <p>
     * 提为常量：原先写成
     * {@code Component.translatable("gui.recipebook.next_page").equals(msg)}，
     * 每次比较都要新建一个 {@code MutableComponent} + {@code TranslatableContents}，
     * 而这段代码在<b>每个翻页按钮、每帧</b>都会执行。常量与临时对象的
     * {@code equals} 结果完全一致（比较 contents / style / siblings）。
     * </p>
     */
    @Unique
    private static final Component YZWC_NEXT_PAGE_MSG = Component.translatable("gui.recipebook.next_page");

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void yzwc$imageButton(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui())
            return;

        ImageButton self = (ImageButton) (Object) this;
        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();
        boolean hovered = self.isHovered();

        // 配方书翻页按钮：绘制 YZUI 风格 '<' '>' 按钮（圆角矩形 + 居中文本），不走原版贴图
        if (w == YZWC_PAGE_BTN_W && h == YZWC_PAGE_BTN_H) {
            yzwc$renderPageButton(self, g, x, y, w, h, hovered);
            ci.cancel();
            return;
        }

        // 配方书关闭时显示红 X（表示配方书当前是"隐藏"状态），打开时显示绿勾（表示"显示"状态）
        LocalPlayer player = Minecraft.getInstance().player;
        @NonNull
        Identifier tex = (player != null && yzwc$isRecipeBookOpen(player))
                ? YZWC_RECIPE_BOOK_SHOW
                : YZWC_RECIPE_BOOK_HIDE;

        // 悬浮高亮
        if (hovered) {
            yzwc$fillRoundedRect(g, x, y, w, h, 4, 0x60FFFFFF);
        }

        // 居中绘制 20×20 贴图（按钮 20×18，贴图略高 2px 容许）
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0f, 0f, 20, 20, 20, 20);

        ci.cancel();
    }

    /**
     * YZUI 风格配方书翻页按钮：半透明圆角矩形背景 + 居中 '<' / '>' 箭头文本。
     * 前后方向通过按钮 message（原版 NEXT_PAGE_TEXT / PREVIOUS_PAGE_TEXT）区分。
     * <p>
     * 位置与悬浮态由调用方传入，避免重复调用 {@code getX/getY/getWidth/getHeight/isHovered}。
     * </p>
     */
    @Unique
    private static void yzwc$renderPageButton(ImageButton self, GuiGraphicsExtractor g,
            int x, int y, int w, int h, boolean hovered) {
        // 原版构造翻页按钮时第 7 参传入 NEXT_PAGE_TEXT / PREVIOUS_PAGE_TEXT 作为 message
        boolean forward = YZWC_NEXT_PAGE_MSG.equals(self.getMessage());
        String arrow = forward ? ">" : "<";

        // 半透明圆角矩形背景
        yzwc$fillRoundedRect(g, x, y, w, h, YZWC_PAGE_BTN_RADIUS,
                hovered ? YZWC_PAGE_BTN_BG_HOVER : YZWC_PAGE_BTN_BG);

        // 居中绘制箭头文本
        Font font = Minecraft.getInstance().font;
        int tx = x + (w - font.width(arrow)) / 2;
        int ty = y + (h - font.lineHeight) / 2;
        g.text(font, arrow, tx, ty, YZWC_PAGE_BTN_TEXT, true);

        // 先判等级再取参数：本方法每个翻页按钮每帧各跑一次，
        // 4 个 int + 2 个 boolean 装箱与 varargs 数组在日志关闭时是白扔的垃圾。
        // 另降级为 trace：原先用 info（仅需 LEVEL_BASIC），一开日志就会以帧率刷屏。
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(YZWC_PAGE_BTN_DBG,
                    "YZUI page button at (%d,%d) %dx%d forward=%s hovered=%s",
                    x, y, w, h, forward, hovered);
        }
    }

    @Unique
    private static void yzwc$fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (!ClientExternalSettings.isYzuiEnabled())
            return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }

    /**
     * 判定当前配方书开关按钮对应的配方书是否处于"打开"状态。
     * <p>
     * 26.2 中 {@code RecipeBook.isOpen(RecipeBookType)} 按<b>配方书类型</b>区分开合状态，
     * 而每种 RecipeBookMenu 的类型不同：工作台/物品栏 = {@link RecipeBookType#CRAFTING}、
     * 熔炉 = {@link RecipeBookType#FURNACE}、高炉 =
     * {@link RecipeBookType#BLAST_FURNACE}、
     * 烟熏炉 = {@link RecipeBookType#SMOKER}。此前硬编码 CRAFTING 导致熔炉类屏幕打开
     * 配方书后按钮贴图不变（始终红 X），这里改为从当前屏幕菜单动态获取类型。
     * <p>
     * 兜底：当前屏幕不是 {@link AbstractRecipeBookScreen} 时按 CRAFTING 处理
     * （非配方书屏幕不存在该按钮，仅防御未知调用路径）。
     */
    @SuppressWarnings("null")
    @Unique
    private static boolean yzwc$isRecipeBookOpen(LocalPlayer player) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof AbstractRecipeBookScreen<?> arb) {
            return player.getRecipeBook().isOpen(arb.getMenu().getRecipeBookType());
        }
        return player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
    }
}