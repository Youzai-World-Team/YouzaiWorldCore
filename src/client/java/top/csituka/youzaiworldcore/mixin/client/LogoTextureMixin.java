package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.render.YzuiBrandLogo;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.io.InputStream;

/**
 * 拦截 {@code LoadingOverlay$LogoTexture.loadContents()} 中对
 * {@code VanillaPackResources.asProvider().open()} 的调用，
 * 直接从模组 classpath 读取官网彩色标志。该纹理在资源管理器就绪前注册，
 * 与启动窗口和标题页复用同一文件，不再读取旧的分片标志图片。
 */
@Mixin(targets = "net.minecraft.client.gui.screens.LoadingOverlay$LogoTexture")
public class LogoTextureMixin {

    /**
     * 保留原版纹理注册及流关闭流程，仅替换图片来源；不依赖首次重载完成。
     */
    @Redirect(
        method = "loadContents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceProvider;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"
        )
    )
    private InputStream youzaiworldcore$openBrandLogo(ResourceProvider provider, Identifier id) throws IOException {
        DebugLogger.debug("LogoTextureMixin", "加载官网彩色标志: %s", YzuiBrandLogo.CLASSPATH_RESOURCE);
        return YzuiBrandLogo.openStream();
    }
}
