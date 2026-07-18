package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 冒险等级 HUD 渲染器。
 * 在玩家获得冒险经验时，在物品栏上方显示经验条、当前等级与进度。
 *
 * <p>动画行为：</p>
 * <ul>
 *   <li>收到经验时 HUD 滑入并显示</li>
 *   <li>最后一次获得经验后持续显示 5 秒，然后滑出隐藏</li>
 * </ul>
 */
@SuppressWarnings("null")
public class AdventureLevelHudRenderer {

    // ─── 布局常量 ───
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 2;
    private static final int SLIDE_DIST = 20;
    private static final int SHOW_MS = 250;
    private static final int HIDE_MS = 300;
    private static final long DISPLAY_DURATION_MS = 5000; // 5 秒无新经验后隐藏

    // ─── 客户端缓存的 HUD 数据（由 LevelExpSyncPayload 更新） ───
    private static int displayLevel = 1;
    private static int displayCurrentExp = 0;
    private static int displayNeededExp = 100;
    private static int lastGainedExp = 0;

    /**
     * 供菜单页面读取当前冒险等级。
     */
    public static int getLevel() { return displayLevel; }

    /**
     * 供菜单页面读取当前等级内的经验进度。
     */
    public static int getCurrentExp() { return displayCurrentExp; }

    /**
     * 供菜单页面读取升至下一级所需的总经验。
     */
    public static int getNeededExp() { return displayNeededExp; }

    // ─── 动画状态 ───
    private enum AnimState { HIDDEN, SHOWING, VISIBLE, HIDING }
    private static AnimState animState = AnimState.HIDDEN;
    private static float animProgress = 0.0f;
    private static long prevFrameTime = 0;
    private static long lastExpGainTime = 0;

    // ─── 升级提示动画 ───
    private static long lastLevelUpTime = 0;
    private static final int LEVEL_UP_CROSSFADE_MS = 500;   // 淡入/淡出各 500ms
    private static final int LEVEL_UP_HOLD_MS = 2000;        // 升级文字保持 2000ms
    private static final int LEVEL_UP_TOTAL_MS = LEVEL_UP_CROSSFADE_MS * 2 + LEVEL_UP_HOLD_MS; // = 2200ms

    // ─── 平滑经验条 ───
    private static float smoothDisplayExp = 0.0f;
    private static long smoothLastUpdate = 0;

    /**
     * 由客户端网络包调用，更新 HUD 数据并触发展示。
     */
    public static void onExpGained(int level, int currentExp, int neededExp, int gainedExp, boolean leveledUp) {
        displayLevel = level;
        displayCurrentExp = currentExp;
        displayNeededExp = neededExp;
        lastGainedExp = gainedExp;
        lastExpGainTime = System.currentTimeMillis();

        // 记录升级时间戳，驱动升级文字动画
        if (leveledUp) {
            lastLevelUpTime = lastExpGainTime;
        }

        // 触发滑入动画
        if (animState == AnimState.HIDDEN || animState == AnimState.HIDING) {
            animState = AnimState.SHOWING;
            if (animState == AnimState.HIDING) {
                // 从当前进度继续
            } else {
                animProgress = 0.0f;
            }
        }
        // 如果已经可见，重置隐藏倒计时
    }

    /**
     * 由 HudMixin 在 HUD 渲染末尾调用。
     */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        if (prevFrameTime == 0) prevFrameTime = now;
        float deltaMs = Math.min(now - prevFrameTime, 100);
        prevFrameTime = now;

        // 动画状态机
        switch (animState) {
            case SHOWING:
                animProgress += deltaMs / SHOW_MS;
                if (animProgress >= 1.0f) {
                    animProgress = 1.0f;
                    animState = AnimState.VISIBLE;
                }
                break;
            case VISIBLE:
                // 检查是否超时应隐藏
                if (now - lastExpGainTime > DISPLAY_DURATION_MS) {
                    animState = AnimState.HIDING;
                }
                break;
            case HIDING:
                animProgress -= deltaMs / HIDE_MS;
                if (animProgress <= 0.0f) {
                    animProgress = 0.0f;
                    animState = AnimState.HIDDEN;
                    smoothDisplayExp = 0.0f;
                    return;
                }
                break;
            case HIDDEN:
            default:
                smoothDisplayExp = 0.0f;
                return;
        }

        // 平滑经验条动画
        if (smoothLastUpdate == 0) smoothLastUpdate = now;
        float smoothDelta = Math.min(now - smoothLastUpdate, 100) / 1000.0f;
        smoothLastUpdate = now;
        float targetExp = displayNeededExp > 0 ? (float) displayCurrentExp / displayNeededExp : 0.0f;
        // 快速逼近（体验优于缓动，但保留平滑感）
        smoothDisplayExp += (targetExp - smoothDisplayExp) * Math.min(1.0f, smoothDelta * 8.0f);
        if (Math.abs(smoothDisplayExp - targetExp) < 0.001f) smoothDisplayExp = targetExp;

