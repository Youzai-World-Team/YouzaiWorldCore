package top.csituka.youzaiworldcore.chat;

import org.jspecify.annotations.Nullable;

/** 系统事件的结构化前缀及配色，服务端基础渐变与客户端显示共用。 */
public enum ChatEventType {
    ADVANCEMENT("advancement", "[成就]", 0xEAB308, 0xFACC15, 0xF59E0B),
    JOIN("join", "[加入]", 0x22C55E, 0x34D399, 0x22C55E),
    DEATH("death", "[死亡]", 0xEF4444, 0xFB7185, 0xDC2626),
    LEAVE("leave", "[退出]", 0x94A3B8, 0x94A3B8, 0x64748B);

    private final String headerKey;
    private final String label;
    private final int plainColor;
    private final int gradientStart;
    private final int gradientEnd;

    ChatEventType(String key, String label, int plainColor, int gradientStart, int gradientEnd) {
        this.headerKey = "chat.youzaiworldcore.event." + key;
        this.label = label;
        this.plainColor = plainColor;
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
    }

    public String headerKey() { return headerKey; }
    public String label() { return label; }
    public int plainColor() { return plainColor; }
    public int gradientStart() { return gradientStart; }
    public int gradientEnd() { return gradientEnd; }

    /** 按翻译键识别事件类型，不检查或解析正文文字。 */
    @Nullable
    public static ChatEventType fromHeaderKey(String key) {
        for (ChatEventType type : values()) {
            if (type.headerKey.equals(key)) return type;
        }
        return null;
    }
}
