package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import top.csituka.youzaiworldcore.network.TrinketInteractPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.TrinketHelper;

import eu.pb4.trinkets.impl.TrinketSlot;

/**
 * YZUI 生存模式物品栏屏幕。
 * <p>
 * 替代原版 InventoryScreen，YZUI 开启时使用白底圆角风格渲染面板与槽位，
 * 关闭时回退原版。
 * <p>
 * 槽位布局同原版（InventoryMenu 固定坐标），但外观变更为 YZUI 圆角矩形风格：
 * <ul>
 * <li>半透明白色圆角面板背景</li>
 * <li>半透明圆角槽位背景（悬浮时高亮）</li>
 * <li>配方书打开时左侧显示 YZUI 风格配方书面板</li>
 * <li>配方书切换按钮位于副手槽上方</li>
 * <li>左键拖拽手势：有物品时合并同种，Shift+左键拖拽批量快速转移</li>
 * </ul>
 */
@SuppressWarnings({ "null", "unused" })
public class YzuInventoryScreen extends AbstractRecipeBookScreen<InventoryMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuInventoryScreen");

    // ========== YZUI 配色常量 ==========
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int SLOT_COLOR = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x60FFFFFF;
    private static final int CRAFT_RESULT_BG = 0x60FFFFFF;
    /** 副手槽背景色（褐色块） */
    private static final int OFFHAND_SLOT_COLOR = 0x60A08050;

    private static final int PANEL_RADIUS = 6;
    private static final int SLOT_RADIUS = 3;
    private static final int SLOT_SIZE = 16;

    // ========== 配方书布局常量 ==========

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
    // Trinkets 悬停状态
    private int trinketSourceSlot = -1;
    private java.util.List<TrinketHelper.TrinketSlotInfo> activeTrinketSlots = java.util.List.of();

    // ========== 构造 ==========

    public YzuInventoryScreen(Player player) {
        super(
                player.inventoryMenu,
                new CraftingRecipeBookComponent(player.inventoryMenu),
                player.getInventory(),
                Component.translatable("container.crafting"));
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
        drawMainPanel(g);
        // 在 2×2 合成格与输出槽之间绘制合成箭头
        drawCraftArrow(g);
        drawPlayerModel(g);

        // ===== Trinkets 悬停提示（在 super.extractRenderState 之前渲染，避免遮盖鼠标物品） =====
        if (TrinketHelper.isLoaded())
            trinketOverlayTick(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        this.xMouse = (float) mouseX;
        this.yMouse = (float) mouseY;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
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

        // Trinket 指示器点击
        if (TrinketHelper.isLoaded() && trinketSourceSlot >= 0 && !activeTrinketSlots.isEmpty()) {
            int ex = (int) ev.x();
            int ey = (int) ev.y();
            int ti = getTrinketIndicatorAt(ex, ey);
            DebugLogger.info("TrinketClick", "click at (%d,%d) trinketSrc=%d indCount=%d ti=%d",
                    ex, ey, trinketSourceSlot, activeTrinketSlots.size(), ti);
            if (ti >= 0 && ti < activeTrinketSlots.size()) {
                DebugLogger.info("TrinketClick", "-> handling click on indicator %d", ti);
                // 先清理残留手势状态，避免与指示器交互互相干扰
                resetGesture();
                TrinketHelper.TrinketSlotInfo tsi = activeTrinketSlots.get(ti);
                if (ev.hasShiftDown() && ev.button() == 0) {
                    // Shift+左键点击指示器：快捷移动槽位物品到物品栏
                    trinketQuickMove(tsi);
                } else {
                    trinketHandleClick(tsi, ev.button());
                }
                return true;
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
                    LocalPlayer player = this.minecraft.player;
                    if (player != null) {
                        this.menu.clicked(gestureOriginSlot, 0, ContainerInput.QUICK_MOVE, player);
                    }
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
        LocalPlayer player = this.minecraft.player;
        if (player == null)
            return;
        if (gestureMode == 2) {
            // Shift 批量拖拽：快速转移
            this.menu.clicked(slotIndex, 0, ContainerInput.QUICK_MOVE, player);
            return;
        }
        if (gestureMode == 1) {
            // 合并拖拽：将经过的同种物品合并到光标
            ItemStack ca = this.menu.getCarried();
            if (ca.isEmpty())
                return;
            this.menu.clicked(slotIndex, 0, ContainerInput.PICKUP, player);
        }
    }

    private void resetGesture() {
        gestureMode = 0;
        gestureProcessed.clear();
        gestureSlots.clear();
    }

    // ========== YZUI 面板绘制方法 ==========

    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillRoundedRect(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                PANEL_RADIUS, PANEL_BG);
    }

    /** 在 2×2 合成格与输出槽之间绘制原版交易箭头贴图。 */
    private void drawCraftArrow(GuiGraphicsExtractor g) {
        // 箭头位置：合成格右边缘 (left+132) 与输出槽左边缘 (left+154) 之间
        int ax = this.leftPos + 133;
        int ay = this.topPos + 30;
        g.blitSprite(RenderPipelines.GUI_TEXTURED,
                Identifier.parse("container/villager/trade_arrow"),
                ax, ay, 20, 16);
    }

    private void drawPlayerModel(@NonNull GuiGraphicsExtractor g) {
        var player = this.minecraft != null ? this.minecraft.player : null;
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g,
                    this.leftPos + ENTITY_X,
                    this.topPos + ENTITY_Y,
                    this.leftPos + ENTITY_W,
                    this.topPos + ENTITY_H,
                    ENTITY_SIZE,
                    ENTITY_Y_OFFSET,
                    this.xMouse,
                    this.yMouse,
                    player);
        }
    }

    // ========== 槽位背景绘制 ==========

    private void drawSlotBackgrounds(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive())
                continue;

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

    // ========== Trinkets 悬停提示 ==========

    private void trinketOverlayTick(GuiGraphicsExtractor g, int mx, int my) {
        if (!TrinketHelper.isLoaded() || this.minecraft == null || this.minecraft.player == null)
            return;

        Slot hitSlot = getSlotAt(mx, my);
        boolean onSource = hitSlot != null;

        boolean onIndicator = false;
        if (trinketSourceSlot >= 0 && !activeTrinketSlots.isEmpty()) {
            Slot srcSlot = trinketSourceSlot >= 0 && trinketSourceSlot < this.menu.slots.size()
                    ? this.menu.slots.get(trinketSourceSlot) : null;
            if (srcSlot != null) {
                int baseX = srcSlot.x + this.leftPos + 16 + 2;
                int baseY = srcSlot.y + this.topPos;
                int indEndX = baseX + activeTrinketSlots.size() * 18;
                onIndicator = mx >= baseX && mx < indEndX && my >= baseY && my < baseY + 16;
            }
        }

        if (onSource) {
            int hitIdx = this.menu.slots.indexOf(hitSlot);
            if (hitIdx != trinketSourceSlot && !onIndicator) {
                activeTrinketSlots = TrinketHelper.getSlotsAttachedTo(this.minecraft.player, hitSlot);
                trinketSourceSlot = activeTrinketSlots.isEmpty() ? -1 : hitIdx;
                if (!activeTrinketSlots.isEmpty()) {
                    DebugLogger.info("TrinketOverlay", "Show %d indicators for slot %d",
                            activeTrinketSlots.size(), hitIdx);
                }
            }
        } else if (!onIndicator) {
            activeTrinketSlots = List.of();
            trinketSourceSlot = -1;
        }

        if (!activeTrinketSlots.isEmpty() && trinketSourceSlot >= 0 && trinketSourceSlot < this.menu.slots.size()) {
            Slot src = this.menu.slots.get(trinketSourceSlot);
            int baseX = src.x + this.leftPos + 16 + 2;
            int baseY = src.y + this.topPos;
            int indW = activeTrinketSlots.size() * 18 - 2;
            int sr = Math.min(4, Math.min(indW / 2, 10));
            g.fill(baseX - 2 + sr, baseY - 2, baseX - 2 + indW + 4 - sr, baseY - 2 + 16 + 4, 0x50000000);
            g.fill(baseX - 2, baseY - 2 + sr, baseX - 2 + sr, baseY - 2 + 16 + 4 - sr, 0x50000000);
            g.fill(baseX - 2 + indW + 4 - sr, baseY - 2 + sr, baseX - 2 + indW + 4, baseY - 2 + 16 + 4 - sr, 0x50000000);
            for (int i = 0; i < activeTrinketSlots.size(); i++) {
                int sx = baseX + i * 18;
                TrinketHelper.TrinketSlotInfo slotInfo = activeTrinketSlots.get(i);
                ItemStack ti = slotInfo.stack();
                if (ti.isEmpty()) {
                    Identifier iconId = TrinketHelper.getSlotIcon(slotInfo);
                    if (iconId != null) {
                        g.fill(sx, baseY, sx + 16, baseY + 16, 0xFFFFFFFF);
                        g.blitSprite(RenderPipelines.GUI_TEXTURED, Objects.requireNonNull(iconId), sx, baseY, 16, 16);
                    } else {
                        g.fill(sx, baseY, sx + 16, baseY + 16, 0xFFFFFFFF);
                    }
                } else {
                    g.fill(sx, baseY, sx + 16, baseY + 16, 0xFFFFFFFF);
                    g.fakeItem(ti, sx, baseY);
                }
            }
        }
    }

    private int getTrinketIndicatorAt(int mx, int my) {
        if (trinketSourceSlot < 0 || activeTrinketSlots.isEmpty()
                || trinketSourceSlot >= this.menu.slots.size())
            return -1;
        Slot src = this.menu.slots.get(trinketSourceSlot);
        int baseX = src.x + this.leftPos + 16 + 2;
        int baseY = src.y + this.topPos;
        int count = activeTrinketSlots.size();
        if (mx < baseX || mx >= baseX + count * 18 || my < baseY || my >= baseY + 16)
            return -1;
        return (mx - baseX) / 18;
    }

    private void trinketHandleClick(TrinketHelper.TrinketSlotInfo tsi, int button) {
        if (this.minecraft == null || this.minecraft.player == null)
            return;
        LocalPlayer player = this.minecraft.player;
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack slotStack = TrinketHelper.getSlotStack(tsi);
        DebugLogger.info("TrinketClick", "Click on %s[%d] button=%d carried=%s slot=%s",
                tsi.groupKey(), tsi.slotIndex(), button,
                carried.isEmpty() ? "empty" : carried.getHoverName().getString(),
                slotStack.isEmpty() ? "empty" : slotStack.getHoverName().getString());
        byte action;
        if (button == 0) {
            if (carried.isEmpty()) {
                action = TrinketInteractPayload.ACTION_TAKE;
            } else {
                // 有携带物：如果槽位有物品则交换，否则放入
                action = slotStack.isEmpty() ? TrinketInteractPayload.ACTION_PLACE : TrinketInteractPayload.ACTION_SWAP;
            }
        } else {
            return;
        }
        // 携带 cursor 一并上报：服务端 carried 与客户端不同步（如刚点击拿起物品、
        // 点击数据包尚未处理）时以 cursor 兜底，避免操作被静默丢弃。
        ClientPlayNetworking.send(new TrinketInteractPayload(tsi.groupKey(), tsi.slotIndex(), action, carried));
        // 本地预览：立即更新鼠标物品与槽位显示（服务端权威广播到达后最终校正）。
        // 注意只修改客户端本地状态，服务端仍以权威数据为准。
        try {
            if (action == TrinketInteractPayload.ACTION_PLACE && !carried.isEmpty()) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                TrinketHelper.setSlotStack(tsi, carried.copy());
            } else if (action == TrinketInteractPayload.ACTION_TAKE && !slotStack.isEmpty()) {
                player.containerMenu.setCarried(slotStack.copy());
                TrinketHelper.setSlotStack(tsi, ItemStack.EMPTY);
            } else if (action == TrinketInteractPayload.ACTION_SWAP && !carried.isEmpty()) {
                player.containerMenu.setCarried(slotStack.copy());
                TrinketHelper.setSlotStack(tsi, carried.copy());
            }
        } catch (Exception e) {
            DebugLogger.warn("TrinketClick", "Local preview failed: %s", e.getMessage());
        }
        // 让下一次 tick 重新查询
        trinketSourceSlot = -1;
        activeTrinketSlots = List.of();
    }

    /**
     * Shift+左键点击指示器：将饰品槽内物品快捷移动到主物品栏/快捷栏（槽位 9..45，不含副手）。
     * <p>
     * 直接复用标准 menu.clicked(QUICK_MOVE) 点击：客户端与服务端均由 Trinkets 的
     * InventoryMenuMixin.quickMove 拦截 trinket 槽索引并执行
     * {@code moveItemStackTo(stack, 9, 45, false)}，与原版 shift+点击 trinket 槽行为完全一致，
     * 槽位内容与物品栏槽位由标准菜单同步广播校正。
     */
    private void trinketQuickMove(TrinketHelper.TrinketSlotInfo tsi) {
        if (this.minecraft == null || this.minecraft.player == null)
            return;
        LocalPlayer player = this.minecraft.player;
        ItemStack slotStack = TrinketHelper.getSlotStack(tsi);
        if (slotStack.isEmpty()) {
            DebugLogger.info("TrinketClick", "Shift-click on %s[%d]: slot empty, nothing to move",
                    tsi.groupKey(), tsi.slotIndex());
            return;
        }
        // 在 menu.slots 中定位该 trinket 槽对应的真实 Slot 索引（Trinkets 注入在菜单末尾）
        int slotIdx = -1;
        var slots = player.containerMenu.slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s instanceof TrinketSlot ts && ts.getAccess() != null
                    && ts.getAccess().getAsIdentifierPath().equals(tsi.groupKey() + "/" + tsi.slotIndex())) {
                slotIdx = i;
                break;
            }
        }
        if (slotIdx < 0) {
            DebugLogger.info("TrinketClick", "Shift-click on %s[%d]: matching menu slot not found",
                    tsi.groupKey(), tsi.slotIndex());
            return;
        }
        DebugLogger.info("TrinketClick", "Shift-click on %s[%d] -> QUICK_MOVE to inventory (menu slot %d)",
                tsi.groupKey(), tsi.slotIndex(), slotIdx);
        this.menu.clicked(slotIdx, 0, ContainerInput.QUICK_MOVE, player);
        // 让下一次 tick 重新查询
        trinketSourceSlot = -1;
        activeTrinketSlots = List.of();
    }
}
