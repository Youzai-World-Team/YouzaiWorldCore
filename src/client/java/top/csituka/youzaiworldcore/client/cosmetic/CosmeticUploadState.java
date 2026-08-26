package top.csituka.youzaiworldcore.client.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 按服务器实例和玩家 UUID 保存三个本地外观文件的已确认哈希快照。
 * <p>
 * 文件位于 {@code yzwc/client/config/cosmetic_module/upload_state.json}。
 * </p>
 */
public final class CosmeticUploadState {

    private static final String MODULE = "CosmeticUploadState";
    private static final String FILE_NAME = "upload_state.json";
    private static final int MAX_SCOPES = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CosmeticUploadState() {
    }

    /** 读取指定服务器和玩家上次由服务端确认成功的外观文件状态。 */
    public static Map<String, FileState> load(UUID serverInstanceId, UUID playerUuid) {
        StoredState stored = readStoredState();
        ScopedState scope = stored.scopes.get(scopeKey(serverInstanceId, playerUuid));
        if (scope == null || scope.files == null) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, FileState> result = new LinkedHashMap<>();
            scope.files.forEach((name, state) -> {
                if (name != null && state != null) {
                    state.normalize();
                    result.put(name, state);
                }
            });
            return result;
        } catch (RuntimeException e) {
            DebugLogger.exception(MODULE, "loadScope", e);
            return new LinkedHashMap<>();
        }
    }

    /** 原子写入指定服务器和玩家最近一次由服务端确认成功的外观文件状态。 */
    public static void save(UUID serverInstanceId, UUID playerUuid, Map<String, FileState> files) {
        Path file = stateFile();
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        StoredState stored = readStoredState();
        ScopedState scope = new ScopedState();
        scope.updatedAtEpochMillis = System.currentTimeMillis();
        scope.files.putAll(files);
        stored.scopes.put(scopeKey(serverInstanceId, playerUuid), scope);
        trimScopes(stored);
        try {
            ModPaths.ensureParentDir(file);
            Files.writeString(temp, GSON.toJson(stored), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            DebugLogger.exception(MODULE, "save", e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                DebugLogger.exception(MODULE, "cleanupTemp", e);
            }
        }
    }

    private static Path stateFile() {
        return ModPaths.clientConfig(ClientGlobalSettings.COSMETIC_MODULE).resolve(FILE_NAME);
    }

    @SuppressWarnings("null")
    private static StoredState readStoredState() {
        Path file = stateFile();
        if (!Files.isRegularFile(file)) {
            return new StoredState();
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StoredState stored = GSON.fromJson(reader, StoredState.class);
            if (stored.scopes == null) {
                stored.scopes = new LinkedHashMap<>();
            }
            stored.scopes.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            stored.scopes.values().forEach(scope -> {
                if (scope.files == null) {
                    scope.files = new LinkedHashMap<>();
                }
            });
            return stored;
        } catch (IOException | RuntimeException e) {
            DebugLogger.exception(MODULE, "readStoredState", e);
            return new StoredState();
        }
    }

    private static String scopeKey(UUID serverInstanceId, UUID playerUuid) {
        return serverInstanceId + "/" + playerUuid;
    }

    private static void trimScopes(StoredState stored) {
        while (stored.scopes.size() > MAX_SCOPES) {
            String oldestKey = stored.scopes.entrySet().stream()
                    .min((left, right) -> Long.compare(
                            left.getValue().updatedAtEpochMillis,
                            right.getValue().updatedAtEpochMillis))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldestKey == null) {
                return;
            }
            stored.scopes.remove(oldestKey);
        }
    }

    /** 单个候选文件的内容状态。 */
    public static final class FileState {
        private String sha256;
        private long size;
        private boolean valid;

        @SuppressWarnings("unused")
        private FileState() {
        }

        public FileState(String sha256, long size, boolean valid) {
            this.sha256 = sha256 == null ? "" : sha256;
            this.size = size;
            this.valid = valid;
        }

        private void normalize() {
            if (sha256 == null) {
                sha256 = "";
            }
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof FileState other)) {
                return false;
            }
            return size == other.size && valid == other.valid && Objects.equals(sha256, other.sha256);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sha256, size, valid);
        }
    }

    private static final class StoredState {
        private Map<String, ScopedState> scopes = new LinkedHashMap<>();
    }

    private static final class ScopedState {
        private long updatedAtEpochMillis;
        private Map<String, FileState> files = new LinkedHashMap<>();
    }
}
