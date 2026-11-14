package top.csituka.youzaiworldcore.client.screen.pause;

/** 暂停菜单按真实 GUI 尺寸排版；账户与选项并排，窄屏隐藏独立玩家卡片。 */
public record PauseMenuLayout(Rect actions, Rect profile, Rect resume, Rect advancements, Rect statistics,
        Rect options, Rect account, Rect disconnect, Rect model, int padding, int footerY) {

    /** 不使用整体缩放，保留正常字号；最低支持 320×180 GUI 单位。 */
    public static PauseMenuLayout of(int width, int height, boolean accountAllowed) {
        boolean profileVisible = width >= 460 && height >= 260;
        boolean compact = height < 260 || (!profileVisible && height < 320);
        int padding = compact ? 8 : 16;
        int gap = compact ? 4 : 8;
        int buttonHeight = compact ? 20 : 32;
        int tileHeight = compact ? 22 : 38;
        int headerHeight = 26;
        int cardHeight = padding * 2 + headerHeight + buttonHeight * 2 + tileHeight * 2 + gap * 3;
        int actionWidth = Math.min(profileVisible ? 280 : 320, width - 24);
        int profileWidth = profileVisible ? 144 : 0;
        int totalWidth = actionWidth + (profileVisible ? 12 + profileWidth : 0);
        int footerSpace = height >= cardHeight + 34 ? 22 : 0;
        int x = (width - totalWidth) / 2;
        int y = Math.max(6, (height - cardHeight - footerSpace) / 2);
        Rect actions = new Rect(x, y, actionWidth, cardHeight);
        Rect profile = new Rect(x + actionWidth + 12, y, profileWidth, cardHeight);
        int innerWidth = actionWidth - padding * 2;
        int halfWidth = (innerWidth - gap) / 2;
        int left = x + padding, right = left + halfWidth + gap;
        int row = y + padding + headerHeight;
        Rect resume = new Rect(left, row, innerWidth, buttonHeight);
        row += buttonHeight + gap;
        Rect advancements = new Rect(left, row, halfWidth, tileHeight);
        Rect statistics = new Rect(right, row, innerWidth - halfWidth - gap, tileHeight);
        row += tileHeight + gap;
        Rect options = new Rect(left, row, accountAllowed ? halfWidth : innerWidth, tileHeight);
        Rect account = new Rect(right, row, accountAllowed ? innerWidth - halfWidth - gap : 0, tileHeight);
        row += tileHeight + gap;
        Rect disconnect = new Rect(left, row, innerWidth, buttonHeight);
        Rect model = new Rect(profile.x() + 12, y + 72, Math.max(0, profileWidth - 24),
                Math.max(0, profile.bottom() - 16 - (y + 72)));
        return new PauseMenuLayout(actions, profile, resume, advancements, statistics, options,
                account, disconnect, model, padding, footerSpace == 0 ? -1 : actions.bottom() + 10);
    }

    /** 控件与绘制共用同一矩形，避免视觉和点击范围不一致。 */
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }
}
