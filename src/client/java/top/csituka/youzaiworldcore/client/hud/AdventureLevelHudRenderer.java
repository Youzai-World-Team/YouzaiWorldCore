package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

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
    private static int displayNeededExp = 200;
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

    // ─── 字符串缓存（避免每帧拼接） ───
    private static String cachedNormalText = "";
    private static String cachedLevelUpText = "";
    private static int cachedLevel = -1;
    private static int cachedCurrentExp = -1;
    private static int cachedNeededExp = -1;
    private static final int LEVEL_UP_TOTAL_MS = LEVEL_UP_CROSSFADE_MS * 2 + LEVEL_UP_HOLD_MS;

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
            if (animState == AnimState.HIDDEN) {
                animProgress = 0.0f;
            }
            animState = AnimState.SHOWING;
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

        if (!GuiAnimationController.isEnabled()) {
            if (lastExpGainTime == 0 || now - lastExpGainTime > DISPLAY_DURATION_MS) {
                animState = AnimState.HIDDEN;
                animProgress = 0.0f;
                smoothDisplayExp = 0.0f;
                return;
            }
            animState = AnimState.VISIBLE;
            animProgress = 1.0f;
        } else switch (animState) {
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
        smoothDisplayExp = GuiAnimationController.isEnabled()
                ? smoothDisplayExp + (targetExp - smoothDisplayExp) * Math.min(1.0f, smoothDelta * 8.0f)
                : targetExp;
        if (Math.abs(smoothDisplayExp - targetExp) < 0.001f) smoothDisplayExp = targetExp;

        int alpha = Math.min(255, Math.max(0, (int) (animProgress * 255)));
        int slide = (int) ((1.0f - animProgress) * SLIDE_DIST);

        renderExpBar(graphics, client, slide, alpha);
    }

    private static void renderExpBar(GuiGraphicsExtractor g, Minecraft client, int slide, int alpha) {
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        // 位置：置于 YZUI 状态条之上
        // 计算 YZUI 第一行顶部（参考 HealthBarMixin 的布局逻辑）
        int row1BarY = sh - HealthBarRenderer.Y_OFFSET_FROM_BOTTOM;        // 第一行条顶 Y
        // 第一行文字顶部 ≈ 条顶 - 条高(5) - 文字间距(10) - 字高(9)
        int yzuiTextTop = row1BarY - HealthBarRenderer.BAR_HEIGHT - 10 - 9;

        // 根据左右两列实际占用的最高状态行避让；氧气与魔力同时显示时右列会占到第三行。
        Player player = client.player;
        if (player != null) {
            boolean armorVisible = player.getArmorValue() > 0;
            boolean oxygenVisible = player.getAirSupply() < player.getMaxAirSupply();
            boolean manaVisible = ManaHudRenderer.isYzuiManaBarVisible();

            int upperRows = (armorVisible || oxygenVisible || manaVisible) ? 1 : 0;
            if (oxygenVisible && manaVisible) {
                upperRows = 2;
            }

            if (upperRows > 0) {
                // 首个额外行沿用原有的文字与动画避让；后续行只增加实际的 17px 行距。
                int firstRowOffset = HealthBarRenderer.BAR_HEIGHT + 12 + 10 + 9;
                int additionalRowOffset = (upperRows - 1) * (HealthBarRenderer.BAR_HEIGHT + 12);
                yzuiTextTop -= firstRowOffset + additionalRowOffset;
            }
        }

        // 经验 HUD 置于 YZUI 文字顶部之上，间隔 2px
        int barX = (sw - BAR_WIDTH) / 2;
        int barY = yzuiTextTop - 2 - BAR_HEIGHT + slide;

        // ─── 主题轨道 ───
        g.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT,
                YzuiTheme.multiplyAlpha(YzuiTheme.hudSurface(), alpha / 255f));

        // ─── 经验填充条 ───
        int fillWidth = (int) (smoothDisplayExp * BAR_WIDTH);
        if (fillWidth > 0) {
            int fillColor = YzuiTheme.alpha(YzuiTheme.primary(), alpha / 255f);
            g.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, fillColor);
        }

        // ─── 文字（带升级交叉淡入淡出），仅在值变化时重建字符串 ───
        if (displayLevel != cachedLevel || displayCurrentExp != cachedCurrentExp || displayNeededExp != cachedNeededExp) {
            cachedLevel = displayLevel;
            cachedCurrentExp = displayCurrentExp;
            cachedNeededExp = displayNeededExp;
            cachedNormalText = "冒险等级 Lv." + displayLevel + "  " + displayCurrentExp + " / " + displayNeededExp;
            cachedLevelUpText = "等级提升：" + displayLevel + "级";
        }
        String normalText = cachedNormalText;
        String levelUpText = cachedLevelUpText;

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
        } else if (!GuiAnimationController.isEnabled()) {
            normalAlpha = 0.0f;
            levelUpAlpha = 1.0f;
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

        // 两段交叉淡入淡出的文字共用一块底色，避免叠加半透明卡片。
        int labelWidth = Math.max(normalAlpha > 0.01f ? client.font.width(normalText) : 0,
                levelUpAlpha > 0.01f ? client.font.width(levelUpText) : 0);
        YzuiTheme.hudLabelBackground(g, client.font, labelWidth,
                barX + (BAR_WIDTH - labelWidth) / 2, textY, alpha / 255f);

        // 渲染普通文字（带透明度）
        if (normalAlpha > 0.01f) {
            int normalTextWidth = client.font.width(normalText);
            int normalTextX = barX + (BAR_WIDTH - normalTextWidth) / 2;
            int na = (int) (alpha * normalAlpha);
            YzuiTheme.hudText(g, client.font, normalText, normalTextX, textY,
                    YzuiTheme.text(), na / 255f);
        }

        // 渲染升级文字（带透明度，主题强调色）
        if (levelUpAlpha > 0.01f) {
            int levelUpTextWidth = client.font.width(levelUpText);
            int levelUpTextX = barX + (BAR_WIDTH - levelUpTextWidth) / 2;
            int la = (int) (alpha * levelUpAlpha);
            YzuiTheme.hudText(g, client.font, levelUpText, levelUpTextX, textY,
                    YzuiTheme.primary(), la / 255f);
        }

        // ─── 获得经验飘字 ───
        if (lastGainedExp > 0 && animState != AnimState.HIDDEN) {
            long elapsedSinceGain = System.currentTimeMillis() - lastExpGainTime;
            if (elapsedSinceGain < 2000) {
                String gainText = "+" + lastGainedExp + " 冒险经验";
                int gainAlpha = alpha;
                // 渐隐
                if (GuiAnimationController.isEnabled() && elapsedSinceGain > 1000) {
                    gainAlpha = (int) (alpha * (1.0f - (elapsedSinceGain - 1000) / 1000.0f));
                }
                int gainWidth = client.font.width(gainText);
                int gainX = barX + (BAR_WIDTH - gainWidth) / 2;
                int gainY = textY - 12;
                YzuiTheme.hudLabel(g, client.font, gainText, gainX, gainY,
                        YzuiTheme.success(), Math.max(0, gainAlpha) / 255f);
            }
        }
    }

    @SuppressWarnings("EmptyMethod")
    public static void register() {
        // 占位方法，保持与 ManaHudRenderer 接口一致
    }
}
