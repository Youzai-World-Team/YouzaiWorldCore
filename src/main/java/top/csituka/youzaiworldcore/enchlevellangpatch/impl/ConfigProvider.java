package top.csituka.youzaiworldcore.enchlevellangpatch.impl;

import org.jetbrains.annotations.Nullable;

public interface ConfigProvider {
    @Nullable
    String getEnchantmentConfig();

    @Nullable
    String getPotionConfig();
}
