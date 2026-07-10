package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 拦截 {@link Window#setTitle(String)} 调用，将窗口标题替换为自定义标题。
 * 此方式确保无论 Minecraft 何时设置标题（包括初始化、资源重载等），
 * 自定义标题始终生效。
 */
@Mixin(Window.class)
public class WindowTitleMixin {

    private static final String CUSTOM_TITLE = buildTitle();

    private static String buildTitle() {
        String version = FabricLoader.getInstance()
                .getModContainer("youzaiworldcore")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return "Youzai World Server · Wanderer v" + version + " | [Minecraft JAVA 26.2]";
    }

    /**
     * 将 {@code Window.setTitle(String)} 的第一个参数（标题字符串）替换为自定义标题。
     */
    @ModifyVariable(
            method = "setTitle",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private String youzaiworldcore$modifyWindowTitle(String original) {
        return CUSTOM_TITLE;
    }
}