        int alpha = Math.min(255, Math.max(0, (int) (animProgress * 255)));
        int slide = (int) ((1.0f - animProgress) * SLIDE_DIST);

        renderExpBar(graphics, client, slide, alpha);
    }

    private static void renderExpBar(GuiGraphicsExtractor g, Minecraft client, int slide, int alpha) {
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        // 位置：物品栏上方，进一步上移
        int barX = (sw - BAR_WIDTH) / 2;
        int barY = sh - 48 + slide;

        // ─── 背景（纯色，无描边） ───
        g.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT,
                packARGB(40, 40, 40, alpha));

        // ─── 经验填充条 ───
        int fillWidth = (int) (smoothDisplayExp * BAR_WIDTH);
        if (fillWidth > 0) {
            int fillColor = packARGB(255, 200 + (int) (55 * smoothDisplayExp), 50, alpha);
            g.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, fillColor);
        }

        // ─── 文字（带升级交叉淡入淡出） ───
        String normalText = "冒险等级 Lv." + displayLevel + "  " + displayCurrentExp + " / " + displayNeededExp;
        String levelUpText = "等级提升：" + displayLevel + "级";

        int textY = barY - 12;

        // 计算升级动画进度
        long now = System.currentTimeMillis();
        long elapsed = now - lastLevelUpTime;
        boolean inLevelUpWindow = elapsed < LEVEL_UP_TOTAL_MS;

        float normalAlpha;  // 普通文字透明度
        float levelUpAlpha;  // 升级文字透明度

        if (!inLevelUpWindow || elapsed < 0) {
            // 不在升级窗口 → 只显示普通文字
            normalAlpha = 1.0f;
            levelUpAlpha = 0.0f;
        } else if (elapsed < LEVEL_UP_CROSSFADE_MS) {
            // 阶段1：淡出普通 → 淡入升级
            float t = (float) elapsed / LEVEL_UP_CROSSFADE_MS;
            normalAlpha = 1.0f - t;
            levelUpAlpha = t;
        } else if (elapsed < LEVEL_UP_CROSSFADE_MS + LEVEL_UP_HOLD_MS) {
            // 阶段2：升级文字完全可见
            normalAlpha = 0.0f;
            levelUpAlpha = 1.0f;
        } else {
            // 阶段3：淡出升级 → 淡入普通
            float t = (float) (elapsed - LEVEL_UP_CROSSFADE_MS - LEVEL_UP_HOLD_MS) / LEVEL_UP_CROSSFADE_MS;
            normalAlpha = t;
            levelUpAlpha = 1.0f - t;
        }

        // 渲染普通文字（带透明度）
        if (normalAlpha > 0.01f) {
            int normalTextWidth = client.font.width(normalText);
            int normalTextX = barX + (BAR_WIDTH - normalTextWidth) / 2;
            int na = (int) (alpha * normalAlpha);
            g.text(client.font, normalText, normalTextX + 1, textY + 1,
                    packARGB(0, 0, 0, na), false);
            g.text(client.font, normalText, normalTextX, textY,
                    packARGB(255, 255, 255, na), false);
        }

        // 渲染升级文字（带透明度，金色）
        if (levelUpAlpha > 0.01f) {
            int levelUpTextWidth = client.font.width(levelUpText);
            int levelUpTextX = barX + (BAR_WIDTH - levelUpTextWidth) / 2;
            int la = (int) (alpha * levelUpAlpha);
            g.text(client.font, levelUpText, levelUpTextX + 1, textY + 1,
                    packARGB(0, 0, 0, la), false);
            g.text(client.font, levelUpText, levelUpTextX, textY,
                    packARGB(255, 215, 0, la), false);
        }

        // ─── 获得经验飘字 ───
        if (lastGainedExp > 0 && animState != AnimState.HIDDEN) {
            long elapsedSinceGain = System.currentTimeMillis() - lastExpGainTime;
            if (elapsedSinceGain < 2000) {
                String gainText = "+" + lastGainedExp + " 冒险经验";
                int gainAlpha = alpha;
                // 渐隐
                if (elapsedSinceGain > 1000) {
                    gainAlpha = (int) (alpha * (1.0f - (elapsedSinceGain - 1000) / 1000.0f));
                }
                int gainWidth = client.font.width(gainText);
                int gainX = barX + (BAR_WIDTH - gainWidth) / 2;
                int gainY = textY - 12;
                g.text(client.font, gainText, gainX, gainY,
                        packARGB(100, 255, 100, Math.max(0, gainAlpha)), false);
            }
        }
    }

    // ─── 颜色工具 ───

    private static int packARGB(int r, int g, int b, int a) {
        return (Math.max(0, Math.min(255, a)) << 24)
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    @SuppressWarnings("EmptyMethod")
    public static void register() {
        // 占位方法，保持与 ManaHudRenderer 接口一致
    }
}
