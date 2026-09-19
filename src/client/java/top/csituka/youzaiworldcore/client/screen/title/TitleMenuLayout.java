package top.csituka.youzaiworldcore.client.screen.title;

import top.csituka.youzaiworldcore.client.render.YzuiBrandLogo;

/** 标题页统一布局；坐标为 GUI 像素，绘制、按钮与滚动命中共用同一份结果。 */
public record TitleMenuLayout(Rect logo, Rect subtitle, Rect theme, Rect navigation, Rect news,
                              Rect join, Rect options, Rect quit, Rect test, int padding) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
    }

    /** 支持原版最小 GUI 尺寸 320×240；小窗口缩减留白，保留全部操作入口。 */
    public static TitleMenuLayout of(int width, int height, boolean developer) {
        boolean compact = width < 480 || height < 300;
        int margin = compact ? 10 : 20;
        int total = Math.min(720, Math.max(240, width - margin * 2));
        int gap = compact ? 10 : 16;
        int padding = compact ? 10 : 18;
        int header = compact ? 64 : 84;
        int cardHeight = Math.min(268, height - header - 28);
        int startX = (width - total) / 2;
        int startY = Math.max(10, (height - cardHeight - header - 16) / 2);
        int navWidth = compact ? (total - gap) * 44 / 100 : Math.min(248, total * 38 / 100);
        Rect navigation = new Rect(startX, startY + header, navWidth, cardHeight);
        Rect news = new Rect(navigation.right() + gap, navigation.y(), total - navWidth - gap, cardHeight);
        int themeWidth = compact ? 104 : 132;
        Rect theme = new Rect(startX + total - themeWidth, startY + 2, themeWidth, compact ? 24 : 28);
        int logoWidth = Math.min(compact ? 184 : 260, theme.x() - startX - 14);
        Rect logo = new Rect(startX, startY, logoWidth, YzuiBrandLogo.heightForWidth(logoWidth));
        Rect subtitle = new Rect(startX, logo.bottom() + 7, total, 10);
        int rows = developer ? 4 : 3;
        int buttonGap = compact ? 6 : 10;
        int buttonHeight = Math.min(compact ? 28 : 32,
                (cardHeight - padding * 2 - 30 - (rows - 1) * buttonGap) / rows);
        int buttonY = navigation.bottom() - padding - rows * buttonHeight - (rows - 1) * buttonGap;
        int buttonX = startX + padding;
        int buttonWidth = navWidth - padding * 2;
        Rect join = new Rect(buttonX, buttonY, buttonWidth, buttonHeight);
        Rect options = new Rect(buttonX, join.bottom() + buttonGap, buttonWidth, buttonHeight);
        Rect quit = new Rect(buttonX, options.bottom() + buttonGap, buttonWidth, buttonHeight);
        Rect test = new Rect(buttonX, quit.bottom() + buttonGap, buttonWidth, developer ? buttonHeight : 0);
        return new TitleMenuLayout(logo, subtitle, theme, navigation, news, join, options, quit, test, padding);
    }

    /** 公告正文与底部操作区分离；长更新日志只滚动正文，不遮挡下载按钮。 */
    public Rect body(boolean update, boolean forced) {
        int footer = update ? (forced ? 30 : 58) : 0;
        int top = news.y() + padding + 22;
        return new Rect(news.x() + padding, top, news.width() - padding * 2 - 6,
                Math.max(1, news.bottom() - padding - footer - top));
    }

    public Rect download(boolean forced) {
        return new Rect(news.x() + padding, news.bottom() - padding - (forced ? 24 : 52),
                news.width() - padding * 2, 24);
    }

    public Rect ignore() {
        return new Rect(news.x() + padding, news.bottom() - padding - 24, news.width() - padding * 2, 24);
    }
}
