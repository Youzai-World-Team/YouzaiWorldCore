package top.csituka.youzaiworldcore.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.*;

/**
 * 实验性功能注册系统
 * <p>
 * 管理所有实验性功能的开关状态，支持服务端存储和客户端同步。
 * 功能状态默认保存在服务端内存中，通过 FeatureSyncPayload 同步到客户端。
 * </p>
 */
public final class ExperimentalFeatures {

    public static final Logger LOGGER = LoggerFactory.getLogger("ExperimentalFeatures");

    /** 内部功能注册表 (id -> entry) */
    private static final Map<String, FeatureEntry> REGISTRY = new LinkedHashMap<>();

    /** 当前启用状态 (服务端或客户端本地快照) */
    private static final Map<String, Boolean> ENABLED = new HashMap<>();

    private ExperimentalFeatures() {}

    /**
     * 注册一个实验性功能。
     *
     * @param id              功能内部 ID（如 "chicken_warden_model"）
     * @param name            功能名称（显示名）
     * @param provider        提供者名称
     * @param providerUrl     提供者可点击链接
     * @param description     功能描述
     * @param source          来源名称
     * @param sourceUrl       来源可点击链接
     * @param defaultEnabled  默认启用状态
     */
    public static void register(
            String id, String name, String provider, String providerUrl,
            String description, String source, String sourceUrl,
            boolean defaultEnabled
    ) {
        if (REGISTRY.containsKey(id)) {
            LOGGER.warn("实验性功能 '{}' 重复注册", id);
            return;
        }
        REGISTRY.put(id, new FeatureEntry(
                id, name, provider, providerUrl, description, source, sourceUrl, defaultEnabled
        ));
        // 初始化状态为默认值
        ENABLED.putIfAbsent(id, defaultEnabled);
        LOGGER.info("注册实验性功能: {} ({})，默认: {}", name, id, defaultEnabled);
    }

    /**
     * 获取功能当前是否启用。
     */
    public static boolean isEnabled(String id) {
        return ENABLED.getOrDefault(id, false);
    }

    /**
     * 设置功能启用状态（仅在服务端有效）。
     * 客户端接受服务端同步的生效值。
     */
    public static boolean setEnabled(String id, boolean enabled) {
        if (!REGISTRY.containsKey(id)) {
            return false;
        }
        boolean old = ENABLED.getOrDefault(id, false);
        ENABLED.put(id, enabled);
        LOGGER.info("实验性功能 '{}' 状态: {} -> {}", id, old, enabled);
        return true;
    }

    /**
     * 获取功能条目信息。
     */
    public static FeatureEntry getEntry(String id) {
        return REGISTRY.get(id);
    }

    /**
     * 获取所有已注册的功能。
     */
    public static Map<String, FeatureEntry> getAllEntries() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * 初始化时加载默认状态
     */
    public static void loadDefaults() {
        for (FeatureEntry entry : REGISTRY.values()) {
            ENABLED.putIfAbsent(entry.id(), entry.defaultEnabled());
        }
    }

    /**
     * 实验性功能条目
     */
    public record FeatureEntry(
            String id,
            String name,
            String provider,
            String providerUrl,
            String description,
            String source,
            String sourceUrl,
            boolean defaultEnabled
    ) {}
}
