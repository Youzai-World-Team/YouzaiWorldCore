package top.csituka.youzaiworldcore.title;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/** Api 下发的称号定义。文字和贴图字体都通过原版 Component 渲染。 */
@SuppressWarnings("null")
public record TitleDefinition(
        String id,
        String displayName,
        RenderType renderType,
        String textContent,
        int textColor,
        boolean bold,
        boolean italic,
        String textureKey,
        String fontId,
        String glyph,
        int sortOrder) {

    public enum RenderType {
        TEXT, TEXTURE, TEXT_TEXTURE;

        public static RenderType parse(String value) {
            return switch (value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) {
                case "texture" -> TEXTURE;
                case "text_texture" -> TEXT_TEXTURE;
                default -> TEXT;
            };
        }
    }

    public TitleDefinition {
        id = id == null ? "" : id;
        displayName = displayName == null ? id : displayName;
        renderType = renderType == null ? RenderType.TEXT : renderType;
        textContent = textContent == null || textContent.isBlank() ? displayName : textContent;
        textureKey = textureKey == null ? "" : textureKey;
        fontId = fontId == null || fontId.isBlank() ? "youzaiworldcore:title" : fontId;
        glyph = glyph == null ? "" : glyph;
    }

    public Component asComponent() {
        MutableComponent result = Component.empty();
        if (renderType != RenderType.TEXT && !glyph.isBlank()) {
            try {
                FontDescription.Resource font = new FontDescription.Resource(Identifier.parse(fontId));
                result.append(Component.literal(glyph).withStyle(style -> style.withFont(font)));
            } catch (RuntimeException ignored) {
                result.append(styledText());
                return result;
            }
        }
        if (renderType == RenderType.TEXT || renderType == RenderType.TEXT_TEXTURE) {
            if (renderType == RenderType.TEXT_TEXTURE && !glyph.isBlank()) {
                result.append(Component.literal(" "));
            }
            result.append(styledText());
        }
        if (result.getString().isBlank()) {
            return styledText();
        }
        return result;
    }

    private Component styledText() {
        return Component.literal(textContent).withStyle(style -> style
                .withColor(textColor)
                .withBold(bold)
                .withItalic(italic));
    }
}
