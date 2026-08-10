package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 热键栏渲染器。
 *
 * <p>替换原版热键栏，采用 YZUI 白底圆角风格，提供以下增强：</p>
 * <ul>
 *   <li>半透明白色圆角面板 + 圆角槽位背景</li>
 *   <li>选中槽位高亮框的平滑过渡动画（滚轮 / 数字键切换均生效）</li>
 *   <li>每个槽位左上角显示对应数字键编号（1~9）</li>
 *   <li>副手槽使用褐色调以与主槽位区分</li>
 * </ul>
 *
 * <p>布局设计（右撇子默认）：</p>
 * <pre>
 * ┌─┐ ┌──────────────────────────────────────────────────┐ ┌─┐
 * │副│ │ 1  2  3  4  5  6  7  8  9                       │ │副│
 * │手│ │ [][][][][][][][][]                              │ │手│
 * └─┘ └──────────────────────────────────────────────────┘ └─┘
 *  左                                                          右
 * </pre>
 */
@SuppressWarnings("null")
public final class HotbarRenderer {

    private static final String LOG_TAG = "HotbarRenderer";

    // ===== 面板常量 =====
    /** 热键栏面板宽度 */
    private static final int PANEL_WIDTH = 184;
    /** 热键栏面板高度 */
    private static final int PANEL_HEIGHT = 24;
    /** 面板圆角半径 */
    private static final int PANEL_RADIUS = 6;
    /** 面板背景色（50% 白色） */
    private static final int PANEL_BG = 0x80FFFFFF;
    /** 面板距屏幕底部偏移 */
    private static final int PANEL_BOTTOM_OFFSET = 2;

    // ===== 槽位常量 =====
    /** 单个槽位尺寸 */
    private static final int SLOT_SIZE = 18;
    /** 槽位圆角半径 */
    private static final int SLOT_RADIUS = 3;
    /** 槽位默认背景色（25% 白色） */
    private static final int SLOT_COLOR = 0x40FFFFFF;
    /** 槽位选中背景色（50% 白色，足够覆盖底层高亮） */
    private static final int SLOT_SELECTED_COLOR = 0x80FFFFFF;
    /** 槽位距面板左边缘的水平内边距 */
    private static final int SLOT_PADDING_X = 2;
    /** 槽位距面板上边缘的垂直内边距 */
    private static final int SLOT_PADDING_Y = 3;
    /** 槽位中心间距（对齐原版 20px 间隔） */
    private static final int SLOT_SPACING = 20;
    /** 物品在槽位内的偏移（将 16×16 物品居中于 18×18 槽位） */
    private static final int ITEM_INSET = 1;

    // ===== 选中高亮常量 =====
    /** 高亮框外尺寸（外框） */
    private static final int SELECTION_OUTER_SIZE = 22;
    /** 高亮框外圆角半径 */
    private static final int SELECTION_OUTER_RADIUS = 4;
    /** 高亮框内圆角半径（内填充尺寸恒为外框 -2，由 RoundedRect.fillWithBorder 推导） */
    private static final int SELECTION_INNER_RADIUS = 3;
    /** 高亮框边框颜色（不透明白色，清晰勾勒边缘） */
    private static final int SELECTION_BORDER_COLOR = 0xFFFFFFFF;
    /** 高亮框填充颜色（69% 白色，对齐 YZUI 按钮悬停态） */
    private static final int SELECTION_FILL_COLOR = 0xB0FFFFFF;

    // ===== 副手槽常量 =====
    /** 副手槽外尺寸（外框） */
    private static final int OFFHAND_OUTER_SIZE = 22;
    /** 副手槽外圆角半径 */
    private static final int OFFHAND_OUTER_RADIUS = 4;
    /** 副手槽内圆角半径（内填充尺寸恒为外框 -2，由 RoundedRect.fillWithBorder 推导） */
    private static final int OFFHAND_INNER_RADIUS = 3;
    /** 副手槽背景色（褐色调） */
    private static final int OFFHAND_FILL_COLOR = 0x60A08050;
    /** 副手槽边框色（半透明白色，清晰勾勒圆角边缘） */
    private static final int OFFHAND_BORDER_COLOR = 0xB0CCBBAA;
    /** 副手槽与面板的间距 */
    private static final int OFFHAND_GAP = 3;
    /** 物品在副手槽内的偏移 */
    private static final int OFFHAND_ITEM_INSET = 3;

