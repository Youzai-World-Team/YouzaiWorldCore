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
 *
 * @param latestVersion 最新版本号
 * @param type          最新版本类型（indev / release 等），仅作展示
 * @param forcedUpdate  是否强制更新（仅影响呈现严重度，不阻断游戏）
 * @param releaseDate   发布日期，原样展示，如 "2026.7.19"
 * @param releaseTime   发布时间，原样展示，如 "22:19:30"
 * @param changelog     更新日志
 */
public record RemoteVersionInfo(
        String latestVersion,
        String type,
        boolean forcedUpdate,
        String releaseDate,
        String releaseTime,
        List<String> changelog
) {
}
