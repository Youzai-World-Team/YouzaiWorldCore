package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 潜影盒屏幕（16 色染色 + 未染色）。
 * <p>
 * 26.2 潜影盒使用独立的 {@link ShulkerBoxMenu} 与 {@code ShulkerBoxScreen}
 * （extends {@link AbstractContainerScreen}），布局与箱子一致：容器格 3×9
 * 起点 {@code (8,18)}，玩家背包起点 {@code (8,84)}，面板 {@code 176×167}。
 * 本类以"全量替换屏幕"的方式提供 YZUI 风格渲染，交互逻辑（点击/Shift 快捷移动/
 * 双击收集/拖拽分发/Esc 关闭/悬停提示）全部继承原版实现。
 * <p>
 * <h3>颜色跟随</h3>
 * 服务端 {@code ShulkerBoxBlockEntityNameMixin} 将潜影盒标题修正为方块名称
 * （{@code block.minecraft.<color>_shulker_box}），客户端据此解析 {@link DyeColor}，
 * 使<b>标题图标与主题色跟随潜影盒实际颜色</b>：
 * <ul>
 * <li>图标：{@code Items.DYED_SHULKER_BOX.asList().get(dyeColor.ordinal())}
 *     （16 色物品，ColorCollection 顺序与 DyeColor.values() 一致）；未染色/自定义
 *     命名无法解析 → {@link Items#SHULKER_BOX}（默认紫色）；</li>
 * <li>主题色：由 {@code DyeColor.getTextColor()}（0xRRGGBB）派生——
 *     标题 100% 不透明、强调条 ~69% alpha、槽位底 ~31%/悬停 ~44% alpha；</li>
 * <li>布局：顶部留白 5px、文字与图标顶部对齐、强调条 15..17、关闭按钮 14×14
 *     距格子 2px（与 YzuContainerScreen 同一套设计语言）。</li>
 * </ul>
 * 开关机制：{@code YzuContainerScreenSwitchMixin} 在 {@code Gui.setScreen}
 * 拦截原版 {@code ShulkerBoxScreen}，仅当 {@code yzuiEnabled} 时替换为本类。
 * 零新增纹理（纯色矩形 + 原版物品图标），无资源加载与缓存开销。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuShulkerBoxScreen extends AbstractContainerScreen<ShulkerBoxMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuShulkerBoxScreen");

    // ========== YZUI 统一设计常量（与 YzuContainerScreen 一致） ==========

    /** 面板背景：半透明白 */
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3; // r≤3 走矩形快速路径，等价实心矩形

    /** 玩家背包标题（深灰，白底可读，无阴影） */
    private static final int LABEL_COLOR = 0xCC404040;

    // ========== 关闭按钮 ==========

    private static final int CLOSE_SIZE = 14;
    private static final int CLOSE_RADIUS = 4;
    /** 距面板右缘的间距 */
    private static final int CLOSE_MARGIN = 6;
    /** 距面板上缘的间距（底部 ≤16，与容器格 y=18 保持 2px 间隙） */
    private static final int CLOSE_TOP = 2;
    private static final int CLOSE_BG = 0x40FFFFFF;
    private static final int CLOSE_BG_HOVER = 0x80FFFFFF;
    private static final int CLOSE_ICON = 0xCC404040;
    private static final int CLOSE_ICON_HOVER = 0xFF000000;
    private static final String CLOSE_GLYPH = "\u00d7"; // ×

    // ========== 标题区 ==========

    /** 标题图标与文字间距 */
    private static final int TITLE_ICON_GAP = 4;
    /** 标题区图标缩放（16px 物品模型 → 12px 显示，为顶部留白腾空间） */
    private static final float ICON_SCALE = 0.75f;
    private static final int ICON_SIZE = 12;

    /** 未染色/无法解析颜色时的默认染料（紫色潜影盒） */
    private static final DyeColor DEFAULT_DYE = DyeColor.PURPLE;

    // ========== 实例主题（按潜影盒颜色派生） ==========

    /** 标题文字色（100% 不透明） */
    private final int titleColor;
    /** 槽位常态底色（~31% alpha） */
    private final int slotColor;
    /** 槽位悬停底色（~44% alpha） */
    private final int slotHoverColor;
    /** 标题下方强调条色（~69% alpha） */
    private final int accentBarColor;
    /** 标题区图标（跟随潜影盒颜色） */
    private final ItemStack icon;

    // ========== 构造 ==========

    public YzuShulkerBoxScreen(ShulkerBoxMenu menu, Inventory playerInventory, Component title) {
        // 沿用原版 ShulkerBoxScreen 的面板尺寸：176 × 167
        super(menu, playerInventory, title, 176, 167);

        DyeColor dye = resolveDyeColor(title);
        int base = (dye != null ? dye : DEFAULT_DYE).getTextColor() & 0xFFFFFF;
        this.titleColor = 0xFF000000 | base;
        this.accentBarColor = 0xB0000000 | base;
        this.slotColor = 0x50000000 | base;
        this.slotHoverColor = 0x70000000 | base;
        this.icon = dye != null
                ? new ItemStack(Items.DYED_SHULKER_BOX.asList().get(dye.ordinal()))
                : new ItemStack(Items.SHULKER_BOX);

        DebugLogger.info("YzuShulkerBoxScreen", "创建 YZUI 潜影盒屏幕: title=%s dye=%s baseColor=#%06X",
                title.getString(), dye != null ? dye.getName() : "undyed", base);
    }

    /**
     * 从服务端下发的标题翻译键解析潜影盒颜色：
     * {@code block.minecraft.<color>_shulker_box} → 对应 {@link DyeColor}；
     * 未染色（{@code block.minecraft.shulker_box}）、自定义命名（非翻译键）
     * 或其他异常 → {@code null}（使用默认紫色主题）。
     */
    private static DyeColor resolveDyeColor(Component title) {
        if (title.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            String prefix = "block.minecraft.";
            String suffix = "_shulker_box";
            if (key.startsWith(prefix) && key.endsWith(suffix)) {
                String color = key.substring(prefix.length(), key.length() - suffix.length());
                if (!color.isEmpty()) {
                    return DyeColor.byName(color, null);
                }
            }
        }
        return null;
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuShulkerBoxScreen.init() — leftPos={} topPos={} image={}x{} title={}",
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.title.getString());
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
        // no-op — YZUI 面板在 extractRenderState 中绘制（与 YzuContainerScreen 一致）
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawSlotBackgrounds(guiGraphics, mouseX, mouseY);
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op — 标题由 drawTitle 绘制
    }

    // ========== 鼠标事件（仅关闭按钮，其余交互全部委托原版） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuShulkerBoxScreen", "点击关闭按钮，关闭潜影盒: %s", this.title.getString());
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== YZUI 面板绘制 ==========

    /** 面板背景：半透明白圆角矩形。 */
    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillR(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, PANEL_RADIUS, PANEL_BG);
    }

    /**
     * 标题区：潜影盒图标（12×12 底衬 + 缩放物品，颜色跟随潜影盒）+ 标题文字 + 强调条。
     * <p>
     * ⚠️ 26.2 {@code GuiGraphicsExtractor.text()} 的 y 参数是<b>文字顶部</b>
     * （非基线），字形占 y..y+9。头部可用高度 18px（容器格起点 y=18），
     * 顶部留白 5px：图标/文字顶部 y=5，强调条 15..17。
     */
    private void drawTitle(GuiGraphicsExtractor g) {
        int ix = this.leftPos + 8;
        int iy = this.topPos + 5;

        // 图标底衬 12×12（物品模型缩放 0.75 → 12×12 居中覆盖）
        fillR(g, ix, iy, ICON_SIZE, ICON_SIZE, 3, slotColor);
        if (!this.icon.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(ix, iy);
            g.pose().scale(ICON_SCALE, ICON_SCALE);
            g.item(this.icon, 0, 0, 0);
            g.pose().popMatrix();
        }

        int tx = ix + ICON_SIZE + TITLE_ICON_GAP;
        int ty = this.topPos + 5; // 文字顶部（字形 5..14），与图标行顶部对齐
        g.text(this.font, this.title, tx, ty, titleColor, false);

        // 强调条：位于标题下方（字形底 14 + 1 → 15..17），不压容器格
        int titleWidth = Math.min(this.font.width(this.title), this.imageWidth - 8 - ICON_SIZE - TITLE_ICON_GAP - 8);
        fillR(g, tx, ty + 10, titleWidth, 2, 1, accentBarColor);
    }

    /** 玩家背包区域标签（沿用原版标签坐标 imageHeight-94，无阴影）。 */
    private void drawInventoryLabel(GuiGraphicsExtractor g) {
        g.text(this.font, this.playerInventoryTitle,
                this.leftPos + 8, this.topPos + this.imageHeight - 94, LABEL_COLOR, false);
    }

    /** 关闭按钮：圆角矩形 + × 图标，悬停提亮。 */
    private void drawCloseButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cx = this.leftPos + this.imageWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int cy = this.topPos + CLOSE_TOP;
        boolean hovered = isOverCloseButton(mouseX, mouseY);
        fillR(g, cx, cy, CLOSE_SIZE, CLOSE_SIZE, CLOSE_RADIUS, hovered ? CLOSE_BG_HOVER : CLOSE_BG);
        int tx = cx + (CLOSE_SIZE - this.font.width(CLOSE_GLYPH)) / 2;
        int ty = cy + (CLOSE_SIZE - this.font.lineHeight) / 2;
        g.text(this.font, CLOSE_GLYPH, tx, ty, hovered ? CLOSE_ICON_HOVER : CLOSE_ICON, false);
    }

    /** 槽位背景：每个活动槽绘制主题色圆角矩形，悬浮提亮。 */
    private void drawSlotBackgrounds(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive())
                continue;
            boolean hovered = mouseX >= this.leftPos + slot.x && mouseX < this.leftPos + slot.x + SLOT_SIZE
                    && mouseY >= this.topPos + slot.y && mouseY < this.topPos + slot.y + SLOT_SIZE;
            fillR(g, slot.x, slot.y, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS,
                    hovered ? slotHoverColor : slotColor);
        }
    }

    /** 关闭按钮命中检测（鼠标绝对坐标）。 */
    private boolean isOverCloseButton(int mx, int my) {
        int cx = this.leftPos + this.imageWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int cy = this.topPos + CLOSE_TOP;
        return mx >= cx && mx < cx + CLOSE_SIZE && my >= cy && my < cy + CLOSE_SIZE;
    }

    // ========== 工具方法 ==========

    /**
     * 圆角矩形填充（优化版，与 YzuContainerScreen 一致）。
     * <p>
     * 每次 {@code g.fill} 都会往 GuiRenderState 塞一个 ColoredRectangleRenderState，
     * 圆角必须按整行扫描线输出，不能逐像素填：
     * <ul>
     * <li>{@code r <= 3}：圆角判定对所有角像素恒成立 → 只发 1 个 fill（实心矩形）；</li>
     * <li>其余：中间整块 1 个 + 上下各 r 行、每行 1 个，共 1 + 2r 个。</li>
     * </ul>
     */
    private static void fillR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0)
            return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 3) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int j = 0; j < r; j++) {
            int n = 0;
            while (n < r && n * n + j * j < r * r)
                n++;
            int x0 = x + r - n, x1 = x + w - r + n;
            g.fill(x0, y + r - j - 1, x1, y + r - j, c);         // 顶部第 j 行
            g.fill(x0, y + h - r + j, x1, y + h - r + j + 1, c); // 底部第 j 行
        }
    }
}
