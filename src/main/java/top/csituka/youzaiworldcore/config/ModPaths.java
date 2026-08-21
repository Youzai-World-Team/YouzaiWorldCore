package top.csituka.youzaiworldcore.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 全模组统一的文件存放路径解析入口。
 * <p>
 * 本项目所有服务端侧的配置 / 数据 / 备份 / 缓存文件都<b>只能</b>通过本类取路径，
 * 禁止再在各模块里手写 {@code getConfigDir().resolve("youzaiworldcore")} 之类的散装路径。
 * </p>
 *
 * <h2>目录布局（服务端，相对游戏根目录）</h2>
 *
 * <pre>
 * &lt;gameDir&gt;/yzwc/server/
 * ├── config/
 * │   ├── global_settings.json                # 全局配置（与世界无关），按模块分节
 * │   └── user_settings/
 * │       └── &lt;玩家 UUID&gt;.json               # 玩家个人配置，按模块分节
 * ├── data/
 * │   └── &lt;模块名&gt;/data.json                 # 各模块数据文件
 * ├── backup/
 * │   └── &lt;模块名&gt;/*.zip                     # 各模块备份压缩包
 * └── temp/
 *     └── &lt;模块名&gt;/                          # 各模块缓存 / 临时文件（每次开服清空）
 * </pre>
 *
 * <h2>目录布局（客户端，相对游戏根目录）</h2>
 *
 * <pre>
 * &lt;gameDir&gt;/yzwc/client/
 * ├── global_settings.json                    # 客户端全部结构化配置，按模块分节
 * └── config/&lt;模块名&gt;/                       # 少数模块需要的玩家外部文件（如自定义皮肤 PNG）
 * </pre>
 *
 * <h2>目录布局（世界侧，随存档走）</h2>
 *
 * <pre>
 * &lt;world_name&gt;/data/yzwc/
 * ├── config/
 * ├── data/&lt;模块名&gt;/
 * ├── backup/&lt;模块名&gt;/
 * └── temp/&lt;模块名&gt;/
 * </pre>
 *
 * <p>
 * 需要跟随存档迁移的内容（统计、维度池背包等）走世界侧；其余走服务端侧。
 * </p>
 */
@SuppressWarnings("null")
public final class ModPaths {

    /** 存放根目录名，服务端侧为 {@code <gameDir>/yzwc/server}，世界侧为 {@code <world>/data/yzwc} */
    public static final String ROOT_DIR_NAME = "yzwc";

    /** 四类存放区的目录名 */
    public static final String CONFIG_DIR_NAME = "config";
    public static final String DATA_DIR_NAME = "data";
    public static final String BACKUP_DIR_NAME = "backup";
    public static final String TEMP_DIR_NAME = "temp";

    /** 全局配置文件名 */
    public static final String GLOBAL_SETTINGS_FILE_NAME = "global_settings.json";
    /** 客户端存放目录名（{@code yzwc/client}） */
    public static final String CLIENT_DIR_NAME = "client";
    /** 玩家个人配置目录名 */
    public static final String USER_SETTINGS_DIR_NAME = "user_settings";
    /** 各模块数据文件的默认文件名 */
    public static final String DEFAULT_DATA_FILE_NAME = "data.json";

    private ModPaths() {
    }

    // ===== 服务端侧（<gameDir>/yzwc/server） =====

    /** 服务端存放根：{@code <gameDir>/yzwc/server} */
    public static Path serverRoot() {
        return FabricLoader.getInstance().getGameDir()
                .resolve(ROOT_DIR_NAME)
                .resolve("server")
                .normalize();
    }

    /** 服务端配置根：{@code <gameDir>/yzwc/server/config} */
    public static Path serverConfigRoot() {
        return serverRoot().resolve(CONFIG_DIR_NAME);
    }

    /** 全局配置文件：{@code <gameDir>/yzwc/server/config/global_settings.json} */
    public static Path globalSettingsFile() {
        return serverConfigRoot().resolve(GLOBAL_SETTINGS_FILE_NAME);
    }

    /** 玩家个人配置目录：{@code <gameDir>/yzwc/server/config/user_settings} */
    public static Path userSettingsDir() {
        return serverConfigRoot().resolve(USER_SETTINGS_DIR_NAME);
    }

    /** 单个玩家的个人配置文件：{@code .../user_settings/<UUID>.json} */
    public static Path userSettingsFile(UUID playerUuid) {
        return userSettingsDir().resolve(playerUuid.toString() + ".json");
    }

    /** 服务端数据根：{@code <gameDir>/yzwc/server/data} */
    public static Path serverDataRoot() {
        return serverRoot().resolve(DATA_DIR_NAME);
    }

    /** 某模块的服务端数据目录：{@code <gameDir>/yzwc/server/data/<module>} */
    public static Path serverData(String module) {
        return serverDataRoot().resolve(module);
    }

    /** 某模块的服务端主数据文件：{@code .../data/<module>/data.json} */
    public static Path serverDataFile(String module) {
        return serverData(module).resolve(DEFAULT_DATA_FILE_NAME);
    }

    /** 某模块下按玩家 UUID 分隔的数据目录。 */
    public static Path serverPlayerData(String module, UUID playerUuid) {
        return serverData(module).resolve(playerUuid.toString());
    }

    /** 服务端备份根：{@code <gameDir>/yzwc/server/backup} */
    public static Path serverBackupRoot() {
        return serverRoot().resolve(BACKUP_DIR_NAME);
    }

