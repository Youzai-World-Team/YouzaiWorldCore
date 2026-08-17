package top.csituka.youzaiworldcore.mail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每玩家邮件索引（收件箱）：{@code yzwc/server/data/mail_module/box/<player-uuid>.json}。
 * <p>
 * 每玩家一个 JSON 文件，含轻量 {@link MailRef} 列表，不含正文。
 * 加载时自动剔除正文已不存在（被撤回）与过期未星标的条目。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public class MailDataStorage {

    private static final String MODULE = "MailDataStorage";

    private static Path BOX_DIR;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 内存缓存：玩家 UUID -> 收件箱。避免每次操作都读写磁盘。 */
    private static final Map<UUID, PlayerMailbox> CACHE = new ConcurrentHashMap<>();

    /**
     * 每玩家信箱（存储及 Gson 序列化用）。
     */
    public static class PlayerMailbox {
        private UUID playerUuid;
        private List<MailRef> mails = new ArrayList<>();

        public PlayerMailbox() {
        }

        public PlayerMailbox(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public void setPlayerUuid(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        public List<MailRef> getMails() {
            return mails;
        }

        public void setMails(List<MailRef> mails) {
            this.mails = mails;
        }
    }

    // ===== 初始化 =====

    /**
     * 初始化收件箱存储目录 {@code yzwc/server/data/mail_module/box/}。
     */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        BOX_DIR = ModPaths.serverData(GlobalSettings.MAIL_MODULE).resolve("box");
        ModPaths.ensureDir(BOX_DIR);
        DebugLogger.info(MODULE, "收件箱目录=%s", BOX_DIR.toAbsolutePath());
        DebugLogger.exiting(MODULE, "initialize");
    }

    // ===== 核心操作 =====

    /**
     * 加载指定玩家的收件箱。
     * <p>
     * 自动剔除正文缺失（已撤回）与过期未星标的条目。
     * </p>
     *
     * @param playerUuid 玩家 UUID
     * @return 玩家信箱（不含已剔除条目）；新玩家返回空索引
     */
    public static PlayerMailbox load(UUID playerUuid) {
        DebugLogger.entering(MODULE, "load", "playerUuid=" + playerUuid);

        PlayerMailbox box = loadInternal(playerUuid, true);
        DebugLogger.exiting(MODULE, "load", "mails=" + box.getMails().size());
        return box;
    }

    /**
     * 加载指定玩家的收件箱，但不把此前未缓存的离线邮箱留在内存中。
     * 群发邮件使用此路径，避免一次操作把所有离线账户都加入缓存。
     */
    private static PlayerMailbox loadInternal(UUID playerUuid, boolean cacheResult) {

        // 先查内存缓存
        PlayerMailbox cached = CACHE.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        Path file = getPlayerFile(playerUuid);
        PlayerMailbox box;

        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                box = GSON.fromJson(json, PlayerMailbox.class);
                if (box == null) {
                    box = new PlayerMailbox(playerUuid);
                }
            } catch (IOException e) {
                YouzaiworldCore.LOGGER.error("读取玩家收件箱失败 [{}]: {}", playerUuid, e.getMessage());
                box = new PlayerMailbox(playerUuid);
            }
        } else {
            box = new PlayerMailbox(playerUuid);
        }
        if (box.getMails() == null) {
            box.setMails(new ArrayList<>());
        }

        // 加载时清理：剔除正文不存在（已撤回）与过期未星标
        int before = box.getMails().size();
        box.getMails().removeIf(ref -> {
            Mail mail = SentMailRepository.get(ref.getMailId());
            if (mail == null) {
                // 正文不存在（已撤回）
                DebugLogger.info(MODULE, "剔除引用: mailId=%s (仓库已删除)", ref.getMailId());
                return true;
            }
            // 过期且未星标
            if (mail.isExpired() && !ref.isStarred() && !MailSettings.get().isKeepStarredAfterExpire()) {
                DebugLogger.info(MODULE, "剔除引用: mailId=%s (已过期且未星标)", ref.getMailId());
                return true;
            }
            // hidden 标记的不显示（编辑中隐藏），但保留引用（不剔除，仅渲染时不显示）
            return false;
        });
        int removed = before - box.getMails().size();
        if (removed > 0) {
            DebugLogger.info(MODULE, "加载清理: 移除了 %d 个引用 (playerUuid=%s)", removed, playerUuid);
            saveInternal(playerUuid, box, cacheResult);
        }
        // 放入缓存
        if (cacheResult) {
            CACHE.put(playerUuid, box);
        }
        return box;
    }

    /**
     * 保存指定玩家的收件箱。
     *
     * @param playerUuid 玩家 UUID
     * @param box        玩家信箱
     */
    public static void save(UUID playerUuid, PlayerMailbox box) {
        DebugLogger.entering(MODULE, "save", "playerUuid=" + playerUuid + ", mails=" + box.getMails().size());
        saveInternal(playerUuid, box, true);
        DebugLogger.exiting(MODULE, "save");
    }

    private static void saveInternal(UUID playerUuid, PlayerMailbox box, boolean cacheResult) {
        // 已经存在的在线缓存必须保持更新；纯离线操作则不创建新缓存。
        if (cacheResult || CACHE.containsKey(playerUuid)) {
            CACHE.put(playerUuid, box);
        }
        Path file = getPlayerFile(playerUuid);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(box));
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("保存玩家收件箱失败 [{}]: {}", playerUuid, e.getMessage());
        }
    }

    /**
     * 为指定玩家添加一条邮件引用（快捷方法）。
     *
     * @param playerUuid 玩家 UUID
     * @param ref        邮件引用
     */
    public static void addRef(UUID playerUuid, MailRef ref) {
        DebugLogger.entering(MODULE, "addRef", "playerUuid=" + playerUuid + ", mailId=" + ref.getMailId());
        addRefInternal(playerUuid, ref, true);
        DebugLogger.exiting(MODULE, "addRef");
    }

    /** 为离线玩家添加邮件引用，不创建新的长期缓存条目。 */
    public static void addRefUncached(UUID playerUuid, MailRef ref) {
        addRefInternal(playerUuid, ref, false);
    }

    private static void addRefInternal(UUID playerUuid, MailRef ref, boolean cacheResult) {
        PlayerMailbox box = loadInternal(playerUuid, cacheResult);
        // 避免重复添加
        boolean exists = box.getMails().stream()
                .anyMatch(r -> r.getMailId().equals(ref.getMailId()));
        if (!exists) {
            box.getMails().add(ref);
            saveInternal(playerUuid, box, cacheResult);
            DebugLogger.info(MODULE, "已添加邮件引用: playerUuid=%s, mailId=%s", playerUuid, ref.getMailId());
        } else {
            DebugLogger.info(MODULE, "邮件引用已存在，跳过: playerUuid=%s, mailId=%s", playerUuid, ref.getMailId());
        }
    }

    /** 玩家断开后释放其邮箱缓存；磁盘内容已在每次变更时保存。 */
    public static void invalidate(UUID playerUuid) {
        CACHE.remove(playerUuid);
    }

    /**
     * 更新指定玩家的某条邮件引用（读/星标/领取状态变更）。
     *
     * @param playerUuid 玩家 UUID
     * @param ref        已修改的邮件引用
     */
    public static void updateRef(UUID playerUuid, MailRef ref) {
        DebugLogger.entering(MODULE, "updateRef", "playerUuid=" + playerUuid + ", mailId=" + ref.getMailId());
        PlayerMailbox box = load(playerUuid);
        for (int i = 0; i < box.getMails().size(); i++) {
            if (box.getMails().get(i).getMailId().equals(ref.getMailId())) {
                box.getMails().set(i, ref);
                save(playerUuid, box);
                DebugLogger.info(MODULE, "已更新邮件引用: playerUuid=%s, mailId=%s, read=%s, starred=%s, claimed=%s",
                        playerUuid, ref.getMailId(), ref.isRead(), ref.isStarred(), ref.isClaimed());
                DebugLogger.exiting(MODULE, "updateRef");
                return;
            }
        }
        DebugLogger.warn(MODULE, "未找到邮件引用，不可更新: playerUuid=%s, mailId=%s", playerUuid, ref.getMailId());
        DebugLogger.exiting(MODULE, "updateRef", "not found");
    }

    /**
     * 删除指定玩家的邮件引用（玩家自行删除）。
     *
     * @param playerUuid 玩家 UUID
     * @param mailId     邮件 ID
     */
    public static void removeRef(UUID playerUuid, UUID mailId) {
        DebugLogger.entering(MODULE, "removeRef", "playerUuid=" + playerUuid + ", mailId=" + mailId);
        PlayerMailbox box = load(playerUuid);
        boolean removed = box.getMails().removeIf(ref -> ref.getMailId().equals(mailId));
        if (removed) {
            save(playerUuid, box);
            DebugLogger.info(MODULE, "已删除邮件引用: playerUuid=%s, mailId=%s", playerUuid, mailId);
        }
        DebugLogger.exiting(MODULE, "removeRef");
    }

    /**
     * 删除指定玩家的整个收件箱（账户注销时调用）。
     *
     * @param playerUuid 玩家 UUID
     */
    public static void removeBox(UUID playerUuid) {
        DebugLogger.entering(MODULE, "removeBox", "playerUuid=" + playerUuid);
        CACHE.remove(playerUuid);
        Path file = getPlayerFile(playerUuid);
        try {
            boolean deleted = Files.deleteIfExists(file);
            DebugLogger.info(MODULE, "已%s删除玩家收件箱: playerUuid=%s", deleted ? "" : "未", playerUuid);
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("删除玩家收件箱失败 [{}]: {}", playerUuid, e.getMessage());
        }
        DebugLogger.exiting(MODULE, "removeBox");
    }

    /**
     * 获取未读邮件数量。
     *
     * @param playerUuid 玩家 UUID
     * @return 未读邮件数
     */
    public static int getUnreadCount(UUID playerUuid) {
        PlayerMailbox box = load(playerUuid);
        return (int) box.getMails().stream().filter(ref -> !ref.isRead()).count();
    }

    // ===== 全局扫描（启动清理用） =====

    /**
     * 扫描 box 目录下所有玩家收件箱，汇总被任意玩家星标过的邮件 ID。
     * <p>
     * 直接读文件而不走 {@link #load}：load 会顺带触发剔除与回写，
     * 而本方法只做只读统计，且需要覆盖离线玩家。
     * </p>
     *
     * @return 被至少一名玩家星标的 mailId 集合（目录不存在时返回空集）
     */
    public static Set<UUID> collectStarredMailIds() {
        DebugLogger.entering(MODULE, "collectStarredMailIds");
        Set<UUID> starred = new HashSet<>();
        if (BOX_DIR == null || !Files.isDirectory(BOX_DIR)) {
            DebugLogger.exiting(MODULE, "collectStarredMailIds", "box dir missing");
            return starred;
        }
        try (var stream = Files.list(BOX_DIR)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                PlayerMailbox box = readBoxFile(file);
                if (box == null) {
                    continue;
                }
                for (MailRef ref : box.getMails()) {
                    if (ref.isStarred() && ref.getMailId() != null) {
                        starred.add(ref.getMailId());
                    }
                }
            }
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("扫描收件箱目录失败", e);
            DebugLogger.exception(MODULE, "collectStarredMailIds", e);
        }
        DebugLogger.exiting(MODULE, "collectStarredMailIds", "starred=" + starred.size());
        return starred;
    }

    /**
     * 剔除所有玩家收件箱中指向「仓库已不存在的邮件」的悬空引用。
     *
     * @return 实际发生改动并回写的收件箱数量
     */
    public static int pruneDanglingRefs() {
        DebugLogger.entering(MODULE, "pruneDanglingRefs");
        int changed = 0;
        if (BOX_DIR == null || !Files.isDirectory(BOX_DIR)) {
            DebugLogger.exiting(MODULE, "pruneDanglingRefs", "box dir missing");
            return 0;
        }
        try (var stream = Files.list(BOX_DIR)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                PlayerMailbox box = readBoxFile(file);
                if (box == null || box.getPlayerUuid() == null) {
                    continue;
                }
                boolean removed = box.getMails().removeIf(ref ->
                        ref.getMailId() == null || SentMailRepository.get(ref.getMailId()) == null);
                if (removed) {
                    saveInternal(box.getPlayerUuid(), box, false);
                    changed++;
                }
            }
        } catch (IOException e) {
            YouzaiworldCore.LOGGER.error("整理收件箱目录失败", e);
            DebugLogger.exception(MODULE, "pruneDanglingRefs", e);
        }
        DebugLogger.exiting(MODULE, "pruneDanglingRefs", "changed=" + changed);
        return changed;
    }

    /** 只读方式解析单个收件箱文件，失败时返回 null。 */
    private static PlayerMailbox readBoxFile(Path file) {
        try {
            String json = Files.readString(file);
            if (json.isBlank()) {
                return null;
            }
            PlayerMailbox box = GSON.fromJson(json, PlayerMailbox.class);
            if (box == null) {
                return null;
            }
            if (box.getMails() == null) {
                box.setMails(new ArrayList<>());
            }
            return box;
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("读取收件箱文件失败 [{}]: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    // ===== 工具方法 =====

    private static Path getPlayerFile(UUID playerUuid) {
        return BOX_DIR.resolve(playerUuid.toString() + ".json");
    }
}
