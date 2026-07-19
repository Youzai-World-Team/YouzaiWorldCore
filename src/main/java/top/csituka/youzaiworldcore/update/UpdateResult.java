package top.csituka.youzaiworldcore.update;

import java.util.List;

/**
 * 一次更新检查的结果。
 * <p>
 * 当 {@link #errorMessage()} 非 null 时表示检查失败（网络/解析异常），此时
 * {@link #updateAvailable()} 恒为 false，其余字段可能为 null。
 * </p>
 *
 * @param updateAvailable 是否存在新版本（当前版本 < 最新版本）
 * @param currentVersion 当前模组版本（含预发布后缀，如 "1.19.0-indev"）
 * @param latestVersion  远程最新版本（可能含预发布后缀）
 * @param latestType     远程最新版本类型（indev / release 等）
 * @param forcedUpdate   是否强制更新（来自远程）
 * @param releaseDate    发布日期（原样）
 * @param releaseTime    发布时间（原样）
 * @param changelog      更新日志（逐行）
 * @param downloadUrl    构造好的下载页地址（基于当前版本号与类型）
 * @param errorMessage   失败原因；null 表示成功
 */
public record UpdateResult(
        boolean updateAvailable,
        String currentVersion,
        String latestVersion,
        String latestType,
        boolean forcedUpdate,
        String releaseDate,
        String releaseTime,
        List<String> changelog,
        String downloadUrl,
        String errorMessage
) {
}