    /** 某模块的服务端备份目录：{@code <gameDir>/yzwc/server/backup/<module>} */
    public static Path serverBackup(String module) {
        return serverBackupRoot().resolve(module);
    }

    /** 服务端缓存根：{@code <gameDir>/yzwc/server/temp}（每次开服清空） */
    public static Path serverTempRoot() {
        return serverRoot().resolve(TEMP_DIR_NAME);
    }

    /** 某模块的服务端缓存目录：{@code <gameDir>/yzwc/server/temp/<module>}（每次开服清空） */
    public static Path serverTemp(String module) {
        return serverTempRoot().resolve(module);
    }

    // ===== 客户端侧（<gameDir>/yzwc/client） =====

    /**
     * 客户端存放根：{@code <gameDir>/yzwc/client}。
     * <p>
     * 与服务端不同，客户端结构化设置集中在一个扁平配置文件中，不建立 data / backup / temp。
     * 少数需要玩家直接放置资源文件的模块可通过 {@link #clientConfig(String)} 使用 config 子目录。
     * </p>
     */
    public static Path clientRoot() {
        return FabricLoader.getInstance().getGameDir()
                .resolve(ROOT_DIR_NAME)
                .resolve(CLIENT_DIR_NAME)
                .normalize();
    }

    /** 客户端唯一的配置文件：{@code <gameDir>/yzwc/client/global_settings.json} */
    public static Path clientSettingsFile() {
        return clientRoot().resolve(GLOBAL_SETTINGS_FILE_NAME);
    }

    /** 客户端模块外部文件目录：{@code <gameDir>/yzwc/client/config/<module>}。 */
    public static Path clientConfig(String module) {
        return clientRoot().resolve(CONFIG_DIR_NAME).resolve(module);
    }

    // ===== 世界侧（<world_name>/data/yzwc） =====
    /** 世界存放根：{@code <world_name>/data/yzwc} */
    public static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(DATA_DIR_NAME)
                .resolve(ROOT_DIR_NAME)
                .normalize();
    }

    /** 世界配置根：{@code <world_name>/data/yzwc/config} */
    public static Path worldConfigRoot(MinecraftServer server) {
        return worldRoot(server).resolve(CONFIG_DIR_NAME);
    }

    /** 世界数据根：{@code <world_name>/data/yzwc/data} */
    public static Path worldDataRoot(MinecraftServer server) {
        return worldRoot(server).resolve(DATA_DIR_NAME);
    }

    /** 某模块的世界数据目录：{@code <world_name>/data/yzwc/data/<module>} */
    public static Path worldData(MinecraftServer server, String module) {
        return worldDataRoot(server).resolve(module);
    }

    /** 某模块的世界主数据文件：{@code .../data/<module>/data.json} */
    public static Path worldDataFile(MinecraftServer server, String module) {
        return worldData(server, module).resolve(DEFAULT_DATA_FILE_NAME);
    }

    /** 世界备份根：{@code <world_name>/data/yzwc/backup} */
    public static Path worldBackupRoot(MinecraftServer server) {
        return worldRoot(server).resolve(BACKUP_DIR_NAME);
    }

    /** 某模块的世界备份目录：{@code <world_name>/data/yzwc/backup/<module>} */
    public static Path worldBackup(MinecraftServer server, String module) {
        return worldBackupRoot(server).resolve(module);
    }

    /** 世界缓存根：{@code <world_name>/data/yzwc/temp}（每次开服清空） */
    public static Path worldTempRoot(MinecraftServer server) {
        return worldRoot(server).resolve(TEMP_DIR_NAME);
    }

    /** 某模块的世界缓存目录：{@code <world_name>/data/yzwc/temp/<module>}（每次开服清空） */
    public static Path worldTemp(MinecraftServer server, String module) {
        return worldTempRoot(server).resolve(module);
    }

    // ===== 工具 =====

    /**
     * 新开服时预先建好服务端侧的四层目录骨架，让管理员一眼看清东西该放哪。
     * <p>在 {@code YouzaiworldCore.onInitialize()} 最开头调用一次。</p>
     */
    public static void bootstrapServerLayout() {
        ensureDir(serverConfigRoot());
        ensureDir(userSettingsDir());
        ensureDir(serverDataRoot());
        ensureDir(serverBackupRoot());
        ensureDir(serverTempRoot());
    }

    /**
     * 开服时预先建好世界侧的四层目录骨架。
     *
     * @param server 当前服务器实例
     */
    public static void bootstrapWorldLayout(MinecraftServer server) {
        ensureDir(worldConfigRoot(server));
        ensureDir(worldDataRoot(server));
        ensureDir(worldBackupRoot(server));
        ensureDir(worldTempRoot(server));
    }

    /**
     * 确保目录存在；失败视为致命的存放布局错误，直接输出错误并崩溃退出。
     *
     * @param dir 目标目录
     * @return 传入的目录本身，便于链式调用
     */
    public static Path ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ConfigCrash.fail(dir, "<目录>", "无法创建目录：" + e.getMessage(), e);
        }
        return dir;
    }

    /**
     * 确保某个文件的父目录存在。
     *
     * @param file 目标文件
     * @return 传入的文件本身，便于链式调用
     */
    public static Path ensureParentDir(Path file) {
        Path parent = file.getParent();
        if (parent != null) {
            ensureDir(parent);
        }
        return file;
    }
}
