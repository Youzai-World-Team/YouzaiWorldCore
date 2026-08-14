package top.csituka.youzaiworldcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 配置文件错误的统一致命处理。
 * <p>
 * 本项目<b>不做</b>配置迁移与容错回退。读到结构 / 类型非法的配置时的处理流程固定为：
 * </p>
 * <ol>
 *   <li>把出错的文件<b>改名隔离</b>为 {@code <原文件名>.error}（已存在则追加时间戳），保留现场供比对；</li>
 *   <li>在原路径<b>重新生成一份默认配置文件</b>，让管理员有个正确格式的参照；</li>
 *   <li>把「文件 / 位置 / 原因 / 隔离到哪 / 已重建哪个」全部打到控制台；</li>
 *   <li>抛出 {@link ConfigFormatException} 让服务端崩溃退出。</li>
 * </ol>
 * <p>
 * 这么做是刻意的 —— 静默回退默认值会让「配置写错了但服务器照常跑」，
 * 玩家数据按错误配置继续写盘后更难收拾。
 * </p>
 */
public final class ConfigCrash {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/Config");

    private static final String BORDER =
            "========================================================================";

    /** 隔离文件的后缀 */
    public static final String ERROR_SUFFIX = ".error";

    private static final DateTimeFormatter STAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ConfigCrash() {
    }

    /**
     * 输出错误现场并崩溃退出（不做隔离与重建，用于目录创建失败等无法自动修复的场景）。
     *
     * @param file     出问题的文件或目录
     * @param jsonPath 出问题的位置，形如 {@code afk_module.threshold_seconds}
     * @param reason   人类可读的原因描述
     */
    public static void fail(Path file, String jsonPath, String reason) {
        fail(file, jsonPath, reason, null);
    }

    /**
     * 输出错误现场并崩溃退出（带原始异常）。
     *
     * @param file     出问题的文件或目录
     * @param jsonPath 出问题的位置，形如 {@code afk_module.threshold_seconds}
     * @param reason   人类可读的原因描述
     * @param cause    触发本次失败的原始异常，可为 null
     */
    public static void fail(Path file, String jsonPath, String reason, Throwable cause) {
        report(file, jsonPath, reason, cause, null, null);
        throw newException(file, jsonPath, reason, cause);
    }

    /**
     * 输出错误现场（含隔离与重建结果）并崩溃退出。
     *
     * @param file        出问题的文件
     * @param jsonPath    出问题的位置
     * @param reason      人类可读的原因描述
     * @param cause       触发本次失败的原始异常，可为 null
     * @param quarantined 坏文件被改名到的位置；未能隔离时为 null
     * @param regenerated 已重新生成默认配置的位置；未能重建时为 null
     */
    public static void failAfterRecovery(Path file, String jsonPath, String reason, Throwable cause,
                                         Path quarantined, Path regenerated) {
        report(file, jsonPath, reason, cause, quarantined, regenerated);
        throw newException(file, jsonPath, reason, cause);
    }

    /**
     * 把出错的文件改名隔离为 {@code <原文件名>.error}。
     * <p>
     * 若目标名已被占用，则退化为 {@code <原文件名>.error-<yyyyMMdd_HHmmss>}，
     * 绝不覆盖已有的隔离文件 —— 现场只增不减。
     * </p>
     *
     * @param file 出错的文件
     * @return 隔离后的路径；文件不存在或改名失败时返回 null
     */
    public static Path quarantine(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        Path target = file.resolveSibling(file.getFileName().toString() + ERROR_SUFFIX);
        if (Files.exists(target)) {
            String stamp = LocalDateTime.now().format(STAMP_FORMATTER);
            target = file.resolveSibling(file.getFileName().toString() + ERROR_SUFFIX + "-" + stamp);
        }
        try {
            Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException | UnsupportedOperationException e) {
            // 跨文件系统等场景下 ATOMIC_MOVE 不可用，退化为普通移动
            try {
                Files.move(file, target);
                return target;
            } catch (IOException e2) {
                LOGGER.error("无法隔离出错的配置文件 {} → {}：{}", file, target, e2.getMessage());
                return null;
            }
        }
    }

    // ===== 内部 =====

    /** 把错误现场打到控制台 */
    private static void report(Path file, String jsonPath, String reason, Throwable cause,
                              Path quarantined, Path regenerated) {
        LOGGER.error(BORDER);
        LOGGER.error("YouzaiWorldCore 配置 / 数据文件错误，服务端已停止启动");
        LOGGER.error("  文件：{}", file == null ? "<未知>" : file.toAbsolutePath());
        LOGGER.error("  位置：{}", describePath(jsonPath));
        LOGGER.error("  原因：{}", reason);
        if (cause != null) {
            LOGGER.error("  底层异常：{}: {}", cause.getClass().getName(), cause.getMessage());
        }
        if (quarantined != null) {
            LOGGER.error("  出错的原文件已改名保留为：{}", quarantined.toAbsolutePath());
        } else if (file != null) {
            LOGGER.error("  未能改名保留原文件，请手工检查该文件。");
        }
        if (regenerated != null) {
            LOGGER.error("  已在原路径重新生成一份默认配置：{}", regenerated.toAbsolutePath());
            LOGGER.error("  请对照 {} 把你的改动重新填进新文件后再启动。", ERROR_SUFFIX);
        } else {
            LOGGER.error("  未能重新生成默认配置，请修正后重新启动。");
        }
        LOGGER.error(BORDER);
    }

    /** 构造崩溃用异常 */
    private static ConfigFormatException newException(Path file, String jsonPath, String reason,
                                                      Throwable cause) {
        String message = "配置文件错误 [" + (file == null ? "<未知>" : file.toAbsolutePath()) + "] "
                + "位置 [" + describePath(jsonPath) + "]：" + reason;
        return new ConfigFormatException(message, cause);
    }

    private static String describePath(String jsonPath) {
        return jsonPath == null || jsonPath.isBlank() ? "<根节点>" : jsonPath;
    }

    /**
     * 配置文件格式错误。由 {@link ConfigCrash} 抛出，不应被业务代码捕获。
     */
    public static final class ConfigFormatException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ConfigFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
