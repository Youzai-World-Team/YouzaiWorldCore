package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeInventoryListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import java.util.Locale;
import java.util.Set;

import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;
import top.csituka.youzaiworldcore.network.TrinketInteractPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.TrinketHelper;

/**
 * YZUI 创造模式物品栏屏幕。
 * <p>
 * 左侧提供创造物品分类、搜索和物品选择，右侧保留玩家的真实装备与背包槽位。
 * 右侧背包区域支持以下 Mouse Tweaks 手势：
 * <ul>
 * <li>右键持物拖拽：向经过的槽位逐个分发物品</li>
 * <li>左键取物后拖拽：收集经过槽位中的同类物品</li>
 * <li>Shift+左键拖拽：空手连续快速移动任意物品，持物时仅移动同类物品</li>
 * <li>滚轮：在主背包与快捷栏之间逐个推出或拉入同类物品</li>
 * </ul>
 */
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

    /**
     * 原版搜索标签物品变体 + 小写名称索引的进程级缓存。
     * <p>
     * 没必要每次 {@link #init()}（开屏、改窗口大小都会触发）都重新处理几千个
     * ItemStack。注册表、创造标签构建参数或语言变化时会自动重建。
     */
    private static List<ItemStack> cachedAllItems;
    private static List<String> cachedAllItemNames;
    private static int cachedRegistrySize = -1;
    private static String cachedLanguage;

    private final @NonNull Player player;
    /** 原版创造物品栏使用的槽位监听器，负责把 InventoryMenu 的本地变更同步给服务端。 */
    private CreativeInventoryListener creativeInventoryListener;
    private final List<CreativeModeTab> tabs = new ArrayList<>();
    private CreativeModeTab selTab;
    private EditBox searchBox;
    private List<ItemStack> allItems = List.of();
    /** 与 {@link #allItems} 一一对应的小写名称，供搜索直接比对 */
    private List<String> allItemNames = List.of();
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
    /** 0=无手势, 1=合并拖拽（左键有物品拖过相同物品），2=Shift批量拖拽（空手任意、持物同类） */
    private int yzwcDragMode;
    /** Shift+左键持物拖拽时仅快速移动同类物品；EMPTY 表示空手拖拽任意物品。 */
    private ItemStack shiftDragFilter = ItemStack.EMPTY;
    /** 手势拖拽中已处理过的槽位（避免重复处理） */
    private final Set<Integer> processedSlots = new HashSet<>();
    // 双击检测
    private long lastClickTime;
    private int lastClickSlot = -1;
    private int lastClickButton = -1;
    // Trinkets 悬停状态
    private int trinketSourceSlot = -1;
    /** 上次向 Trinkets 查询过的槽位（含"查了但没有饰品槽"的结果），避免同一槽位每帧重复查询 */
    private int trinketQueriedSlot = -1;
    private java.util.List<TrinketHelper.TrinketSlotInfo> activeTrinketSlots = java.util.List.of();
    /** 子菜单隐藏宽限期（鼠标离开有效区后仍保留的毫秒数） */
    private static final long TRINKET_HIDE_GRACE_MS = 400;
    private long trinketHideDeadline = -1;

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
        registerCreativeInventoryListener();
        LOG.info("init lp={} tp={} tabs={} allItems={} vis={}", lp, tp, tabs.size(), allItems.size(), vis.size());
    }

    /**
     * 注册原版创造物品栏槽位监听器。
     * <p>
     * {@link net.minecraft.world.inventory.InventoryMenu#clicked} 只修改客户端本地菜单；原版
     * {@code CreativeModeInventoryScreen} 会在每次点击后调用 {@code broadcastChanges()}，再由此
     * 监听器通过创造模式槽位数据包同步服务端。缺少该监听器会产生仅客户端可见的幽灵物品。
     */
    private void registerCreativeInventoryListener() {
        if (creativeInventoryListener != null) {
            player.inventoryMenu.removeSlotListener(creativeInventoryListener);
        }
        creativeInventoryListener = new CreativeInventoryListener(minecraft);
        player.inventoryMenu.addSlotListener(creativeInventoryListener);
        DebugLogger.info("YzuCreativeInventoryScreen", "已注册创造模式物品栏同步监听器");
    }

    @Override
    public void removed() {
        super.removed();
        // 原版创造界面使用一次性的 ItemPickerMenu，关闭后光标副本随菜单一起丢弃；
        // YZUI 复用了长期存在的 InventoryMenu，因此必须显式清空，避免下次打开残留隐藏光标物品。
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        resetDragState();
        if (creativeInventoryListener != null) {
            player.inventoryMenu.removeSlotListener(creativeInventoryListener);
            creativeInventoryListener = null;
            DebugLogger.info("YzuCreativeInventoryScreen", "已移除创造模式物品栏同步监听器");
        }
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
            boolean op = player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get();
            var holders = level.registryAccess(); // RegistryAccess implements HolderLookup.Provider
            if (CreativeModeTabs.tryRebuildTabContents(features, op, holders)) {
                // 功能旗标、权限或注册表变化后，搜索标签内容也会变化，必须丢弃旧缓存。
                cachedAllItems = null;
                cachedAllItemNames = null;
            }
            DebugLogger.info("YzuCreativeInventoryScreen", "已构建 %d 个可见创造模式分类", tabs.size());
        } catch (Exception e) {
            DebugLogger.exception("YzuCreativeInventoryScreen", "forceBuildTabContents", e);
        }
    }

    private void populateAll() {
        int registrySize = BuiltInRegistries.ITEM.size();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (cachedAllItems == null || cachedRegistrySize != registrySize || !language.equals(cachedLanguage)) {
            Collection<ItemStack> searchItems = CreativeModeTabs.searchTab().getDisplayItems();
            List<ItemStack> stacks = new ArrayList<>(Math.max(registrySize, searchItems.size()));
            List<String> names = new ArrayList<>(Math.max(registrySize, searchItems.size()));
            for (ItemStack st : searchItems) {
                if (st.isEmpty())
                    continue;
                stacks.add(st);
                names.add(st.getHoverName().getString().toLowerCase(Locale.ROOT));
            }
            cachedAllItems = List.copyOf(stacks);
            cachedAllItemNames = List.copyOf(names);
            cachedRegistrySize = registrySize;
            cachedLanguage = language;
            DebugLogger.info("YzuCreativeInventoryScreen", "已重建创造模式搜索缓存，共 %d 个物品变体",
                    cachedAllItems.size());
        }
        allItems = cachedAllItems;
        allItemNames = cachedAllItemNames;
    }

    private void rebuildVis() {
        vis.clear();
        if (selTab == null) {
            tabItems = null;
            // 搜索/全部模式：按搜索文本过滤预先算好的小写名称索引
            String q = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT).trim() : "";
            int n = allItems.size();
            if (q.isEmpty()) {
                for (int i = 0; i < n; i++)
                    vis.add(i);
            } else {
                for (int i = 0; i < n; i++) {
                    if (allItemNames.get(i).contains(q))
                        vis.add(i);
                }
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

        // ===== Trinkets 悬停提示（在拖拽预览和鼠标物品之前渲染，避免遮盖） =====
        trinketOverlayTick(g, mx, my);

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
            // 左侧网格悬停提示
            int hov = getHoveredGridIndex(mx, my);
            if (hov >= 0 && hov < vis.size()) {
                int vi = vis.get(hov);
                ItemStack st = getItemForVis(vi);
                if (!st.isEmpty())
                    g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, st), st.getTooltipImage(), mx,
                            my, null);
            }
            // 右侧面板（装备/副手/生存背包/快捷栏）悬停提示
            if (hov < 0) {
                int rps = getRightPanelSlotAt(mx, my);
                if (rps >= 0) {
                    ItemStack rpst = slots.get(rps).getItem();
                    if (!rpst.isEmpty())
                        g.setTooltipForNextFrame(font, Screen.getTooltipFromItem(minecraft, rpst),
                                rpst.getTooltipImage(), mx, my, null);
                }
            }
        }
    }

    /** Trinkets 悬停提示的每帧状态更新 + 渲染（在鼠标物品之前调用，避免遮盖）。 */
    private void trinketOverlayTick(GuiGraphicsExtractor g, int mx, int my) {
        if (!TrinketHelper.isLoaded() || player == null)
            return;
        var slots = player.inventoryMenu.slots;
        int hitSlot = getRightPanelSlotAt(mx, my);
        boolean onSource = hitSlot >= 0 && hitSlot < slots.size();

        boolean onIndicator = false;
        if (trinketSourceSlot >= 0 && !activeTrinketSlots.isEmpty()) {
            int baseX = getSlotScreenX(trinketSourceSlot);
            int baseY = getSlotScreenY(trinketSourceSlot);
            int indStartX = baseX + SS + SG;
            int indEndX = indStartX + activeTrinketSlots.size() * (SS + SG);
            onIndicator = mx >= indStartX && mx < indEndX && my >= baseY && my < baseY + SS;
        }

        // 按 trinketQueriedSlot 去重：悬停在"没有饰品槽"的槽位上时，原先每帧都会重新遍历
        // 一遍 SlotGroup 并新建 ArrayList，这里改成同一槽位只查一次。
        if (onSource) {
            if (hitSlot != trinketQueriedSlot && !onIndicator) {
                Slot slotObj = slots.get(hitSlot);
                trinketQueriedSlot = hitSlot;
                activeTrinketSlots = TrinketHelper.getSlotsAttachedTo(player, slotObj);
                trinketSourceSlot = activeTrinketSlots.isEmpty() ? -1 : hitSlot;
            }
            trinketHideDeadline = -1; // 鼠标在有效区，取消隐藏计时
        } else if (onIndicator) {
            trinketHideDeadline = -1; // 鼠标在弹出区，保持显示
        } else {
            // 鼠标离开有效区：先进入宽限期，不立即隐藏（防止路径经过间隙时闪没）
            long now = System.currentTimeMillis();
            if (trinketHideDeadline < 0) {
                trinketHideDeadline = now + TRINKET_HIDE_GRACE_MS;
            } else if (now >= trinketHideDeadline) {
                activeTrinketSlots = List.of();
                trinketSourceSlot = -1;
                trinketQueriedSlot = -1;
                trinketHideDeadline = -1;
            }
        }

        if (!activeTrinketSlots.isEmpty() && trinketSourceSlot >= 0) {
            int baseX = getSlotScreenX(trinketSourceSlot);
            int baseY = getSlotScreenY(trinketSourceSlot);
            int indStartX = baseX + SS + SG;
            int indW = activeTrinketSlots.size() * (SS + SG) - SG;
            fillR(g, indStartX - 2, baseY - 2, indW + 4, SS + 4, 4, 0x50000000);
            for (int i = 0; i < activeTrinketSlots.size(); i++) {
                int sx = indStartX + i * (SS + SG);
                TrinketHelper.TrinketSlotInfo slotInfo = activeTrinketSlots.get(i);
                // TrinketSlotInfo 是悬停时创建的快照；必须通过 access 读取实时内容，
                // 否则放入/取出后会一直绘制旧物品，直到指示器关闭并重新查询。
                ItemStack ti = TrinketHelper.getSlotStack(slotInfo);
                if (ti.isEmpty()) {
                    // 空槽：绘制 Trinkets 自定义占位图标
                    Identifier iconId = TrinketHelper.getSlotIcon(slotInfo);
                    if (iconId != null) {
                        fillR(g, sx, baseY, SS, SS, 3, 0xFFFFFFFF);
                        g.blitSprite(RenderPipelines.GUI_TEXTURED, iconId, sx, baseY, SS, SS);
                    } else {
                        fillR(g, sx, baseY, SS, SS, 3, 0xFFFFFFFF);
                    }
                } else {
                    fillR(g, sx, baseY, SS, SS, 3, 0xFFFFFFFF);
                    g.fakeItem(ti, sx, baseY);
                }
            }
        }
    }

    /** 返回鼠标在 Trinket 指示器区域内的槽位索引。 */
    private int getTrinketIndicatorAt(int mx, int my) {
        if (trinketSourceSlot < 0 || activeTrinketSlots.isEmpty())
            return -1;
        int baseX = getSlotScreenX(trinketSourceSlot) + SS + SG;
        int baseY = getSlotScreenY(trinketSourceSlot);
        int count = activeTrinketSlots.size();
        if (mx < baseX || mx >= baseX + count * (SS + SG) || my < baseY || my >= baseY + SS)
            return -1;
        return (mx - baseX) / (SS + SG);
    }

    /** 处理 Trinket 饰品槽点击（左键取放/交换，右键取放）。 */
    private void trinketHandleClick(TrinketHelper.TrinketSlotInfo tsi, int button) {
        ItemStack carried = minecraft.player.containerMenu.getCarried();
        ItemStack slotStack = TrinketHelper.getSlotStack(tsi);
        byte action;
        if (button < 0 || button > 1) {
            return;
        }
        if (carried.isEmpty() && !slotStack.isEmpty()) {
            action = TrinketInteractPayload.ACTION_TAKE;
        } else if (!carried.isEmpty() && slotStack.isEmpty()) {
            action = TrinketInteractPayload.ACTION_PLACE;
        } else if (button == 0 && !carried.isEmpty() && !slotStack.isEmpty()) {
            action = TrinketInteractPayload.ACTION_SWAP;
        } else {
            return;
        }

        if ((action == TrinketInteractPayload.ACTION_PLACE || action == TrinketInteractPayload.ACTION_SWAP)
                && !TrinketHelper.canPlaceInSlot(tsi, carried, player)) {
            DebugLogger.info("TrinketClick", "物品 %s 未通过饰品槽 %s[%d] 的客户端校验",
                    carried.getHoverName().getString(), tsi.groupKey(), tsi.slotIndex());
            return;
        }

        // 发送 C2S 数据包（携带 cursor 兜底服务端 carried 不同步）
        ClientPlayNetworking.send(new TrinketInteractPayload(tsi.groupKey(), tsi.slotIndex(), action, carried));
        // 本地预览：立即更新鼠标物品与槽位显示（服务端权威广播到达后最终校正）
        try {
            if (action == TrinketInteractPayload.ACTION_PLACE && !carried.isEmpty()) {
                int placeCount = Math.min(carried.getCount(), TrinketHelper.getSlotMaxStackSize(tsi, carried));
                if (placeCount > 0) {
                    ItemStack placed = carried.copyWithCount(placeCount);
                    if (TrinketHelper.setSlotStack(tsi, placed)) {
                        ItemStack remainder = carried.copy();
                        remainder.shrink(placeCount);
                        minecraft.player.containerMenu.setCarried(remainder.isEmpty() ? ItemStack.EMPTY : remainder);
                    }
                }
            } else if (action == TrinketInteractPayload.ACTION_TAKE && !slotStack.isEmpty()) {
                if (TrinketHelper.setSlotStack(tsi, ItemStack.EMPTY)) {
                    minecraft.player.containerMenu.setCarried(slotStack.copy());
                }
            } else if (action == TrinketInteractPayload.ACTION_SWAP && !carried.isEmpty()) {
                int maxStackSize = TrinketHelper.getSlotMaxStackSize(tsi, carried);
                if (carried.getCount() <= maxStackSize && TrinketHelper.setSlotStack(tsi, carried.copy())) {
                    minecraft.player.containerMenu.setCarried(slotStack.copy());
                }
            }
        } catch (Exception e) {
            DebugLogger.warn("TrinketClick", "Local preview failed: %s", e.getMessage());
        }
    }

    /**
     * Shift+左键点击指示器：将饰品槽内物品快捷移动到主物品栏/快捷栏。
     * <p>
     * 创造模式菜单（CreativeModeMenu）没有 Trinkets 注入槽，无法复用标准
     * QUICK_MOVE 点击（Trinkets 的 quickMove mixin 仅针对 InventoryMenu），
     * 改为发送 {@link TrinketInteractPayload#ACTION_QUICK_MOVE}，由服务端把物品
     * 转移到玩家背包（0-35 主栏+快捷栏）；饰品槽同步走 Trinkets 网络层。
     * <p>
     * 发送后执行客户端本地预览：物品栏槽位（player.inventoryMenu.slots）引用的是
     * 客户端本地 Inventory，服务端槽位推送（broadcastChanges→synchronizeSlotToRemote）
     * 依赖 synchronizer/remoteSlots 变化检测，创造模式下可能缺失导致物品栏不刷新
     * （需重开物品栏才显示）。本地按与服务端相同逻辑（同种堆叠→空位）模拟转移，
     * 保证即时可见；服务端权威广播到达后最终校正。
     */
    private void trinketQuickMove(TrinketHelper.TrinketSlotInfo tsi) {
        ItemStack slotStack = TrinketHelper.getSlotStack(tsi);
        if (slotStack.isEmpty()) {
            DebugLogger.info("TrinketClick", "Shift-click on %s[%d]: slot empty, nothing to move",
                    tsi.groupKey(), tsi.slotIndex());
            return;
        }
        DebugLogger.info("TrinketClick", "Shift-click on %s[%d] -> QUICK_MOVE to inventory",
                tsi.groupKey(), tsi.slotIndex());
        ClientPlayNetworking.send(new TrinketInteractPayload(tsi.groupKey(), tsi.slotIndex(),
                TrinketInteractPayload.ACTION_QUICK_MOVE));
        // 本地预览：同种堆叠 → 空位，与服务端逻辑一致
        try {
            net.minecraft.world.entity.player.Inventory clientInv = player.getInventory();
            ItemStack toMove = slotStack.copy();
            for (int i = 0; i < clientInv.getContainerSize(); i++) {
                ItemStack existing = clientInv.getItem(i);
                if (existing.isEmpty()) {
                    clientInv.setItem(i, toMove);
                    toMove = ItemStack.EMPTY;
                    break;
                } else if (ItemStack.isSameItemSameComponents(existing, toMove)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int take = Math.min(space, toMove.getCount());
                    existing.grow(take);
                    toMove.shrink(take);
                    if (toMove.isEmpty()) {
                        break;
                    }
                }
            }
            TrinketHelper.setSlotStack(tsi, toMove.isEmpty() ? ItemStack.EMPTY : toMove);
        } catch (Exception e) {
            DebugLogger.warn("TrinketClick", "Local preview failed: %s", e.getMessage());
        }
        trinketSourceSlot = -1;
        activeTrinketSlots = List.of();
        trinketQueriedSlot = -1; // 强制下一帧重新查询，让指示器按新内容刷新
        trinketHideDeadline = -1;
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
            Slot slot = slots.get(si);
            if (!slot.mayPlace(carried))
                continue;
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
                validSlots.add(si);
                continue;
            }
            if (ItemStack.isSameItemSameComponents(carried, existing)
                    && existing.getCount() < slot.getMaxStackSize(carried)) {
                validSlots.add(si);
            }
        }
        int validCount = validSlots.size();
        if (validCount == 0)
            return;

        for (int idx = 0; idx < validCount; idx++) {
            int si = validSlots.get(idx);
            Slot slot = slots.get(si);
            ItemStack existing = slot.getItem();
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
            preview.setCount(Math.min(previewCount, slot.getMaxStackSize(carried)));
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

    /** 按原版创造物品槽语义处理左侧网格点击。 */
    private void handleCreativeGridClick(ItemStack gridItem, int button, boolean quickMove) {
        ItemStack carried = minecraft.player.containerMenu.getCarried();

        if (button == 2) {
            if (carried.isEmpty()) {
                pickupItem(gridItem, gridItem.getMaxStackSize());
            }
            return;
        }

        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, gridItem)) {
            if (button == 0) {
                carried.setCount(quickMove ? carried.getMaxStackSize()
                        : Math.min(carried.getCount() + 1, carried.getMaxStackSize()));
            } else if (button == 1) {
                carried.shrink(1);
            }
            minecraft.player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            return;
        }

        if (carried.isEmpty()) {
            pickupItem(gridItem, quickMove ? gridItem.getMaxStackSize() : gridItem.getCount());
        } else if (button == 0) {
            clearCarried();
        } else if (button == 1) {
            carried.shrink(1);
            minecraft.player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
    }

    private void clearCarried() {
        minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    /** 清理当前鼠标手势状态，避免拖拽到界面外后在释放事件中再次执行槽位操作。 */
    private void resetDragState() {
        pendingDrag = false;
        isDragInProgress = false;
        draggedSlots.clear();
        yzwcDragMode = 0;
        shiftDragFilter = ItemStack.EMPTY;
        processedSlots.clear();
    }

    /**
     * 创造模式把携带物拖出 YZUI 面板时按原版创造栏的方式丢出。
     * <p>
     * 创造物品栏中的物品本来就是客户端生成的虚拟副本，拖出面板应生成掉落物，
     * 同时通过创造模式协议通知服务端，最后清理鼠标携带状态。
     */
    private boolean dropCreativeCarriedOutside(double mx, double my) {
        boolean inside = mx >= lp && mx < lp + PW && my >= tp && my < tp + PH;
        if (inside || !player.isCreative()) {
            return false;
        }

        ItemStack carried = minecraft.player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            if (pendingDrag || isDragInProgress) {
                resetDragState();
            }
            return false;
        }

        ItemStack toDrop = carried.copy();
        clearCarried();
        player.drop(toDrop, true);
        minecraft.gameMode.handleCreativeModeItemDrop(toDrop);
        resetDragState();
        DebugLogger.info("YzuCreativeInventoryScreen", "创造模式物品拖出界面，已作为掉落物丢出");
        return true;
    }

    /**
     * 按原版创造物品栏的 INVENTORY 标签页路径执行真实背包槽位操作。
     * <p>
     * 操作先由客户端 {@code InventoryMenu} 计算，再调用 {@code broadcastChanges()}；
     * {@link CreativeInventoryListener} 会把所有变化槽位逐个通过创造模式协议同步到服务端。
     */
    private void clickInventorySlot(int slotIndex, int button, ContainerInput input) {
        if (slotIndex < 0 || slotIndex >= player.inventoryMenu.slots.size()) {
            DebugLogger.warn("YzuCreativeInventoryScreen", "忽略越界槽位点击：slot=%d, input=%s",
                    slotIndex, input);
            return;
        }
        player.inventoryMenu.clicked(slotIndex, button, input, player);
        player.inventoryMenu.broadcastChanges();
    }

    /** 创造模式下的双击收集，与原版 INVENTORY 标签页相同地使用 PICKUP_ALL。 */
    private void handlePickupAll(int sourceSlot, int button) {
        clickInventorySlot(sourceSlot, button, ContainerInput.PICKUP_ALL);
    }

    /** 处理右侧真实背包槽位的左/右键与中键克隆。 */
    private void handleSlotClick(int slotIndex, int button) {
        clickInventorySlot(slotIndex, button == 2 ? 0 : button,
                button == 2 ? ContainerInput.CLONE : ContainerInput.PICKUP);
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
            Slot slot = slots.get(si);
            if (!slot.mayPlace(carried)) continue;
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) { vc++; continue; }
            if (ItemStack.isSameItemSameComponents(carried, existing)
                    && existing.getCount() < slot.getMaxStackSize(carried)) {
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
            if (!slot.mayPlace(carried)) {
                continue;
            }
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) {
                validSlots.add(si);
            } else if (ItemStack.isSameItemSameComponents(carried, existing)) {
                if (existing.getCount() < slot.getMaxStackSize(carried)) {
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
                    int space = slot.getMaxStackSize(carried) - existing.getCount();
                    int put = Math.min(space, count);
                    if (put > 0) {
                        existing.grow(put);
                        carried.shrink(put);
                        gameMode.handleCreativeModeItemAdd(existing, si);
                    }
                } else {
                    ItemStack place = carried.copy();
                    int put = Math.min(count, slot.getMaxStackSize(carried));
                    place.setCount(put);
                    slot.set(place);
                    slot.setChanged();
                    gameMode.handleCreativeModeItemAdd(place, si);
                    carried.shrink(put);
                }
            }
        } else if (dragButton == 1) {
            for (int si : validSlots) {
                if (carried.isEmpty())
                    break;
                var slot = player.inventoryMenu.slots.get(si);
                ItemStack existing = slot.getItem();
                if (!existing.isEmpty()) {
                    int space = slot.getMaxStackSize(carried) - existing.getCount();
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
        // 点击搜索框 → 切换到全部模式并显式聚焦
        // 26.2 中 keyPressed/charTyped 经 ContainerEventHandler.getFocused() 路由，
        // 必须显式 setFocused(searchBox) 设置屏幕级焦点，否则无法输入
        int sbx = lp + SX, sby = tp + SY;
        if (searchBox != null && ev.x() >= sbx && ev.x() < sbx + SW && ev.y() >= sby && ev.y() < sby + SH) {
            if (selTab != null) {
                selTab = null;
                rebuildVis();
            }
            searchBox.mouseClicked(ev, real);
            setFocused(searchBox);
            searchBox.setFocused(true);
            return true;
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
                    ItemStack carried = minecraft.player.containerMenu.getCarried();
                    shiftDragFilter = carried.isEmpty() ? ItemStack.EMPTY : carried.copy();
                    processedSlots.clear();
                    return true;
                }
                clickInventorySlot(rpSlot, 0, ContainerInput.QUICK_MOVE);
                return true;
            }

            if (ev.button() == 2) {
                handleSlotClick(rpSlot, 2);
                return true;
            }

            // 双击检测（优先于任何点击处理，防止第一次点击清空槽位后 PICKUP_ALL 找不到目标）
            if (ev.button() == 0 || ev.button() == 1) {
                long now = System.currentTimeMillis();
                boolean doubleClick = (now - lastClickTime < 250L)
                        && rpSlot == lastClickSlot && ev.button() == lastClickButton;
                lastClickTime = now;
                lastClickSlot = rpSlot;
                lastClickButton = ev.button();
                if (doubleClick) {
                    handlePickupAll(rpSlot, ev.button());
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
                    shiftDragFilter = ItemStack.EMPTY;
                    processedSlots.clear();
                } else {
                    // 有携带物右键：暂不处理，等 mouseReleased（右键分发拖拽）
                    pendingDrag = true;
                    dragButton = 1;
                    dragOriginSlot = rpSlot;
                    yzwcDragMode = 0;
                    shiftDragFilter = ItemStack.EMPTY;
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
                    shiftDragFilter = ItemStack.EMPTY;
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
            if (ev.button() >= 0 && ev.button() <= 2) {
                handleCreativeGridClick(cl, ev.button(), ev.hasShiftDown());
                return true;
            }
        }

        // Trinket 指示器点击交互
        if (TrinketHelper.isLoaded() && trinketSourceSlot >= 0 && !activeTrinketSlots.isEmpty()) {
            int ti = getTrinketIndicatorAt((int) ev.x(), (int) ev.y());
            if (ti >= 0 && ti < activeTrinketSlots.size()) {
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

        // 点击 GUI 外部（任意按钮）→ 创造模式丢出携带物，生存按普通掉落处理
        boolean inside = ev.x() >= lp && ev.x() < lp + PW && ev.y() >= tp && ev.y() < tp + PH;
        if (!inside) {
            if (dropCreativeCarriedOutside(ev.x(), ev.y())) {
                return true;
            }
            ItemStack ca = minecraft.player.containerMenu.getCarried();
            if (!ca.isEmpty()) {
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
        // 拖拽物品从创造栏移到面板外后，释放事件是主要处理路径；按原版方式生成掉落物。
        if (dropCreativeCarriedOutside(ev.x(), ev.y())) {
            return true;
        }

        // 右键释放：处理 pending 或 drag 后始终消耗
        if (ev.button() == 1) {
            if (pendingDrag && dragButton == 1) {
                pendingDrag = false;
                handleSlotClick(dragOriginSlot, 1);
            } else if (isDragInProgress && dragButton == 1) {
                endDrag();
            }
            yzwcDragMode = 0;
            shiftDragFilter = ItemStack.EMPTY;
            processedSlots.clear();
            return true;
        }
        if (pendingDrag && ev.button() == dragButton) {
            // 没有拖拽移动 → 视为单格手势点击
            pendingDrag = false;
            if (yzwcDragMode == 2) {
                // Shift 单格快速转移（mouseClicked 未处理，需在此处理）
                processGestureSlot(dragOriginSlot);
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
            shiftDragFilter = ItemStack.EMPTY;
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
            shiftDragFilter = ItemStack.EMPTY;
            processedSlots.clear();
            return true;
        }
        return super.mouseReleased(ev);
    }

    /** 处理手势拖拽经过的单个槽位（实时处理，不等到 endDrag）。 */
    private void processGestureSlot(int si) {
        if (yzwcDragMode == 2) {
            // 空手 Shift 拖拽处理所有物品；持物 Shift 拖拽只处理同类物品。
            ItemStack slotStack = player.inventoryMenu.getSlot(si).getItem();
            if (slotStack.isEmpty() || (!shiftDragFilter.isEmpty()
                    && !ItemStack.isSameItemSameComponents(slotStack, shiftDragFilter))) {
                return;
            }
            clickInventorySlot(si, 0, ContainerInput.QUICK_MOVE);
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
        int hoveredSlot = getRightPanelSlotAt((int) mx, (int) my);
        if (sy != 0.0 && MouseTweaksSupport.isStorageSlot(hoveredSlot)
                && player.inventoryMenu.getCarried().isEmpty()) {
            int steps = Math.max(1, (int) Math.round(Math.abs(sy)));
            for (int i = 0; i < steps; i++) {
                if (!MouseTweaksSupport.scrollOne(player.inventoryMenu, player, hoveredSlot, sy,
                        this::clickInventorySlot)) {
                    break;
                }
            }
            return true;
        }

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

        // 左侧网格悬停快捷键（Q/Ctrl+Q、F、1-9、选取方块键）
        int gridHov = getHoveredGridIndex((int) xm, (int) ym);
        if (gridHov >= 0 && gridHov < vis.size()) {
            int vi = vis.get(gridHov);
            ItemStack gridItem = getItemForVis(vi);
            if (!gridItem.isEmpty()) {
                // Q → 丢弃 1 个；Ctrl+Q → 丢弃 1 组
                if (minecraft.options.keyDrop.matches(ev)) {
                    if (!player.canDropItems()) {
                        return true;
                    }
                    ItemStack toDrop = gridItem.copy();
                    toDrop.setCount(ev.hasControlDown() ? toDrop.getMaxStackSize() : 1);
                    player.drop(toDrop, true);
                    minecraft.gameMode.handleCreativeModeItemDrop(toDrop);
                    DebugLogger.info("YzuCreativeInventoryScreen",
                            "grid drop: %s x%d (ctrl=%b)", toDrop.getHoverName().getString(),
                            toDrop.getCount(), ev.hasControlDown());
                    return true;
                }

                ItemStack carried = player.containerMenu.getCarried();
                if (minecraft.options.keyPickItem.matches(ev)) {
                    if (carried.isEmpty()) {
                        pickupItem(gridItem, gridItem.getMaxStackSize());
                    }
                    return true;
                }
                if (carried.isEmpty()) {
                    ItemStack fullStack = gridItem.copy();
                    fullStack.setCount(fullStack.getMaxStackSize());

                    // F → 将整组物品复制到副手（Inventory 索引 40，对应 InventoryMenu 槽位 45）
                    if (minecraft.options.keySwapOffhand.matches(ev)) {
                        player.getInventory().setItem(40, fullStack);
                        player.inventoryMenu.broadcastChanges();
                        DebugLogger.info("YzuCreativeInventoryScreen", "grid to offhand: %s x%d",
                                fullStack.getHoverName().getString(), fullStack.getCount());
                        return true;
                    }

                    // 1-9 → 将整组物品放入对应快捷栏槽位
                    for (int i = 0; i < 9; i++) {
                        if (minecraft.options.keyHotbarSlots[i].matches(ev)) {
                            player.getInventory().setItem(i, fullStack);
                            player.inventoryMenu.broadcastChanges();
                            DebugLogger.info("YzuCreativeInventoryScreen",
                                    "grid to hotbar[%d]: %s x%d", i,
                                    fullStack.getHoverName().getString(), fullStack.getCount());
                            return true;
                        }
                    }
                }
            }
        }

        // 获取鼠标悬停的右侧面板槽位（键盘快捷键仅对真实背包槽位有效）
        int hovSlot = getRightPanelSlotAt((int) xm, (int) ym);
        if (hovSlot >= 0) {
            // Q → 丢弃（Ctrl+Q 整组丢弃）
            // 参照原版 CreativeModeInventoryScreen.slotClicked() 中 INVENTORY 标签页的 THROW 处理：
            // 不通过 inventoryMenu.clicked(THROW) 走标准容器点击协议（该路径在创造模式下服务器不
            // 会正确同步槽位状态，导致物品视觉上消失但服务端仍保留），而是直接操作本地槽位 +
            // handleCreativeModeItemDrop / handleCreativeModeItemAdd 完成客户端-服务端同步。
            if (minecraft.options.keyDrop.matches(ev)) {
                if (player.canDropItems()) {
                    var slot = player.inventoryMenu.getSlot(hovSlot);
                    if (slot != null && slot.hasItem()) {
                        int count = ev.hasControlDown() ? slot.getItem().getMaxStackSize() : 1;
                        ItemStack removed = slot.remove(count);
                        ItemStack remaining = slot.getItem();
                        minecraft.player.drop(removed, true);
                        minecraft.gameMode.handleCreativeModeItemDrop(removed);
                        minecraft.gameMode.handleCreativeModeItemAdd(
                                remaining.isEmpty() ? ItemStack.EMPTY : remaining, hovSlot);
                        DebugLogger.info("YzuCreativeInventoryScreen",
                                "right-panel drop: slot=%d %s remove=%d remain=%d (ctrl=%b)",
                                hovSlot, removed.getHoverName().getString(),
                                removed.getCount(), remaining.getCount(), ev.hasControlDown());
                    }
                }
                return true;
            }
            // F → 交换副手槽（slot 45, button=40）
            if (minecraft.options.keySwapOffhand.matches(ev)) {
                if (player.containerMenu.getCarried().isEmpty()) {
                    clickInventorySlot(hovSlot, 40, ContainerInput.SWAP);
                }
                return true;
            }
            // 1-9 → 与对应快捷栏槽位交换
            for (int i = 0; i < 9; i++) {
                if (minecraft.options.keyHotbarSlots[i].matches(ev)) {
                    if (player.containerMenu.getCarried().isEmpty()) {
                        clickInventorySlot(hovSlot, i, ContainerInput.SWAP);
                    }
                    return true;
                }
            }
            // 选取方块键（默认中键，可改为键盘键）→ 克隆
            if (minecraft.options.keyPickItem.matches(ev)) {
                clickInventorySlot(hovSlot, 0, ContainerInput.CLONE);
                return true;
            }
        }

        return super.keyPressed(ev);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 绘制圆角矩形。
     * <p>
     * 每次 {@code g.fill} 都会往 GuiRenderState 里塞一个 ColoredRectangleRenderState
     * （并复制一份 Matrix3x2f），所以圆角必须按<b>整行扫描线</b>输出，不能逐像素填。
     * <ul>
     * <li>{@code r <= 3}：圆角判定 {@code i²+j² < r²} 对所有角像素恒成立
     * （{@code 2(r-1)² < r²}），结果与实心矩形完全一致 → 只发 1 个 fill；</li>
     * <li>其余：中间整块 1 个 + 上下各 {@code r} 行、每行 1 个，共 {@code 1 + 2r} 个。</li>
     * </ul>
     * 输出像素与逐像素版本完全相同：16×16 槽位从 39 个 fill 降到 1 个，
     * 168×356 面板（r=6）从 135 个降到 13 个。
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
        // 上下圆角区：每行一条扫描线，行内水平跨度由圆角判定给出
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
