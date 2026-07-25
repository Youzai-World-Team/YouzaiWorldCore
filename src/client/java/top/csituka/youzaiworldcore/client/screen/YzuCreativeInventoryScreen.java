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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YzuCreativeInventoryScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger("YzuCreativeInventoryScreen");

    // layout
    private static final int PW = 380, PH = 210, PR = 6;
    private static final int TY = 4, TW = 28, TH = 22, TG = 2, TR = 4;
    private static final int SX = 250, SY = 6, SW = 120, SH = 16;
    private static final int GX = 10, GY = 34, SS = 16, SG = 2, COLS = 9, VROWS = 7;
    private static final int MX = 180, MY = 28, MW = 50, MH = 80, MSCALE = 35;
    private static final int AX = 236, AY = 28;
    private static final int OX = 236, OY = 100;
    private static final int HB_Y = 184;
    private static final int MAX_VIS = 6; // 最多显示 6 个分类按钮（+< > 共 8 个位置，不重叠搜索框）

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
    private final List<Integer> vis = new ArrayList<>();
    private float soff; private float xm, ym; private int lp, tp;
    private int tabPage;

    public YzuCreativeInventoryScreen(Player player) {
        super(Component.translatable("container.crafting")); this.player = player;
    }

    @Override protected void init() {
        lp = (width - PW) / 2; tp = (height - PH) / 2;
        rebuildTabs();
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

    private void populateAll() {
        allItems.clear();
        BuiltInRegistries.ITEM.stream().forEach(item -> { ItemStack st = new ItemStack(item); if (!st.isEmpty()) allItems.add(st); });
    }

    private void rebuildVis() {
        vis.clear();
        if (selTab == null) {
            // 搜索/全部模式
            String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
            for (int i = 0; i < allItems.size(); i++) {
                ItemStack s = allItems.get(i);
                if (s.isEmpty()) continue;
                if (q.isEmpty() || s.getHoverName().getString().toLowerCase().contains(q)) vis.add(i);
            }
        } else {
            // 分类模式：从 selTab.getDisplayItems() 获取该分类的物品进行匹配
            Collection<ItemStack> tabItems = selTab.getDisplayItems();
            if (tabItems != null && !tabItems.isEmpty()) {
                Set<Item> itemsForTab = new HashSet<>();
                for (ItemStack st : tabItems) if (!st.isEmpty()) itemsForTab.add(st.getItem());
                for (int i = 0; i < allItems.size(); i++) {
                    ItemStack s = allItems.get(i);
                    if (!s.isEmpty() && itemsForTab.contains(s.getItem())) vis.add(i);
                }
            }
            // 回退
            if (vis.isEmpty()) {
                for (int i = 0; i < allItems.size(); i++) if (!allItems.get(i).isEmpty()) vis.add(i);
            }
        }
        soff = 0;
    }

    private void onSearch(String q) {
        lastSearch = q;
        if (!q.trim().isEmpty()) {
            selTab = null; // 键入搜索 → 关闭分类模式
        } else if (selTab == null && !tabs.isEmpty()) {
            selTab = tabs.get(0); // 清空搜索 → 恢复第一个分类
        }
        rebuildVis();
    }

    /** 点击 Tab 时调用：清除搜索框、切换到分类模式 */
    private void selectCategory(CreativeModeTab tab) {
        selTab = tab;
        searchBox.setValue("");
        lastSearch = "";
        rebuildVis();
    }

    private void renderSlot(GuiGraphicsExtractor g, ItemStack st, int x, int y, int seed) {
        if (st != null && !st.isEmpty()) { g.item(st, x, y, seed); g.itemDecorations(font, st, x, y, null); }
    }

    @Override public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mx, int my, float pt) {
        fillR(g, lp, tp, PW, PH, PR, BG);
        drawTabs(g, mx, my);
        drawGrid(g, mx, my);

        var slots = player.inventoryMenu.slots;
        int hbx = lp+GX, hby = tp+HB_Y;
        for (int c = 0; c < 9; c++) {
            int sx = hbx + c*(SS+SG); fillR(g, sx, hby, SS, SS, 3, mx>=sx&&mx<sx+SS&&my>=hby&&my<hby+SS ? SHV : SC);
            renderSlot(g, slots.get(36+c).getItem(), sx, hby, c);
        }
        int ax = lp+AX, ay = tp+AY;
        for (int r = 0; r < 4; r++) { int sy = ay+r*(SS+SG); fillR(g, ax, sy, SS, SS, 3, SC); renderSlot(g, slots.get(5+r).getItem(), ax, sy, 100+r); }
        fillR(g, lp+OX, tp+OY, SS, SS, 3, SC); renderSlot(g, slots.get(45).getItem(), lp+OX, tp+OY, 200);

        if (player != null) InventoryScreen.extractEntityInInventoryFollowsMouse(g, lp+MX, tp+MY, lp+MX+MW, tp+MY+MH, MSCALE, 0.0625f, xm, ym, player);
        super.extractRenderState(g, mx, my, pt);
        xm = mx; ym = my;

        ItemStack carried = minecraft.player.containerMenu.getCarried();
        if (!carried.isEmpty()) { g.item(carried, mx-8, my-8, 0); g.itemDecorations(font, carried, mx-8, my-8, null); }
        if (carried.isEmpty()) {
            int hov = getHoveredGridIndex(mx, my);
            if (hov >= 0 && hov < vis.size()) { int vi = vis.get(hov); if (vi>=0 && vi<allItems.size()) { ItemStack st = allItems.get(vi); if (!st.isEmpty()) g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, st), st.getTooltipImage(), mx, my, null); } }
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
            int vi = vis.get(idx); if (vi>=0&&vi<allItems.size()) { ItemStack st = allItems.get(vi); if (!st.isEmpty()) { g.item(st, gx, gy, vi); g.itemDecorations(font, st, gx, gy, null); } }
        }
    }

    private void pickupItem(ItemStack stack, int count) {
        stack = stack.copy(); stack.setCount(Math.min(count, stack.getMaxStackSize()));
        try { minecraft.player.containerMenu.setCarried(stack); }
        catch (Exception e) { if (!player.getInventory().add(stack)) player.drop(stack, false); }
    }

    private void clearCarried() { minecraft.player.containerMenu.setCarried(ItemStack.EMPTY); }

    @Override public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
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

        // 热栏
        int hbx = lp+GX, hby = tp+HB_Y;
        for (int c = 0; c < 9; c++) {
            int sx = hbx + c*(SS+SG);
            if (ev.x()>=sx&&ev.x()<sx+SS&&ev.y()>=hby&&ev.y()<hby+SS) {
                var slot = player.inventoryMenu.slots.get(36+c);
                ItemStack si = slot.getItem().copy(), ca = minecraft.player.containerMenu.getCarried();
                minecraft.player.containerMenu.setCarried(si); slot.set(ca); return true;
            }
        }

        // 网格
        int hov = getHoveredGridIndex((int)ev.x(), (int)ev.y());
        if (hov >= 0 && hov < vis.size()) {
            int vi = vis.get(hov); if (vi<0||vi>=allItems.size()) return false;
            ItemStack cl = allItems.get(vi); if (cl.isEmpty()) return false;
            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (ev.button()==0) { if (ca.isEmpty()) pickupItem(cl,1); else clearCarried(); return true; }
            if (ev.button()==1) { if (ca.isEmpty()) pickupItem(cl,cl.getMaxStackSize()); else if (ItemStack.isSameItemSameComponents(ca,cl)) pickupItem(cl,ca.getCount()+1); return true; }
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
