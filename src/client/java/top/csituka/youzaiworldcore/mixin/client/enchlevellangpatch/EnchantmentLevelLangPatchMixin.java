package top.csituka.youzaiworldcore.mixin.client.enchlevellangpatch;

import net.minecraft.client.resources.language.ClientLanguage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.enchlevellangpatch.impl.LangPatchImpl;

import java.util.Map;

/**
 * Intercepts {@link ClientLanguage#getOrDefault} to patch enchantment level
 * and potion potency translations at runtime.
 *
 * <p>Targets the method head. If the lang patch pipeline returns a non-null
 * result, that result replaces the original translation; otherwise the
 * original method body proceeds normally.</p>
 */
@Mixin(ClientLanguage.class)
public class EnchantmentLevelLangPatchMixin {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker MARKER = MarkerManager.getMarker("LangPatch/Mixin");

    @Shadow
    @Final
    private Map<String, String> storage;

    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true)
    private void youzaiworldcore$onGetOrDefault(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String result = LangPatchImpl.hookWithFallback(key, this.storage, fallback);
        if (result != null) {
            LOGGER.debug(MARKER, "Patched translation: {} -> {}", key, result);
            cir.setReturnValue(result);
        }
    }
}
