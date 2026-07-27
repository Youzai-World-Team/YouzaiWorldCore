package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * YZUI 生存模式物品栏屏幕。
 * <p>
 * 替代原版 InventoryScreen，YZUI 开启时使用白底圆角风格渲染面板与槽位，
 * 关闭时回退原版。
 * <p>
 * 槽位布局同原版（InventoryMenu 固定坐标），但外观变更为 YZUI 圆角矩形风格：
 * <ul>
 *   <li>半透明白色圆角面板背景</li>
 *   <li>半透明圆角槽位背景（悬浮时高亮）</li>
 *   <li>配方书打开时左侧显示 YZUI 风格配方书面板</li>
 *   <li>配方书切换按钮位于副手槽上方</li>
 *   <li>左键拖拽手势：有物品时合并同种，Shift+左键拖拽批量快速转移</li>
 * </ul>
 */
public class YzuInventoryScreen extends AbstractRecipeBookScreen<InventoryMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuInventoryScreen");

    // ========== YZUI 配色常量 ==========
    private static final int PANEL_BG            = 0x80FFFFFF;
    private static final int SLOT_COLOR          = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR    = 0x60FFFFFF;
    private static final int CRAFT_RESULT_BG     = 0x60FFFFFF;
    /** 副手槽背景色（褐色块） */
    private static final int OFFHAND_SLOT_COLOR  = 0x60A08050;
    /** 配方书面板背景 */
    private static final int RECIPE_BOOK_PANEL   = 0xC0FFFFFF;

    private static final int PANEL_RADIUS = 6;
    private static final int SLOT_RADIUS  = 3;
    private static final int SLOT_SIZE    = 16;

    // ========== 配方书布局常量 ==========
    private static final int RECIPE_BOOK_W = 147;
    private static final int RECIPE_BOOK_H = 166;

    /** 玩家模型渲染区域（相对 leftPos/topPos） */
    private static final int ENTITY_X = 26;
    private static final int ENTITY_Y = 8;
    private static final int ENTITY_W = 75;
    private static final int ENTITY_H = 78;
    private static final int ENTITY_SIZE = 30;
    private static final float ENTITY_Y_OFFSET = 0.0625f;

    private float xMouse;
    private float yMouse;

    // ========== 手势拖拽状态 ==========
    /** true = 等待鼠标移动以确认手势拖拽 */
    private boolean gesturePending;
    /** 0=无, 1=合并拖拽, 2=Shift批量拖拽 */
    private int gestureMode;
    /** 手势起点槽位索引 */
    private int gestureOriginSlot;
    /** 手势拖拽中已处理过的槽位（避免重复） */
    private final Set<Integer> gestureProcessed = new HashSet<>();
    /** 是否为实际拖拽（已移动过） */
    private boolean gestureDragging;
    /** 拖拽经过的槽位列表 */
    private final List<Integer> gestureSlots = new ArrayList<>();

    // ========== 构造 ==========

    public YzuInventoryScreen(Player player) {
        super(
                player.inventoryMenu,
                new CraftingRecipeBookComponent(player.inventoryMenu),
                player.getInventory(),
                Component.translatable("container.crafting")
        );
        LOGGER.debug("YzuInventoryScreen created for player: {}", player.getName().getString());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuInventoryScreen.init() — leftPos={} topPos={} imageWidth={} imageHeight={}",
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean bookVisible = isRecipeBookOpen();
        if (bookVisible) drawRecipeBookPanel(g);
        drawMainPanel(g);
        drawPlayerModel(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        this.xMouse = (float) mouseX;
        this.yMouse = (float) mouseY;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // no-op — YZUI 面板在 extractRenderState 中绘制
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawSlotBackgrounds(guiGraphics, mouseX, mouseY);
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    /** 获取鼠标位置下的槽位（通过 isHovering 检测，getHoveredSlot 为 private 不可用）。 */
    private Slot getSlotAt(int mx, int my) {
        for (Slot s : this.menu.slots) {
            if (s.isActive() && isHovering(s.x, s.y, 16, 16, (double) mx, (double) my))
                return s;
        }
        return null;
    }

    // ========== 鼠标事件（手势拖拽） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        // Shift+左键 → 手势拖拽系统接管（单击=QUICK_MOVE，拖动=批量QUICK_MOVE）
        if (ev.hasShiftDown() && ev.button() == 0) {
            Slot slot = getSlotAt((int) ev.x(), (int) ev.y());
            if (slot != null && getSlotIndex(slot) >= 0) {
                gesturePending = true;
                gestureOriginSlot = getSlotIndex(slot);
                gestureMode = 2;
                gestureProcessed.clear();
                gestureSlots.clear();
                return true;
            }
        }
        // 非Shift左键在槽位上：让标准系统处理点击（PICKUP/quickCraft），
        // 仅当空手拿起物品后进入合并手势待命
        if (ev.button() == 0 && !ev.hasShiftDown()) {
            Slot slot = getSlotAt((int) ev.x(), (int) ev.y());
            if (slot != null && getSlotIndex(slot) >= 0) {
                boolean wasEmpty = this.menu.getCarried().isEmpty();
                boolean handled = super.mouseClicked(ev, real);
                // 空手→拿起物品 → 设合并手势待命
                if (handled && wasEmpty && !this.menu.getCarried().isEmpty()) {
                    gesturePending = true;
                    gestureOriginSlot = getSlotIndex(slot);
                    gestureMode = 1;
                    gestureSlots.clear();
                }
                return handled;
            }
        }
        return super.mouseClicked(ev, real);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent ev, double dx, double dy) {
        // 仅截获我们自己发起的 gesture 拖拽
        if (gesturePending && ev.button() == 0) {
            gesturePending = false;
            gestureDragging = true;
            gestureSlots.clear();
            gestureSlots.add(gestureOriginSlot);
            if (gestureMode == 2)
                processGestureSlot(gestureOriginSlot);
            Slot slot = getSlotAt((int) ev.x(), (int) ev.y());
            if (slot != null) {
                int idx = getSlotIndex(slot);
                if (idx >= 0 && idx != gestureOriginSlot) {
                    gestureSlots.add(idx);
                    processGestureSlot(idx);
                }
            }
            return true;
        }
        if (gestureDragging && ev.button() == 0) {
            Slot slot = getSlotAt((int) ev.x(), (int) ev.y());
            if (slot != null) {
                int idx = getSlotIndex(slot);
                if (idx >= 0 && !gestureSlots.contains(idx)) {
                    gestureSlots.add(idx);
                    processGestureSlot(idx);
                }
            }
            return true;
        }
        // 非 gesture 的拖拽（标准 quickCraft 等）→ 委托标准系统
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent ev) {
        // 仅清理 gesture 状态后，仍让 super 处理标准系统的 state 重置（如 skipNextRelease）
        if (ev.button() == 0) {
            if (gesturePending) {
                gesturePending = false;
                if (gestureMode == 2) {
                    this.menu.clicked(gestureOriginSlot, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
                }
                resetGesture();
                return super.mouseReleased(ev); // 让 super 重置 skipNextRelease 等
            }
            if (gestureDragging) {
                gestureDragging = false;
                resetGesture();
                return super.mouseReleased(ev);
            }
        }
        return super.mouseReleased(ev);
    }

    /** 实时处理手势拖拽经过的单个槽位。 */
    private void processGestureSlot(int slotIndex) {
        if (gestureMode == 2) {
            // Shift 批量拖拽：快速转移
            this.menu.clicked(slotIndex, 0, ContainerInput.QUICK_MOVE, this.minecraft.player);
            return;
        }
        if (gestureMode == 1) {
            // 合并拖拽：将经过的同种物品合并到光标
            ItemStack ca = this.menu.getCarried();
            if (ca.isEmpty()) return;
            this.menu.clicked(slotIndex, 0, ContainerInput.PICKUP, this.minecraft.player);
        }
    }

    private void resetGesture() {
        gestureMode = 0;
        gestureProcessed.clear();
        gestureSlots.clear();
    }

    // ========== YZUI 面板绘制方法 ==========

    private boolean isRecipeBookOpen() {
        var player = this.minecraft != null ? this.minecraft.player : null;
        return player != null && player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
    }

    private void drawRecipeBookPanel(GuiGraphicsExtractor g) {
        int xOffset = (this.width < 379) ? 0 : 86;
        int rx = (this.width - RECIPE_BOOK_W) / 2 - xOffset;
        int ry = (this.height - RECIPE_BOOK_H) / 2;
        fillRoundedRect(g, rx, ry, RECIPE_BOOK_W, RECIPE_BOOK_H, PANEL_RADIUS, RECIPE_BOOK_PANEL);
    }

    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillRoundedRect(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                PANEL_RADIUS, PANEL_BG);
    }

    private void drawPlayerModel(GuiGraphicsExtractor g) {
        var player = this.minecraft != null ? this.minecraft.player : null;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g,
                    this.leftPos + ENTITY_X,
                    this.topPos  + ENTITY_Y,
                    this.leftPos + ENTITY_W,
                    this.topPos  + ENTITY_H,
                    ENTITY_SIZE,
                    ENTITY_Y_OFFSET,
                    this.xMouse,
                    this.yMouse,
                    player
            );
        }
    }

    // ========== 槽位背景绘制 ==========

    private void drawSlotBackgrounds(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) continue;

            int sx = slot.x;
            int sy = slot.y;

            boolean isHovered = mouseX >= sx + this.leftPos && mouseX < sx + this.leftPos + SLOT_SIZE
                    && mouseY >= sy + this.topPos && mouseY < sy + this.topPos + SLOT_SIZE;

            // 副手槽使用褐色背景
            int color;
            if (getSlotIndex(slot) == 45) {
                color = isHovered ? 0x80A08050 : OFFHAND_SLOT_COLOR;
            } else {
                color = isHovered ? SLOT_HOVER_COLOR : SLOT_COLOR;
            }

            fillRoundedRect(guiGraphics, sx, sy, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, color);
        }

        // 合成输出槽额外高亮
        if (!this.menu.slots.isEmpty()) {
            Slot resultSlot = this.menu.slots.get(0);
            if (resultSlot.isActive()) {
                fillRoundedRect(guiGraphics,
                        resultSlot.x, resultSlot.y,
                        SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, CRAFT_RESULT_BG);
            }
        }
    }

    /**
     * 根据 Slot 对象获取其在容器槽位列表中的索引。
     */
    private int getSlotIndex(Slot slot) {
        return this.menu.slots.indexOf(slot);
    }

    // ========== 配方书 ==========

    @Override
    protected @NonNull ScreenPosition getRecipeBookButtonPosition() {
        int btnX = this.leftPos + 72;
        int btnY = this.topPos + 38;
        return new ScreenPosition(btnX, btnY);
    }

    @Override
    protected void onRecipeBookButtonClick() {
        LOGGER.debug("Recipe book button toggled");
        super.onRecipeBookButtonClick();
    }

    // ========== 标签 ==========

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op
    }

    // ========== 杂项 ==========

    @Override
    public boolean showsActiveEffects() {
        return false;
    }

    @Override
    protected boolean isBiggerResultSlot() {
        return false;
    }

    // ========== 工具方法 ==========

    private static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }
}
