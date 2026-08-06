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
    /** 槽位选中背景色（37.5% 白色） */
    private static final int SLOT_SELECTED_COLOR = 0x60FFFFFF;
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
    /** 高亮框内尺寸（填充，比外框小 2px 形成 1px 边框） */
    private static final int SELECTION_INNER_SIZE = 20;
    /** 高亮框内圆角半径 */
    private static final int SELECTION_INNER_RADIUS = 3;
    /** 高亮框边框颜色（不透明白色，清晰勾勒边缘） */
    private static final int SELECTION_BORDER_COLOR = 0xFFFFFFFF;
    /** 高亮框填充颜色（69% 白色，对齐 YZUI 按钮悬停态） */
    private static final int SELECTION_FILL_COLOR = 0xB0FFFFFF;

    // ===== 副手槽常量 =====
    /** 副手槽尺寸 */
    private static final int OFFHAND_SIZE = 22;
    /** 副手槽圆角半径 */
    private static final int OFFHAND_RADIUS = 3;
    /** 副手槽背景色（褐色调） */
    private static final int OFFHAND_COLOR = 0x60A08050;
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
    /** 动画插值速度（每帧趋近比例，经帧率无关归一化） */
    private static final float ANIM_SPEED = 0.28f;

    // ===== 数字指示器常量 =====
    /** 非选中槽位数字颜色（半透明白色） */
    private static final int NUM_COLOR_NORMAL = 0x90FFFFFF;
    /** 选中槽位数字颜色（不透明白色） */
    private static final int NUM_COLOR_SELECTED = 0xFFFFFFFF;
    /** 数字 X 偏移（槽位内左上角） */
    private static final int NUM_OFFSET_X = 2;
    /** 数字 Y 偏移（槽位内左上角） */
    private static final int NUM_OFFSET_Y = 1;

    // ===== 动画状态（@Unique，服务端无关） =====
    /** 当前动画位置（0~8 的浮点数） */
    private static float animSelectedSlot = 0f;
    /** 上次检测到的实际选中槽位（用于变更检测） */
    private static int lastKnownSelectedSlot = 0;
    /** 动画是否已初始化 */
    private static boolean animInitialized = false;

    private HotbarRenderer() {
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
            lastKnownSelectedSlot = currentSlot;
            animInitialized = true;
        }

        // === 动画更新 ===
        if (currentSlot != lastKnownSelectedSlot) {
            DebugLogger.debug(LOG_TAG,
                    "槽位切换: animPos=%.2f → target=%d, 前一=%d",
                    animSelectedSlot, currentSlot, lastKnownSelectedSlot);
            lastKnownSelectedSlot = currentSlot;
        }

        // 帧率无关的平滑插值
        float delta = deltaTracker.getGameTimeDeltaTicks();
        float t = 1.0f - (float) Math.pow(1.0f - ANIM_SPEED, delta * 60.0f);
        animSelectedSlot += (currentSlot - animSelectedSlot) * t;

        // 吸附：距离目标足够近时直接跳至目标位置
        if (Math.abs(animSelectedSlot - currentSlot) < 0.005f) {
            animSelectedSlot = currentSlot;
        }

        // === 1. 面板背景 ===
        fillRoundedRect(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, PANEL_RADIUS, PANEL_BG);

        // === 2. 副手槽（仅当副手有物品时显示） ===
        ItemStack offhandItem = player.getOffhandItem();
        if (!offhandItem.isEmpty()) {
            boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
            int offhandX;
            if (leftHanded) {
                offhandX = panelX + PANEL_WIDTH + OFFHAND_GAP;
            } else {
                offhandX = panelX - OFFHAND_SIZE - OFFHAND_GAP;
            }
            int offhandY = panelY + (PANEL_HEIGHT - OFFHAND_SIZE) / 2;

            drawOffhandSlot(graphics, player, offhandItem, offhandX, offhandY);
        }

        // === 3. 动画选中高亮框（边框 + 填充，1px 白色描边 + 高不透明度白色填充） ===
        int selOuterX = panelX + SLOT_PADDING_X
                + Math.round(animSelectedSlot * SLOT_SPACING)
                - (SELECTION_OUTER_SIZE - SLOT_SIZE) / 2;
        int selOuterY = panelY + SLOT_PADDING_Y - (SELECTION_OUTER_SIZE - SLOT_SIZE) / 2;
        // 外框（1px 不透明白色描边）
        fillRoundedRect(graphics, selOuterX, selOuterY,
                SELECTION_OUTER_SIZE, SELECTION_OUTER_SIZE,
                SELECTION_OUTER_RADIUS, SELECTION_BORDER_COLOR);
        // 内填充（69% 白色，向内收缩 1px 形成边框效果）
        fillRoundedRect(graphics, selOuterX + 1, selOuterY + 1,
                SELECTION_INNER_SIZE, SELECTION_INNER_SIZE,
                SELECTION_INNER_RADIUS, SELECTION_FILL_COLOR);

        // === 4. 9 个槽位 + 物品 + 数字 ===
        Font font = client.font;
        for (int i = 0; i < 9; i++) {
            int slotX = panelX + SLOT_PADDING_X + i * SLOT_SPACING;
            int slotY = panelY + SLOT_PADDING_Y;

            // 槽位背景：当前选中槽位更亮
            int slotBg = (i == currentSlot) ? SLOT_SELECTED_COLOR : SLOT_COLOR;
            fillRoundedRect(graphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, slotBg);

            // 物品
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                graphics.item(stack, slotX + ITEM_INSET, slotY + ITEM_INSET);
                graphics.itemDecorations(font, stack, slotX + ITEM_INSET, slotY + ITEM_INSET);
            }

            // 数字指示器
            String numText = String.valueOf(i + 1);
            int numColor = (i == currentSlot) ? NUM_COLOR_SELECTED : NUM_COLOR_NORMAL;
            graphics.text(font, numText,
                    slotX + NUM_OFFSET_X, slotY + NUM_OFFSET_Y,
                    numColor, true);
        }

        // === 5. 攻击冷却指示器（当选项设为「热键栏」时显示） ===
        drawAttackIndicator(graphics, client, panelX, panelY);

        DebugLogger.debug(LOG_TAG,
                "热键栏已渲染: animPos=%.3f, actual=%d, panel=(%d,%d), delta=%.4f",
                animSelectedSlot, currentSlot, panelX, panelY, delta);
    }

    // ===== 副手槽绘制 =====

    private static void drawOffhandSlot(GuiGraphicsExtractor graphics, Player player,
            ItemStack offhandStack, int x, int y) {
        fillRoundedRect(graphics, x, y, OFFHAND_SIZE, OFFHAND_SIZE,
                OFFHAND_RADIUS, OFFHAND_COLOR);

        if (!offhandStack.isEmpty()) {
            graphics.item(offhandStack, x + OFFHAND_ITEM_INSET, y + OFFHAND_ITEM_INSET);
            Font font = Minecraft.getInstance().font;
            graphics.itemDecorations(font, offhandStack,
                    x + OFFHAND_ITEM_INSET, y + OFFHAND_ITEM_INSET);
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

        DebugLogger.debug(LOG_TAG,
                "攻击冷却指示器: strength=%.3f, progressW=%d, pos=(%d,%d)",
                attackStrength, progressWidth, indicatorX, indicatorY);
    }

    // ===== 圆角矩形绘制（与 YzuInventoryScreen.fillRoundedRect 一致） =====

    /**
     * 使用像素填充方式绘制实心圆角矩形。
     *
     * @param g     GuiGraphicsExtractor 实例
     * @param x     左上角 X 坐标
     * @param y     左上角 Y 坐标
     * @param w     宽度
     * @param h     高度
     * @param r     圆角半径
     * @param color ARGB 颜色
     */
    private static void fillRoundedRect(GuiGraphicsExtractor g,
            int x, int y, int w, int h, int r, int color) {
        // 主体（排除左右两端的完整高度列）
        if (w > r * 2) {
            g.fill(x + r, y, x + w - r, y + h, color);
        } else {
            // 宽度不足以容纳两端圆角时回退为全宽
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        // 左右边缘（排除上下角落区域）
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // 四角圆形填充
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    // 左上角
                    g.fill(x + r - i - 1, y + r - j - 1,
                            x + r - i, y + r - j, color);
                    // 右上角
                    g.fill(x + w - r + i, y + r - j - 1,
                            x + w - r + i + 1, y + r - j, color);
                    // 左下角
                    g.fill(x + r - i - 1, y + h - r + j,
                            x + r - i, y + h - r + j + 1, color);
                    // 右下角
                    g.fill(x + w - r + i, y + h - r + j,
                            x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }
}
