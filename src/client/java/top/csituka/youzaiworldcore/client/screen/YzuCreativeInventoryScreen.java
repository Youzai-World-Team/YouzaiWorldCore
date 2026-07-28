package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ContainerInput;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;
import top.csituka.youzaiworldcore.util.TrinketHelper;

@SuppressWarnings("null")
public class YzuCreativeInventoryScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("YzuCreativeInventoryScreen");

    // layout
    private static final int PW = 356, PH = 168, PR = 6;
    private static final int TY = 4, TW = 28, TH = 22, TG = 2, TR = 4;
    private static final int SX = 250, SY = 6, SW = 96, SH = 16; // 搜索框右缘 ≤ 物品栏右缘 (x=346)
    private static final int GX = 10, GY = 34, SS = 16, SG = 2, COLS = 9, VROWS = 7;
    private static final int SCROLL_X = 174, SCROLL_Y = 34, SCROLL_W = 4, SCROLL_H = 124; // 网格右侧滚动条
    // 右侧面板：玩家模型（左上）+ 装备+副手（右上）/ 生存物品栏3×9（中）/ 快捷栏（底，与网格底对齐）
    private static final int PM_X = 184, PM_Y = 34, PM_W = 30, PM_H = 50, PM_SCALE = 22;
    private static final int ARM_X = 222, ARM_Y = 34; // 装备 2×2（slots 5-8）
    private static final int OFF_X = 264, OFF_Y = 46; // 副手槽（slot 45）
    private static final int INV_X = 184, INV_Y = 86, INV_ROWS = 3, INV_COLS = 9; // 生存物品栏 3×9（slots 9-35）
    private static final int HB_X = 184, HB_Y = 140; // 1×9 快捷栏（底部对齐网格底 y=158）
    private static final int MAX_VIS = 6;

    // colors
    private static final int BG = 0x80FFFFFF, SC = 0x40FFFFFF, SHV = 0x60FFFFFF, TA = 0x90FFFFFF;
    private static final int[] TC = { 0x60CC8866, 0x6099CCFF, 0x6066AA44, 0x60AA66CC, 0x60FF6644, 0x604488CC,
            0x60FF8844, 0x60FFCC44, 0x60CCAACC, 0x60FFAAAA, 0x60FF66AA };

    private static final String YZWC_SEARCH_HINT = "搜索物品...";

    /** 跨会话持久化的搜索文本 */
    private static String lastSearch = "";

    private final @NonNull Player player;
    private final List<CreativeModeTab> tabs = new ArrayList<>();
    private CreativeModeTab selTab;
    private EditBox searchBox;
    private NonNullList<ItemStack> allItems = NonNullList.create();
    private List<ItemStack> tabItems;
    private final List<Integer> vis = new ArrayList<>();
    private float soff;
    private float xm, ym;
    private int lp, tp;
    private int tabPage;
    // 拖拽 / pending 状态
    private boolean isDragInProgress;
    private int dragButton;
    private boolean pendingDrag;
    private int dragOriginSlot;
    private final List<Integer> draggedSlots = new ArrayList<>();
    /** 0=无手势, 1=合并拖拽（左键有物品拖过相同物品），2=Shift批量拖拽（Shift+左键拖过任意物品） */
    private int yzwcDragMode;
    /** 手势拖拽中已处理过的槽位（避免重复处理） */
    private final Set<Integer> processedSlots = new HashSet<>();
    // 双击检测
    private long lastClickTime;
    private int lastClickSlot = -1;

    public YzuCreativeInventoryScreen(Player player) {
        super(Component.translatable("container.crafting"));
        this.player = player;
    }

    @Override
    protected void init() {
        lp = (width - PW) / 2;
        tp = (height - PH) / 2;
        rebuildTabs();
        // 强制构建 Tab 物品列表（原版 CreativeModeInventoryScreen.init() 中由 tryRebuildTabContents
        // 完成，
        // 但我们替换了原版屏幕阻止了该调用，因此需要手动触发。）
        forceBuildTabContents();
        populateAll();
        initSearch();

        // 恢复上次搜索文本，或显示第一个分类
        if (!lastSearch.isEmpty()) {
            searchBox.setValue(lastSearch);
            selTab = null; // 搜索模式
        } else {
            selTab = tabs.isEmpty() ? null : tabs.get(0);
        }
        rebuildVis();

        super.init();
        LOG.info("init lp={} tp={} tabs={} allItems={} vis={}", lp, tp, tabs.size(), allItems.size(), vis.size());
    }

    private void initSearch() {
        searchBox = new EditBox(font, lp + SX, tp + SY, SW, SH, Component.translatable("gui.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setVisible(true);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setHint(Component.literal(YZWC_SEARCH_HINT));
        searchBox.setResponder(this::onSearch);
        addRenderableWidget(searchBox);
    }

    private void rebuildTabs() {
        tabs.clear();
        for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
            if (t.getType() == CreativeModeTab.Type.CATEGORY)
                tabs.add(t);
        }
        selTab = tabs.isEmpty() ? null : tabs.get(0);
    }

    /** 强制构建所有 Tab 的 displayItems（原版在 CreativeModeInventoryScreen.init 中完成）。 */
    private void forceBuildTabContents() {
        try {
            var level = player.level();
            var features = level.enabledFeatures();
            boolean op = player.isCreative() || player.isSpectator();
            var holders = level.registryAccess(); // RegistryAccess implements HolderLookup.Provider
            var params = new CreativeModeTab.ItemDisplayParameters(features, op, holders);
            for (CreativeModeTab tab : tabs) {
                if (tab.getDisplayItems() == null || tab.getDisplayItems().isEmpty()) {
                    tab.buildContents(params);
                }
            }
            LOG.debug("forceBuildTabContents done for {} tabs", tabs.size());
        } catch (Exception e) {
            LOG.warn("forceBuildTabContents failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("null")
    private void populateAll() {
        allItems.clear();
        BuiltInRegistries.ITEM.stream().forEach(item -> {
            ItemStack st = new ItemStack(item);
            if (!st.isEmpty())
                allItems.add(st);
        });
    }

    private void rebuildVis() {
        vis.clear();
        if (selTab == null) {
            tabItems = null;
            // 搜索/全部模式：从 allItems 中按搜索文本过滤
            String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
            for (int i = 0; i < allItems.size(); i++) {
                ItemStack s = allItems.get(i);
                if (s.isEmpty())
                    continue;
                if (q.isEmpty() || s.getHoverName().getString().toLowerCase().contains(q))
                    vis.add(i);
            }
        } else {
            // 分类模式：直接使用该分类原版的物品列表及其排列顺序（不通过 Set 过滤，保留 tab 内部顺序）
            Collection<ItemStack> tabColl = selTab.getDisplayItems();
            tabItems = tabColl != null ? List.copyOf(tabColl) : List.of();
            for (int i = 0; i < tabItems.size(); i++) {
                if (!tabItems.get(i).isEmpty())
                    vis.add(i);
            }
        }
        soff = 0;
    }

    private void onSearch(String q) {
        lastSearch = q;
        if (!q.trim().isEmpty()) {
            selTab = null; // 键入搜索 → 关闭分类模式
        }
        // 清空搜索时保持 selTab 不变：留在全部模式显示所有物品，或留在当前分类
        rebuildVis();
    }

    /** 点击 Tab 时调用：清除搜索框、切换到分类模式 */
    private void selectCategory(CreativeModeTab tab) {
        selTab = tab;
        searchBox.setValue(""); // 触发 onSearch("") → selTab 保持 tab → rebuildVis 已调用
        lastSearch = "";
    }

    /** 根据当前模式（分类/全部）返回 vis 索引对应的 ItemStack */
    private ItemStack getItemForVis(int vi) {
        if (vi < 0)
            return ItemStack.EMPTY;
        if (selTab != null && tabItems != null) {
            if (vi < tabItems.size())
                return tabItems.get(vi);
        } else {
            if (vi < allItems.size())
                return allItems.get(vi);
        }
        return ItemStack.EMPTY;
    }

    private void renderSlot(GuiGraphicsExtractor g, ItemStack st, int x, int y, int seed) {
        if (st != null && !st.isEmpty()) {
            g.item(st, x, y, seed);
            g.itemDecorations(font, st, x, y, null);
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mx, int my, float pt) {
        fillR(g, lp, tp, PW, PH, PR, BG);
        drawTabs(g, mx, my);
        drawGrid(g, mx, my);
        drawScroll(g, mx, my);

        var slots = player.inventoryMenu.slots;
        // 玩家模型 3D 渲染（原版 InventoryScreen 风格，鼠标跟随旋转）
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, lp + PM_X, tp + PM_Y, lp + PM_X + PM_W,
                    tp + PM_Y + PM_H, PM_SCALE, 0.0625f, xm, ym, player);
        }

        // 装备 2×2（slots 5-8：helmet/chest/legs/boots）
        int ax = lp + ARM_X, ay = tp + ARM_Y;
        for (int r = 0; r < 2; r++)
            for (int c = 0; c < 2; c++) {
                int sx = ax + c * (SS + SG), sy = ay + r * (SS + SG);
                fillR(g, sx, sy, SS, SS, 3, mx >= sx && mx < sx + SS && my >= sy && my < sy + SS ? SHV : SC);
                int slotIdx = 5 + r * 2 + c;
                ItemStack ast = slots.get(slotIdx).getItem();
                // 空槽绘制原版占位图标（通过 Slot.getNoItemIcon() 获取对应 sprite）
                if (ast.isEmpty()) {
                    Identifier iconId = slots.get(slotIdx).getNoItemIcon();
                    if (iconId != null) {
                        g.blitSprite(RenderPipelines.GUI_TEXTURED, iconId, sx, sy, SS, SS);
                    }
                }
                renderSlot(g, ast, sx, sy, 200 + slotIdx);
                ItemBorderRenderer.renderBorder(g, sx, sy, ast);
            }

        // 副手（slot 45）
        int ox = lp + OFF_X, oy = tp + OFF_Y;
        fillR(g, ox, oy, SS, SS, 3, mx >= ox && mx < ox + SS && my >= oy && my < oy + SS ? SHV : SC);
        ItemStack ost = slots.get(45).getItem();
        if (ost.isEmpty()) {
            Identifier offId = slots.get(45).getNoItemIcon();
            if (offId != null) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, offId, ox, oy, SS, SS);
            }
        }
        renderSlot(g, ost, ox, oy, 210);
        ItemBorderRenderer.renderBorder(g, ox, oy, ost);

        // 生存物品栏 3×9（slots 9-35）
        int invX = lp + INV_X, invY = tp + INV_Y;
        for (int r = 0; r < INV_ROWS; r++)
            for (int c = 0; c < INV_COLS; c++) {
                int sx = invX + c * (SS + SG), sy = invY + r * (SS + SG);
                fillR(g, sx, sy, SS, SS, 3, mx >= sx && mx < sx + SS && my >= sy && my < sy + SS ? SHV : SC);
                ItemStack ist = slots.get(9 + r * INV_COLS + c).getItem();
                renderSlot(g, ist, sx, sy, 220 + r * INV_COLS + c);
                ItemBorderRenderer.renderBorder(g, sx, sy, ist);
            }

        // 快捷栏 1×9（slots 36-44）
        int hbx = lp + HB_X, hby = tp + HB_Y;
        for (int c = 0; c < 9; c++) {
            int sx = hbx + c * (SS + SG);
            fillR(g, sx, hby, SS, SS, 3, mx >= sx && mx < sx + SS && my >= hby && my < hby + SS ? SHV : SC);
            ItemStack hst = slots.get(36 + c).getItem();
            renderSlot(g, hst, sx, hby, c);
            ItemBorderRenderer.renderBorder(g, sx, hby, hst);
        }

        super.extractRenderState(g, mx, my, pt);
        xm = mx;
        ym = my;

        ItemStack carried = minecraft.player.containerMenu.getCarried();
        // 先渲染拖拽预览（底层），再渲染鼠标物品（顶层）
        drawDragPreview(g, carried);
        if (!carried.isEmpty()) {
            // 拖拽中实时计算鼠标上的预期剩余数量
            int cursorCount = carried.getCount();
            if (isDragInProgress) {
                int vc = getDragValidCount(carried);
                if (vc > 0) {
                    if (dragButton == 0) {
                        // 左键平均分：全部分配完毕，余数 = total % validCount
                        int total = carried.getCount();
                        cursorCount = total % vc;
                    } else {
                        // 右键每格1个：每格消耗1
                        cursorCount = Math.max(0, carried.getCount() - vc);
                    }
                }
            }
            ItemStack cursorDisplay = cursorCount == carried.getCount() ? carried : carried.copy();
            if (cursorDisplay != carried) cursorDisplay.setCount(cursorCount);
            g.item(cursorDisplay, mx - 8, my - 8, 0);
            if (!cursorDisplay.isEmpty()) g.itemDecorations(font, cursorDisplay, mx - 8, my - 8, null);
        }
        if (carried.isEmpty()) {
            int hov = getHoveredGridIndex(mx, my);
            if (hov >= 0 && hov < vis.size()) {
                int vi = vis.get(hov);
                ItemStack st = getItemForVis(vi);
                if (!st.isEmpty())
                    g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, st), st.getTooltipImage(), mx,
                            my, null);
            }
        }

        // Trinkets 悬停提示：鼠标放在关联的容器槽位（盔甲/副手）上时，绘制饰品槽位指示器
        if (TrinketHelper.isLoaded()) {
            int hoverSlot = getRightPanelSlotAt(mx, my);
            if (hoverSlot >= 0 && hoverSlot < slots.size()) {
                Slot slotObj = slots.get(hoverSlot);
                java.util.List<TrinketHelper.TrinketSlotInfo> attached =
                        TrinketHelper.getSlotsAttachedTo(player, slotObj);
                if (!attached.isEmpty()) {
                    // 在悬停槽位右上侧显示一排小槽位指示器
                    int baseX = getSlotScreenX(hoverSlot);
                    int baseY = getSlotScreenY(hoverSlot);
                    int indicatorX = baseX + SS + SG;
                    int indicatorY = baseY;
                    for (int i = 0; i < attached.size(); i++) {
                        int sx = indicatorX + i * (SS + SG);
                        // 指示器背景（浅色半透明）
                        fillR(g, sx, indicatorY, SS, SS, 3, 0x70FFFFFF);
                        ItemStack ti = attached.get(i).stack();
                        if (!ti.isEmpty()) {
                            g.fakeItem(ti, sx, indicatorY);
                        }
                    }
                }
            }
        }
    }

    /** 拖拽中在被拖槽位上渲染物品预览（含数字）。异种/满格跳过，数量按有效格均分计算。 */
    private void drawDragPreview(GuiGraphicsExtractor g, ItemStack carried) {
        if (!isDragInProgress || carried.isEmpty() || draggedSlots.isEmpty())
            return;
        var slots = player.inventoryMenu.slots;
        // 先过滤出有效格（同种或空格的允许插入）
        List<Integer> validSlots = new ArrayList<>();
        for (int si : draggedSlots) {
            if (si < 0 || si >= slots.size())
                continue;
            ItemStack existing = slots.get(si).getItem();
            if (existing.isEmpty()) {
                validSlots.add(si);
                continue;
            }
            if (ItemStack.isSameItemSameComponents(carried, existing)
                    && existing.getCount() < existing.getMaxStackSize()) {
                validSlots.add(si);
            }
        }
        int validCount = validSlots.size();
        if (validCount == 0)
            return;

        for (int si : validSlots) {
            int idx = validSlots.indexOf(si);
            ItemStack existing = slots.get(si).getItem();
            int previewCount = existing.getCount();
            if (dragButton == 0) {
                int avg = carried.getCount() / validCount;
                int rem = carried.getCount() % validCount;
                int add = avg + (idx < rem ? 1 : 0);
                previewCount += add;
            } else {
                previewCount += 1;
            }
            ItemStack preview = existing.isEmpty() ? carried.copy() : existing.copy();
            preview.setCount(Math.min(previewCount, preview.getMaxStackSize()));
            if (!preview.isEmpty()) {
                int sx = getSlotScreenX(si), sy = getSlotScreenY(si);
                g.item(preview, sx, sy, 9999);
                g.itemDecorations(font, preview, sx, sy, null);
            }
        }
    }

    private int getHoveredGridIndex(int mx, int my) {
        int sx = lp + GX, sy = tp + GY, rows = (vis.size() + COLS - 1) / COLS, maxS = Math.max(0, rows - VROWS),
                sr = Math.min(Math.round(soff), maxS);
        for (int r = 0; r < VROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int idx = (sr + r) * COLS + c;
                if (idx >= vis.size())
                    break;
                int gx = sx + c * (SS + SG), gy = sy + r * (SS + SG);
                if (mx >= gx && mx < gx + SS && my >= gy && my < gy + SS)
                    return idx;
            }
        return -1;
    }

    private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
        int mv = Math.min(MAX_VIS, tabs.size()), pc = Math.max(1, (tabs.size() + mv - 1) / mv);
        if (tabPage >= pc)
            tabPage = pc - 1;
        int st = tabPage * mv, en = Math.min(st + mv, tabs.size());
        int arrowY = tp + TY;

        // 左翻页（始终显示）
        int leftX = lp + 3;
        boolean canLeft = tabPage > 0;
        boolean lh = canLeft && mx >= leftX && mx < leftX + TW && my >= arrowY && my < arrowY + TH;
        fillR(g, leftX, arrowY, TW, TH, TR, lh ? 0x80FFFFFF : (canLeft ? 0x60FFFFFF : 0x30FFFFFF));
        int ltx = leftX + (TW - font.width("<")) / 2;
        int lty = arrowY + (TH - font.lineHeight) / 2;
        g.text(font, "<", ltx, lty, canLeft ? 0xCCFFFFFF : 0x60FFFFFF, true);

        // Tab
        int tx = leftX + TW + TG;
        for (int i = st; i < en; i++) {
            CreativeModeTab t = tabs.get(i);
            boolean sel = selTab != null && t == selTab;
            boolean h = mx >= tx && mx < tx + TW && my >= arrowY && my < arrowY + TH;
            fillR(g, tx, arrowY, TW, TH, TR, sel ? TA : (h ? 0x70FFFFFF : TC[i % TC.length]));
            ItemStack icon = t.getIconItem();
            if (!icon.isEmpty())
                g.item(icon, tx + (TW - 16) / 2, arrowY + (TH - 16) / 2, 0);
            if (h)
                g.setTooltipForNextFrame(font, t.getDisplayName(), mx, my);
            tx += TW + TG;
        }

        // 右翻页（固定位置：最大 6 个 Tab 尾部之后）
        int rightX = lp + 3 + TW + TG + MAX_VIS * (TW + TG);
        boolean canRight = tabPage < pc - 1;
        boolean rh = canRight && mx >= rightX && mx < rightX + TW && my >= arrowY && my < arrowY + TH;
        fillR(g, rightX, arrowY, TW, TH, TR, rh ? 0x80FFFFFF : (canRight ? 0x60FFFFFF : 0x30FFFFFF));
        int rtx = rightX + (TW - font.width(">")) / 2;
        int rty = arrowY + (TH - font.lineHeight) / 2;
        g.text(font, ">", rtx, rty, canRight ? 0xCCFFFFFF : 0x60FFFFFF, true);
    }

    private void drawGrid(GuiGraphicsExtractor g, int mx, int my) {
        if (vis.isEmpty())
            return;
        int sx = lp + GX, sy = tp + GY, rows = (vis.size() + COLS - 1) / COLS, maxS = Math.max(0, rows - VROWS),
                sr = Math.min(Math.round(soff), maxS);
        for (int r = 0; r < VROWS; r++)
            for (int c = 0; c < COLS; c++) {
                int idx = (sr + r) * COLS + c;
                if (idx >= vis.size())
                    break;
                int gx = sx + c * (SS + SG), gy = sy + r * (SS + SG);
                boolean h = mx >= gx && mx < gx + SS && my >= gy && my < gy + SS;
                fillR(g, gx, gy, SS, SS, 3, h ? SHV : SC);
                int vi = vis.get(idx);
                ItemStack st = getItemForVis(vi);
                if (!st.isEmpty()) {
                    g.item(st, gx, gy, vi);
                    g.itemDecorations(font, st, gx, gy, null);
                    ItemBorderRenderer.renderBorder(g, gx, gy, st);
                }
            }
    }

    private void drawScroll(GuiGraphicsExtractor g, int mx, int my) {
        int rows = (vis.size() + COLS - 1) / COLS, maxS = Math.max(0, rows - VROWS);
        int bx = lp + SCROLL_X, by = tp + SCROLL_Y;
        // 背景槽
        g.fill(bx, by, bx + SCROLL_W, by + SCROLL_H, 0x30FFFFFF);
        if (maxS <= 0)
            return; // 无可滚动时不绘制 thumb
        int thumbH = Math.max(6, (int) ((float) VROWS / rows * SCROLL_H));
        int trackH = SCROLL_H - thumbH;
        int thumbY = by + (int) ((float) Math.round(soff) / maxS * trackH);
        boolean hov = mx >= bx - 1 && mx < bx + SCROLL_W + 1 && my >= by && my < by + SCROLL_H;
        g.fill(bx, thumbY, bx + SCROLL_W, thumbY + thumbH, hov ? 0xCCFFFFFF : 0x80FFFFFF);
    }

    private void pickupItem(ItemStack stack, int count) {
        stack = stack.copy();
        stack.setCount(Math.min(count, stack.getMaxStackSize()));
        try {
            minecraft.player.containerMenu.setCarried(stack);
        } catch (Exception e) {
            if (!player.getInventory().add(stack))
                player.drop(stack, false);
        }
    }

    private void clearCarried() {
        minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    /** 创造模式下的双击收集：手动合并所有同种物品到光标，避免标准 clicked(PICKUP_ALL) 协议导致服务端物品残留。 */
    private void handlePickupAll(int sourceSlot) {
        var container = minecraft.player.containerMenu;
        var slots = player.inventoryMenu.slots;
        var gameMode = minecraft.gameMode;

        // 确定目标类型：优先用光标物品（首次点击已取到手中），若为空则从触发槽取
        ItemStack carried = container.getCarried();
        ItemStack type;
        if (!carried.isEmpty()) {
            type = carried;
        } else {
            type = slots.get(sourceSlot).getItem();
            if (type.isEmpty()) return;
        }

        int total = carried.isEmpty() ? 0 : carried.getCount();
        int maxStack = type.getMaxStackSize();
        if (total >= maxStack) return;

        // 扫描右侧面板所有槽位（5-45：装备/物品栏/热栏/副手），收集同种物品
        for (int si = 5; si <= 45 && total < maxStack; si++) {
            if (si == sourceSlot) continue;
            ItemStack siStack = slots.get(si).getItem();
            if (siStack.isEmpty() || !ItemStack.isSameItemSameComponents(type, siStack))
                continue;
            int take = Math.min(maxStack - total, siStack.getCount());
            if (take <= 0) continue;
            total += take;
            siStack.shrink(take);
            slots.get(si).setChanged();
            gameMode.handleCreativeModeItemAdd(siStack.isEmpty() ? ItemStack.EMPTY : siStack, si);
        }

        // 仅客户端设置光标（创造模式下光标物品是虚拟的，关闭 GUI 即丢弃，不同步 -1 避免服务端误解）
        ItemStack result = type.copy();
        result.setCount(total);
        container.setCarried(result);
    }

    /** 处理生存背包的真实容器槽位点击。右键（有携带物）手动+仅槽位同步，避免服务端携带物处理导致丢物品。 */
    private void handleSlotClick(int slotIndex, int button) {
        if (player.isCreative()) {
            var gameMode = minecraft.gameMode;
            var slot = player.inventoryMenu.slots.get(slotIndex);
            ItemStack si = slot.getItem(), ca = minecraft.player.containerMenu.getCarried();

            // 中键克隆
            if (button == 2) {
                if (!si.isEmpty()) {
                    ItemStack clone = si.copy();
                    clone.setCount(clone.getMaxStackSize());
                    minecraft.player.containerMenu.setCarried(clone);
                }
                return;
            }

            if (button == 1) {
                if (ca.isEmpty()) {
                    // 空手右键取半：手动分 + handleCreativeModeItemAdd 同步槽位，避免标准协议在创造模式下失效
                    if (!si.isEmpty()) {
                        int half = (si.getCount() + 1) / 2;
                        ItemStack toCursor = si.copy();
                        toCursor.setCount(half);
                        si.shrink(half);
                        slot.setChanged();
                        minecraft.player.containerMenu.setCarried(toCursor);
                        gameMode.handleCreativeModeItemAdd(si.isEmpty() ? ItemStack.EMPTY : si, slotIndex);
                    }
                } else {
                    // 有携带物右键：手动操作 + 仅同步槽位（不同步携带物，避免服务端创建物品导致丢出）
                    if (!si.isEmpty() && ItemStack.isSameItemSameComponents(ca, si)) {
                        int space = si.getMaxStackSize() - si.getCount();
                        if (space > 0) {
                            si.grow(1);
                            ca.shrink(1);
                        }
                        minecraft.player.containerMenu.setCarried(ca.isEmpty() ? ItemStack.EMPTY : ca);
                        gameMode.handleCreativeModeItemAdd(slot.getItem(), slotIndex);
                    } else if (si.isEmpty()) {
                        ItemStack one = ca.copy();
                        one.setCount(1);
                        slot.set(one);
                        slot.setChanged();
                        ca.shrink(1);
                        minecraft.player.containerMenu.setCarried(ca.isEmpty() ? ItemStack.EMPTY : ca);
                        gameMode.handleCreativeModeItemAdd(one, slotIndex);
                    } // 不同物品：空操作
                }
                return;
            }

            // 左键：手动操作 + handleCreativeModeItemAdd
            if (ca.isEmpty()) {
                if (!si.isEmpty()) {
                    minecraft.player.containerMenu.setCarried(si.copy());
                    slot.set(ItemStack.EMPTY);
                    gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, slotIndex);
                }
            } else {
                if (!si.isEmpty() && ItemStack.isSameItemSameComponents(ca, si)) {
                    int space = si.getMaxStackSize() - si.getCount();
                    int move = Math.min(space, ca.getCount());
                    if (move > 0) {
                        si.grow(move);
                        ca.shrink(move);
                    }
                    minecraft.player.containerMenu.setCarried(ca.isEmpty() ? ItemStack.EMPTY : ca);
                    gameMode.handleCreativeModeItemAdd(slot.getItem(), slotIndex);
                    var nc = minecraft.player.containerMenu.getCarried();
                    if (!nc.isEmpty())
                        gameMode.handleCreativeModeItemAdd(nc.copy(), -1);
                } else {
                    minecraft.player.containerMenu.setCarried(si.copy());
                    slot.set(ca.copy());
                    gameMode.handleCreativeModeItemAdd(ca.copy(), slotIndex);
                    var nc = minecraft.player.containerMenu.getCarried();
                    if (!nc.isEmpty())
                        gameMode.handleCreativeModeItemAdd(nc, -1);
                }
            }
            return;
        }
        // 生存模式（仅作为安全回退，此屏幕仅对创造模式可见）
        var slot = player.inventoryMenu.slots.get(slotIndex);
        ItemStack si = slot.getItem(), ca = minecraft.player.containerMenu.getCarried();
        if (ca.isEmpty()) {
            if (!si.isEmpty()) {
                minecraft.player.containerMenu.setCarried(si.copy());
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
            }
            return;
        }
        if (!si.isEmpty() && ItemStack.isSameItemSameComponents(ca, si)) {
            int space = si.getMaxStackSize() - si.getCount();
            int move = Math.min(space, ca.getCount());
            if (move > 0) {
                si.grow(move);
                ca.shrink(move);
                minecraft.player.containerMenu.setCarried(ca.isEmpty() ? ItemStack.EMPTY : ca);
            }
        } else {
            minecraft.player.containerMenu.setCarried(si.copy());
            slot.set(ca.copy());
            slot.setChanged();
        }
    }

    private boolean inRect(int mx, int my, int x, int y) {
        return mx >= x && mx < x + SS && my >= y && my < y + SS;
    }

    /** 返回鼠标位置在右侧面板（装备/副手/物品栏/热栏）对应的槽位索引，不是右侧面板区域返回 -1 */
    private int getRightPanelSlotAt(int mx, int my) {
        // 装备 2×2（slots 5-8）
        int ax = lp + ARM_X, ay = tp + ARM_Y;
        for (int r = 0; r < 2; r++)
            for (int c = 0; c < 2; c++) {
                int sx = ax + c * (SS + SG), sy = ay + r * (SS + SG);
                if (inRect(mx, my, sx, sy))
                    return 5 + r * 2 + c;
            }
        // 副手（slot 45）
        if (inRect(mx, my, lp + OFF_X, tp + OFF_Y))
            return 45;
        // 生存物品栏 3×9（slots 9-35）
        int invX = lp + INV_X, invY = tp + INV_Y;
        for (int r = 0; r < INV_ROWS; r++)
            for (int c = 0; c < INV_COLS; c++) {
                int sx = invX + c * (SS + SG), sy = invY + r * (SS + SG);
                if (inRect(mx, my, sx, sy))
                    return 9 + r * INV_COLS + c;
            }
        // 快捷栏 1×9（slots 36-44）
        int hbx = lp + HB_X, hby = tp + HB_Y;
        for (int c = 0; c < 9; c++) {
            if (inRect(mx, my, hbx + c * (SS + SG), hby))
                return 36 + c;
        }
        return -1;
    }

    /** 根据槽位索引返回屏幕 X 坐标（仅右侧面板槽位有效）。 */
    private int getSlotScreenX(int si) {
        int ax = lp + ARM_X;
        int invX = lp + INV_X;
        int hbx = lp + HB_X;
        int offX = lp + OFF_X;
        if (si >= 5 && si <= 8) {
            int c = (si - 5) % 2;
            return ax + c * (SS + SG);
        }
        if (si == 45)
            return offX;
        if (si >= 9 && si <= 35) {
            int idx = si - 9;
            int c = idx % INV_COLS;
            return invX + c * (SS + SG);
        }
        if (si >= 36 && si <= 44) {
            return hbx + (si - 36) * (SS + SG);
        }
        return 0;
    }

    private int getSlotScreenY(int si) {
        int ay = tp + ARM_Y;
        int invY = tp + INV_Y;
        int hby = tp + HB_Y;
        int offY = tp + OFF_Y;
        if (si >= 5 && si <= 8) {
            int r = (si - 5) / 2;
            return ay + r * (SS + SG);
        }
        if (si == 45)
            return offY;
        if (si >= 9 && si <= 35) {
            int idx = si - 9;
            int r = idx / INV_COLS;
            return invY + r * (SS + SG);
        }
        if (si >= 36 && si <= 44) {
            return hby;
        }
        return 0;
    }

    /** 手动收集右侧面板（9-45）中与指定槽位相同类型的物品到光标。 */
    /** 记录拖拽起点（由 {@link #mouseClicked} 设置 pendingDrag，实际拖拽由 mouseDragged 触发）。 */
    private void startDrag(int button, int firstSlot) {
        isDragInProgress = true;
        dragButton = button;
        draggedSlots.clear();
        draggedSlots.add(firstSlot);
    }

    /** 返回当前拖拽中有效的槽位数（空格 或 同类型有空位）。用于实时计算鼠标上的预期剩余数量。 */
    private int getDragValidCount(ItemStack carried) {
        if (carried.isEmpty()) return 0;
        int vc = 0;
        var slots = player.inventoryMenu.slots;
        for (int si : draggedSlots) {
            if (si < 0 || si >= slots.size()) continue;
            ItemStack existing = slots.get(si).getItem();
            if (existing.isEmpty()) { vc++; continue; }
            if (ItemStack.isSameItemSameComponents(carried, existing) && existing.getCount() < existing.getMaxStackSize()) {
                vc++;
            }
        }
        return vc;
    }

    /** 结束拖拽并执行分发。异种物品格跳过，满格跳过。 */
    private void endDrag() {
        if (!isDragInProgress)
            return;
        isDragInProgress = false;

        var gameMode = minecraft.gameMode;
        var container = minecraft.player.containerMenu;
        ItemStack carried = container.getCarried();
        if (carried.isEmpty() || draggedSlots.isEmpty()) {
            draggedSlots.clear();
            return;
        }

        // 过滤出可放置的槽位（空格 或 同类型有空位）
        List<Integer> validSlots = new ArrayList<>();
        for (int si : draggedSlots) {
            var slot = player.inventoryMenu.slots.get(si);
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
                validSlots.add(si);
            } else if (ItemStack.isSameItemSameComponents(carried, existing)) {
                if (existing.getCount() < existing.getMaxStackSize()) {
                    validSlots.add(si);
                }
            }
            // 异种物品 → 跳过
        }
        if (validSlots.isEmpty()) {
            draggedSlots.clear();
            return;
        }

        int slots = validSlots.size();
        if (dragButton == 0) {
            int total = carried.getCount();
            int perSlot = total / slots;
            int remainder = total % slots;
            for (int i = 0; i < slots && !carried.isEmpty(); i++) {
                int si = validSlots.get(i);
                var slot = player.inventoryMenu.slots.get(si);
                ItemStack existing = slot.getItem();
                int count = perSlot + (i < remainder ? 1 : 0);
                if (!existing.isEmpty()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int put = Math.min(space, count);
                    if (put > 0) {
                        existing.grow(put);
                        carried.shrink(put);
                        gameMode.handleCreativeModeItemAdd(existing, si);
                    }
                } else {
                    ItemStack place = carried.copy();
                    place.setCount(count);
                    slot.set(place);
                    slot.setChanged();
                    gameMode.handleCreativeModeItemAdd(place, si);
                    carried.shrink(count);
                }
            }
        } else if (dragButton == 1) {
            for (int si : validSlots) {
                if (carried.isEmpty())
                    break;
                var slot = player.inventoryMenu.slots.get(si);
                ItemStack existing = slot.getItem();
                if (!existing.isEmpty()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    if (space > 0) {
                        existing.grow(1);
                        carried.shrink(1);
                        gameMode.handleCreativeModeItemAdd(existing, si);
                    }
                } else {
                    ItemStack one = carried.copy();
                    one.setCount(1);
                    slot.set(one);
                    slot.setChanged();
                    gameMode.handleCreativeModeItemAdd(one, si);
                    carried.shrink(1);
                }
            }
        }
        container.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        // 不同步光标 (-1)：创造模式下光标虚拟，退出 GUI 自动丢弃
        draggedSlots.clear();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        // 点击搜索框 → 立即切换到全部物品模式（让 EditBox 接管焦点）
        int sbx = lp + SX, sby = tp + SY;
        if (searchBox != null && ev.x() >= sbx && ev.x() < sbx + SW && ev.y() >= sby && ev.y() < sby + SH) {
            if (selTab != null) {
                selTab = null;
                rebuildVis();
            }
            return super.mouseClicked(ev, real);
        }

        // 点击搜索框外任意 GUI 元素 → 搜索框失去焦点
        if (searchBox != null && searchBox.isFocused())
            searchBox.setFocused(false);

        int mv = Math.min(MAX_VIS, tabs.size()), pc = Math.max(1, (tabs.size() + mv - 1) / mv);
        int st = Math.min(tabPage * mv, tabs.size());
        int arrowY = tp + TY;

        // 左翻页
        int leftX = lp + 3;
        if (ev.x() >= leftX && ev.x() < leftX + TW && ev.y() >= arrowY && ev.y() < arrowY + TH) {
            if (ev.button() == 0 && tabPage > 0)
                tabPage--;
            return true;
        }

        // Tab 按钮
        int tx = leftX + TW + TG;
        for (int i = st; i < Math.min(st + mv, tabs.size()); i++) {
            if (ev.x() >= tx && ev.x() < tx + TW && ev.y() >= arrowY && ev.y() < arrowY + TH) {
                if (ev.button() == 0)
                    selectCategory(tabs.get(i));
                return true;
            }
            tx += TW + TG;
        }

        // 右翻页（固定位置）
        int rightX = lp + 3 + TW + TG + MAX_VIS * (TW + TG);
        if (ev.x() >= rightX && ev.x() < rightX + TW && ev.y() >= arrowY && ev.y() < arrowY + TH) {
            if (ev.button() == 0 && tabPage < pc - 1)
                tabPage++;
            return true;
        }

        // 右侧面板（装备/副手/物品栏/热栏）
        int rpSlot = getRightPanelSlotAt((int) ev.x(), (int) ev.y());
        if (rpSlot >= 0) {
            // Shift+左键 → 开始手势拖拽（批量快速转移），Shift+其他按钮 → 单格快速转移
            if (ev.hasShiftDown()) {
                if (ev.button() == 0) {
                    pendingDrag = true;
                    dragButton = 0;
                    dragOriginSlot = rpSlot;
                    yzwcDragMode = 2; // Shift批量拖拽
                    processedSlots.clear();
                    return true;
                }
                player.inventoryMenu.clicked(rpSlot, 0, ContainerInput.QUICK_MOVE, player);
                return true;
            }

            if (ev.button() == 2) {
                handleSlotClick(rpSlot, 2);
                return true;
            }

            // 双击检测（优先于任何点击处理，防止第一次点击清空槽位后 PICKUP_ALL 找不到目标）
            if (ev.button() == 0 || ev.button() == 1) {
                long now = System.currentTimeMillis();
                boolean doubleClick = (now - lastClickTime < 250L) && rpSlot == lastClickSlot;
                lastClickTime = now;
                lastClickSlot = rpSlot;
                if (doubleClick) {
                    handlePickupAll(rpSlot);
                    return true;
                }
            }

            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (!ca.isEmpty() && player.isCreative()) {
                if (ev.button() == 0) {
                    // 有携带物左键：不处理点击（推迟到 endDrag 统一分发），仅设标准拖拽待命
                    pendingDrag = true;
                    dragButton = 0;
                    dragOriginSlot = rpSlot;
                    yzwcDragMode = 0;
                    processedSlots.clear();
                } else {
                    // 有携带物右键：暂不处理，等 mouseReleased（右键分发拖拽）
                    pendingDrag = true;
                    dragButton = 1;
                    dragOriginSlot = rpSlot;
                    yzwcDragMode = 0;
                }
                return true;
            }
            // 空手左键：先标准取物品，再进入合并拖拽待命
            if (ev.button() == 0) {
                handleSlotClick(rpSlot, 0);
                ca = minecraft.player.containerMenu.getCarried();
                if (!ca.isEmpty()) {
                    pendingDrag = true;
                    dragButton = 0;
                    dragOriginSlot = rpSlot;
                    yzwcDragMode = 1; // 合并拖拽（仅刚从空格拿到物品时启用）
                    processedSlots.clear();
                }
                return true;
            }
            // 空手右键：标准点击
            handleSlotClick(rpSlot, ev.button());
            return true;
        }

        // 网格
        int hov = getHoveredGridIndex((int) ev.x(), (int) ev.y());
        if (hov >= 0 && hov < vis.size()) {
            int vi = vis.get(hov);
            ItemStack cl = getItemForVis(vi);
            if (cl.isEmpty())
                return false;
            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (ev.button() == 0) {
                if (ca.isEmpty())
                    pickupItem(cl, 1);
                else
                    clearCarried();
                return true;
            }
            if (ev.button() == 1) {
                if (ca.isEmpty()) {
                    pickupItem(cl, cl.getMaxStackSize());
                } else if (ItemStack.isSameItemSameComponents(ca, cl)) {
                    pickupItem(cl, ca.getCount() + 1);
                } else {
                    if (ca.getCount() > 1) {
                        ca.shrink(1);
                        minecraft.player.containerMenu.setCarried(ca);
                    } else
                        clearCarried();
                }
                return true;
            }
            if (ev.button() == 2) {
                if (ca.isEmpty())
                    pickupItem(cl, cl.getMaxStackSize());
                return true;
            }
        }

        // 点击 GUI 外部（任意按钮）→ 创造模式清手，生存丢出
        boolean inside = ev.x() >= lp && ev.x() < lp + PW && ev.y() >= tp && ev.y() < tp + PH;
        if (!inside) {
            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (!ca.isEmpty()) {
                if (player.isCreative()) {
                    clearCarried();
                    minecraft.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, -1);
                } else {
                    if (ev.button() == 0) {
                        player.drop(ca, false);
                        clearCarried();
                    } else if (ev.button() == 1 && !ca.isEmpty()) {
                        ItemStack one = ca.copy();
                        one.setCount(1);
                        ca.shrink(1);
                        player.containerMenu.setCarried(ca.isEmpty() ? ItemStack.EMPTY : ca);
                        player.drop(one, false);
                    }
                }
                return true;
            }
        }

        return super.mouseClicked(ev, real);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent ev, double dx, double dy) {
        if (pendingDrag && ev.button() == dragButton) {
            // 鼠标真正移动了 → 从 pending 转为实际手势拖拽
            pendingDrag = false;
            startDrag(ev.button(), dragOriginSlot);
            // 仅 Shift 手势（模式 2）需要处理起点槽位（左键已在 mouseClicked 处理）
            if (yzwcDragMode == 2)
                processGestureSlot(dragOriginSlot);
            int si = getRightPanelSlotAt((int) ev.x(), (int) ev.y());
            if (si >= 0 && si != dragOriginSlot && !draggedSlots.contains(si)) {
                draggedSlots.add(si);
                processGestureSlot(si);
            }
            return true;
        }
        if (isDragInProgress && ev.button() == dragButton) {
            int si = getRightPanelSlotAt((int) ev.x(), (int) ev.y());
            if (si >= 0 && !draggedSlots.contains(si)) {
                draggedSlots.add(si);
                processGestureSlot(si);
            }
            return true;
        }
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent ev) {
        // 右键释放：处理 pending 或 drag 后始终消耗
        if (ev.button() == 1) {
            if (pendingDrag && dragButton == 1) {
                pendingDrag = false;
                handleSlotClick(dragOriginSlot, 1);
            } else if (isDragInProgress && dragButton == 1) {
                endDrag();
            }
            yzwcDragMode = 0;
            processedSlots.clear();
            return true;
        }
        if (pendingDrag && ev.button() == dragButton) {
            // 没有拖拽移动 → 视为单格手势点击
            pendingDrag = false;
            if (yzwcDragMode == 2) {
                // Shift 单格快速转移（mouseClicked 未处理，需在此处理）
                player.inventoryMenu.clicked(dragOriginSlot, 0, ContainerInput.QUICK_MOVE, player);
            } else if (yzwcDragMode == 1) {
                // 合并手势未拖拽：点击已在 mouseClicked 中由 handleSlotClick 处理完毕，无需重复
                // no-op
            } else if (ev.button() == 0) {
                // 标准左键（有携带物）未拖拽：mouseClicked 未处理点击（推迟给 endDrag），
                // 未拖拽则在此处理（放置/合并/互换）
                handleSlotClick(dragOriginSlot, 0);
            } else if (ev.button() == 1) {
                // 普通右键（未设手势）：处理点击（mouseClicked 未处理）
                handleSlotClick(dragOriginSlot, 1);
            }
            yzwcDragMode = 0;
            processedSlots.clear();
            return true;
        }
        if (isDragInProgress && ev.button() == dragButton) {
            if (yzwcDragMode > 0) {
                // 手势拖拽结束：物品已在鼠标拖动时实时处理
                isDragInProgress = false;
                draggedSlots.clear();
            } else {
                endDrag();
            }
            yzwcDragMode = 0;
            processedSlots.clear();
            return true;
        }
        return super.mouseReleased(ev);
    }

    /** 处理手势拖拽经过的单个槽位（实时处理，不等到 endDrag）。 */
    private void processGestureSlot(int si) {
        if (yzwcDragMode == 2) {
            // Shift 批量拖拽：QUICK_MOVE
            player.inventoryMenu.clicked(si, 0, ContainerInput.QUICK_MOVE, player);
            return;
        }
        if (yzwcDragMode == 1) {
            // 合并拖拽：将经过的同类型物品合并到光标
            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (ca.isEmpty()) return;
            var slot = player.inventoryMenu.slots.get(si);
            ItemStack siStack = slot.getItem();
            if (siStack.isEmpty() || !ItemStack.isSameItemSameComponents(ca, siStack)) return;
            int space = ca.getMaxStackSize() - ca.getCount();
            if (space <= 0) return;
            int move = Math.min(space, siStack.getCount());
            if (move <= 0) return;
            ca.grow(move);
            siStack.shrink(move);
            slot.setChanged();
            minecraft.player.containerMenu.setCarried(ca);
            if (player.isCreative())
                minecraft.gameMode.handleCreativeModeItemAdd(siStack.isEmpty() ? ItemStack.EMPTY : slot.getItem(), si);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int rows = (vis.size() + COLS - 1) / COLS;
        int maxS = Math.max(0, rows - VROWS);
        if (maxS <= 0)
            return false;
        soff = Math.max(0, Math.min(maxS, soff + (float) -sy * 0.5f));
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent ev) {
        if (ev.key() == 256 || minecraft.options.keyInventory.matches(ev)) {
            minecraft.gui.setScreen(null);
            return true;
        }

        // 获取鼠标悬停的右侧面板槽位（键盘快捷键仅对真实背包槽位有效）
        int hovSlot = getRightPanelSlotAt((int) xm, (int) ym);
        if (hovSlot >= 0) {
            // Q → 丢弃（Ctrl+Q 整组丢弃）
            if (minecraft.options.keyDrop.matches(ev)) {
                player.inventoryMenu.clicked(hovSlot, ev.hasControlDown() ? 1 : 0, ContainerInput.THROW, player);
                return true;
            }
            // F → 交换副手槽（slot 45, button=40）
            if (minecraft.options.keySwapOffhand.matches(ev)) {
                if (player.containerMenu.getCarried().isEmpty()) {
                    player.inventoryMenu.clicked(hovSlot, 40, ContainerInput.SWAP, player);
                }
                return true;
            }
            // 1-9 → 与对应快捷栏槽位交换
            for (int i = 0; i < 9; i++) {
                if (minecraft.options.keyHotbarSlots[i].matches(ev)) {
                    if (player.containerMenu.getCarried().isEmpty()) {
                        player.inventoryMenu.clicked(hovSlot, i, ContainerInput.SWAP, player);
                    }
                    return true;
                }
            }
            // 选取方块键（默认中键，可改为键盘键）→ 克隆
            if (minecraft.options.keyPickItem.matches(ev)) {
                player.inventoryMenu.clicked(hovSlot, 0, ContainerInput.CLONE, player);
                return true;
            }
        }

        return super.keyPressed(ev);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void fillR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        g.fill(x + r, y, x + w - r, y + h, c);
        g.fill(x, y + r, x + r, y + h - r, c);
        g.fill(x + w - r, y + r, x + w, y + h - r, c);
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, c);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, c);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, c);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, c);
                }
            }
    }
}
