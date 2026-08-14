package top.csituka.youzaiworldcore.config;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 缓存 / 临时数据目录管理。
 * <p>
 * 两个临时区：
 * </p>
 * <ul>
 *   <li>服务端侧 {@code <gameDir>/yzwc/server/temp/&lt;模块名&gt;/}</li>
 *   <li>世界侧 {@code <world_name>/data/yzwc/temp/&lt;模块名&gt;/}</li>
 * </ul>
 * <p>
 * 两者都在<b>每次开启服务器时被整体清空</b>（{@code SERVER_STARTING} 阶段），
 * 因此任何写进 temp 的内容都不得假设能跨重启存活。
 * 需要跨重启保留的请写 {@code data/} 或 {@code backup/}。
 * </p>
 */
public final class TempManager {

    private static final String MODULE = "TempManager";
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/TempManager");

    private TempManager() {
    }

    /**
     * 开服时清空两个临时区并重建空目录。
     *
     * @param server 当前服务器实例
     */
    public static void clearOnServerStart(MinecraftServer server) {
        DebugLogger.entering(MODULE, "clearOnServerStart");
        int serverSide = clearRoot(ModPaths.serverTempRoot());
        int worldSide = clearRoot(ModPaths.worldTempRoot(server));
        LOGGER.info("临时目录已清空（服务端侧 {} 项，世界侧 {} 项）", serverSide, worldSide);
        DebugLogger.exiting(MODULE, "clearOnServerStart",
                "server=" + serverSide + ", world=" + worldSide);
    }

    /**
     * 取某模块的服务端临时目录（自动创建）。
     *
     * @param module 模块名，与 {@link GlobalSettings} 里的分节名保持一致
     */
    public static Path serverTempDir(String module) {
        return ModPaths.ensureDir(ModPaths.serverTemp(module));
    }

    /**
     * 取某模块的世界临时目录（自动创建）。
     *
     * @param server 当前服务器实例
     * @param module 模块名，与 {@link GlobalSettings} 里的分节名保持一致
     */
    public static Path worldTempDir(MinecraftServer server, String module) {
        return ModPaths.ensureDir(ModPaths.worldTemp(server, module));
    }

    // ===== 内部 =====

    /**
     * 递归删除某个临时区下的全部内容，然后重建空的根目录。
     *
     * @return 实际删除的文件 / 目录数量
     */
    private static int clearRoot(Path root) {
        if (!Files.isDirectory(root)) {
            ModPaths.ensureDir(root);
            return 0;
        }
        int deleted = 0;
        // 自底向上删除：先文件后目录，最后单独重建根目录
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(root)) {
                    continue;
                }
                try {
                    if (Files.deleteIfExists(path)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    // 单个文件被占用不应阻断开服，记录后继续
                    LOGGER.warn("清理临时文件失败（已跳过）: {} — {}", path, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("遍历临时目录失败（已跳过）: {} — {}", root, e.getMessage());
        }
        ModPaths.ensureDir(root);
        return deleted;
    }
}
