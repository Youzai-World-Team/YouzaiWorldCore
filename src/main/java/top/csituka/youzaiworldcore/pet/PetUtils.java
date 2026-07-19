package top.csituka.youzaiworldcore.pet;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Random;

/**
 * 宠物模块工具方法 — 名称生成与通用辅助。
 */
public final class PetUtils {

    private static final String MODULE = "PetUtils";
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/Pet");

    /** Base36 字符集：0-9, A-Z（大写字母 + 数字） */
    private static final char[] BASE36_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /** 内部名称前缀 */
    private static final String NAME_PREFIX = "DOG";

    /** 随机后缀长度 */
    private static final int SUFFIX_LENGTH = 6;

    private static final Random RANDOM = new Random();

    private PetUtils() {
    }

    /**
     * 生成唯一的宠物内部名称。
     * <p>
     * 格式为 {@code DOG} + 6 位 Base36 随机字符（如 {@code DOGAB3F9}）。
     * 生成后通过全局注册表验证唯一性，若碰撞则循环重试。
     * </p>
     *
     * @param server Minecraft 服务器实例，用于查询全局注册表
     * @return 唯一且未被占用的内部名称
     */
    @NotNull
    public static String generateUniqueInternalName(@NotNull MinecraftServer server) {
        DebugLogger.entering(MODULE, "generateUniqueInternalName");

        PetGlobalState state;
        try {
            state = PetGlobalState.get(server);
        } catch (IllegalStateException e) {
            // 主世界不可用，仅做随机生成（极不可能发生）
            String fallback = generateRandomName();
            LOGGER.warn("PetGlobalState 不可用，使用 fallback 名称: {}", fallback);
            DebugLogger.exiting(MODULE, "generateUniqueInternalName", "fallback=" + fallback);
            return fallback;
        }

        String name;
        int attempts = 0;
        do {
            name = generateRandomName();
            attempts++;
            if (attempts > 1000) {
                // 极端碰撞保护（理论上不应发生）
                LOGGER.error("内部名称生成碰撞超过 1000 次！使用时间戳后缀规避");
                name = NAME_PREFIX + System.currentTimeMillis();
                break;
            }
        } while (state.isNameTaken(name));

        DebugLogger.info(MODULE, "生成内部名称: %s (尝试 %d 次)", name, attempts);
        DebugLogger.exiting(MODULE, "generateUniqueInternalName", name);
        return name;
    }

    /**
     * 生成一个随机的内部名称字符串（不检查唯一性）。
     *
     * @return 格式为 {@code DOGXXXXXX} 的随机字符串
     */
    @NotNull
    public static String generateRandomName() {
        StringBuilder sb = new StringBuilder(NAME_PREFIX.length() + SUFFIX_LENGTH);
        sb.append(NAME_PREFIX);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(BASE36_CHARS[RANDOM.nextInt(BASE36_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 验证一个字符串是否符合内部名称格式。
     *
     * @param name 待验证的字符串
     * @return true 如果格式正确（以 DOG 开头，后接 6 位 Base36 大写字符）
     */
    public static boolean isValidInternalName(@NotNull String name) {
        if (name.length() != NAME_PREFIX.length() + SUFFIX_LENGTH) {
            return false;
        }
        if (!name.startsWith(NAME_PREFIX)) {
            return false;
        }
        for (int i = NAME_PREFIX.length(); i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }
}
