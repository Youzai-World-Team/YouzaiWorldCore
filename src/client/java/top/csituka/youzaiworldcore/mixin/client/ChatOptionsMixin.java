package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修改「聊天设置」页面，删除多余选项：
 * - 索引 15：{@link Options#reducedDebugInfo()} — "简化调试信息"
 * - 索引 16：{@link Options#onlyShowSecureChat()} — "仅显示安全的聊天"
 * <p>
 * 原理：拦截 {@link ChatOptionsScreen#options(Options)} 静态方法的返回值，
 * 从 18 元素的数组中移除不需要的两项，剩 16 项自动由 {@code OptionsList.addSmall()} 渲染。
 * <p>
 * 注意：索引顺序全部来自 Mojang 官方 26.2 未混淆反编译结果。
 * 如果未来 Mojang 新增/调整了聊天设置项，需据此文件同步更新索引。
 */
@Mixin(ChatOptionsScreen.class)
public class ChatOptionsMixin {

    /**
     * 原版 {@code options(Options)} 返回 18 个 {@link OptionInstance} 数组。
     * 我们在 RETURN 处拦截，抛弃索引 15（简化调试信息）和 16（仅显示安全的聊天）。
     */
    @Inject(method = "options", at = @At("RETURN"), cancellable = true)
    private static void youzaiworldcore$removeChatOptions(
            Options options,
            CallbackInfoReturnable<OptionInstance<?>[]> cir
    ) {
        OptionInstance<?>[] original = cir.getReturnValue();

        // 过滤掉索引 15 和 16
        int targetLen = original.length - 2;
        OptionInstance<?>[] filtered = new OptionInstance<?>[targetLen];
        for (int src = 0, dst = 0; src < original.length; src++) {
            if (src != 15 && src != 16) {
                filtered[dst++] = original[src];
            }
        }
        cir.setReturnValue(filtered);
    }
}
