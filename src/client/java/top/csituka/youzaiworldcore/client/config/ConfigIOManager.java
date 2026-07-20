package top.csituka.youzaiworldcore.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 配置文件导入/导出管理器。
 * <p>
 * 纯客户端功能，负责：
 * <ul>
 *   <li>导出 {@code config/} 及 {@code options.txt} 为 ZIP（PC 通过文件选择器，Android 自动保存至 {@code config_backups/}）</li>
 *   <li>导入 ZIP 包，三步安全写入（备份→解压→清理），含路径遍历校验与 ZIP 炸弹防御</li>
 *   <li>启动时崩溃自愈（恢复孤立的 {@code config_bak_*}/options_bak_* 备份）</li>
 * </ul>
 * </p>
 * <p>
 * <b>注意</b>：Swing/AWT 文件对话框通过<em>反射</em>调用，避免 Android 环境中类加载失败。
 * </p>
 */
public final class ConfigIOManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ConfigIO");
    private static final String DEBUG_TAG = "ConfigIO";

    /** ZIP 炸弹防御：最大解压字节数（500 MB） */
    private static final long MAX_TOTAL_BYTES = 500L * 1024 * 1024;
    /** ZIP 炸弹防御：最大条目数 */
    private static final int MAX_TOTAL_ENTRIES = 50_000;

    /** 导入进行中标志 — {@link top.csituka.youzaiworldcore.mixin.client.OptionsSaveMixin} 据此拦截 {@code options.save()} */
    public static final AtomicBoolean isImporting = new AtomicBoolean(false);

    /** 导出进行中标志 — 用于按钮防并发点击 */
    public static final AtomicBoolean isExporting = new AtomicBoolean(false);

    /** 进度回调接口 */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * 处理进度更新（在任意线程调用，UI 更新必须自行回抛主线程）。
         *
         * @param processed 已处理条目数
         * @param total     总条目数
         * @param phase     当前阶段描述
         */
        void onProgress(int processed, int total, String phase);
    }

    private ConfigIOManager() {}

    // ========================================================================
    // 公开 API
    // ========================================================================

    /**
     * 导出配置（全平台统一行为：自动保存至 {@code config_backups/} 目录）。
     * {@code gameDir} 即 {@code Minecraft.getInstance().gameDirectory}。
     *
     * @param gameDir 游戏根目录
     * @param callback 进度回调（可选）
     * @return 导出 ZIP 的路径
     */
    public static CompletableFuture<Path> exportConfig(File gameDir, ProgressCallback callback) {
        DebugLogger.entering(DEBUG_TAG, "exportConfig", "gameDir=" + gameDir);
        Path backupDir = gameDir.toPath().resolve("config_backups");
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            DebugLogger.exception(DEBUG_TAG, "create config_backups dir", e);
            return CompletableFuture.failedFuture(e);
        }
        String fileName = "config_export_" + timestamp() + ".zip";
        Path zipPath = backupDir.resolve(fileName);
        DebugLogger.info(DEBUG_TAG, "导出路径: %s", zipPath);
        return runExport(zipPath, gameDir, callback)
                .thenApply(v -> { cleanupOldBackups(backupDir); return zipPath; });
    }

    /**
     * 导入配置。
     *
     * @param zipPath  ZIP 包路径
     * @param gameDir  游戏根目录
     * @param callback 进度回调（可选）
     * @return {@link CompletableFuture}，完成时表示导入成功
     */
    public static CompletableFuture<Void> importConfig(Path zipPath, File gameDir, ProgressCallback callback) {
        DebugLogger.entering(DEBUG_TAG, "importConfig", "zipPath=" + zipPath);

        isImporting.set(true);
        DebugLogger.stateChange(DEBUG_TAG, "ConfigIOManager", "isImporting", false, true);

        return CompletableFuture.runAsync(() -> {
            try {
                performImport(zipPath, gameDir, callback);
            } catch (Exception e) {
                // 有任何异常则回滚
                LOGGER.error("导入过程异常，开始回滚", e);
                DebugLogger.exception(DEBUG_TAG, "importConfig", e);
                try {
                    rollbackImport(gameDir);
                } catch (Exception rollbackEx) {
                    LOGGER.error("回滚失败！请手动恢复备份", rollbackEx);
                    DebugLogger.exception(DEBUG_TAG, "导入回滚失败", rollbackEx);
                }
                throw new RuntimeException(e);
            } finally {
                isImporting.set(false);
                DebugLogger.stateChange(DEBUG_TAG, "ConfigIOManager", "isImporting", true, false);
            }
        });
    }

    /**
     * 启动时崩溃自愈（第 8 节）。
     * <p>
     * 检测孤立的 config_bak_* 及 options_bak_* 备份，
     * 若对应的 {@code config}/options.txt 缺失，则恢复最新备份。
     * </p>
     *
     * @param gameDir 游戏根目录
     */
    public static void recoverIfNeeded(File gameDir) {
        DebugLogger.entering(DEBUG_TAG, "recoverIfNeeded");
        Path root = gameDir.toPath();
        Path configDir = root.resolve("config");
        Path optionsFile = root.resolve("options.txt");

        // 查找所有 config_bak_* 目录
        List<File> configBaks = new ArrayList<>();
        // 查找所有 options_bak_*.txt 文件
        List<File> optionsBaks = new ArrayList<>();

        File[] entries = gameDir.listFiles();
        if (entries == null) {
            DebugLogger.warn(DEBUG_TAG, "gameDir.listFiles() 返回 null，跳过自愈");
            return;
        }
        for (File f : entries) {
            String name = f.getName();
            if (f.isDirectory() && name.startsWith("config_bak_")) {
                configBaks.add(f);
            } else if (f.isFile() && name.startsWith("options_bak_") && name.endsWith(".txt")) {
                optionsBaks.add(f);
            }
        }

        boolean configMissing = !Files.isDirectory(configDir);
        boolean optionsMissing = !Files.isRegularFile(optionsFile);

        if (!configMissing && !optionsMissing) {
            DebugLogger.debug(DEBUG_TAG, "自愈无需执行：config 和 options.txt 均存在");
            return;
        }

        if (configMissing) {
            if (configBaks.isEmpty()) {
                DebugLogger.warn(DEBUG_TAG, "config 缺失但未找到 config_bak_* 备份，无法自愈");
            } else {
                // 按最后修改时间排序，取最新的
                configBaks.sort(Comparator.comparingLong((File f) -> f.lastModified()).reversed());
                File newest = configBaks.get(0);
                try {
                    Files.move(newest.toPath(), configDir);
                    DebugLogger.info(DEBUG_TAG, "已恢复 config 备份: %s", newest.getName());
                } catch (IOException e) {
                    LOGGER.error("恢复 config 备份失败: {}", newest.getName(), e);
                    DebugLogger.exception(DEBUG_TAG, "恢复 config 备份", e);
                }
                // 删除其他孤立备份
                for (int i = 1; i < configBaks.size(); i++) {
                    deleteQuietly(configBaks.get(i).toPath());
                }
            }
        }

        if (optionsMissing) {
            if (optionsBaks.isEmpty()) {
                DebugLogger.warn(DEBUG_TAG, "options.txt 缺失但未找到 options_bak_* 备份，无法自愈");
            } else {
                optionsBaks.sort(Comparator.comparingLong((File f) -> f.lastModified()).reversed());
                File newest = optionsBaks.get(0);
                try {
                    Files.move(newest.toPath(), optionsFile);
                    DebugLogger.info(DEBUG_TAG, "已恢复 options.txt 备份: %s", newest.getName());
                } catch (IOException e) {
                    LOGGER.error("恢复 options.txt 备份失败: {}", newest.getName(), e);
                    DebugLogger.exception(DEBUG_TAG, "恢复 options.txt 备份", e);
                }
                for (int i = 1; i < optionsBaks.size(); i++) {
                    deleteQuietly(optionsBaks.get(i).toPath());
                }
            }
        }

        DebugLogger.info(DEBUG_TAG, "启动自愈流程完成 (configMissing=%s, optionsMissing=%s)",
                configMissing, optionsMissing);
    }

    // ========================================================================
    // 内部：导出执行
    // ========================================================================

    private static CompletableFuture<Void> runExport(Path zipPath, File gameDir, ProgressCallback callback) {
        return CompletableFuture.runAsync(() -> {
            DebugLogger.entering(DEBUG_TAG, "performExport", "zipPath=" + zipPath);
            try {
                performExport(zipPath, gameDir, callback);
                DebugLogger.info(DEBUG_TAG, "导出成功: %s", zipPath);
            } catch (IOException e) {
                DebugLogger.exception(DEBUG_TAG, "导出失败", e);
                throw new RuntimeException(e);
            }
            DebugLogger.exiting(DEBUG_TAG, "performExport");
        });
    }

    private static void performExport(Path zipPath, File gameDir, ProgressCallback callback) throws IOException {
        File configDir = new File(gameDir, "config");
        File optionsFile = new File(gameDir, "options.txt");

        // 统计待打包文件数
        List<Path> configFiles = new ArrayList<>();
        if (configDir.isDirectory()) {
            try (Stream<Path> walk = Files.walk(configDir.toPath())) {
                walk.filter(Files::isRegularFile).forEach(configFiles::add);
            }
        }
        int totalFiles = configFiles.size();
        if (optionsFile.isFile()) totalFiles++;

        DebugLogger.debug(DEBUG_TAG, "待打包文件: config(%d) + options.txt(%d)", totalFiles, totalFiles);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            int processed = 0;
            long lastReportTime = 0L;

            // 添加 config/ 下的文件
            for (Path file : configFiles) {
                String entryName = "config/" + configDir.toPath().relativize(file)
                        .toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zos);
                zos.closeEntry();
                processed++;
                lastReportTime = reportProgress(callback, processed, totalFiles, "exporting", processed, lastReportTime);
            }

            // 添加 options.txt（如果存在）
            if (optionsFile.isFile()) {
                zos.putNextEntry(new ZipEntry("options.txt"));
                Files.copy(optionsFile.toPath(), zos);
                zos.closeEntry();
                processed++;
                lastReportTime = reportProgress(callback, processed, totalFiles, "exporting", processed, lastReportTime);
            }
        }
    }

    // ========================================================================
    // 内部：导入执行（三步安全写入）
    // ========================================================================

    private static void performImport(Path zipPath, File gameDir, ProgressCallback callback) throws Exception {
        LOGGER.info("开始导入配置: {}", zipPath);
        DebugLogger.info(DEBUG_TAG, "=== 导入流程开始 ===");

        // === 第零步：并发防护 ===
        // 刷新当前设置到磁盘
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.options.save();
            DebugLogger.info(DEBUG_TAG, "已强制落盘 options");
        } catch (Exception e) {
            DebugLogger.warn(DEBUG_TAG, "options.save() 未预期异常: %s", e.getMessage());
        }

        // === 第一步：备份旧文件 ===
        String timestamp = timestamp();
        Path root = gameDir.toPath().normalize();
        Path configDir = root.resolve("config");
        Path optionsFile = root.resolve("options.txt");

        String bakSuffix = "_bak_" + timestamp;
        Path bakConfigDir = root.resolve("config" + bakSuffix);
        Path bakOptionsFile = root.resolve("options_bak_" + timestamp + ".txt");

        boolean hasConfigBackup = false;
        boolean hasOptionsBackup = false;

        if (Files.isDirectory(configDir)) {
            try {
                Files.move(configDir, bakConfigDir);
                hasConfigBackup = true;
                DebugLogger.stateChange(DEBUG_TAG, "config", "renamed", configDir, bakConfigDir);
            } catch (IOException e) {
                LOGGER.error("备份 config 失败（文件被占用？）", e);
                DebugLogger.exception(DEBUG_TAG, "备份 config 目录", e);
                isImporting.set(false);
                throw new IOException("导入失败：检测到配置文件被外部程序占用，请关闭相关软件后重试。", e);
            }
        }

        if (Files.isRegularFile(optionsFile)) {
            try {
                Files.move(optionsFile, bakOptionsFile);
                hasOptionsBackup = true;
                DebugLogger.stateChange(DEBUG_TAG, "options.txt", "renamed", optionsFile, bakOptionsFile);
            } catch (IOException e) {
                LOGGER.error("备份 options.txt 失败（文件被占用？）", e);
                DebugLogger.exception(DEBUG_TAG, "备份 options.txt", e);
                // 回滚 config
                if (hasConfigBackup) {
                    try { Files.move(bakConfigDir, configDir); } catch (IOException ignored) {}
                }
                isImporting.set(false);
                throw new IOException("导入失败：检测到配置文件被外部程序占用，请关闭相关软件后重试。", e);
            }
        }

        DebugLogger.branch(DEBUG_TAG, "备份结果", hasConfigBackup, "config=" + hasConfigBackup + " options=" + hasOptionsBackup);

        // === 第二步：执行解压（带路径校验 + ZIP 炸弹防御） ===
        long totalBytes = 0;
        int totalEntries = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            int processed = 0;
            long lastReportTime = 0L;

            // 先遍历一次统计总条目数用于进度
            int totalEntryCount = countZipEntries(zipPath);
            DebugLogger.debug(DEBUG_TAG, "ZIP 总条目数: %d", totalEntryCount);

            while ((entry = zis.getNextEntry()) != null) {
                totalEntries++;
                if (totalEntries > MAX_TOTAL_ENTRIES) {
                    throw new IOException("ZIP 炸弹防御触发：条目数超过 " + MAX_TOTAL_ENTRIES);
                }

                Path targetPath = root.resolve(entry.getName()).normalize();

                // ▸ 路径安全校验
                if (!isPathAllowed(root, targetPath)) {
                    DebugLogger.debug(DEBUG_TAG, "跳过不允许的条目: %s", entry.getName());
                    zis.closeEntry();
                    continue;
                }

                // 创建父目录并解压
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    long bytes;
                    if (entry.getSize() >= 0) {
                        // 直接复制
                        bytes = Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // 未知大小：手动复制并计数
                        bytes = copyWithCounting(zis, targetPath);
                    }
                    totalBytes += bytes;
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw new IOException("ZIP 炸弹防御触发：总大小超过 " + MAX_TOTAL_BYTES + " bytes");
                    }
                }
                zis.closeEntry();
                processed++;
                lastReportTime = reportProgress(callback, processed, totalEntryCount, "importing", totalEntries, lastReportTime);
            }
        }

        DebugLogger.info(DEBUG_TAG, "解压完成：%d 条目 / %d bytes", totalEntries, totalBytes);

        // === 第三步：清理备份（同步完成确保无磁盘操作遗留） ===
        if (hasConfigBackup) {
            try {
                deleteRecursively(bakConfigDir);
                DebugLogger.debug(DEBUG_TAG, "已删除备份: %s", bakConfigDir);
            } catch (IOException e) {
                LOGGER.warn("删除 config 备份失败（无害）: {}", bakConfigDir, e);
                DebugLogger.exception(DEBUG_TAG, "删除备份", e);
            }
        }
        if (hasOptionsBackup) {
            try {
                Files.deleteIfExists(bakOptionsFile);
                DebugLogger.debug(DEBUG_TAG, "已删除备份: %s", bakOptionsFile);
            } catch (IOException e) {
                LOGGER.warn("删除 options 备份失败（无害）: {}", bakOptionsFile, e);
                DebugLogger.exception(DEBUG_TAG, "删除备份", e);
            }
        }

        DebugLogger.info(DEBUG_TAG, "=== 导入流程成功完成 ===");
        LOGGER.info("配置导入成功");
    }

    // ========================================================================
    // 回滚
    // ========================================================================

    private static void rollbackImport(File gameDir) {
        Path root = gameDir.toPath().normalize();

        // 查找当前存在的备份
        Path latestConfigBak = null;
        Path latestOptionsBak = null;
        long latestTime = 0;

        File[] entries = gameDir.listFiles();
        if (entries == null) return;

        for (File f : entries) {
            String name = f.getName();
            if (name.startsWith("config_bak_") && f.isDirectory()) {
                if (f.lastModified() > latestTime) {
                    latestTime = f.lastModified();
                    latestConfigBak = f.toPath();
                }
            } else if (name.startsWith("options_bak_") && name.endsWith(".txt") && f.isFile()) {
                if (f.lastModified() > latestTime) {
                    latestTime = f.lastModified();
                    latestOptionsBak = f.toPath();
                }
            }
        }

        // 删除半成品 config 目录
        Path configDir = root.resolve("config");
        if (Files.isDirectory(configDir)) {
            try {
                deleteRecursively(configDir);
                DebugLogger.debug(DEBUG_TAG, "回滚：删除了半成品 config");
            } catch (IOException e) {
                LOGGER.error("回滚删除半成品 config 失败", e);
                DebugLogger.exception(DEBUG_TAG, "回滚删除 config", e);
            }
        }

        // 恢复 config 备份
        if (latestConfigBak != null) {
            try {
                Files.move(latestConfigBak, configDir);
                DebugLogger.info(DEBUG_TAG, "回滚：已恢复 config 备份");
            } catch (IOException e) {
                LOGGER.error("回滚恢复 config 失败！请手动恢复", e);
                DebugLogger.exception(DEBUG_TAG, "回滚恢复 config", e);
            }
        }

        // 恢复 options.txt 备份
        Path optionsFile = root.resolve("options.txt");
        if (latestOptionsBak != null) {
            try {
                Files.move(latestOptionsBak, optionsFile);
                DebugLogger.info(DEBUG_TAG, "回滚：已恢复 options.txt 备份");
            } catch (IOException e) {
                LOGGER.error("回滚恢复 options.txt 失败！请手动恢复", e);
                DebugLogger.exception(DEBUG_TAG, "回滚恢复 options.txt", e);
            }
        }

        // 尝试重载 options 内存状态
        try {
            net.minecraft.client.Minecraft.getInstance().options.load();
            DebugLogger.debug(DEBUG_TAG, "回滚后已重载 options 内存状态");
        } catch (Exception e) {
            DebugLogger.warn(DEBUG_TAG, "回滚后 options.load() 异常（无害，重启后可恢复）: %s", e.getMessage());
        }
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /** 路径安全校验 */
    private static boolean isPathAllowed(Path root, Path targetPath) {
        Path configDir = root.resolve("config").normalize();

        // 条件 A：在 config/ 目录内（且不是 config/ 目录本身）
        if (targetPath.startsWith(configDir) && !targetPath.equals(configDir)) {
            return true;
        }

        // 条件 B：options.txt 在根目录
        if ("options.txt".equals(targetPath.getFileName().toString())
                && targetPath.getParent() != null
                && targetPath.getParent().equals(root)) {
            return true;
        }

        return false;
    }

    /** 统计 ZIP 中的条目数 */
    private static int countZipEntries(Path zipPath) throws IOException {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            while (zis.getNextEntry() != null) {
                count++;
                zis.closeEntry();
            }
        }
        return count;
    }

    /** 手动复制输入流到目标文件并计数字节 */
    private static long copyWithCounting(ZipInputStream zis, Path target) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
            int read;
            while ((read = zis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                total += read;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IOException("ZIP 炸弹防御触发");
                }
            }
        }
        return total;
    }

    /** Android 端：仅保留最新 5 份备份 */
    private static void cleanupOldBackups(Path backupDir) {
        DebugLogger.entering(DEBUG_TAG, "cleanupOldBackups");
        File[] files = backupDir.toFile().listFiles((dir, name) -> name.startsWith("config_export_") && name.endsWith(".zip"));
        if (files == null || files.length <= 5) return;

        Arrays.sort(files, Comparator.comparingLong((File f) -> f.lastModified()).reversed());
        for (int i = 5; i < files.length; i++) {
            deleteQuietly(files[i].toPath());
            DebugLogger.debug(DEBUG_TAG, "清理旧备份: %s", files[i].getName());
        }
        DebugLogger.info(DEBUG_TAG, "已清理 %d 份旧备份", files.length - 5);
    }

    /** 递归删除目录或文件（静默忽略失败） */
    private static void deleteQuietly(Path path) {
        try {
            if (Files.isDirectory(path)) {
                deleteRecursively(path);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            DebugLogger.warn(DEBUG_TAG, "清理文件失败（无害）: %s", path);
        }
    }

    /** 递归删除目录 */
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                for (Path child : (Iterable<Path>) children::iterator) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /** 返回当前时间戳字符串 {@code yyyyMMdd_HHmmss} */
    private static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    /**
     * 上报进度（含每 200 文件 / 100ms 节流）。
     *
     * @return 本次上报的时间戳（用于下次节流比较）
     */
    private static long reportProgress(ProgressCallback callback, int processed, int total,
                                        String phase, int entryCount, long lastReportTime) {
        if (callback == null) return lastReportTime;
        long now = System.currentTimeMillis();
        // 每 200 文件或每 5% 变化且距上次 > 100ms 时上报
        boolean byFile = entryCount % 200 == 0;
        boolean byPct = total > 0 && (int) ((float) processed / total * 100) % 5 == 0;
        boolean timeElapsed = (now - lastReportTime) >= 100;
        if ((byFile || byPct) && timeElapsed) {
            callback.onProgress(processed, total, phase);
            return now;
        }
        return lastReportTime;
    }
}
