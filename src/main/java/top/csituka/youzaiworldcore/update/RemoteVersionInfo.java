package top.csituka.youzaiworldcore.update;

import java.util.List;

/**
 * 远程更新 API 的原始解析结果。
 * <p>
 * Schema（{@code https://api.mcyzw.top/api/update/core} 与 {@code .../core_force}，二者同构）：
 * <pre>{@code
 * {
 *   "latestVersion": "1.6.3.4",
 *   "type": "indev",
 *   "forcedUpdate": false,
 *   "release_date": "2026.8.15",
 *   "release_time": "23:22:15",
 *   "changelog": ["1"]
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
