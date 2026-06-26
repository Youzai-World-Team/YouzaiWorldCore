package top.csituka.youzaiworldcore.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账户管理器（核心类）。
 * <p>
 * 职责：</p>
 * <ul>
 *   <li>SHA-256 密码哈希</li>
 *   <li>账户文件 JSON 持久化（读取/写入/删除）</li>
 *   <li>玩家代号查重（跨 UUID 遍历所有账户文件）</li>
 *   <li>注册与登录校验（含失败阈值判定）</li>
 *   <li>登录状态管理（LoggedIn / LoginState）</li>
 *   <li>管理员操作（重置密码 / 删除账户 / 解封 / 查看状态）</li>
 * </ul>
 *
 * <p>所有方法均为 static，以单例方式运行。</p>
 *
 * @see AccountEntry     存储的数据模型
 * @see AccountRegisterResult  注册结果枚举
 * @see LoginResult      登录结果枚举
 * @see LoginState       玩家登录期间的状态快照
 */
public class AccountManager {

    /** Gson 实例，用于 JSON 序列化/反序列化（带 pretty-print） */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 账户文件存储目录：config/youzaiworldcore/account/ */
    private static final Path ACCOUNT_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("youzaiworldcore")
            .resolve("account");

    /** 玩家登录状态缓存（UUID → LoginState），登出或超时后移除 */
    private static final Map<UUID, LoginState> LOGIN_STATES = new ConcurrentHashMap<>();

    /** 账户数据内存缓存（UUID → AccountEntry），文件回写时同步更新 */
    private static final Map<UUID, AccountEntry> ACCOUNT_CACHE = new ConcurrentHashMap<>();

    /** 是否已经完成初始化（防止重复创建目录） */
    private static boolean initialized = false;

    /**
     * 初始化账户管理器。
     * 创建账户存储目录（如果不存在），仅在首次调用时执行。
     */
    public static void initialize() {
        if (initialized) return;
        try {
            Files.createDirectories(ACCOUNT_DIR);
            YouzaiworldCore.LOGGER.info("账户目录已初始化: {}", ACCOUNT_DIR);
            initialized = true;
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("无法创建账户目录: {}", e.getMessage());
        }
    }

    // ===== SHA-256 哈希 =====

    /**
     * 对明文密码进行 SHA-256 哈希，返回小写十六进制字符串。
     *
     * @param password 明文密码
     * @return 64 字符的小写十六进制哈希值
     * @throws RuntimeException 如果当前 JVM 不支持 SHA-256（理论上不会发生）
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ===== 文件 I/O =====

    /**
     * 获取指定 UUID 对应的账户文件路径。
     *
     * @param uuid 玩家 UUID
     * @return Path 对象，指向 {@code .../account/{uuid}.json}
     */
    private static Path getAccountFile(UUID uuid) {
        return ACCOUNT_DIR.resolve(uuid.toString() + ".json");
    }

