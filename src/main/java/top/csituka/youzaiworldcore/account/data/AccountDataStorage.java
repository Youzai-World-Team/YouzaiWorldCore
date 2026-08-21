package top.csituka.youzaiworldcore.account.data;

import top.csituka.youzaiworldcore.api.ApiServiceClient;
import top.csituka.youzaiworldcore.config.UserSettings;
import top.csituka.youzaiworldcore.mail.MailManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Api 服务端权威的账户运行期缓存；模组不读写账户凭据文件。 */
@SuppressWarnings("null")
public final class AccountDataStorage {
    private static final String MODULE = "AccountDataStorage";
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final ConcurrentHashMap<String, PlayerAccount> CACHE = new ConcurrentHashMap<>();
    private static volatile int loginCooldown = 300;

    private AccountDataStorage() {
    }

    public static int getLoginCooldown() {
        refreshSettings();
        return loginCooldown;
    }

    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        ApiServiceClient.getAccountSettings().ifPresentOrElse(settings -> {
            loginCooldown = settings.loginCooldown();
        }, () -> DebugLogger.warn(MODULE, "无法从 Api 加载账户设置，账户认证操作将继续以 Api 可达性为准"));
        reload();
        DebugLogger.exiting(MODULE, "initialize", "accounts=" + CACHE.size());
    }

    public static boolean setLoginCooldown(int seconds) {
        return ApiServiceClient.setLoginCooldown(seconds).map(settings -> {
            loginCooldown = settings.loginCooldown();
            return true;
        }).orElse(false);
    }

    public static PlayerAccount get(String username) {
        if (username == null)
            return null;
        LOCK.readLock().lock();
        try {
            return CACHE.get(username.toLowerCase(java.util.Locale.ENGLISH));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public static PlayerAccount getOrCreate(String username, UUID uuid) {
        PlayerAccount cached = get(username);
        if (cached != null) {
            if ((cached.uuid == null || cached.uuid.isBlank()) && uuid != null) {
                cached.uuid = uuid.toString();
                update(cached);
            }
            return cached;
        }
        ApiServiceClient.AccountResult result = ApiServiceClient.ensureAccount(username, uuid);
        if (!result.success() || result.account() == null) {
            DebugLogger.warn(MODULE, "Api 创建/读取账户失败：%s (%d)", result.message(), result.statusCode());
            return null;
        }
        cache(result.account());
        return result.account();
    }

    public static PlayerAccount ensureRemoteAccount(String username, UUID uuid) {
        PlayerAccount previous = get(username);
        ApiServiceClient.AccountResult result = ApiServiceClient.ensureAccount(username, uuid);
        if (!result.success() || result.account() == null) {
            DebugLogger.warn(MODULE, "Api 创建/读取账户失败：%s (%d)", result.message(), result.statusCode());
            return null;
        }
        cache(result.account());
        UUID remoteUuid = parseUuid(result.account());
        if (!result.account().isRegistered() && remoteUuid != null
                && ((previous != null && previous.isRegistered()) || UserSettings.exists(remoteUuid))) {
            deleteLinkedLocalData(previous != null ? previous : result.account());
        }
        return result.account();
    }

    public static void update(PlayerAccount account) {
        if (account == null)
            return;
        ApiServiceClient.updateAccount(account).ifPresentOrElse(AccountDataStorage::cache,
                () -> DebugLogger.warn(MODULE, "Api 更新账户失败：%s", account.username));
    }

    /** 玩家断线时同步账户状态；再次加入服务器仍需重新输入密码。 */
    public static void updateForDisconnect(PlayerAccount account) {
        if (account == null)
            return;
        ApiServiceClient.updateAccountForDisconnect(account).ifPresentOrElse(AccountDataStorage::cache,
                () -> DebugLogger.warn(MODULE, "Api 更新断线会话失败：%s", account.username));
    }

    /** 仅同步位置，避免未认证断开时重新创建 Api 会话。 */
    public static void updatePosition(PlayerAccount account) {
        if (account == null)
            return;
        ApiServiceClient.updateAccountPosition(account).ifPresentOrElse(AccountDataStorage::cache,
                () -> DebugLogger.warn(MODULE, "Api 更新账户位置失败：%s", account.username));
    }

    public static boolean delete(String username) {
        PlayerAccount existing = get(username);
        ApiServiceClient.AccountResult result = ApiServiceClient.deleteAccount(username);
        if (!result.success())
            return false;
        if (existing != null) {
            CACHE.remove(existing.usernameLowerCase);
            deleteLinkedLocalData(existing);
        }
        return true;
    }

    /** 接收 Api 已确认的账户状态，不再次发送更新请求。 */
    public static void acceptRemoteAccount(PlayerAccount account, boolean createSettings) {
        cache(account);
        if (createSettings)
            createUserSettings(account);
    }

    /** Api 已完成注销后，仅清理模组运行期缓存和其他模块的个人配置。 */
    public static void removeRemoteAccount(String username) {
        PlayerAccount existing = get(username);
        if (existing == null)
            return;
        CACHE.remove(existing.usernameLowerCase);
        deleteLinkedLocalData(existing);
    }

    public static int reload() {
        return ApiServiceClient.loadAccounts().map(accounts -> {
            LOCK.writeLock().lock();
            try {
                for (PlayerAccount previous : CACHE.values()) {
                    if (previous.isRegistered() && !accounts.containsKey(previous.usernameLowerCase)) {
                        deleteLinkedLocalData(previous);
                    }
                }
                CACHE.clear();
                CACHE.putAll(accounts);
                return CACHE.size();
            } finally {
                LOCK.writeLock().unlock();
            }
        }).orElseGet(() -> {
            LOCK.writeLock().lock();
            try {
                CACHE.clear();
                return 0;
            } finally {
                LOCK.writeLock().unlock();
            }
        });
    }

    public static Map<String, PlayerAccount> getAll() {
        LOCK.readLock().lock();
        try {
            return new ConcurrentHashMap<>(CACHE);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public static PlayerAccount getByExactName(String username) {
        PlayerAccount account = get(username);
        return account != null && username.equals(account.username) ? account : null;
    }

    /** 兼容旧调用点：账户状态已由 Api 同步，不执行任何本地写入。 */
    public static void saveToDisk() {
        DebugLogger.debug(MODULE, "saveToDisk 已停用：账户由 Api 服务端保存");
    }

    public static void flushToDisk() {
        saveToDisk();
    }

    private static void cache(PlayerAccount account) {
        if (account == null || account.usernameLowerCase == null)
            return;
        LOCK.writeLock().lock();
        try {
            CACHE.put(account.usernameLowerCase, account);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private static void refreshSettings() {
        ApiServiceClient.getAccountSettings().ifPresent(settings -> {
            loginCooldown = settings.loginCooldown();
        });
    }

    private static void createUserSettings(PlayerAccount account) {
        UUID uuid = parseUuid(account);
        if (uuid != null)
            UserSettings.create(uuid);
    }

    /** 清理账户注销后仍由 Minecraft 服务端保存的强关联本地数据，不迁移其所属模块。 */
    private static void deleteLinkedLocalData(PlayerAccount account) {
        UUID uuid = parseUuid(account);
        if (uuid == null)
            return;
        UserSettings.delete(uuid);
        MailManager.onAccountDeleted(uuid);
    }

    private static UUID parseUuid(PlayerAccount account) {
        if (account == null || account.uuid == null || account.uuid.isBlank())
            return null;
        try {
            return UUID.fromString(account.uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
