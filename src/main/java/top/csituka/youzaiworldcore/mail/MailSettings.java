package top.csituka.youzaiworldcore.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 邮件系统配置：{@code config/youzaiworldcore/mail_settings.json}。
 * <p>
 * 采用懒加载单例模式，仅在首次访问时从磁盘读取。
 * </p>
 */
@SuppressWarnings("null")
public class MailSettings {

    private static final String MODULE = "MailSettings";

    private static MailSettings INSTANCE = null;
    private static Path CONFIG_FILE;
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

    // ===== 默认值 =====

    @Expose
    private String defaultExpire = "30d";

    @Expose
    private List<String> expireOptions = new ArrayList<>(List.of("1d", "7d", "30d", "permanent"));

    @Expose
    private boolean keepStarredAfterExpire = true;

    @Expose
    private int maxMailsPerPlayer = 200;

    @Expose
    private int autoPurgeIntervalTicks = 3000;

    @Expose
    private int mailPermissionLevel = 4;

    @Expose
    private String mailPermissionNode = "youzaiworldcore.mail";

    @Expose
    private int maxItemAttachments = 10;

    @Expose
    private int maxAttachmentsPerMail = 16;

    // ===== 初始化 =====

    /**
     * 初始化配置（读取或创建默认配置）。
     *
     * @param configDir 配置目录 {@code config/youzaiworldcore}
     */
    public static void initialize(Path configDir) {
        DebugLogger.entering(MODULE, "initialize", "configDir=" + configDir);
        CONFIG_FILE = configDir.resolve("mail_settings.json");
        if (Files.exists(CONFIG_FILE)) {
            load();
        } else {
            INSTANCE = new MailSettings();
            save();
            YouzaiworldCore.LOGGER.info("已创建默认邮件配置: {}", CONFIG_FILE.toAbsolutePath());
        }
        DebugLogger.exiting(MODULE, "initialize");
    }

    // ===== 加载 / 保存 =====

    /**
     * 从磁盘加载配置。
     */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        if (CONFIG_FILE == null) {
            DebugLogger.branch(MODULE, "CONFIG_FILE not initialized", true);
            // 尝试默认路径（应已由 initialize 设置）
            INSTANCE = new MailSettings();
            DebugLogger.exiting(MODULE, "load", "fallback to defaults");
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            MailSettings loaded = GSON.fromJson(json, MailSettings.class);
            if (loaded != null) {
                INSTANCE = loaded;
                DebugLogger.info(MODULE, "已加载邮件配置");
            }
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("读取邮件配置失败，使用默认值", e);
            INSTANCE = new MailSettings();
        }
        DebugLogger.exiting(MODULE, "load");
    }

    /**
     * 保存当前配置到磁盘。
     */
    public static void save() {
        DebugLogger.entering(MODULE, "save");
        if (CONFIG_FILE == null || INSTANCE == null) {
            DebugLogger.exiting(MODULE, "save", "skipped: no file or instance");
            return;
        }
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(INSTANCE));
            DebugLogger.info(MODULE, "已保存邮件配置");
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("保存邮件配置失败", e);
        }
        DebugLogger.exiting(MODULE, "save");
    }

    // ===== 单例访问 =====

    public static MailSettings get() {
        if (INSTANCE == null) {
            INSTANCE = new MailSettings();
        }
        return INSTANCE;
    }

    // ===== Getters =====

    public String getDefaultExpire() {
        return defaultExpire;
    }

    public List<String> getExpireOptions() {
        return expireOptions;
    }

    public boolean isKeepStarredAfterExpire() {
        return keepStarredAfterExpire;
    }

    public int getMaxMailsPerPlayer() {
        return maxMailsPerPlayer;
    }

    public int getAutoPurgeIntervalTicks() {
        return autoPurgeIntervalTicks;
    }

    public int getMailPermissionLevel() {
        return mailPermissionLevel;
    }

    public String getMailPermissionNode() {
        return mailPermissionNode;
    }

    public int getMaxItemAttachments() {
        return maxItemAttachments;
    }

    public int getMaxAttachmentsPerMail() {
        return maxAttachmentsPerMail;
    }
}
