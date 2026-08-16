package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;

/**
 * YZUI 熔炉/高炉/烟熏炉屏幕（替换原版 {@code FurnaceScreen}、
 * {@code BlastFurnaceScreen}、{@code SmokerScreen}）。
 * <p>
 * 26.2 中三个熔炉类屏幕共享同一基类 {@code AbstractFurnaceScreen}
 * （{@code extends AbstractRecipeBookScreen}），本类沿用同一基类
 * {@link AbstractRecipeBookScreen}（与 {@link YzuCraftingScreen} 一致），
 * 将背景纹理替换为 YZUI 白底圆角面板，并保留全部原版交互与动态指示：
 * <ul>
 *   <li><b>配方书</b>：{@link FurnaceRecipeBookComponent} 完整保留（熔炉、高炉、
 *       烟熏炉共用同一组件，{@link RecipeBookComponent.TabInfo} 列表按类型区分），
 *       配方书按钮位于原版坐标 {@code (leftPos+20, height/2-49)}；</li>
 *   <li><b>烧制进度</b>：{@code menu.getBurnProgress()} 0..1 对应箭头填充宽度
 *       （{@code Mth.ceil(24 * progress)}，0..24 px），用 YZUI 纯色圆角矩形
 *       从左到右填充（与 {@link YzuBrewingStandScreen} 进度条同款风格）；</li>
 *   <li><b>燃料燃烧</b>：{@code menu.isLit()} 时显示火焰，{@code menu.getLitProgress()}
 *       0..1 对应火焰高度（{@code Mth.ceil(13 * progress) + 1}，1..14 px），
 *       沿用 26.2 原版 sprite（{@code container/{kind}/lit_progress} 14×14），
 *       通过 10 参 {@code blitSprite} 截取顶部火焰部分（sprite 全尺寸 14×14，
 *       截取 v=14-l 起 14×l 区域至屏幕 {@code (leftPos+56, topPos+50-l)}），
 *       与原版视觉完全一致（YZUI 优先复用 Vanilla sprite）；</li>
 *   <li><b>槽位交互</b>：点击、Shift 快捷移动、双击收集、拖拽分发全继承原版；
 *       服务端校验（FurnaceFuelSlot/FurnaceResultSlot）原样生效。</li>
 * </ul>
 * <p>
 * <h3>槽位布局（沿用原版，相对面板左上角）</h3>
 * <table>
 *   <tr><th>槽位</th><th>坐标</th><th>说明</th></tr>
 *   <tr><td>输入</td><td>(56, 17)</td><td>{@code INGREDIENT_SLOT}</td></tr>
 *   <tr><td>燃料</td><td>(56, 53)</td><td>{@code FUEL_SLOT}（FurnaceFuelSlot）</td></tr>
 *   <tr><td>结果</td><td>(116, 35)</td><td>{@code RESULT_SLOT}（FurnaceResultSlot）</td></tr>
 *   <tr><td>玩家背包</td><td>起点 (8, 84)</td><td>36 格</td></tr>
 * </table>
 * 面板尺寸 176×166（沿用 {@code AbstractContainerScreen} 4 参构造默认）；
 * 火焰区域 {@code (56, 36, 14, 14)}、进度箭头 {@code (79, 34, 24, 16)}。
 * <p>
 * <h3>主题色（三类容器差异化设计语言）</h3>
 * <table>
 *   <tr><th>容器</th><th>标题色</th><th>槽位色</th><th>强调条色</th><th>图标</th></tr>
 *   <tr><td>熔炉</td><td>{@code 0xFF505050} 中性灰</td><td>白色系 {@code 0x40FFFFFF / 0x60FFFFFF}</td><td>{@code 0x80A0A0A0}</td><td>{@link Items#FURNACE}</td></tr>
 *   <tr><td>高炉</td><td>{@code 0xFF6A6A6A} 钢灰（与铁砧同系）</td><td>白色系</td><td>{@code 0xB09A9A9A}</td><td>{@link Items#BLAST_FURNACE}</td></tr>
 *   <tr><td>烟熏炉</td><td>{@code 0xFF8B6F47} 浅木棕（与工作台同系）</td><td>白色系</td><td>{@code 0xB0C8A05C}</td><td>{@link Items#SMOKER}</td></tr>
 * </table>
 * <p>
 * 开关机制：{@code YzuContainerScreenSwitchMixin} 在 {@code Gui.setScreen}
 * 拦截原版 {@code FurnaceScreen} / {@code BlastFurnaceScreen} / {@code SmokerScreen}，
 * 仅当 {@code ClientExternalSettings.isYzuiEnabled()} 为 {@code true} 时替换为本类。
 * 零新增纹理（除复用原版 {@code container/{kind}/lit_progress} sprite 外）。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuFurnaceScreen extends AbstractRecipeBookScreen<AbstractFurnaceMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuFurnaceScreen");

    // ========== YZUI 统一设计常量（与 YzuContainerScreen / YzuBrewingStandScreen 一致） ==========

    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3;

    private static final int LABEL_COLOR = 0xCC404040;

    // ========== 关闭按钮（与现有 YZUI 容器屏一致） ==========

    private static final int CLOSE_SIZE = 14;
    private static final int CLOSE_RADIUS = 4;
    private static final int CLOSE_MARGIN = 6;
    private static final int CLOSE_TOP = 2;
    private static final int CLOSE_BG = 0x40FFFFFF;
    private static final int CLOSE_BG_HOVER = 0x80FFFFFF;
    private static final int CLOSE_ICON = 0xCC404040;
    private static final int CLOSE_ICON_HOVER = 0xFF000000;
    private static final String CLOSE_GLYPH = "\u00d7"; // ×

    // ========== 标题区（与现有 YZUI 容器屏一致） ==========

    private static final int TITLE_ICON_GAP = 4;
    private static final float ICON_SCALE = 0.75f;
    private static final int ICON_SIZE = 12;

    // ========== 火焰（lit progress，复用原版 sprite） ==========

    /** 火焰 sprite 全尺寸（14×14），位于相对面板 (56, 36) */
    private static final int LIT_X = 56;
    private static final int LIT_Y = 36;
    private static final int LIT_W = 14;
    private static final int LIT_H = 14;
    /** 火焰高度上限（原版 13px，加 1 修正使满燃时显示完整火焰） */
    private static final int LIT_FULL = 14;

    // ========== 进度箭头（burn progress，纯色 YZUI 风格） ==========

    /** 进度箭头位置（相对面板）：(79, 34)，尺寸 24×16（沿用原版坐标） */
    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 34;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 16;
    private static final int ARROW_BG = 0x30000000;       // 半透明深灰底槽
    private static final int ARROW_FILL = 0xC0C8E068;     // 黄绿色填充（与酿造台进度条同色）
    /** 烧制进度上限（原版 24px） */
    private static final int ARROW_FULL = 24;

    private final FurnaceKind kind;

    // ========== 构造 ==========

    public YzuFurnaceScreen(AbstractFurnaceMenu menu, Inventory playerInventory, Component title) {
        // AbstractRecipeBookScreen 4 参构造内部调用 AbstractContainerScreen(T,Inv,Title)
        // 默认 imageWidth=176, imageHeight=166（与原版 furnace.png 完全一致）
        super(menu,
                new FurnaceRecipeBookComponent(menu,
                        FurnaceKind.resolveFilterName(menu.getType()),
                        FurnaceKind.resolveTabs(menu.getType())),
                playerInventory, title);
        this.kind = FurnaceKind.resolve(menu.getType());
        DebugLogger.info("YzuFurnaceScreen",
                "创建 YZUI 熔炉屏幕: kind=%s title=%s menuType=%s",
                kind, title.getString(), menu.getType());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuFurnaceScreen.init() — kind={} leftPos={} topPos={} image={}x{} title={}",
                kind, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.title.getString());
    }

    /** 配方书按钮位置（沿用 AbstractFurnaceScreen 原版坐标 leftPos+20, height/2-49）。 */
    @Override
    protected @NonNull ScreenPosition getRecipeBookButtonPosition() {
        return new ScreenPosition(this.leftPos + 20, this.height / 2 - 49);
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);
        drawFurnaceIndicators(g);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
        // no-op — YZUI 面板在 extractRenderState 中绘制
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawSlotBackgrounds(guiGraphics, mouseX, mouseY);
        // 必须调 super（AbstractRecipeBookScreen.extractSlots），处理配方书打开时的槽位右移偏移
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op — 标题/背包标签由 YZUI 自绘
    }

    // ========== 鼠标事件（仅关闭按钮，其余交互委托原版） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuFurnaceScreen", "点击关闭按钮，关闭熔炉: %s kind=%s",
                    this.title.getString(), kind);
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== 熔炉动态指示器（火焰 + 进度箭头） ==========

    /**
     * 绘制火焰与进度箭头（坐标与节奏沿用原版）。
     * <ul>
     *   <li>火焰：{@code menu.isLit()} 时绘制，复用原版 {@code container/{kind}/lit_progress}
     *       sprite（14×14），10 参 blitSprite 截取顶部 l 高度（与 26.2 原版字节码逻辑一致）。</li>
     *   <li>进度箭头：常显底槽 + 动态填充（纯色圆角矩形，零纹理依赖）。</li>
     * </ul>
     * 本方法在 extractRenderState 中调用（无 translate），坐标为屏幕绝对坐标。
     */
    private void drawFurnaceIndicators(GuiGraphicsExtractor g) {
        int ox = this.leftPos;
        int oy = this.topPos;

        // 1. 进度箭头底槽（常显）+ 填充（按比例）
        fillR(g, ox + ARROW_X, oy + ARROW_Y, ARROW_W, ARROW_H, 2, ARROW_BG);
        int burned = Mth.ceil(ARROW_FULL * this.menu.getBurnProgress());
        if (burned > 0) {
            fillR(g, ox + ARROW_X, oy + ARROW_Y, burned, ARROW_H, 2, ARROW_FILL);
        }

        // 2. 火焰（仅燃烧时显示，沿用 26.2 原版 10 参 blitSprite 截取逻辑）
        if (this.menu.isLit()) {
            int lit = Mth.ceil(13.0F * this.menu.getLitProgress()) + 1; // 1..14
            g.blitSprite(RenderPipelines.GUI_TEXTURED, kind.litProgressSprite,
                    LIT_W, LIT_H,                  // sprite 全尺寸
                    0, LIT_H - lit,                // u=0, v=14-l 截取顶部 l 高度
                    ox + LIT_X, oy + LIT_Y + LIT_H - lit,  // 屏幕 (56, 50-l)
                    LIT_W, lit);                   // 区域 14×l
        }
    }

    // ========== YZUI 面板绘制 ==========

    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillR(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, PANEL_RADIUS, PANEL_BG);
    }

    /** 标题区：容器图标（12×12 底衬 + 缩放物品）+ 标题文字 + 类型强调条。 */
    private void drawTitle(GuiGraphicsExtractor g) {
        int ix = this.leftPos + 8;
        int iy = this.topPos + 5;

        // 图标底衬 12×12
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
        int ty = this.topPos + 5;
        g.text(this.font, this.title, tx, ty, kind.titleColor, false);

        // 强调条：标题下方，字形底 14 + 1 → 15..17
        int titleWidth = Math.min(this.font.width(this.title),
                this.imageWidth - 8 - ICON_SIZE - TITLE_ICON_GAP - 8);
        fillR(g, tx, ty + 10, titleWidth, 2, 1, kind.accentBarColor);
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

    /** 槽位背景：每个活动槽绘制主题色圆角矩形，悬浮提亮（与 YzuContainerScreen 同款）。 */
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
     * 圆角矩形填充（YZUI 统一实现，与 YzuContainerScreen / YzuBrewingStandScreen 一致）。
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
            g.fill(x0, y + r - j - 1, x1, y + r - j, c);
            g.fill(x0, y + h - r + j, x1, y + h - r + j + 1, c);
        }
    }

    // ========== 熔炉类型 ==========

    /**
     * YZUI 熔炉类型。共享 YZUI 设计语言，仅通过标题强调色与图标区分；槽位底色统一
     * 白色系派生（与现有 YZUI 容器屏协调）。三类的 RecipeBookComponent.TabInfo 列表
     * 沿用 26.2 原版 FurnaceScreen/BlastFurnaceScreen/SmokerScreen 的 TABS。
     * <p>
     * 类型识别依据 {@code menu.getType()}（服务端权威，构造时由 {@code AbstractFurnaceMenu}
     * 子类注入 {@code MenuType.FURNACE/BLAST_FURNACE/SMOKER}）。
     */
    public enum FurnaceKind {
        /** 普通熔炉：中性灰标题，3 类配方（FOOD/BLOCKS/MISC） */
        FURNACE(MenuType.FURNACE, "gui.recipebook.toggleRecipes.smeltable",
                0xFF505050, 0x40FFFFFF, 0x60FFFFFF, 0x80A0A0A0, Items.FURNACE,
                Identifier.withDefaultNamespace("container/furnace/lit_progress"),
                List.of(
                        new RecipeBookComponent.TabInfo(SearchRecipeBookCategory.FURNACE),
                        new RecipeBookComponent.TabInfo(Items.PORKCHOP, RecipeBookCategories.FURNACE_FOOD),
                        new RecipeBookComponent.TabInfo(Items.STONE, RecipeBookCategories.FURNACE_BLOCKS),
                        new RecipeBookComponent.TabInfo(Items.LAVA_BUCKET, Items.EMERALD,
                                RecipeBookCategories.FURNACE_MISC))),
        /** 高炉：钢灰标题（与铁砧同系），2 类配方（BLOCKS/MISC） */
        BLAST_FURNACE(MenuType.BLAST_FURNACE, "gui.recipebook.toggleRecipes.blastable",
                0xFF6A6A6A, 0x40FFFFFF, 0x60FFFFFF, 0xB09A9A9A, Items.BLAST_FURNACE,
                Identifier.withDefaultNamespace("container/blast_furnace/lit_progress"),
                List.of(
                        new RecipeBookComponent.TabInfo(SearchRecipeBookCategory.BLAST_FURNACE),
                        new RecipeBookComponent.TabInfo(Items.REDSTONE_ORE, RecipeBookCategories.BLAST_FURNACE_BLOCKS),
                        new RecipeBookComponent.TabInfo(Items.IRON_SHOVEL, Items.GOLDEN_LEGGINGS,
                                RecipeBookCategories.BLAST_FURNACE_MISC))),
        /** 烟熏炉：浅木棕标题（与工作台同系），1 类配方（FOOD） */
        SMOKER(MenuType.SMOKER, "gui.recipebook.toggleRecipes.smokable",
                0xFF8B6F47, 0x40FFFFFF, 0x60FFFFFF, 0xB0C8A05C, Items.SMOKER,
                Identifier.withDefaultNamespace("container/smoker/lit_progress"),
                List.of(
                        new RecipeBookComponent.TabInfo(SearchRecipeBookCategory.SMOKER),
                        new RecipeBookComponent.TabInfo(Items.PORKCHOP, RecipeBookCategories.SMOKER_FOOD)));

        private final MenuType<?> menuType;
        /** 配方书过滤器名称（用于 FurnaceRecipeBookComponent 构造） */
        private final String filterNameKey;
        /** 标题文字色 */
        private final int titleColor;
        /** 槽位常态底色 */
        private final int slotColor;
        /** 槽位悬停底色 */
        private final int slotHoverColor;
        /** 标题下方强调条色 */
        private final int accentBarColor;
        /** 标题区容器图标 */
        private final ItemStack icon;
        /** 燃料燃烧 sprite（{@code container/{kind}/lit_progress}） */
        private final Identifier litProgressSprite;
        /** 配方书 TabInfo 列表 */
        private final List<RecipeBookComponent.TabInfo> tabs;

        FurnaceKind(MenuType<?> menuType, String filterNameKey,
                int titleColor, int slotColor, int slotHoverColor, int accentBarColor,
                net.minecraft.world.item.Item icon,
                Identifier litProgressSprite,
                List<RecipeBookComponent.TabInfo> tabs) {
            this.menuType = menuType;
            this.filterNameKey = filterNameKey;
            this.titleColor = titleColor;
            this.slotColor = slotColor;
            this.slotHoverColor = slotHoverColor;
            this.accentBarColor = accentBarColor;
            this.icon = new ItemStack(icon);
            this.litProgressSprite = litProgressSprite;
            this.tabs = tabs;
        }

        /**
         * 依据 {@code menu.getType()} 解析熔炉类型。
         */
        static FurnaceKind resolve(MenuType<?> menuType) {
            for (FurnaceKind k : values()) {
                if (k.menuType == menuType)
                    return k;
            }
            // 兜底：未知类型按普通熔炉处理
            LOGGER.warn("未知熔炉 menuType={}，回退为普通熔炉", menuType);
            return FURNACE;
        }

        /** 解析对应类型的配方书过滤器名称（{@link Component#translatable}）。 */
        static Component resolveFilterName(MenuType<?> menuType) {
            for (FurnaceKind k : values()) {
                if (k.menuType == menuType)
                    return Component.translatable(k.filterNameKey);
            }
            return Component.translatable(FURNACE.filterNameKey);
        }

        /** 解析对应类型的 TabInfo 列表（用于 FurnaceRecipeBookComponent 构造）。 */
        static List<RecipeBookComponent.TabInfo> resolveTabs(MenuType<?> menuType) {
            for (FurnaceKind k : values()) {
                if (k.menuType == menuType)
                    return k.tabs;
            }
            return FURNACE.tabs;
        }

        @Override
        public String toString() {
            return name();
        }
    }
}