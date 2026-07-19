package top.csituka.youzaiworldcore.pet;

import org.jetbrains.annotations.NotNull;

/**
 * 宠物模块使用的实体标签常量。
 * <p>
 * 用于在狼实体的 entityTags 中存储宠物标识和内部名称，
 * 支持实体加载时快速判断和持久化。
 * </p>
 */
public final class PetInternalTags {

    /** 标记该实体为宠物系统管理的狼 */
    public static final String TAG_PET_MARKER = "yz_pet";

    /** 内部名称标签前缀（完整标签格式为 "yz_internal:DOGAB3F9"） */
    public static final String TAG_INTERNAL_NAME_PREFIX = "yz_internal:";

    private PetInternalTags() {
    }

    /**
     * 构建内部名称的标签字符串。
     *
     * @param internalName 内部名称
     * @return 标签字符串（如 {@code yz_internal:DOGAB3F9}）
     */
    @NotNull
    public static String internalNameTag(@NotNull String internalName) {
        return TAG_INTERNAL_NAME_PREFIX + internalName;
    }

    /**
     * 从标签中提取内部名称。
     *
     * @param tag 实体标签
     * @return 提取的内部名称，如果标签格式不匹配则返回 {@code null}
     */
    public static String extractInternalName(@NotNull String tag) {
        if (tag.startsWith(TAG_INTERNAL_NAME_PREFIX)) {
            return tag.substring(TAG_INTERNAL_NAME_PREFIX.length());
        }
        return null;
    }
}