    // ===== 攻击冷却指示器常量 =====
    /** 攻击冷却指示器尺寸 */
    private static final int ATTACK_INDICATOR_SIZE = 18;
    /** 攻击冷却指示器与面板的间距 */
    private static final int ATTACK_INDICATOR_GAP = 3;
    /** 攻击冷却指示器背景精灵 */
    private static final Identifier ATTACK_INDICATOR_BG =
            Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    /** 攻击冷却指示器进度精灵 */
    private static final Identifier ATTACK_INDICATOR_PROGRESS =
            Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

    // ===== 动画常量 =====
    /** 选中槽位动画速度（指数衰减因子，越大越快；8.0 ≈ 290ms 达 90% 目标） */
    private static final float ANIM_SPEED = 8.0f;

    // ===== 副手槽动画常量 =====
    /** 副手槽滑入滑出距离（px） */
    private static final int OFFHAND_SLIDE_DIST = 12;
    /** 副手槽动画速度（略快于选中槽位动画，≈230ms 达 90%） */
    private static final float OFFHAND_ANIM_SPEED = 10.0f;

    // ===== 数字指示器常量 =====
    /** 非选中槽位数字颜色（淡半透明，不喧宾夺主） */
    private static final int NUM_COLOR_NORMAL = 0x60FFFFFF;
    /** 选中槽位数字颜色（半透明白色） */
    private static final int NUM_COLOR_SELECTED = 0xA0FFFFFF;
    /** 数字 X 偏移（槽位内左上角，尽可能靠边） */
    private static final int NUM_OFFSET_X = 1;
    /** 数字 Y 偏移（槽位内左上角） */
    private static final int NUM_OFFSET_Y = 0;
    /** 预分配的槽位编号文本（1~9），避免每帧 9 次 {@code String.valueOf} 分配 */
    private static final String[] SLOT_NUMBERS =
            { "1", "2", "3", "4", "5", "6", "7", "8", "9" };

    // ===== 动画状态（@Unique，服务端无关） =====
    /** 当前动画位置（0~8 的浮点数） */
    private static float animSelectedSlot = 0f;
    /** 上次检测到的实际选中槽位（用于变更检测） */
    private static int lastKnownSelectedSlot = 0;
    /** 动画是否已初始化 */
    private static boolean animInitialized = false;
    /** 副手槽动画进度（0.0=隐藏，1.0=完全可见） */
    private static float offhandAnimProgress = 0f;
    /** 上次副手是否有物品（用于检测过渡） */
    private static boolean lastOffhandHasItem = false;
    /** 副手动画是否已初始化 */
    private static boolean offhandAnimInit = false;
    /** 选中高亮虚拟目标（可超出 0~8，用于循环包装动画） */
    private static float virtualTarget = 0f;
    /** 本帧滚轮方向（+1=前滚/向右, -1=后滚/向左, 0=无滚轮输入） */
    private static volatile int scrollDirectionThisFrame = 0;

    private HotbarRenderer() {
    }

    // ===== 外部标记接口 =====

    /** 由 ScrollHotbarInputMixin 调用，标记滚轮方向 (+1 前滚 / -1 后滚) */
    public static void markScrollInput(int direction) {
        scrollDirectionThisFrame = direction;
    }

    // ===== 公共渲染入口 =====

