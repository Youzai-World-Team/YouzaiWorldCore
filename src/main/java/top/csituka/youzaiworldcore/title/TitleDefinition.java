package top.csituka.youzaiworldcore.title;

import net.minecraft.network.chat.Component;

/** Api 下发的文字称号定义，所有展示位置统一使用「{称号}」格式。 */
@SuppressWarnings("null")
public record TitleDefinition(
        String id,
        String displayName,
        String textContent,
        int textColor,
        boolean bold,
        boolean italic,
        int sortOrder) {

    public TitleDefinition {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        textContent = textContent == null || textContent.isBlank() ? displayName : textContent;
    }

    public Component asComponent() {
        if (textContent.isBlank()) return Component.empty();
        return Component.literal("{" + textContent + "}").withStyle(style -> style
                .withColor(textColor)
                .withBold(bold)
                .withItalic(italic));
    }
}
