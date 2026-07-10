package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;

/**
 * 拦截 {@code LoadingOverlay$LogoTexture.loadContents()} 中对
 * {@code VanillaPackResources.asProvider().open()} 的调用，
 * 将其重定向到 classpath 加载（优先）或 ResourceManager 加载（回退）。
 * <p>
 * 修复两个问题：
 * <ol>
 *   <li><b>原版绕过资源包系统</b>：原版直接读取 jar 内的 VanillaPackResources，
 *       导致 mod 在 {@code assets/minecraft/} 下的覆盖文件无效。</li>
 *   <li><b>初始化早期白块</b>：{@code Minecraft.createTextures()} 在构造阶段调用
 *       {@code registerTextures()}，此时 {@code ResourceManager} 尚未加载 mod 资源包，
 *       若直接使用 {@code getResourceManager().open()} 会找不到纹理，显示为白色方块。
 *       classpath 在任何阶段均可访问，因此优先使用。</li>
 * </ol>
 */
@Mixin(targets = "net.minecraft.client.gui.screens.LoadingOverlay$LogoTexture")
public class LogoTextureMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/LogoTextureMixin");

    /**
     * 将 {@code ResourceProvider.open(Identifier)} 调用重定向：
     * <ol>
     *   <li>优先从 classpath 直接加载（mod jar 始终在 classpath 上，不受生命周期阶段影响）</li>
     *   <li>回退到 {@code Minecraft.getInstance().getResourceManager().open(id)}</li>
     * </ol>
     */
    @Redirect(
        method = "loadContents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceProvider;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"
        )
    )
    private InputStream redirectLogoTextureOpen(ResourceProvider provider, Identifier id) throws IOException {
        // 1) 优先试 classpath（始终可用）
        // Identifier path = "textures/gui/title/mojangstudios.png", namespace = "minecraft"
        // 但 mod jar 中文件实际位于 assets/minecraft/... → 需要补上 "assets/" 前缀
        String classpathPath = "/assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream classpathStream = LogoTextureMixin.class.getResourceAsStream(classpathPath);
        if (classpathStream != null) {
            LOGGER.debug("Loaded logo texture from classpath: {}", classpathPath);
            return classpathStream;
        }

        // 2) 回退到 ResourceManager（在资源重载阶段 mod 资源已就绪）
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        LOGGER.debug("Falling back to ResourceManager for logo texture: {}", id);
        return resourceManager.open(id);
    }
}
