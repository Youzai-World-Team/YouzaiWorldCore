package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ConfigIOManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Mixin — 导入期间拦截 {@link Options#save()}，防止主线程与解压线程并发写入 {@code options.txt}。
 * <p>
 * 当 {@link ConfigIOManager#isImporting} 为 {@code true} 时，{@code save()} 变为空操作。
 * 导入完成后标志复位，恢复正常行为。
 * </p>
 */
@Mixin(Options.class)
public class OptionsSaveMixin {

    private static final String LOG_MODULE = "OptionsSaveMixin";

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void onSave(CallbackInfo ci) {
        if (ConfigIOManager.isImporting.get()) {
            DebugLogger.debug(LOG_MODULE, "导入进行中，跳过 options.save()");
            ci.cancel();
        }
    }
}
