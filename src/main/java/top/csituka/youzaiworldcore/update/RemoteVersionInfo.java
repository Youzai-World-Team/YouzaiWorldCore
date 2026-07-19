package top.csituka.youzaiworldcore.update;

import java.util.List;

/**
 * 远程 {@code version.json} 的原始解析结果。
 * <p>
 * Schema（{@code https://mcyzw.top/yzwc/version.json}）：
 * <pre>{@code
 * {
 *   "latestVersion": "1.20.1",
 *   "type": "indev",
 *   "forcedUpdate": true,
 *   "release_date": "2026.7.19",
 *   "release_time": "22:19:30",
 *   "changelog": ["修复了一些已知问题，提升稳定性。", "所有用户都必须更新！"]
 * }
 * }</pre>
 * </p>
 */
public record RemoteVersionInfo(
        /** 最新版本号，如 "1.20.1" */
        String latestVersion,
        /** 最新版本类型（indev / release 等），仅作展示 */
        String type,
        /** 是否强制更新（仅影响呈现严重度，不阻断游戏） */
        boolean forcedUpdate,
        /** 发布日期，原样展示，如 "2026.7.19" */
        String releaseDate,
        /** 发布时间，原样展示，如 "22:19:30" */
        String releaseTime,
        /** 更新日志（逐行） */
        List<String> changelog
) {
}
