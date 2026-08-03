package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 邮件界面顶部的浮动提示条。
 * <p>
 * 服务端返回的操作结果（领取 / 撤回 / 权限不足等）原先只发到聊天栏，界面打开时看不见。
 * 本组件在邮件界面顶部居中显示一条彩色提示：淡入 → 停留 3 秒 → 淡出，不阻挡任何点击。
 * 状态为静态单例，因为收包时机与界面实例无关。
 * </p>
 */
@SuppressWarnings("null")
public final class MailToast {

    /** 停留时长（毫秒） */
    private static final long HOLD_MS = 3000L;
    /** 淡入时长（毫秒） */
    private static final long FADE_IN_MS = 150L;
    /** 淡出时长（毫秒） */
    private static final long FADE_OUT_MS = 400L;

    private static String message = "";
    private static boolean success = true;
    private static long shownAt = -1L;

    private MailToast() {
    }

    /**
     * 显示一条提示。
     *
     * @param text      提示文本（空白则忽略）
     * @param isSuccess true 为绿色成功样式，false 为红色失败样式
     */
    public static void show(String text, boolean isSuccess) {
        if (text == null || text.isBlank()) {
            return;
        }
        message = text;
        success = isSuccess;
        shownAt = System.currentTimeMillis();
    }

    /** 立即清除当前提示（切换界面时调用，避免残留）。 */
    public static void clear() {
        shownAt = -1L;
        message = "";
    }

    /** 当前是否有任意邮件界面处于打开状态（决定反馈走提示条还是聊天栏）。 */
    public static boolean isMailScreenOpen() {
        var screen = Minecraft.getInstance().gui.screen();
        return screen instanceof MailScreen
                || screen instanceof MailSentScreen
                || screen instanceof MailComposeScreen;
    }

    /**
     * 在设计空间坐标系内绘制提示条（由各邮件界面在缩放矩阵内调用）。
     *
     * @param graphics    绘制上下文
     * @param font        字体
     * @param designWidth 设计空间宽度，用于水平居中
     */
    public static void render(GuiGraphicsExtractor graphics, Font font, int designWidth) {
        float alpha = currentAlpha();
        if (alpha <= 0.01f) {
            return;
        }

        int textWidth = font.width(message);
        int boxWidth = textWidth + 46;
        int boxHeight = 24;
        int x = (designWidth - boxWidth) / 2;
        int y = 12;

        int accent = success ? MailUi.GREEN : MailUi.RED;
        MailUi.roundedRect(graphics, x, y, boxWidth, boxHeight, 6, withAlpha(0xFF1E1E1E, alpha * 0.95f));
        // 左侧色条标示成败
        MailUi.roundedRect(graphics, x, y, 4, boxHeight, 2, withAlpha(accent, alpha));
        graphics.text(font, success ? "✔" : "✖", x + 14, y + (boxHeight - font.lineHeight) / 2,
                withAlpha(accent, alpha), false);
        graphics.text(font, message, x + 32, y + (boxHeight - font.lineHeight) / 2,
                withAlpha(0xFFFFFFFF, alpha), false);
    }

    /** 按「淡入 / 停留 / 淡出」三段计算当前透明度。 */
    private static float currentAlpha() {
        if (shownAt < 0 || message.isBlank()) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed < 0) {
            return 0f;
        }
        if (elapsed < FADE_IN_MS) {
            return elapsed / (float) FADE_IN_MS;
        }
        if (elapsed < FADE_IN_MS + HOLD_MS) {
            return 1f;
        }
        long fadeElapsed = elapsed - FADE_IN_MS - HOLD_MS;
        if (fadeElapsed >= FADE_OUT_MS) {
            shownAt = -1L;
            return 0f;
        }
        return 1f - fadeElapsed / (float) FADE_OUT_MS;
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round((argb >>> 24) * alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