    /**
     * 渲染 YZUI 风格热键栏。
     *
     * @param graphics     GuiGraphicsExtractor 实例
     * @param deltaTracker 帧时间追踪器（用于平滑动画）
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();

        // === 计算面板位置 ===
        int panelX = (sw - PANEL_WIDTH) / 2;
        int panelY = sh - PANEL_HEIGHT - PANEL_BOTTOM_OFFSET;

        int currentSlot = player.getInventory().getSelectedSlot();

        // === 动画初始化 ===
        if (!animInitialized) {
            animSelectedSlot = currentSlot;
            virtualTarget = currentSlot;
            lastKnownSelectedSlot = currentSlot;
            animInitialized = true;
        }

        // === 动画更新（连续空间循环包装，仅滚轮触发） ===
        if (currentSlot != lastKnownSelectedSlot) {
            int scrollDir = scrollDirectionThisFrame;
            scrollDirectionThisFrame = 0; // 消费

            if (scrollDir != 0) {
                // 滚轮输入：在连续空间中追踪目标，支持跨边界累计滚动
                if (scrollDir > 0 && currentSlot < lastKnownSelectedSlot) {
                    // 向前滚过 8→0 边界：target += 9 进入连续右空间
                    virtualTarget = currentSlot + 9.0f;
                } else if (scrollDir < 0 && currentSlot > lastKnownSelectedSlot) {
                    // 向后滚过 0→8 边界：target -= 9 进入连续左空间
                    virtualTarget = currentSlot - 9.0f;
                } else if (virtualTarget > 8.0f && scrollDir > 0) {
                    // 包装动画进行中继续同向前滚，延长虚拟目标
                    virtualTarget += 1.0f;
                } else if (virtualTarget < 0.0f && scrollDir < 0) {
                    // 包装动画进行中继续同向后滚
                    virtualTarget -= 1.0f;
                } else {
                    virtualTarget = currentSlot;
                }
                DebugLogger.debug(LOG_TAG,
                        "槽位切换(滚轮 dir=%d): animPos=%.2f → 实际=%d, 虚拟目标=%.1f",
                        scrollDir, animSelectedSlot, currentSlot, virtualTarget);
            } else {
                // 数字键：直接跳转，不触发包装
                virtualTarget = currentSlot;
                DebugLogger.debug(LOG_TAG,
                        "槽位切换(按键): animPos=%.2f → 实际=%d",
                        animSelectedSlot, currentSlot);
            }
            lastKnownSelectedSlot = currentSlot;
        }

        // 时间驱动平滑插值
        float deltaSec = deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        float animT = 1.0f - (float) Math.exp(-ANIM_SPEED * deltaSec);
        animSelectedSlot += (virtualTarget - animSelectedSlot) * animT;

        // 包装瞬移：高亮滑出屏幕边缘后瞬间跳到对侧，virtualTarget 同步偏移
        if (virtualTarget > 8.0f && animSelectedSlot > 8.8f) {
            float snap = 9.0f;
            animSelectedSlot -= snap;
            virtualTarget -= snap;
            DebugLogger.debug(LOG_TAG,
                    "包装瞬移(右→左): animPos=%.3f, 目标=%.1f",
                    animSelectedSlot, virtualTarget);
        } else if (virtualTarget < 0.0f && animSelectedSlot < -0.8f) {
            float snap = 9.0f;
            animSelectedSlot += snap;
            virtualTarget += snap;
            DebugLogger.debug(LOG_TAG,
                    "包装瞬移(左→右): animPos=%.3f, 目标=%.1f",
                    animSelectedSlot, virtualTarget);
        }

        // 吸附
        if (Math.abs(animSelectedSlot - virtualTarget) < 0.005f) {
            animSelectedSlot = virtualTarget;
        }

        // === 1. 面板背景 ===
        RoundedRect.fillOrSquare(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS, PANEL_BG);

        // === 2. 副手槽（带滑入滑出动画） ===
        ItemStack offhandItem = player.getOffhandItem();
        boolean offhandHasItem = !offhandItem.isEmpty();

        if (!offhandAnimInit) {
            offhandAnimProgress = offhandHasItem ? 1.0f : 0.0f;
            lastOffhandHasItem = offhandHasItem;
            offhandAnimInit = true;
        }

        if (offhandHasItem != lastOffhandHasItem) {
            lastOffhandHasItem = offhandHasItem;
            DebugLogger.debug(LOG_TAG,
                    "副手槽过渡: hasItem=%b, animProgress=%.3f",
                    offhandHasItem, offhandAnimProgress);
        }

        // 指数衰减动画（与选中槽位动画共用 animT）
        float offhandTarget = offhandHasItem ? 1.0f : 0.0f;
        float offhandT = 1.0f - (float) Math.exp(-OFFHAND_ANIM_SPEED * deltaSec);
        offhandAnimProgress += (offhandTarget - offhandAnimProgress) * offhandT;
        if (Math.abs(offhandAnimProgress - offhandTarget) < 0.005f) {
            offhandAnimProgress = offhandTarget;
        }

        // 仅在动画进度 > 0 时渲染（淡出完毕后完全移除）
        if (offhandAnimProgress > 0.005f || offhandHasItem) {
            boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
            int offhandX;
            if (leftHanded) {
                offhandX = panelX + PANEL_WIDTH + OFFHAND_GAP;
            } else {
                offhandX = panelX - OFFHAND_OUTER_SIZE - OFFHAND_GAP;
            }
            int offhandBaseY = panelY + (PANEL_HEIGHT - OFFHAND_OUTER_SIZE) / 2;

            // 滑入（从下向上）：progress 0→1 时 Y 从 baseY+SLIDE_DIST → baseY
            // 滑出（从上向下）：progress 1→0 时 Y 从 baseY → baseY+SLIDE_DIST
            int offhandSlideY = offhandBaseY
                    + (int) ((1.0f - offhandAnimProgress) * OFFHAND_SLIDE_DIST);

            drawOffhandSlot(graphics, player, offhandItem,
                    offhandX, offhandSlideY, offhandAnimProgress);
        }

        // === 3. 动画选中高亮框（绘制于槽位之下，scissor 裁剪到面板内） ===
        int selOuterX = panelX + SLOT_PADDING_X
                + Math.round(animSelectedSlot * SLOT_SPACING)
                - (SELECTION_OUTER_SIZE - SLOT_SIZE) / 2;
        int selOuterY = panelY + SLOT_PADDING_Y - (SELECTION_OUTER_SIZE - SLOT_SIZE) / 2;
        // enableScissor 签名为 (left, top, right, bottom)，四个均为边界坐标
        graphics.enableScissor(panelX, panelY,
                panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT);
        try {
            RoundedRect.fillWithBorder(graphics, selOuterX, selOuterY,
                    SELECTION_OUTER_SIZE, SELECTION_OUTER_SIZE,
                    SELECTION_OUTER_RADIUS, SELECTION_INNER_RADIUS,
                    SELECTION_BORDER_COLOR, SELECTION_FILL_COLOR);
        } finally {
            graphics.disableScissor();
        }

        // === 4. 9 个槽位 + 物品 + 数字（绘制于高亮之上） ===
        Font font = client.font;
        // 槽位背景：包装动画期间保持离开槽位高亮，瞬移后切换到目标槽位。
        // 该判定与槽位下标无关，提到循环外只算一次。
        int highlightedSlot;
        if (virtualTarget > 8.0f) {
            highlightedSlot = 8;  // 向右跨越边界中，保持 slot 8 高亮
        } else if (virtualTarget < 0.0f) {
            highlightedSlot = 0;  // 向左跨越边界中，保持 slot 0 高亮
        } else {
            highlightedSlot = currentSlot;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < 9; i++) {
            int slotX = panelX + SLOT_PADDING_X + i * SLOT_SPACING;
            int slotY = panelY + SLOT_PADDING_Y;

            int slotBg = (i == highlightedSlot) ? SLOT_SELECTED_COLOR : SLOT_COLOR;
            RoundedRect.fillOrSquare(graphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, slotBg);

            // 物品
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                graphics.item(stack, slotX + ITEM_INSET, slotY + ITEM_INSET);
                graphics.itemDecorations(font, stack, slotX + ITEM_INSET, slotY + ITEM_INSET);
                ItemBorderRenderer.renderBorder(graphics, slotX + ITEM_INSET, slotY + ITEM_INSET, stack);
            }

            // 数字指示器（复用常量字符串，避免每帧 9 次 String 分配）
            int numColor = (i == currentSlot) ? NUM_COLOR_SELECTED : NUM_COLOR_NORMAL;
            graphics.text(font, SLOT_NUMBERS[i],
                    slotX + NUM_OFFSET_X, slotY + NUM_OFFSET_Y,
                    numColor, false);
        }

        // === 5. 攻击冷却指示器（当选项设为「热键栏」时显示） ===
        drawAttackIndicator(graphics, client, panelX, panelY);
    }

    // ===== 副手槽绘制 =====

    private static void drawOffhandSlot(GuiGraphicsExtractor graphics, Player player,
            ItemStack offhandStack, int x, int y, float alpha) {
        // 外框（颜色 alpha 通道乘以动画进度）
        int borderAlpha = (int) ((OFFHAND_BORDER_COLOR >>> 24) * alpha);
        int fillAlpha = (int) ((OFFHAND_FILL_COLOR >>> 24) * alpha);
        int animBorderColor = (borderAlpha << 24) | (OFFHAND_BORDER_COLOR & 0x00FFFFFF);
        int animFillColor = (fillAlpha << 24) | (OFFHAND_FILL_COLOR & 0x00FFFFFF);

        RoundedRect.fillWithBorder(graphics, x, y,
                OFFHAND_OUTER_SIZE, OFFHAND_OUTER_SIZE,
                OFFHAND_OUTER_RADIUS, OFFHAND_INNER_RADIUS,
                animBorderColor, animFillColor);

        if (!offhandStack.isEmpty()) {
            graphics.item(offhandStack, x + OFFHAND_ITEM_INSET, y + OFFHAND_ITEM_INSET);
            Font font = Minecraft.getInstance().font;
            graphics.itemDecorations(font, offhandStack,
                    x + OFFHAND_ITEM_INSET, y + OFFHAND_ITEM_INSET);
            ItemBorderRenderer.renderBorder(graphics,
                    x + OFFHAND_ITEM_INSET, y + OFFHAND_ITEM_INSET, offhandStack);
        }
    }

    // ===== 攻击冷却指示器绘制 =====

    /**
     * 当攻击指示器选项设为「热键栏」且攻击未完全冷却时，
     * 在热键栏右侧绘制攻击冷却指示器。
     */
    private static void drawAttackIndicator(GuiGraphicsExtractor graphics,
            Minecraft client, int panelX, int panelY) {
        if (!(client.player instanceof LocalPlayer localPlayer)) {
            return;
        }

        // 检查选项：仅在「热键栏」模式下显示
        if (client.options.attackIndicator().get() != AttackIndicatorStatus.HOTBAR) {
            return;
        }

        float attackStrength = localPlayer.getAttackStrengthScale(0f);
        if (attackStrength >= 1.0f) {
            return; // 已完全冷却，不显示
        }

        int indicatorX = panelX + PANEL_WIDTH + ATTACK_INDICATOR_GAP;
        int indicatorY = panelY + (PANEL_HEIGHT - ATTACK_INDICATOR_SIZE) / 2;

        // 背景
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                ATTACK_INDICATOR_BG,
                indicatorX, indicatorY,
                ATTACK_INDICATOR_SIZE, ATTACK_INDICATOR_SIZE);

        // 进度（根据冷却比例裁切宽度）
        int progressWidth = (int) (attackStrength * (float) ATTACK_INDICATOR_SIZE);
        if (progressWidth > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    ATTACK_INDICATOR_PROGRESS,
                    ATTACK_INDICATOR_SIZE, ATTACK_INDICATOR_SIZE,
                    0, 0,
                    indicatorX, indicatorY,
                    progressWidth, ATTACK_INDICATOR_SIZE);
        }

        // 先判等级再取参数：varargs 数组与 4 个基本类型装箱在每帧路径上是白扔的垃圾
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(LOG_TAG,
                    "攻击冷却指示器: strength=%.3f, progressW=%d, pos=(%d,%d)",
                    attackStrength, progressWidth, indicatorX, indicatorY);
        }
    }
}
