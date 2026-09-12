package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.YzuiThemeMode;
import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;

/**
 * YZUI 共用的 MD3 绘制与配色入口。每次绘制读取当前主题，切换无需重新登录或重载资源。
 * 仅提供客户端视觉，不参与网络、权限和物品操作。
 */
public final class YzuiTheme {
    public enum ButtonStyle { TONAL, FILLED, TEXT, DANGER }

    private YzuiTheme() { }

    /** 共用控件在原版页面遵守总开关，自定义页面始终使用主题。 */
    public static boolean enabled() {
        return ClientExternalSettings.isYzuiEnabled() || isCustomScreen(Minecraft.getInstance().gui.screen());
    }

    /** 保留有色文本的色相，并使其在当前提示卡片上具备足够对比度。 */
    public static int tooltipText(int color) {
        int rgb = color & 0xFFFFFF;
        int red = (rgb >>> 16) & 255, green = (rgb >>> 8) & 255, blue = rgb & 255;
        if (red == green && green == blue) return legacyText(color) & 0xFFFFFF;
        return surfaceText(color) & 0xFFFFFF;
    }

    /** 保留文本样式和色相，为服务器提供的有色名称补足卡片上的对比度。 */
    public static FormattedCharSequence readableText(FormattedCharSequence source, int foreground, int background) {
        return sink -> source.accept((index, style, codepoint) -> {
            if (style.getColor() == null) return sink.accept(index, style, codepoint);
            int color = style.getColor().getValue();
            int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
            int adjusted = red == green && green == blue ? foreground : YzuiPalette.readable(color, background);
            return sink.accept(index, style.withColor(adjusted & 0xFFFFFF), codepoint);
        });
    }

    public static YzuiPalette palette() {
        return ClientExternalSettings.getYzuiTheme() == YzuiThemeMode.DARK
                ? YzuiPalette.DARK : YzuiPalette.LIGHT;
    }

    public static boolean frosted() {
        return ClientExternalSettings.getYzuiVisualStyle() == YzuiVisualStyle.FROSTED;
    }

    public static boolean minimal() {
        return ClientExternalSettings.getYzuiVisualStyle() == YzuiVisualStyle.MINIMAL;
    }

    public static boolean isCustomScreen(Screen screen) {
        return screen != null && screen.getClass().getName()
                .startsWith("top.csituka.youzaiworldcore.client.screen.");
    }

    public static int background() { return palette().background(); }
    public static YzuiVisualStyle visualStyle() { return ClientExternalSettings.getYzuiVisualStyle(); }
    public static int surface() { return alpha(palette().surface(), visualStyle().surfaceOpacity()); }
    public static int surfaceLow() { return alpha(palette().surfaceLow(), visualStyle().layerOpacity()); }
    public static int surfaceHigh() { return alpha(palette().surfaceHigh(), visualStyle().layerOpacity()); }
    public static int primary() { return palette().primary(); }
    public static int onPrimary() { return palette().onPrimary(); }
    public static int primaryContainer() { return palette().primaryContainer(); }
    public static int onPrimaryContainer() { return palette().onPrimaryContainer(); }
    public static int secondaryContainer() { return palette().secondaryContainer(); }
    public static int text() { return palette().text(); }
    public static int textMuted() { return surfaceText(palette().textMuted()); }
    public static int outline() { return palette().outline(); }
    public static int outlineVariant() { return palette().outlineVariant(); }
    public static int error() { return surfaceText(palette().error()); }
    public static int errorContainer() { return palette().errorContainer(); }
    public static int success() { return surfaceText(palette().success()); }
    public static int warning() { return surfaceText(palette().warning()); }
    public static int info() { return surfaceText(palette().info()); }
    public static int slot() { return alpha(palette().surfaceHigh(), visualStyle().layerOpacity()); }
    public static int slotHover() { return alpha(mix(slot(), primaryContainer(), 0.28f), 0.66f); }
    public static int selection() { return alpha(primary(), 0.20f); }
    public static int scrim() { return frosted() ? 0x280B1912 : 0x480B1912; }

    private static int surfaceText(int color) {
        return palette().translucentText(color, visualStyle().surfaceOpacity());
    }

    /** 卡片上最不利的世界底色，供带独立颜色样式的文字统一校正。 */
    private static int textBackground() {
        int behind = YzuiPalette.luminance(palette().surface()) < 0.18 ? 0xFFFFFF : 0;
        return YzuiPalette.composite(palette().surface(), behind, visualStyle().surfaceOpacity());
    }

