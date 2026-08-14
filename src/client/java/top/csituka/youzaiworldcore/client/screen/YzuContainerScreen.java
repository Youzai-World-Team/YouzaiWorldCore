package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 容器屏幕 — 普通箱子 / 大箱子（双格）/ 末影箱 / 木桶 / 铜箱子（含锈蚀与涂蜡变体）。
 * <p>
 * 26.2 中原版箱子、陷阱箱、末影箱、木桶、双格大箱子统一由
 * {@code ContainerScreen（原 ChestScreen 重命名）} 承载，全部走
 * {@link ChestMenu}（GENERIC_9x1 ~ GENERIC_9x6）。本类以"全量替换屏幕"的方式
 * 提供 YZUI 风格渲染，交互逻辑（点击/Shift 快捷移动/双击收集/拖拽分发/数字键/
 * Esc 关闭/悬停提示）全部继承 {@link AbstractContainerScreen} 原生实现，零改动。
 * <p>
 * <h3>YZUI 设计语言（与 YzuInventoryScreen / YzuCreativeInventoryScreen 一致）</h3>
 * <table>
 *   <caption>统一设计规范</caption>
 *   <tr><th>元素</th><th>取值</th><th>说明</th></tr>
 *   <tr><td>面板背景</td><td>{@code 0x80FFFFFF} 半透明白</td><td>圆角半径 6，随 world 透出</td></tr>
 *   <tr><td>槽位背景</td><td>{@code 0x40FFFFFF} 半透明白</td><td>16×16，半径 3（r≤3 走矩形快速路径）</td></tr>
 *   <tr><td>槽位悬停</td><td>{@code 0x60FFFFFF}（或类型色）</td><td>悬浮时提亮</td></tr>
 *   <tr><td>标题文字</td><td>类型强调色，无阴影</td><td>跟随各容器强调色；强调条位于标题下、容器格之上（不重叠）</td></tr>
 *   <tr><td>关闭按钮</td><td>圆角 4，默认 {@code 0x40FFFFFF} / 悬停 {@code 0x80FFFFFF}</td><td>右上角 × 号，左键点击关闭容器</td></tr>
 * </table>
 * <p>
 * <h3>容器类型独特元素</h3>
 * <table>
 *   <caption>类型差异（强调色均派生自统一白色系，保证视觉协调）</caption>
 *   <tr><th>容器</th><th>布局</th><th>标题色</th><th>槽位色（常态/悬停）</th><th>强调条</th><th>图标</th></tr>
 *   <tr><td>普通箱子（含陷阱箱）</td><td>3×9</td><td>{@code 0xFF505050} 中性灰</td><td>{@code 0x40FFFFFF / 0x60FFFFFF}</td><td>{@code 0x80A0A0A0}</td><td>{@link Items#CHEST}</td></tr>
 *   <tr><td>大箱子（双格）</td><td>6×9</td><td>{@code 0xFFB08930} 暗金</td><td>{@code 0x40FFFFFF / 0x60FFFFFF}</td><td>{@code 0xB0E8C468}</td><td>{@link Items#CHEST}</td></tr>
 *   <tr><td>末影箱</td><td>3×9</td><td>{@code 0xFF9A5BD8} 暗紫</td><td>{@code 0x50B57BFF / 0x70B57BFF}</td><td>{@code 0xB0B57BFF}</td><td>{@link Items#ENDER_CHEST}</td></tr>
 *   <tr><td>木桶</td><td>3×9</td><td>{@code 0xFF9A6A38} 深木</td><td>{@code 0x50A08050 / 0x70A08050}</td><td>{@code 0xB0D9A066}</td><td>{@link Items#BARREL}</td></tr>
 *   <tr><td>铜箱子（含锈蚀/涂蜡变体）</td><td>3×9</td><td>{@code 0xFFA0522D} 铜橙</td><td>{@code 0x50B87333 / 0x70B87333}</td><td>{@code 0xB0D9824F}</td><td>{@code Items.COPPER_CHEST} 未氧化形态</td></tr>
 * </table>
 * <p>
 * 面板尺寸与槽位坐标完全沿用原版（{@code 176 × (114 + rows*18)}，容器格起点
 * {@code (8,18)}，玩家背包起点 {@code (8, 18+rows*18+13)}），因此与
 * {@link ChestMenu} 的槽位布局天然对齐，无需重新计算。
 * <p>
 * <h3>类型识别（重要）</h3>
 * 客户端创建容器菜单时（{@code MenuType.create}）走无容器工厂，{@code menu.getContainer()}
 * 恒为客户端新建的 {@code SimpleContainer}，因此<b>不能</b>通过容器实例区分方块类型。
 * 类型识别改为依据服务端下发的<b>标题翻译键</b>：
 * <ul>
 *   <li>{@code container.enderchest} → 末影箱；</li>
 *   <li>{@code container.barrel} → 木桶；</li>
 *   <li>{@code block.minecraft.*copper_chest} → 铜箱子（标题已由服务端
 *       {@code ChestBlockEntityCopperTitleMixin} 修正为方块真实名称）；</li>
 *   <li>{@code container.chest} / 自定义命名 → 普通箱子。</li>
 * </ul>
 * 行数 ≥ 6（GENERIC_9x6）→ 大箱子。
 * <p>
 * <h3>开关机制</h3>
 * {@link top.csituka.youzaiworldcore.mixin.client.YzuContainerScreenSwitchMixin}
 * 在 {@code Gui.setScreen} 拦截原版 {@code ContainerScreen}；仅当
 * {@code ClientExternalSettings.isYzuiEnabled()} 为 {@code true} 时替换为本类，
 * 否则原版屏幕原样通过。本类不加载任何自定义纹理（纯色矩形 + 原版物品图标），
 * 无资源加载与缓存开销。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuContainerScreen extends AbstractContainerScreen<ChestMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuContainerScreen");

    // ========== YZUI 统一设计常量 ==========

    /** 面板背景：半透明白（与 YzuInventoryScreen 一致） */
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3; // r≤3 走矩形快速路径，等价实心矩形

    /** 玩家背包标题（深灰，白底可读，YZUI 标签风格） */
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

    /** 标题图标与文字间距 */
    private static final int TITLE_ICON_GAP = 4;
    /** 标题区图标缩放（16px 物品模型 → 12px 显示，为顶部留白腾空间） */
    private static final float ICON_SCALE = 0.75f;
    private static final int ICON_SIZE = 12;

    private final int containerRows;
    private final Kind kind;

    // ========== 构造 ==========

    public YzuContainerScreen(ChestMenu menu, Inventory playerInventory, Component title) {
        // 沿用原版 ContainerScreen 的面板尺寸：176 × (114 + rows*18)
        super(menu, playerInventory, title, 176, 114 + menu.getRowCount() * 18);
        this.containerRows = menu.getRowCount();
        this.kind = Kind.resolve(menu, title);
        DebugLogger.info("YzuContainerScreen", "创建 YZUI 容器屏幕: kind=%s rows=%d title=%s menuType=%s",
                kind, containerRows, title.getString(), menu.getType());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuContainerScreen.init() — kind={} rows={} leftPos={} topPos={} image={}x{} title={}",
                kind, containerRows, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                this.title.getString());
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 面板、标题、强调条、背包标签、关闭按钮均在 super 之前绘制（底层）
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
        // no-op — YZUI 面板在 extractRenderState 中绘制（与 YzuInventoryScreen 一致）
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawSlotBackgrounds(guiGraphics, mouseX, mouseY);
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op — 标题由 drawTitle 绘制（替换原版深灰文字）
    }

    // ========== 鼠标事件（仅关闭按钮，其余交互全部委托原版） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuContainerScreen", "点击关闭按钮，关闭容器: %s", this.title.getString());
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== YZUI 面板绘制 ==========

    /** 面板背景：半透明白圆角矩形（尺寸与槽位布局严格一致）。 */
    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillR(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, PANEL_RADIUS, PANEL_BG);
    }

    /**
     * 标题区：容器图标（带槽位风格底衬）+ 标题文字 + 类型强调条。
     * <p>
     * ⚠️ 26.2 {@code GuiGraphicsExtractor.text()} 的 y 参数是<b>文字顶部</b>
     * （非基线，与原版 titleLabelY=6 的标题渲染位置一致），字形占 y..y+9。
     * 头部可用高度 18px（容器格起点 y=18），顶部留白 5px：
     * <ul>
     * <li>图标底衬 12×12 @ (8,5) → 5..17；物品模型 16×16 经 pose 矩阵缩放
     *     0.75 渲染为 12×12，与底衬精确对齐（translate+scale，参照
     *     MailViewport/ItemDisplayEntry 的既有用法）；</li>
     * <li>标题文字顶部 ty=5 → 字形 5..14，与图标行顶部对齐；</li>
     * <li>强调条 y=15..17，紧跟文字下方、不压容器格。</li>
     * </ul>
     */
    private void drawTitle(GuiGraphicsExtractor g) {
        int ix = this.leftPos + 8;
        int iy = this.topPos + 5;

        // 图标底衬 12×12（物品模型缩放 0.75 → 12×12 居中覆盖）
        fillR(g, ix, iy, ICON_SIZE, ICON_SIZE, 3, kind.slotColor);
        ItemStack icon = kind.icon;
        if (!icon.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(ix, iy);
            g.pose().scale(ICON_SCALE, ICON_SCALE);
            g.item(icon, 0, 0, 0);
            g.pose().popMatrix();
        }

        int tx = ix + ICON_SIZE + TITLE_ICON_GAP;
        int ty = this.topPos + 5; // 文字顶部（字形 5..14），与图标行顶部对齐
        g.text(this.font, this.title, tx, ty, kind.titleColor, false);

        // 强调条：位于标题下方（字形底 14 + 1 → 15..17），不压容器格；宽度取标题实际宽度
        int titleWidth = Math.min(this.font.width(this.title), this.imageWidth - 8 - ICON_SIZE - TITLE_ICON_GAP - 8);
        fillR(g, tx, ty + 10, titleWidth, 2, 1, kind.accentBarColor);
    }

    /** 玩家背包区域标签（沿用原版标签坐标 imageHeight-94，仅改 YZUI 配色，无阴影）。 */
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

    /** 槽位背景：每个活动槽绘制类型色圆角矩形，悬浮提亮（与 YzuInventoryScreen 同款）。 */
    private void drawSlotBackgrounds(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive())
                continue;
            boolean hovered = mouseX >= this.leftPos + slot.x && mouseX < this.leftPos + slot.x + SLOT_SIZE
                    && mouseY >= this.topPos + slot.y && mouseY < this.topPos + slot.y + SLOT_SIZE;
            fillR(g, slot.x, slot.y, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS,
                    hovered ? kind.slotHoverColor : kind.slotColor);
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
     * 圆角矩形填充（优化版，与 YzuCreativeInventoryScreen 一致）。
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
            // 圆角不裁掉任何像素，等价于实心矩形
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        // 中间整块（含左右两条直边）
        g.fill(x, y + r, x + w, y + h - r, c);
        // 上下圆角区：每行一条扫描线
        for (int j = 0; j < r; j++) {
            int n = 0;
            while (n < r && n * n + j * j < r * r)
                n++;
            int x0 = x + r - n, x1 = x + w - r + n;
            g.fill(x0, y + r - j - 1, x1, y + r - j, c);         // 顶部第 j 行
            g.fill(x0, y + h - r + j, x1, y + h - r + j + 1, c); // 底部第 j 行
        }
    }

    // ========== 容器类型 ==========

    /**
     * YZUI 容器类型。各容器共享统一的 YZUI 设计语言，仅通过
     * 强调色、槽位底色与图标区分，保证视觉协调。
     */
    public enum Kind {
        /** 普通箱子（含陷阱箱）：3×9，中性灰强调 */
        CHEST("container.chest", 0xFF505050, 0x40FFFFFF, 0x60FFFFFF, 0x80A0A0A0, Items.CHEST),
        /** 大箱子（双格）：6×9，暗金强调 */
        DOUBLE_CHEST("container.chestDouble", 0xFFB08930, 0x40FFFFFF, 0x60FFFFFF, 0xB0E8C468, Items.CHEST),
        /** 末影箱：3×9，暗紫强调 */
        ENDER_CHEST("container.enderchest", 0xFF9A5BD8, 0x50B57BFF, 0x70B57BFF, 0xB0B57BFF, Items.ENDER_CHEST),
        /** 木桶：3×9，深木强调 */
        BARREL("container.barrel", 0xFF9A6A38, 0x50A08050, 0x70A08050, 0xB0D9A066, Items.BARREL),
        /** 铜箱子（含锈蚀/涂蜡变体，26.2 新增）：3×9，铜橙强调 */
        COPPER_CHEST("block.minecraft.copper_chest", 0xFFA0522D, 0x50B87333, 0x70B87333, 0xB0D9824F, copperChestIcon());

        /** 原版翻译键（仅用于日志/定位） */
        private final String langKey;
        /** 标题文字色（ARGB，白底可读的深色强调） */
        private final int titleColor;
        /** 槽位常态底色 */
        private final int slotColor;
        /** 槽位悬停底色 */
        private final int slotHoverColor;
        /** 标题下方强调条色（ARGB） */
        private final int accentBarColor;
        /** 标题区容器图标 */
        private final ItemStack icon;

        Kind(String langKey, int titleColor, int slotColor, int slotHoverColor, int accentBarColor, Item icon) {
            this.langKey = langKey;
            this.titleColor = titleColor;
            this.slotColor = slotColor;
            this.slotHoverColor = slotHoverColor;
            this.accentBarColor = accentBarColor;
            this.icon = new ItemStack(icon);
        }

        public String getLangKey() {
            return langKey;
        }

        /** 铜箱子图标：取 WeatheringCopperCollection 中未氧化（UNAFFECTED）形态的物品。 */
        private static Item copperChestIcon() {
            return Items.COPPER_CHEST.asList().getFirst();
        }

        /**
         * 依据菜单与标题解析容器类型。
         * <p>
         * <b>不能</b>使用 {@code menu.getContainer()} 区分方块：客户端创建菜单时
         * （{@code MenuType.create}）走无容器工厂，容器恒为客户端新建的
         * {@code SimpleContainer}，方块实体信息不可达。类型识别改为服务端下发的
         * <b>标题翻译键</b>：
         * <ol>
         * <li>行数 ≥ 6（GENERIC_9x6）→ 大箱子（双格）；</li>
         * <li>{@code container.enderchest} → 末影箱；</li>
         * <li>{@code container.barrel} → 木桶；</li>
         * <li>{@code block.minecraft.*copper_chest} → 铜箱子（服务端
         *     {@code ChestBlockEntityCopperTitleMixin} 已将标题修正为方块名称）；</li>
         * <li>其余（{@code container.chest} / 自定义命名）→ 普通箱子。</li>
         * </ol>
         */
        static Kind resolve(ChestMenu menu, Component title) {
            if (menu.getRowCount() >= 6)
                return DOUBLE_CHEST;
            if (title.getContents() instanceof TranslatableContents tc) {
                String key = tc.getKey();
                if ("container.enderchest".equals(key))
                    return ENDER_CHEST;
                if ("container.barrel".equals(key))
                    return BARREL;
                if (key.startsWith("block.minecraft.") && key.endsWith("copper_chest"))
                    return COPPER_CHEST;
            }
            return CHEST;
        }

        @Override
        public String toString() {
            return name();
        }
    }
}
