package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;

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
    private static final int[] TC = { 0x60CC8866,0x6099CCFF,0x6066AA44,0x60AA66CC,0x60FF6644,0x604488CC,0x60FF8844,0x60FFCC44,0x60CCAACC,0x60FFAAAA,0x60FF66AA };

    /** 跨会话持久化的搜索文本 */
    private static String lastSearch = "";

    private final Player player;
    private final List<CreativeModeTab> tabs = new ArrayList<>();
    private CreativeModeTab selTab;
    private EditBox searchBox;
    private NonNullList<ItemStack> allItems = NonNullList.create();
    private List<ItemStack> tabItems;
    private final List<Integer> vis = new ArrayList<>();
    private float soff; private float xm, ym; private int lp, tp;
    private int tabPage;

    public YzuCreativeInventoryScreen(Player player) {
        super(Component.translatable("container.crafting")); this.player = player;
    }

    @Override protected void init() {
        lp = (width - PW) / 2; tp = (height - PH) / 2;
        rebuildTabs();
        // 强制构建 Tab 物品列表（原版 CreativeModeInventoryScreen.init() 中由 tryRebuildTabContents 完成，
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
        searchBox = new EditBox(font, lp+SX, tp+SY, SW, SH, Component.translatable("gui.search"));
        searchBox.setMaxLength(50); searchBox.setBordered(false); searchBox.setVisible(true);
        searchBox.setTextColor(0xFFFFFFFF); searchBox.setResponder(this::onSearch);
        addRenderableWidget(searchBox);
    }

    private void rebuildTabs() {
        tabs.clear();
        for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
            if (t.getType() == CreativeModeTab.Type.CATEGORY) tabs.add(t);
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

    private void populateAll() {
        allItems.clear();
        BuiltInRegistries.ITEM.stream().forEach(item -> { ItemStack st = new ItemStack(item); if (!st.isEmpty()) allItems.add(st); });
    }

    private void rebuildVis() {
        vis.clear();
        if (selTab == null) {
            tabItems = null;
            // 搜索/全部模式：从 allItems 中按搜索文本过滤
            String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
            for (int i = 0; i < allItems.size(); i++) {
                ItemStack s = allItems.get(i);
                if (s.isEmpty()) continue;
                if (q.isEmpty() || s.getHoverName().getString().toLowerCase().contains(q)) vis.add(i);
            }
        } else {
            // 分类模式：直接使用该分类原版的物品列表及其排列顺序（不通过 Set 过滤，保留 tab 内部顺序）
            Collection<ItemStack> tabColl = selTab.getDisplayItems();
            tabItems = tabColl != null ? List.copyOf(tabColl) : List.of();
            for (int i = 0; i < tabItems.size(); i++) {
                if (!tabItems.get(i).isEmpty()) vis.add(i);
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
        if (vi < 0) return ItemStack.EMPTY;
        if (selTab != null && tabItems != null) {
            if (vi < tabItems.size()) return tabItems.get(vi);
        } else {
            if (vi < allItems.size()) return allItems.get(vi);
        }
        return ItemStack.EMPTY;
    }

    private void renderSlot(GuiGraphicsExtractor g, ItemStack st, int x, int y, int seed) {
        if (st != null && !st.isEmpty()) { g.item(st, x, y, seed); g.itemDecorations(font, st, x, y, null); }
    }

    @Override public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mx, int my, float pt) {
        fillR(g, lp, tp, PW, PH, PR, BG);
        drawTabs(g, mx, my);
        drawGrid(g, mx, my);
        drawScroll(g, mx, my);

        var slots = player.inventoryMenu.slots;
        // 玩家模型 3D 渲染（原版 InventoryScreen 风格，鼠标跟随旋转）
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, lp+PM_X, tp+PM_Y, lp+PM_X+PM_W, tp+PM_Y+PM_H, PM_SCALE, 0.0625f, xm, ym, player);
        }

        // 装备 2×2（slots 5-8：helmet/chest/legs/boots）
        int ax = lp+ARM_X, ay = tp+ARM_Y;
        for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) {
            int sx = ax + c*(SS+SG), sy = ay + r*(SS+SG);
            fillR(g, sx, sy, SS, SS, 3, mx>=sx&&mx<sx+SS&&my>=sy&&my<sy+SS ? SHV : SC);
            ItemStack ast = slots.get(5+r*2+c).getItem(); renderSlot(g, ast, sx, sy, 200+r*2+c); ItemBorderRenderer.renderBorder(g, sx, sy, ast);
        }

        // 副手（slot 45）
        int ox = lp+OFF_X, oy = tp+OFF_Y;
        fillR(g, ox, oy, SS, SS, 3, mx>=ox&&mx<ox+SS&&my>=oy&&my<oy+SS ? SHV : SC);
        ItemStack ost = slots.get(45).getItem(); renderSlot(g, ost, ox, oy, 210); ItemBorderRenderer.renderBorder(g, ox, oy, ost);

        // 生存物品栏 3×9（slots 9-35）
        int invX = lp+INV_X, invY = tp+INV_Y;
        for (int r = 0; r < INV_ROWS; r++) for (int c = 0; c < INV_COLS; c++) {
            int sx = invX + c*(SS+SG), sy = invY + r*(SS+SG);
            fillR(g, sx, sy, SS, SS, 3, mx>=sx&&mx<sx+SS&&my>=sy&&my<sy+SS ? SHV : SC);
            ItemStack ist = slots.get(9+r*INV_COLS+c).getItem(); renderSlot(g, ist, sx, sy, 220+r*INV_COLS+c); ItemBorderRenderer.renderBorder(g, sx, sy, ist);
        }

        // 快捷栏 1×9（slots 36-44）
        int hbx = lp+HB_X, hby = tp+HB_Y;
        for (int c = 0; c < 9; c++) {
            int sx = hbx + c*(SS+SG);
            fillR(g, sx, hby, SS, SS, 3, mx>=sx&&mx<sx+SS&&my>=hby&&my<hby+SS ? SHV : SC);
            ItemStack hst = slots.get(36+c).getItem(); renderSlot(g, hst, sx, hby, c); ItemBorderRenderer.renderBorder(g, sx, hby, hst);
        }

        super.extractRenderState(g, mx, my, pt);
        xm = mx; ym = my;

        ItemStack carried = minecraft.player.containerMenu.getCarried();
        if (!carried.isEmpty()) { g.item(carried, mx-8, my-8, 0); g.itemDecorations(font, carried, mx-8, my-8, null); }
        if (carried.isEmpty()) {
            int hov = getHoveredGridIndex(mx, my);
            if (hov >= 0 && hov < vis.size()) { int vi = vis.get(hov); ItemStack st = getItemForVis(vi); if (!st.isEmpty()) g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, st), st.getTooltipImage(), mx, my, null); }
        }
    }

    private int getHoveredGridIndex(int mx, int my) {
        int sx = lp+GX, sy = tp+GY, rows = (vis.size()+COLS-1)/COLS, maxS = Math.max(0, rows-VROWS), sr = Math.min(Math.round(soff), maxS);
        for (int r = 0; r < VROWS; r++) for (int c = 0; c < COLS; c++) {
            int idx = (sr+r)*COLS + c; if (idx >= vis.size()) break;
            int gx = sx + c*(SS+SG), gy = sy + r*(SS+SG);
            if (mx >= gx && mx < gx+SS && my >= gy && my < gy+SS) return idx;
        }
        return -1;
    }

    private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
        int mv = Math.min(MAX_VIS, tabs.size()), pc = Math.max(1, (tabs.size()+mv-1)/mv);
        if (tabPage >= pc) tabPage = pc-1;
        int st = tabPage * mv, en = Math.min(st+mv, tabs.size());
        int arrowY = tp+TY;

        // 左翻页（始终显示）
        int leftX = lp+3;
        boolean canLeft = tabPage > 0;
        boolean lh = canLeft && mx>=leftX&&mx<leftX+TW&&my>=arrowY&&my<arrowY+TH;
        fillR(g, leftX, arrowY, TW, TH, TR, lh ? 0x80FFFFFF : (canLeft ? 0x60FFFFFF : 0x30FFFFFF));

        // Tab
        int tx = leftX + TW + TG;
        for (int i = st; i < en; i++) {
            CreativeModeTab t = tabs.get(i); boolean sel = selTab != null && t == selTab;
            boolean h = mx>=tx&&mx<tx+TW&&my>=arrowY&&my<arrowY+TH;
            fillR(g, tx, arrowY, TW, TH, TR, sel ? TA : (h ? 0x70FFFFFF : TC[i%TC.length]));
            ItemStack icon = t.getIconItem(); if (!icon.isEmpty()) g.item(icon, tx+(TW-16)/2, arrowY+(TH-16)/2, 0);
            if (h) g.setTooltipForNextFrame(font, t.getDisplayName(), mx, my);
            tx += TW+TG;
        }

        // 右翻页（始终显示）
        int rightX = tx;
        boolean canRight = tabPage < pc-1;
        boolean rh = canRight && mx>=rightX&&mx<rightX+TW&&my>=arrowY&&my<arrowY+TH;
        fillR(g, rightX, arrowY, TW, TH, TR, rh ? 0x80FFFFFF : (canRight ? 0x60FFFFFF : 0x30FFFFFF));
    }

    private void drawGrid(GuiGraphicsExtractor g, int mx, int my) {
        if (vis.isEmpty()) return;
        int sx = lp+GX, sy = tp+GY, rows = (vis.size()+COLS-1)/COLS, maxS = Math.max(0, rows-VROWS), sr = Math.min(Math.round(soff), maxS);
        for (int r = 0; r < VROWS; r++) for (int c = 0; c < COLS; c++) {
            int idx = (sr+r)*COLS + c; if (idx >= vis.size()) break;
            int gx = sx + c*(SS+SG), gy = sy + r*(SS+SG);
            boolean h = mx>=gx&&mx<gx+SS&&my>=gy&&my<gy+SS;
            fillR(g, gx, gy, SS, SS, 3, h ? SHV : SC);
            int vi = vis.get(idx); ItemStack st = getItemForVis(vi);
            if (!st.isEmpty()) { g.item(st, gx, gy, vi); g.itemDecorations(font, st, gx, gy, null); ItemBorderRenderer.renderBorder(g, gx, gy, st); }
        }
    }

    private void drawScroll(GuiGraphicsExtractor g, int mx, int my) {
        int rows = (vis.size()+COLS-1)/COLS, maxS = Math.max(0, rows-VROWS);
        int bx = lp+SCROLL_X, by = tp+SCROLL_Y;
        // 背景槽
        g.fill(bx, by, bx+SCROLL_W, by+SCROLL_H, 0x30FFFFFF);
        if (maxS <= 0) return; // 无可滚动时不绘制 thumb
        int thumbH = Math.max(6, (int)((float)VROWS/rows * SCROLL_H));
        int trackH = SCROLL_H - thumbH;
        int thumbY = by + (int)((float)Math.round(soff) / maxS * trackH);
        boolean hov = mx>=bx-1 && mx<bx+SCROLL_W+1 && my>=by && my<by+SCROLL_H;
        g.fill(bx, thumbY, bx+SCROLL_W, thumbY+thumbH, hov ? 0xCCFFFFFF : 0x80FFFFFF);
    }

    private void pickupItem(ItemStack stack, int count) {
        stack = stack.copy(); stack.setCount(Math.min(count, stack.getMaxStackSize()));
        try { minecraft.player.containerMenu.setCarried(stack); }
        catch (Exception e) { if (!player.getInventory().add(stack)) player.drop(stack, false); }
    }

    private void clearCarried() { minecraft.player.containerMenu.setCarried(ItemStack.EMPTY); }

    /** 处理真实容器槽位的点击：合并/互换逻辑（与原版 InventoryMenu 行为一致）
     *  <p>创造模式特殊处理：
     *  <ul>
     *  <li>空手拿取 → 复制到光标，不消耗库存（物品无限）</li>
     *  <li>携带放置 → 走 {@link Player#getInventory()}{@code .add()} 真正写入服务端库存，
     *  否则仅修改菜单槽位视图，切换到生存模式后会被服务端真实库存覆盖导致"消失"。</li>
     *  </ul></p>
     */
    private void handleSlotClick(int slotIndex) {
        var slot = player.inventoryMenu.slots.get(slotIndex);
        ItemStack si = slot.getItem(), ca = minecraft.player.containerMenu.getCarried();
        boolean creative = player.isCreative();
        if (ca.isEmpty()) { // 空手 → 复制槽位物品到光标
            if (!si.isEmpty()) {
                minecraft.player.containerMenu.setCarried(si.copy());
                // 创造模式不消耗库存；生存模式置空槽位
                if (!creative) slot.set(ItemStack.EMPTY);
                slot.setChanged();
            }
            return;
        }
        if (creative) {
            // 创造模式携带物品 → 真正写入 Player.inventory（避免切换模式后消失）
            // 同物品 → 先合并到原槽位（视觉反馈），再把剩余 add 到库存
            if (!si.isEmpty() && ItemStack.isSameItemSameComponents(ca, si)) {
                int space = si.getMaxStackSize() - si.getCount();
                int move = Math.min(space, ca.getCount());
                if (move > 0) { si.grow(move); ca.shrink(move); }
            }
            if (!ca.isEmpty()) {
                player.getInventory().add(ca.copy());
                ca.setCount(0);
            }
            minecraft.player.containerMenu.setCarried(ca.isEmpty() ? ItemStack.EMPTY : ca);
            slot.setChanged();
            return;
        }
        if (!si.isEmpty() && ItemStack.isSameItemSameComponents(ca, si)) {
            // 生存模式同物品 → 合并（尽量放入槽位）
            int space = si.getMaxStackSize() - si.getCount();
            int move = Math.min(space, ca.getCount());
            if (move > 0) { si.grow(move); ca.shrink(move); minecraft.player.containerMenu.setCarried(ca.isEmpty() ? ItemStack.EMPTY : ca); }
        } else {
            // 生存模式不同物品（或空槽位）→ 互换
            minecraft.player.containerMenu.setCarried(si.copy()); slot.set(ca.copy()); slot.setChanged();
        }
    }

    private boolean inRect(int mx, int my, int x, int y) {
        return mx>=x && mx<x+SS && my>=y && my<y+SS;
    }

    @Override public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        // 点击搜索框 → 立即切换到全部物品模式（让 EditBox 接管焦点）
        int sbx = lp+SX, sby = tp+SY;
        if (searchBox != null && ev.x()>=sbx && ev.x()<sbx+SW && ev.y()>=sby && ev.y()<sby+SH) {
            if (selTab != null) { selTab = null; rebuildVis(); }
            return super.mouseClicked(ev, real);
        }

        // 点击搜索框外任意 GUI 元素 → 搜索框失去焦点
        if (searchBox != null && searchBox.isFocused()) searchBox.setFocused(false);

        int mv = Math.min(MAX_VIS, tabs.size()), pc = Math.max(1, (tabs.size()+mv-1)/mv);
        int st = Math.min(tabPage*mv, tabs.size());
        int arrowY = tp+TY;

        // 左翻页（始终可以点击检测，但无页时不翻）
        int leftX = lp+3;
        if (ev.x()>=leftX&&ev.x()<leftX+TW&&ev.y()>=arrowY&&ev.y()<arrowY+TH) { if (ev.button()==0&&tabPage>0) tabPage--; return true; }

        // Tab 按钮
        int tx = leftX+TW+TG;
        for (int i = st; i < Math.min(st+mv, tabs.size()); i++) {
            if (ev.x()>=tx&&ev.x()<tx+TW&&ev.y()>=arrowY&&ev.y()<arrowY+TH) { if (ev.button()==0) selectCategory(tabs.get(i)); return true; }
            tx += TW+TG;
        }

        // 右翻页
        if (ev.x()>=tx&&ev.x()<tx+TW&&ev.y()>=arrowY&&ev.y()<arrowY+TH) { if (ev.button()==0&&tabPage<pc-1) tabPage++; return true; }

        // 装备 2×2（slots 5-8：helmet/chest/legs/boots）+ 副手（slot 45）
        int ax = lp+ARM_X, ay = tp+ARM_Y;
        for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) {
            int sx = ax + c*(SS+SG), sy = ay + r*(SS+SG);
            if (inRect((int)ev.x(), (int)ev.y(), sx, sy)) { handleSlotClick(5+r*2+c); return true; }
        }
        if (inRect((int)ev.x(), (int)ev.y(), lp+OFF_X, tp+OFF_Y)) { handleSlotClick(45); return true; }

        // 生存物品栏 3×9（slots 9-35）
        int invX = lp+INV_X, invY = tp+INV_Y;
        for (int r = 0; r < INV_ROWS; r++) for (int c = 0; c < INV_COLS; c++) {
            int sx = invX + c*(SS+SG), sy = invY + r*(SS+SG);
            if (inRect((int)ev.x(), (int)ev.y(), sx, sy)) { handleSlotClick(9+r*INV_COLS+c); return true; }
        }

        // 快捷栏 1×9（slots 36-44）
        int hbx = lp+HB_X, hby = tp+HB_Y;
        for (int c = 0; c < 9; c++) {
            int sx = hbx + c*(SS+SG);
            if (inRect((int)ev.x(), (int)ev.y(), sx, hby)) { handleSlotClick(36+c); return true; }
        }

        // 网格
        int hov = getHoveredGridIndex((int)ev.x(), (int)ev.y());
        if (hov >= 0 && hov < vis.size()) {
            int vi = vis.get(hov); ItemStack cl = getItemForVis(vi); if (cl.isEmpty()) return false;
            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (ev.button()==0) { if (ca.isEmpty()) pickupItem(cl,1); else clearCarried(); return true; }
            if (ev.button()==1) {
                if (ca.isEmpty()) { pickupItem(cl,cl.getMaxStackSize()); }
                else if (ItemStack.isSameItemSameComponents(ca,cl)) { pickupItem(cl,ca.getCount()+1); }
                else { // 不同物品 → 递减携带物 1
                    if (ca.getCount() > 1) { ca.shrink(1); minecraft.player.containerMenu.setCarried(ca); }
                    else clearCarried();
                }
                return true;
            }
            if (ev.button()==2) { if (ca.isEmpty()) pickupItem(cl,cl.getMaxStackSize()); return true; }
        }

        // 点击 GUI 外部 → 丢弃光标上的物品
        if (ev.button()==0) {
            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (!ca.isEmpty()) {
                boolean inside = ev.x() >= lp && ev.x() < lp+PW && ev.y() >= tp && ev.y() < tp+PH;
                if (!inside) {
                    player.drop(ca, false);
                    clearCarried();
                    return true;
                }
            }
        }

        return super.mouseClicked(ev, real);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int rows = (vis.size()+COLS-1)/COLS; int maxS = Math.max(0, rows-VROWS); if (maxS<=0) return false;
        soff = Math.max(0, Math.min(maxS, soff+(float)-sy*0.5f)); return true;
    }
    @Override public boolean keyPressed(@NonNull KeyEvent ev) {
        if (ev.key()==256||minecraft.options.keyInventory.matches(ev)) { minecraft.gui.setScreen(null); return true; }
        return super.keyPressed(ev);
    }
    @Override public boolean isPauseScreen() { return false; }

    private static void fillR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        g.fill(x+r,y,x+w-r,y+h,c); g.fill(x,y+r,x+r,y+h-r,c); g.fill(x+w-r,y+r,x+w,y+h-r,c);
        for (int i=0;i<r;i++) for (int j=0;j<r;j++) { if (i*i+j*j<r*r) {
            g.fill(x+r-i-1,y+r-j-1,x+r-i,y+r-j,c); g.fill(x+w-r+i,y+r-j-1,x+w-r+i+1,y+r-j,c);
            g.fill(x+r-i-1,y+h-r+j,x+r-i,y+h-r+j+1,c); g.fill(x+w-r+i,y+h-r+j,x+w-r+i+1,y+h-r+j+1,c);
        }}
    }
}
