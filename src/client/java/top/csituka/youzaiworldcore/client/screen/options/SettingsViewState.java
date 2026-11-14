package top.csituka.youzaiworldcore.client.screen.options;

import java.util.HashMap;
import java.util.Map;

/** 同一次设置会话的浏览位置；不写入配置，重排时按稳定分类标识恢复。 */
final class SettingsViewState {
    final Map<String, Double> sectionScroll = new HashMap<>();
    String searchQuery = "";
    double searchScroll;
    boolean moreExpanded;
}
