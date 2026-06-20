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
 * 账户管理器
 * 负责玩家账户的注册、登录、密码校验、数据持久化
 */
public class AccountManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ACCOUNT_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("youzaiworldcore")
            .resolve("account");

    private static final Map<UUID, LoginState> LOGIN_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, AccountEntry> ACCOUNT_CACHE = new ConcurrentHashMap<>();

    private static boolean initialized = false;

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

    // ===== SHA-256 =====

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

    private static Path getAccountFile(UUID uuid) {
        return ACCOUNT_DIR.resolve(uuid.toString() + ".json");
    }

    public static AccountEntry loadAccount(UUID uuid) {
        // 先查缓存
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

    /** 检查账户是否已注册 */
    public static boolean isRegistered(UUID uuid) {
        return getAccountFile(uuid).toFile().exists();
    }

    /** 检查指定名称是否已被注册（跨 UUID 查重） */
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

    /** 注册新账户 */
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

    /** 登录校验 */
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
            entry.resetFailedAttempts();
            saveAccount(uuid, entry);
            return LoginResult.SUCCESS;
        } else {
            entry.incrementFailedAttempts();
            saveAccount(uuid, entry);

            int failed = entry.getFailedAttempts();
            int total = entry.getTotalFailedAttempts();

            if (failed >= 5 || total >= 5) {
                entry.setBlocked(true);
                saveAccount(uuid, entry);
                return LoginResult.BLOCKED;
            }
            if (failed >= 3) {
                return LoginResult.KICK;
            }
            return LoginResult.WRONG_PASSWORD;
        }
    }

    // ===== 登录状态管理 =====

    public static LoginState getLoginState(ServerPlayer player) {
        return LOGIN_STATES.computeIfAbsent(player.getUUID(), uuid -> new LoginState(player));
    }

    public static void setLoggedIn(ServerPlayer player, boolean loggedIn) {
        LoginState state = getLoginState(player);
        state.setLoggedIn(loggedIn);
        if (loggedIn) {
            state.resetFailures();
        }
    }

    public static boolean isLoggedIn(ServerPlayer player) {
        LoginState state = LOGIN_STATES.get(player.getUUID());
        return state != null && state.isLoggedIn();
    }

    public static void removePlayer(ServerPlayer player) {
        LOGIN_STATES.remove(player.getUUID());
    }

    /** 通过玩家代号查找 UUID（遍历） */
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

    /** 通过玩家代号直接加载账户 */
    public static AccountEntry loadAccountByPlayerName(String playerName) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) return null;
        return loadAccount(uuid);
    }

    /** 通过玩家代号保存账户 */
    public static void saveAccountByPlayerName(String playerName, AccountEntry entry) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) {
            // 理论上不会走到这里，因为 mgr create 时已经注册过了
            return;
        }
        saveAccount(uuid, entry);
    }

    public static void unblockPlayer(String playerName) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) return;
        AccountEntry entry = loadAccount(uuid);
        if (entry == null) return;
        entry.setBlocked(false);
        entry.setFailedAttempts(0);
        saveAccount(uuid, entry);
    }

    // ===== 新增功能 =====

    /** 快照玩家当前位置（注册/登录前保存位置） */
    public static void snapshotState(ServerPlayer player) {
        LoginState state = getLoginState(player);
        state.snapshotFullState(player);
    }

    /** 修改密码 */
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

    /** 管理员重置密码（通过玩家代号） */
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

    /** 删除账户（通过玩家代号，需验证密码） */
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

    /** 获取账户状态描述 */
    public static String getAccountStatus(String playerName) {
        UUID uuid = findUUIDByPlayerName(playerName);
        if (uuid == null) {
            return "\u73A9\u5BB6" + playerName + "\u7684\u8D26\u6237\u4E0D\u5B58\u5728\uFF01";
        }

        AccountEntry entry = loadAccount(uuid);
        if (entry == null) {
            return "\u73A9\u5BB6" + playerName + "\u7684\u8D26\u6237\u4E0D\u5B58\u5728\uFF01";
        }

        String status = entry.isBlocked() ? "\u963B\u6B62\u767B\u5165" : "\u6B63\u5E38";
        return "\u73A9\u5BB6" + playerName + "\u7684\u8D26\u6237\u5F53\u524D\u72B6\u6001\u4E3A" + status + "\u3002";
    }
}