    public static int hudSurface() { return alpha(palette().surface(), visualStyle().hudPanelOpacity()); }
    public static int hudSlot() { return alpha(palette().surfaceHigh(), visualStyle().hudSlotOpacity()); }
    public static int hudSelected() { return alpha(primaryContainer(), visualStyle().hudSelectionOpacity()); }
    public static int hudRow() { return alpha(palette().surfaceLow(), visualStyle().hudSlotOpacity()); }

    /** 兼容旧按钮的显式文字色；中性色与状态色仍随主题即时变化。 */
    public static int legacyText(int color) {
        int rgb = color & 0xFFFFFF;
        if (rgb == 0xFFFFFF || rgb == 0 || rgb == 0x333333 || rgb == 0x404040
                || rgb == (YzuiPalette.LIGHT.text() & 0xFFFFFF)
                || rgb == (YzuiPalette.DARK.text() & 0xFFFFFF)) return text();
        if (((rgb >> 16) & 255) == ((rgb >> 8) & 255) && ((rgb >> 8) & 255) == (rgb & 255)) {
            return textMuted();
        }
        if (rgb == 0xFF5555 || rgb == 0xFF4444
                || rgb == (YzuiPalette.LIGHT.error() & 0xFFFFFF)
                || rgb == (YzuiPalette.DARK.error() & 0xFFFFFF)) return error();
        if (rgb == (YzuiPalette.LIGHT.primary() & 0xFFFFFF)
                || rgb == (YzuiPalette.DARK.primary() & 0xFFFFFF)) return primary();
        return 0xFF000000 | rgb;
    }

    /** 尚无独立容器外壳的功能屏幕使用统一页面卡片，控件坐标由各屏幕管理。 */
    public static void screenCard(GuiGraphicsExtractor g, Screen screen) {
        if (!isCustomScreen(screen)) return;
        String name = screen.getClass().getSimpleName();
        if (name.startsWith("Yzu") || name.startsWith("Mail") || name.equals("YzuiAppearanceScreen")
                || name.equals("FlyBeaconScreen") || name.equals("DecompositionTableScreen")
                || name.equals("WelcomeGuideScreen") || name.equals("TitleManagementScreen")
                || name.equals("ConfigImportSuccessScreen") || name.equals("LoginScreen")
                || name.equals("RegisterScreen") || name.equals("RegistrationEmailScreen")
                || name.equals("PasswordResetScreen") || name.equals("AccountManagementScreen")
                || name.equals("TeleportAnchorNameScreen") || name.equals("WirelessRedstoneChannelScreen")
                || name.equals("LargeSignEditScreen") || name.equals("TeleportAnchorScreen") || name.equals("QuitConfirmationScreen")
                || name.equals("ForcedUpdateScreen") || name.equals("JoinBlockedScreen")) return;
        int w = Math.min(720, screen.width - 20);
        card(g, (screen.width - w) / 2, 8, w, screen.height - 16);
    }

    /** 替换透明度，RGB 保持不变。 */
    public static int alpha(int color, float opacity) {
        return (Math.round(Math.clamp(opacity, 0f, 1f) * 255f) << 24) | (color & 0xFFFFFF);
    }

    /** 叠乘透明度；动画和用户 HUD 设置不会把半透明底板重新变成实色。 */
    public static int multiplyAlpha(int color, float opacity) {
        return alpha(color, ((color >>> 24) / 255f) * opacity);
    }

