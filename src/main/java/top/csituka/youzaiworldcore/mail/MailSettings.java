package top.csituka.youzaiworldcore.mail;

import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件系统配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code mail_module} 分节。
 * </p>
 * <p>
 * 邮件<b>数据</b>（正文仓库与每玩家收件箱）不在这里，也不再落到本地磁盘：
 * 由 Api 服务端的 {@code game_mails} / {@code game_mail_refs} 表权威保存，
 * 模组侧访问入口是 {@link MailApiClient}。
 * </p>
 */
@SuppressWarnings("null")
public class MailSettings {

    private static final String MODULE = "MailSettings";

    private static MailSettings INSTANCE = null;

    // ===== 默认值 =====

    private String defaultExpire = "30d";

    private List<String> expireOptions = new ArrayList<>(List.of("1d", "7d", "30d", "permanent"));

    private boolean keepStarredAfterExpire = true;

    private int maxMailsPerPlayer = 200;

    private int autoPurgeIntervalTicks = 3000;

    /**
     * 在线玩家未读徽标的兜底刷新间隔（tick），0 表示关闭。
     * <p>
     * 后台管理页正常会通过 MCSM 执行 {@code /yzwc mail pull <mailId>}，由模组即时
     * 拉取并推送新邮件；本周期仅用于面板通知失败、网络抖动等情况下最终校准红点。
     * </p>
     */
    private int unreadRefreshIntervalTicks = 3000;

    private int mailPermissionLevel = 4;

    private String mailPermissionNode = "youzaiworldcore.mail";

    private int maxItemAttachments = 10;

    private int maxAttachmentsPerMail = 16;

    // ===== 初始化 =====

    /**
     * 初始化配置：从全局配置的 {@code mail_module} 分节读取；分节缺失时写入默认值。
     */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        ConfigSection section = GlobalSettings.section(GlobalSettings.MAIL_MODULE);
        if (section.isEmpty()) {
            INSTANCE = new MailSettings();
            save();
            DebugLogger.info(MODULE, "mail_module 分节不存在，已写入默认邮件配置");
        } else {
            load();
        }
        DebugLogger.exiting(MODULE, "initialize");
    }

    // ===== 加载 / 保存 =====

    /**
     * 从全局配置的 {@code mail_module} 分节加载。
     */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.MAIL_MODULE);
        MailSettings loaded = new MailSettings();

        loaded.defaultExpire = section.getString("default_expire", loaded.defaultExpire);
        loaded.expireOptions = new ArrayList<>(section.getStringList("expire_options", loaded.expireOptions));
        loaded.keepStarredAfterExpire =
                section.getBoolean("keep_starred_after_expire", loaded.keepStarredAfterExpire);
        loaded.maxMailsPerPlayer =
                section.getInt("max_mails_per_player", loaded.maxMailsPerPlayer, 1, Integer.MAX_VALUE);
        loaded.autoPurgeIntervalTicks =
                section.getInt("auto_purge_interval_ticks", loaded.autoPurgeIntervalTicks, 0, Integer.MAX_VALUE);
        loaded.unreadRefreshIntervalTicks =
                section.getInt("unread_refresh_interval_ticks", loaded.unreadRefreshIntervalTicks,
                        0, Integer.MAX_VALUE);
        loaded.mailPermissionLevel =
                section.getInt("mail_permission_level", loaded.mailPermissionLevel, 0, 4);
        loaded.mailPermissionNode = section.getString("mail_permission_node", loaded.mailPermissionNode);
        loaded.maxItemAttachments =
                section.getInt("max_item_attachments", loaded.maxItemAttachments, 0, Integer.MAX_VALUE);
        loaded.maxAttachmentsPerMail =
                section.getInt("max_attachments_per_mail", loaded.maxAttachmentsPerMail, 0, Integer.MAX_VALUE);

        if (loaded.expireOptions.isEmpty()) {
            section.fail("expire_options", "过期时长选项不能是空数组");
        }

        INSTANCE = loaded;
        DebugLogger.info(MODULE, "已加载邮件配置");
        DebugLogger.exiting(MODULE, "load");
    }

    /**
     * 重置为默认值并写入 {@code mail_module} 分节（新开服 / 坏文件恢复用）。
     */
    public static void writeDefaults() {
        INSTANCE = new MailSettings();
        save();
    }

    /**
     * 保存当前配置到全局配置文件的 {@code mail_module} 分节。
     */
    public static void save() {
        DebugLogger.entering(MODULE, "save");
        MailSettings current = get();
        ConfigSection section = GlobalSettings.section(GlobalSettings.MAIL_MODULE);
        section.set("default_expire", current.defaultExpire);
        section.setStringCollection("expire_options", current.expireOptions);
        section.set("keep_starred_after_expire", current.keepStarredAfterExpire);
        section.set("max_mails_per_player", current.maxMailsPerPlayer);
        section.set("auto_purge_interval_ticks", current.autoPurgeIntervalTicks);
        section.set("unread_refresh_interval_ticks", current.unreadRefreshIntervalTicks);
        section.set("mail_permission_level", current.mailPermissionLevel);
        section.set("mail_permission_node", current.mailPermissionNode);
        section.set("max_item_attachments", current.maxItemAttachments);
        section.set("max_attachments_per_mail", current.maxAttachmentsPerMail);
        GlobalSettings.save();
        DebugLogger.info(MODULE, "已保存邮件配置");
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

    public int getUnreadRefreshIntervalTicks() {
        return unreadRefreshIntervalTicks;
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
