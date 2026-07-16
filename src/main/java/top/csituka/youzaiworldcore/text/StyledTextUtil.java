package top.csituka.youzaiworldcore.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 格式化文本解析工具。
 * <p>
 * 支持 Minecraft 传统 {@code §} 格式码、自定义 {@code <tag>} 标签
 *（如 {@code <#RRGGBB>}、{@code <size:N>}、{@code <bold>} 等），
 * 以及 CJK 字符宽度估算，用于动画字幕实体的字形排布。
 * </p>
 */
public final class StyledTextUtil {

    private static final Pattern HEX_COLOR_TAG = Pattern.compile("^#?[0-9a-fA-F]{6}$");
    private static final float BASE_FONT_SIZE = 10.0F;
    private static final float MIN_FONT_SIZE = 1.0F;
    private static final float MAX_FONT_SIZE = 128.0F;
    private static final int DEFAULT_RGB = 0xFFFFFF;
    private static final int GLYPH_CACHE_LIMIT = 256;

    private static final Map<CacheKey, List<GlyphSlot>> GLYPH_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, List<GlyphSlot>> eldest) {
                    return size() > GLYPH_CACHE_LIMIT;
                }
            });

    private StyledTextUtil() {}

    // ======================== 公共 API ========================

    public static List<GlyphSlot> splitGlyphSlotsCached(String text, boolean forceBold) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        CacheKey key = new CacheKey(text, forceBold);
        List<GlyphSlot> cached = GLYPH_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<GlyphSlot> computed = List.copyOf(splitGlyphSlots(text, forceBold));
        GLYPH_CACHE.put(key, computed);
        return computed;
    }

    /**
     * 将格式化文本解析为字形槽位列表。
     * 每个字符根据当前样式状态生成对应的带格式前缀的文本片段。
     */
    public static List<GlyphSlot> splitGlyphSlots(String text, boolean forceBold) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<GlyphSlot> slots = new ArrayList<>();
        StyleState styleState = new StyleState(forceBold);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '<') {
                int closeIndex = text.indexOf('>', i + 1);
                if (closeIndex > i) {
                    String token = text.substring(i + 1, closeIndex).trim();
                    if (isLineBreakTag(token)) {
                        slots.add(new GlyphSlot("\n", false, styleState.getSizeScale(), styleState.getRgbColor()));
                        i = closeIndex;
                        continue;
                    }
                    if (applyCustomTag(token, styleState, forceBold)) {
                        i = closeIndex;
                        continue;
                    }
                }
            }

            if (c == '\n' || c == '\r') {
                slots.add(new GlyphSlot("\n", false, styleState.getSizeScale(), styleState.getRgbColor()));
                continue;
            }

            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (isLegacyCode(code)) {
                    applyLegacyCode(code, styleState, forceBold);
                    i++;
                    continue;
                }
            }

            float sizeScale = styleState.getSizeScale();
            int rgbColor = styleState.getRgbColor();

            if (Character.isWhitespace(c)) {
                slots.add(new GlyphSlot(" ", false, sizeScale, rgbColor));
            } else {
                StringBuilder glyph = new StringBuilder();
                glyph.append(styleState.buildStylePrefix());
                glyph.append(c);
                slots.add(new GlyphSlot(glyph.toString(), true, sizeScale, rgbColor));
            }
        }

        return slots;
    }

    public static int countVisibleGlyphs(String text) {
        return countVisibleGlyphs(splitGlyphSlotsCached(text, false));
    }

    public static int countVisibleGlyphs(List<GlyphSlot> slots) {
        int count = 0;
        for (GlyphSlot slot : slots) {
            if (slot.visible()) {
                count++;
            }
        }
        return count;
    }

    public static GlyphSlot getVisibleGlyphAt(List<GlyphSlot> slots, int visibleIndex) {
        int idx = 0;
        for (GlyphSlot slot : slots) {
            if (!slot.visible()) {
                continue;
            }
            if (idx == visibleIndex) {
                return slot;
            }
            idx++;
        }
        return null;
    }

    /**
     * 估算单个字符的像素宽度。
     * <ul>
     *   <li>CJK 字符（中日韩统一表意文字、假名、谚文等）→ 8px</li>
     *   <li>ASCII 窄字符（.,:;!|ilI 等）→ 3px</li>
     *   <li>ASCII 宽字符（mwMW 等）→ 7px</li>
     *   <li>标准 ASCII → 6px</li>
     *   <li>加粗额外 +1px</li>
     * </ul>
     */
    public static int estimateCharWidth(char c, boolean bold) {
        if (isCjk(c)) {
            return bold ? 10 : 8;
        }
        if (".,:;!|ilI'\u00b4".indexOf(c) >= 0) {
            return bold ? 4 : 3;
        }
        if ("mwMW@#".indexOf(c) >= 0) {
            return bold ? 8 : 7;
        }
        return bold ? 7 : 6;
    }

    public static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }

    // ======================== 内部解析逻辑 ========================

    private static boolean isLegacyCode(char c) {
        return isColorCode(c) || isFormatCode(c) || c == 'r';
    }

    private static boolean isColorCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    private static boolean isFormatCode(char c) {
        return c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o';
    }

    private static void applyLegacyCode(char code, StyleState styleState, boolean forceBold) {
        if (code == 'r') {
            styleState.resetFormatting(forceBold);
            styleState.clearColor();
            return;
        }
        if (isColorCode(code)) {
            styleState.setLegacyColor(code);
            styleState.resetFormatting(forceBold);
            return;
        }
        if (code == 'l') {
            styleState.setBold(true);
            return;
        }
        if (code == 'k') {
            styleState.setObfuscated(true);
            return;
        }
        if (code == 'm') {
            styleState.setStrikethrough(true);
            return;
        }
        if (code == 'n') {
            styleState.setUnderlined(true);
            return;
        }
        if (code == 'o') {
            styleState.setItalic(true);
        }
    }

    private static boolean applyCustomTag(String token, StyleState styleState, boolean forceBold) {
        if (token.isEmpty()) {
            return false;
        }
        String normalized = token.toLowerCase(Locale.ROOT);

        if (HEX_COLOR_TAG.matcher(normalized).matches()) {
            styleState.setHexColor(normalized);
            return true;
        }

        if (normalized.startsWith("size:")) {
            String value = normalized.substring("size:".length()).trim();
            try {
                float size = Float.parseFloat(value);
                styleState.setFontSize(size);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        if (normalized.equals("bold")) {
            styleState.setBold(true);
            return true;
        }

        if (normalized.equals("/bold") || normalized.equals("nobold")) {
            styleState.setBold(forceBold);
            return true;
        }

        if (normalized.equals("reset")) {
            styleState.resetFormatting(forceBold);
            styleState.clearColor();
            styleState.setFontSize(BASE_FONT_SIZE);
            return true;
        }

        return false;
    }

    private static boolean isLineBreakTag(String token) {
        return "br".equalsIgnoreCase(token.trim());
    }

    private static int legacyColorToRgb(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> DEFAULT_RGB;
        };
    }

    private static int parseHexRgb(String token) {
        String hex = token.startsWith("#") ? token.substring(1) : token;
        return Integer.parseInt(hex, 16) & 0xFFFFFF;
    }

    // ======================== 内部数据类型 ========================

    public record GlyphSlot(String styledText, boolean visible, float sizeScale, int rgbColor) {}

    private record CacheKey(String text, boolean forceBold) {}

    private static final class StyleState {
        private int rgbColor = DEFAULT_RGB;
        private float fontSize = BASE_FONT_SIZE;
        private boolean bold;
        private boolean obfuscated;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        StyleState(boolean forceBold) {
            this.bold = forceBold;
        }

        void resetFormatting(boolean forceBold) {
            this.bold = forceBold;
            this.obfuscated = false;
            this.strikethrough = false;
            this.underlined = false;
            this.italic = false;
        }

        void clearColor() {
            this.rgbColor = DEFAULT_RGB;
        }

        void setLegacyColor(char code) {
            this.rgbColor = legacyColorToRgb(code);
        }

        void setHexColor(String token) {
            this.rgbColor = parseHexRgb(token);
        }

        void setBold(boolean bold) {
            this.bold = bold;
        }

        void setObfuscated(boolean obfuscated) {
            this.obfuscated = obfuscated;
        }

        void setStrikethrough(boolean strikethrough) {
            this.strikethrough = strikethrough;
        }

        void setUnderlined(boolean underlined) {
            this.underlined = underlined;
        }

        void setItalic(boolean italic) {
            this.italic = italic;
        }

        void setFontSize(float size) {
            this.fontSize = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
        }

        float getSizeScale() {
            return this.fontSize / BASE_FONT_SIZE;
        }

        int getRgbColor() {
            return this.rgbColor;
        }

        String buildStylePrefix() {
            StringBuilder builder = new StringBuilder();
            if (this.bold) {
                builder.append('\u00a7').append('l');
            }
            if (this.obfuscated) {
                builder.append('\u00a7').append('k');
            }
            if (this.strikethrough) {
                builder.append('\u00a7').append('m');
            }
            if (this.underlined) {
                builder.append('\u00a7').append('n');
            }
            if (this.italic) {
                builder.append('\u00a7').append('o');
            }
            return builder.toString();
        }
    }
}