    /** 同时插值四个颜色通道，适用于状态层和动画。 */
    public static int mix(int from, int to, float amount) {
        float t = Math.clamp(amount, 0f, 1f);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255;
            int b = (to >>> shift) & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    /** 全屏背景在内容矩阵之前绘制，避免模糊文字或让遮罩随页面移动。 */
    public static void backdrop(GuiGraphicsExtractor g) {
        YzuiBackdrop.render(g, Minecraft.getInstance().gui.screen(), 0f);
    }

    /** 圆角卡片；低特效不提交阴影绘制。 */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        card(g, x, y, w, h, 1f);
    }

    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, float opacity) {
        if (w <= 0 || h <= 0) return;
        int radius = Math.min(12, Math.min(w, h) / 2);
        if (!minimal()) {
            RoundedRect.fill(g, x, y + 2, w, h, radius, alpha(0xFF000000, 0.125f * opacity));
        }
        RoundedRect.fill(g, x, y, w, h, radius, multiplyAlpha(surface(), opacity));
        border(g, x, y, w, h, radius, alpha(outlineVariant(), 0.45f * opacity));
    }

    /** 通知等 HUD 卡片采用更通透的底板，不在世界画面上叠加模糊。 */
    public static void hudCard(GuiGraphicsExtractor g, int x, int y, int w, int h, float opacity) {
        int radius = Math.min(8, Math.min(w, h) / 2);
        RoundedRect.fill(g, x, y, w, h, radius, multiplyAlpha(hudSurface(), opacity));
        border(g, x, y, w, h, radius, alpha(outlineVariant(), 0.25f * opacity));
    }

    /** 为状态数值提供紧凑底色，保留文字原坐标与相邻状态行的间距。 */
    public static void hudLabelBackground(GuiGraphicsExtractor g, Font font, int textWidth,
                                         int textX, int textY, float opacity) {
        if (opacity <= 0f || textWidth <= 0) return;
        RoundedRect.fill(g, textX - 3, textY - 1, textWidth + 6, font.lineHeight + 1, 3,
                alpha(palette().surface(), visualStyle().hudLabelOpacity() * opacity));
    }

    /** 绘制带主题底色的 HUD 数值；透明度同时作用于文字和底色。 */
    public static void hudLabel(GuiGraphicsExtractor g, Font font, String text,
                               int textX, int textY, int color, float opacity) {
        if (opacity <= 0f) return;
        hudLabelBackground(g, font, font.width(text), textX, textY, opacity);
        hudText(g, font, text, textX, textY, color, opacity);
    }

    public static void hudLabel(GuiGraphicsExtractor g, Font font, FormattedCharSequence text,
                               int textX, int textY, int color, float opacity) {
        if (opacity <= 0f) return;
        hudLabelBackground(g, font, font.width(text), textX, textY, opacity);
        hudText(g, font, text, textX, textY, color, opacity);
    }

    /** 只绘制文字及贴合字形的轻微对比色阴影，不额外叠加矩形底色。 */
    public static void hudText(GuiGraphicsExtractor g, Font font, String text,
                               int textX, int textY, int color, float opacity) {
        hudText(g, font, Component.literal(text).getVisualOrderText(), textX, textY, color, opacity);
    }

    public static void hudText(GuiGraphicsExtractor g, Font font, FormattedCharSequence text,
                               int textX, int textY, int color, float opacity) {
        if (opacity <= 0f) return;
        // 保留原有主题文字与稀有度色相；透景后通过字形阴影辅助阅读，不再提高底衬不透明度。
        var readable = readableText(text, color, palette().surface());
        FormattedCharSequence shaded = sink -> readable.accept((index, style, codepoint) -> {
            int foreground = style.getColor() == null ? color : style.getColor().getValue();
            int shadow = YzuiPalette.luminance(foreground) < 0.18 ? 0xFFFFFFFF : 0xFF000000;
            return sink.accept(index, style.withShadowColor(alpha(shadow, 0.70f)), codepoint);
        });
        g.text(font, shaded, textX, textY, multiplyAlpha(color, opacity), true);
    }

    /** 圆角描边只绘制边缘，不反复覆盖半透明卡片内部。 */
    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w < 2 || h < 2) return;
        int r = Math.clamp(radius, 0, Math.min(w, h) / 2);
        if (r == 0) {
            g.outline(x, y, w, h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            int inset = r - (int) Math.sqrt(r * r - (r - row - 1) * (r - row - 1));
            int nextInset = row == 0 ? r : r - (int) Math.sqrt(r * r - (r - row) * (r - row));
            int thickness = Math.max(1, nextInset - inset);
            g.fill(x + inset, y + row, x + inset + thickness, y + row + 1, color);
            g.fill(x + w - inset - thickness, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - row - 1, x + inset + thickness, y + h - row, color);
            g.fill(x + w - inset - thickness, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    public static int buttonText(ButtonStyle style, boolean enabled) {
        if (!enabled) return textMuted();
        return switch (style) {
            case FILLED -> onPrimary();
            case DANGER -> error();
            case TONAL -> onPrimaryContainer();
            case TEXT -> primary();
        };
    }

    /** MD3 按钮：色调/填充/文字/危险四类，保留键盘焦点和禁用状态反馈。 */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h,
            float hover, boolean focused, boolean enabled, float opacity, ButtonStyle style) {
        int base = switch (style) {
            case FILLED -> primary();
            case TONAL -> primaryContainer();
            case DANGER -> errorContainer();
            case TEXT -> surface();
        };
        if (!enabled) base = surfaceHigh();
        int foreground = buttonText(style, enabled);
        float state = enabled ? Math.clamp(hover, 0f, 1f) : 0f;
        int color = mix(base, foreground, state * 0.08f);
        int radius = Math.min(12, h / 2);
        if (style != ButtonStyle.TEXT || hover > 0f || focused) {
            float a = style == ButtonStyle.TEXT ? state * 0.14f + (focused ? 0.08f : 0f)
                    : style == ButtonStyle.FILLED ? 1f : visualStyle().controlOpacity();
            RoundedRect.fill(g, x, y, w, h, radius, alpha(color, opacity * a));
        }
        if (state > 0f && enabled && !focused) {
            border(g, x, y, w, h, radius, alpha(style == ButtonStyle.FILLED ? onPrimary() : primary(),
                    opacity * state * 0.32f));
        }
        if (focused && enabled) border(g, x, y, w, h, radius, alpha(primary(), opacity));
    }

    /** 圆角输入容器，通过轻微状态层和描边反馈悬停/焦点，不绘制底部横线。 */
    public static void field(GuiGraphicsExtractor g, int x, int y, int w, int h,
            boolean focused, boolean enabled, float opacity) {
        field(g, x, y, w, h, 0f, focused, enabled, opacity);
    }

    public static void field(GuiGraphicsExtractor g, int x, int y, int w, int h,
            float hover, boolean focused, boolean enabled, float opacity) {
        float state = enabled ? Math.clamp(hover, 0f, 1f) : 0f;
        RoundedRect.fill(g, x, y, w, h, 5,
                multiplyAlpha(mix(surfaceHigh(), alpha(primaryContainer(), visualStyle().layerOpacity()), state * 0.20f), opacity));
        int line = enabled && focused ? primary() : mix(outlineVariant(), primary(), state * 0.5f);
        border(g, x, y, w, h, 5, alpha(line, opacity * (focused && enabled ? 0.9f : 0.28f + state * 0.22f)));
    }

    /** 按行显示描述文字，超过可用行数时保留省略提示。 */
    public static void wrapped(GuiGraphicsExtractor g, Font font, Component text,
            int x, int y, int width, int maxLines, int color) {
        if (width <= 0 || maxLines <= 0) return;
        var lines = font.split(text, Math.max(1, width));
        int count = Math.min(maxLines, lines.size());
        for (int row = 0; row < count; row++) {
            var line = readableText(lines.get(row), color, textBackground());
            int lineY = y + row * (font.lineHeight + 2);
            if (row == count - 1 && lines.size() > count) {
                int ellipsisWidth = font.width("…");
                int end = x + Math.max(0, width - ellipsisWidth);
                g.enableScissor(x, lineY - 1, end, lineY + font.lineHeight + 1);
                g.text(font, line, x, lineY, color, false);
                g.disableScissor();
                if (ellipsisWidth <= width) g.text(font, "…", Math.min(end, x + font.width(line)), lineY, color, false);
            } else {
                g.text(font, line, x, lineY, color, false);
            }
        }
    }

    /** 超长控件文字省略，不越过相邻按钮或输入框。 */
    public static void label(GuiGraphicsExtractor g, Font font, Component label,
            int x, int y, int width, int color, boolean centered) {
        int available = Math.max(0, width);
        if (available == 0) return;
        int background = (color & 0xFFFFFF) == (onPrimary() & 0xFFFFFF) ? primary()
                : (color & 0xFFFFFF) == (onPrimaryContainer() & 0xFFFFFF) ? primaryContainer() : palette().surface();
        var text = readableText(label.getVisualOrderText(), color, background);
        int textWidth = font.width(text);
        if (textWidth <= available) {
            g.text(font, text, centered ? x + (available - textWidth) / 2 : x, y, color, false);
            return;
        }
        int ellipsisWidth = font.width("…");
        if (available < ellipsisWidth) return;
        g.enableScissor(x, y - 1, x + available - ellipsisWidth, y + font.lineHeight + 1);
        g.text(font, text, x, y, color, false);
        g.disableScissor();
        g.text(font, "…", x + available - ellipsisWidth, y, color, false);
    }
}