    /**
     * 从磁盘加载指定 UUID 的账户数据。
     * <p>
     * 优先返回内存缓存中的对象；
     * 如果缓存未命中再从磁盘文件读取并回填缓存。
     * </p>
     *
     * @param uuid 玩家 UUID
     * @return AccountEntry 对象；如果文件不存在返回 {@code null}
     */
    public static AccountEntry loadAccount(UUID uuid) {
        // 先查缓存，避免频繁磁盘 I/O
        AccountEntry cached = ACCOUNT_CACHE.get(uuid);
        if (cached != null) return cached;

        Path file = getAccountFile(uuid);
        if (!Files.exists(file)) return null;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            AccountEntry entry = GSON.fromJson(json, AccountEntry.class);
            ACCOUNT_CACHE.put(uuid, entry);
            return entry;
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("读取账户文件失败 [{}]: {}", uuid, e.getMessage());
            return null;
        }
    }

    /**
     * 将指定 UUID 的账户数据写入磁盘并更新缓存。
     *
     * @param uuid  玩家 UUID
     * @param entry 要保存的账户条目
     */
    public static void saveAccount(UUID uuid, AccountEntry entry) {
        try {
            Files.createDirectories(ACCOUNT_DIR);
            Path file = getAccountFile(uuid);
            String json = GSON.toJson(entry);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            ACCOUNT_CACHE.put(uuid, entry);
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("保存账户文件失败 [{}]: {}", uuid, e.getMessage());
        }
    }

    /**
     * 删除指定 UUID 的账户文件，同时清理内存缓存和登录状态。
     *
     * @param uuid 玩家 UUID
     */
    public static void deleteAccount(UUID uuid) {
        try {
            Path file = getAccountFile(uuid);
            Files.deleteIfExists(file);
            ACCOUNT_CACHE.remove(uuid);
            LOGIN_STATES.remove(uuid);
        } catch (Exception e) {
            YouzaiworldCore.LOGGER.error("删除账户文件失败 [{}]: {}", uuid, e.getMessage());
        }
    }

    /**
     * 检查指定 UUID 是否已注册账户。
     *
     * @param uuid 玩家 UUID
     * @return true 如果该 UUID 对应的账户文件存在
     */
    public static boolean isRegistered(UUID uuid) {
        return getAccountFile(uuid).toFile().exists();
    }

    /**
     * 检查指定玩家代号是否已被其他账户占用。
     * <p>
     * 遍历 ACCOUNT_DIR 下所有 JSON 文件，逐一比较 playerName 字段。
     * 比较不区分大小写。
     * </p>
     *
     * @param playerName 待检查的玩家代号
     * @return true 如果已有其他账户使用了该名称
     */
    public static boolean isPlayerNameTaken(String playerName) {
        try {
            return Files.list(ACCOUNT_DIR).anyMatch(path -> {
                if (!path.toString().endsWith(".json")) return false;
                try {
                    String json = Files.readString(path);
                    AccountEntry e = GSON.fromJson(json, AccountEntry.class);
                    return e != null && e.getPlayerName().equalsIgnoreCase(playerName);
                } catch (Exception ignored) {
                    return false;
                }
            });
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 注册 & 登录逻辑 =====

    /**
     * 注册新账户。
     * <p>
     * 流程：</p>
     * <ol>
     *   <li>检查 UUID 是否已注册 → 已注册则返回 {@link AccountRegisterResult#ALREADY_REGISTERED}</li>
     *   <li>检查玩家代号是否被占用 → 被占用则返回 {@link AccountRegisterResult#NAME_TAKEN}</li>
     *   <li>对密码做 SHA-256 哈希，构造 AccountEntry 并持久化</li>
     *   <li>返回 {@link AccountRegisterResult#SUCCESS}</li>
     * </ol>
     *
     * @param uuid       玩家 UUID
     * @param playerName 玩家代号
     * @param password   明文密码
     * @return 注册结果枚举
     */
    public static AccountRegisterResult register(UUID uuid, String playerName, String password) {
        if (isRegistered(uuid)) {
            return AccountRegisterResult.ALREADY_REGISTERED;
        }
        if (isPlayerNameTaken(playerName)) {
            return AccountRegisterResult.NAME_TAKEN;
        }

        String hash = hashPassword(password);
        AccountEntry entry = new AccountEntry(playerName, hash);
        saveAccount(uuid, entry);
        return AccountRegisterResult.SUCCESS;
    }

    /**
     * 登录校验。
     * <p>
     * 流程：</p>
     * <ol>
     *   <li>加载账户 → 不存则返回 {@link LoginResult#NOT_REGISTERED}</li>
     *   <li>检查是否被阻止 → 是则返回 {@link LoginResult#BLOCKED}</li>
     *   <li>比较密码哈希 → 匹配则重置失败计数并返回 {@link LoginResult#SUCCESS}</li>
     *   <li>密码不匹配 → 递增失败计数，根据阈值返回 {@link LoginResult#WRONG_PASSWORD} /
     *       {@link LoginResult#KICK} / {@link LoginResult#BLOCKED}</li>
     * </ol>
     *
     * @param uuid     玩家 UUID
     * @param password 明文密码
     * @return 登录结果枚举
     */
    public static LoginResult login(UUID uuid, String password) {
        AccountEntry entry = loadAccount(uuid);
        if (entry == null) {
            return LoginResult.NOT_REGISTERED;
        }
        if (entry.isBlocked()) {
            return LoginResult.BLOCKED;
        }

        String hash = hashPassword(password);
        if (entry.getPasswordHash().equals(hash)) {
            // 密码正确：重置失败计数并保存
            entry.resetFailedAttempts();
            saveAccount(uuid, entry);
            return LoginResult.SUCCESS;
        } else {
            // 密码错误：递增失败计数
            entry.incrementFailedAttempts();
            saveAccount(uuid, entry);

            int failed = entry.getFailedAttempts();
            int total = entry.getTotalFailedAttempts();

            // 阈值判定：连续 5 次 或 终身累计 5 次 → 阻止登入
            if (failed >= 5 || total >= 5) {
                entry.setBlocked(true);
                saveAccount(uuid, entry);
                return LoginResult.BLOCKED;
            }
            // 连续 3 次 → 踢出服务器
            if (failed >= 3) {
                return LoginResult.KICK;
            }
            // 1~2 次 → 仅提示密码错误
            return LoginResult.WRONG_PASSWORD;
        }
    }

    // ===== 登录状态管理 =====

    /**
     * 获取或创建玩家的登录状态对象。
     *
     * @param player 服务端玩家对象
     * @return 与该玩家关联的 LoginState 实例
     */
    public static LoginState getLoginState(ServerPlayer player) {
        return LOGIN_STATES.computeIfAbsent(player.getUUID(), uuid -> new LoginState(player));
    }

    /**
     * 设置玩家的登录/登出状态。
     * 登录成功时同时重置连续失败计数。
     *
     * @param player   服务端玩家对象
     * @param loggedIn true 表示已登录，false 表示已登出
     */
    public static void setLoggedIn(ServerPlayer player, boolean loggedIn) {
        LoginState state = getLoginState(player);
        state.setLoggedIn(loggedIn);
        if (loggedIn) {
            state.resetFailures();
        }
    }

    /**
     * 检查玩家是否已登录。
     *
     * @param player 服务端玩家对象
     * @return true 如果 LoginState 存在且 loggedIn == true
     */
    public static boolean isLoggedIn(ServerPlayer player) {
        LoginState state = LOGIN_STATES.get(player.getUUID());
        return state != null && state.isLoggedIn();
    }

    /**
     * 获取当前登录状态映射的大小（调试用）。
     *
     * @return LOGIN_STATES 中的条目数
     */
    public static int getLoginStateMapSize() {
        return LOGIN_STATES.size();
    }

    /**
     * 从登录状态映射中移除指定玩家的记录。
     * 通常在玩家登出、被踢出或注销时调用。
     *
     * @param player 服务端玩家对象
     */
    public static void removePlayer(ServerPlayer player) {
        LOGIN_STATES.remove(player.getUUID());
    }

    /**
     * 通过玩家代号查找对应的 UUID。
     * <p>
     * 遍历 ACCOUNT_DIR 下所有 JSON 文件，读取 playerName 字段进行比较。
     * </p>
     *
     * @param playerName 玩家代号（不区分大小写）
     * @return 匹配的 UUID；如果找不到则返回 {@code null}
     */
    public static UUID findUUIDByPlayerName(String playerName) {
        try {
            return Files.list(ACCOUNT_DIR).filter(path -> path.toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            String json = Files.readString(path);
                            AccountEntry e = GSON.fromJson(json, AccountEntry.class);
                            if (e != null && e.getPlayerName().equalsIgnoreCase(playerName)) {
                                String fileName = path.getFileName().toString();
                                return UUID.fromString(fileName.replace(".json", ""));
                            }
                        } catch (Exception ignored) {}
                        return null;
                    })
                    .filter(uuid -> uuid != null)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过玩家代号直接加载账户（无需预知 UUID）。
     *
     * @param playerName 玩家代号
     * @return AccountEntry；如果找不到则返回 {@code null}
     */
    public static AccountEntry loadAccountByPlayerName(String playerName) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) return null;
        return loadAccount(uuid);
    }

    /**
     * 通过玩家代号保存账户（无需预知 UUID）。
     *
     * @param playerName 玩家代号
     * @param entry      要保存的账户条目
     */
    public static void saveAccountByPlayerName(String playerName, AccountEntry entry) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) {
            // 理论上不会走到这里，因为调用方已经确保玩家代号存在
            return;
        }
        saveAccount(uuid, entry);
    }

    /**
     * 解封指定玩家，清除阻止状态和连续失败计数。
     *
     * @param playerName 玩家代号
     */
    public static void unblockPlayer(String playerName) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) return;
        AccountEntry entry = loadAccount(uuid);
        if (entry == null) return;
        entry.setBlocked(false);
        entry.setFailedAttempts(0);
        saveAccount(uuid, entry);
    }

    // ===== 额外功能 =====

    /**
     * 快照玩家当前完整状态（位置、血量、饱食度、背包、状态效果）。
     * 用于在登录/注册前保存状态，以便后续恢复。
     *
     * @param player 服务端玩家对象
     */
    public static void snapshotState(ServerPlayer player) {
        LoginState state = getLoginState(player);
        state.snapshotFullState(player);
    }

    /**
     * 仅快照玩家当前位置和朝向，不保存其他状态数据。
     * <p>
     * 在服务端每 Tick 调用，作为 DISCONNECT 事件可能未触发的兜底措施。
     * 确保玩家断线时，LOGIN_STATES 中始终有最新的位置信息。</p>
     *
     * @param player 服务端玩家对象
     */
    public static void snapshotPositionOnly(ServerPlayer player) {
        LoginState state = LOGIN_STATES.get(player.getUUID());
        if (state != null) {
            state.snapshotPosition(player);
            if (!state.hasSnapshot()) {
                state.markSnapshot();
            }
        }
    }

    /**
     * 修改玩家密码。
     * <p>
     * 需验证旧密码正确性。旧密码匹配后才会更新为新的 SHA-256 哈希。
     * </p>
     *
     * @param uuid        玩家 UUID
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文）
     * @return true 如果修改成功
     */
    public static boolean changePassword(UUID uuid, String oldPassword, String newPassword) {
        AccountEntry entry = loadAccount(uuid);
        if (entry == null) return false;

        String oldHash = hashPassword(oldPassword);
        if (!entry.getPasswordHash().equals(oldHash)) return false;

        String newHash = hashPassword(newPassword);
        entry.setPasswordHash(newHash);
        saveAccount(uuid, entry);
        return true;
    }

    /**
     * 管理员重置指定玩家的密码（通过玩家代号）。
     *
     * @param playerName 玩家代号
     * @param newPassword 新密码（明文）
     * @return true 如果重置成功（玩家存在）
     */
    public static boolean resetPassword(String playerName, String newPassword) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) return false;

        AccountEntry entry = loadAccount(uuid);
        if (entry == null) return false;

        String newHash = hashPassword(newPassword);
        entry.setPasswordHash(newHash);
        saveAccount(uuid, entry);
        return true;
    }

    /**
     * 通过玩家代号删除账户（需验证密码）。
     *
     * @param playerName 玩家代号
     * @param password   账户密码（明文）
     * @return true 如果删除成功
     */
    public static boolean deleteAccountByPlayerName(String playerName, String password) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) return false;

        AccountEntry entry = loadAccount(uuid);
        if (entry == null) return false;

        String hash = hashPassword(password);
        if (!entry.getPasswordHash().equals(hash)) return false;

        deleteAccount(uuid);
        return true;
    }

    /**
     * 获取指定玩家的账户状态描述文本。
     *
     * @param playerName 玩家代号
     * @return 描述字符串，例如 "玩家xxx的账户当前状态为正常。" 或 "玩家xxx的账户不存在！"
     */
    public static String getAccountStatus(String playerName) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) {
            return "玩家" + playerName + "的账户不存在！";
        }

        AccountEntry entry = loadAccount(uuid);
        if (entry == null) {
            return "玩家" + playerName + "的账户不存在！";
        }

        String status = entry.isBlocked() ? "阻止登入" : "正常";
        return "玩家" + playerName + "的账户当前状态为" + status + "。";
    }
}
