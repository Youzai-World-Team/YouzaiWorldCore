package top.csituka.youzaiworldcore.client.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.cosmetic.CosmeticPngValidator;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 独立于上传会话的本地 PNG 预览纹理；页面关闭或重新读取时释放全部资源。 */
@SuppressWarnings("null")
public final class CosmeticPreviewTextures implements AutoCloseable {
    public enum Status { MISSING, LOADED, INVALID }

    public record FilePreview(String fileName, Status status) {
    }

    private record LoadedFile(FilePreview preview, ClientAsset.Texture texture) {
    }

    private static final String MODULE = "CosmeticPreviewTextures";
    private final List<Identifier> textureIds = new ArrayList<>(2);
    private PlayerSkin skin;
    private FilePreview skinFile;
    private FilePreview cloakFile;
    private boolean skinConflict;

    public CosmeticPreviewTextures(PlayerSkin fallback) {
        reload(fallback);
    }

    public static Path directory() {
        return ModPaths.clientConfig(ClientGlobalSettings.COSMETIC_MODULE);
    }

    /** 只读取本地文件，不改变上传状态、玩家外观缓存或任何服务器数据。 */
    public void reload(PlayerSkin fallback) {
        close();
        Path directory = directory();
        boolean wideExists = Files.isRegularFile(directory.resolve("skin.png"));
        boolean slimExists = Files.isRegularFile(directory.resolve("skin_slim.png"));
        skinConflict = wideExists && slimExists;
        String skinName = wideExists || !slimExists ? "skin.png" : "skin_slim.png";
        String scope = UUID.randomUUID().toString();
        LoadedFile body = load(directory.resolve(skinName), false, scope);
        LoadedFile cloak = load(directory.resolve("cloak.png"), true, scope);
        skinFile = body.preview();
        cloakFile = cloak.preview();
        PlayerModelType model = body.texture() == null ? fallback.model()
                : skinName.equals("skin_slim.png") ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        skin = PlayerSkin.insecure(body.texture() == null ? fallback.body() : body.texture(),
                cloak.texture(), null, model);
        DebugLogger.CLIENT.debug(MODULE, "已重新读取本地预览：皮肤={}，披风={}，皮肤冲突={}", skinFile.status(), cloakFile.status(), skinConflict);
    }

    public PlayerSkin skin() { return skin; }
    public FilePreview skinFile() { return skinFile; }
    public FilePreview cloakFile() { return cloakFile; }
    public boolean skinConflict() { return skinConflict; }
    public boolean hasCloak() { return cloakFile.status() == Status.LOADED; }

    private LoadedFile load(Path file, boolean cloak, String scope) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file)) return new LoadedFile(new FilePreview(name, Status.MISSING), null);
        NativeImage image = null;
        DynamicTexture texture = null;
        try {
            long size = Files.size(file);
            if (size <= 0 || size > CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES) {
                DebugLogger.CLIENT.warn(MODULE, "预览文件大小不合法：{}（{} 字节）", name, size);
                return invalid(name);
            }
            byte[] bytes = Files.readAllBytes(file);
            CosmeticPngValidator.Validation validation = cloak
                    ? CosmeticPngValidator.validateCloak(bytes, CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES)
                    : CosmeticPngValidator.validateSkin(bytes, CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES);
            if (!validation.valid()) {
                DebugLogger.CLIENT.warn(MODULE, "预览文件校验失败：{}（{}）", name, validation.reason());
                return invalid(name);
            }
            image = NativeImage.read(bytes);
            if (image.getWidth() != validation.width() || image.getHeight() != validation.height()) {
                DebugLogger.CLIENT.warn(MODULE, "预览文件解码尺寸与 PNG 头不一致：{}", name);
                return invalid(name);
            }
            if (!cloak) image = CosmeticSkinImages.prepare(image);
            Identifier identifier = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID,
                    "cosmetic_preview/" + scope + "/" + name);
            texture = new DynamicTexture(() -> "YouzaiWorldCore local preview " + name, image);
            image = null;
            Minecraft.getInstance().getTextureManager().register(identifier, texture);
            texture = null;
            textureIds.add(identifier);
            return new LoadedFile(new FilePreview(name, Status.LOADED), new CosmeticTexture(identifier, identifier));
        } catch (IOException | RuntimeException e) {
            DebugLogger.CLIENT.exception(MODULE, "读取预览文件 " + name, e);
            return invalid(name);
        } finally {
            if (texture != null) texture.close();
            else if (image != null) image.close();
        }
    }

    private static LoadedFile invalid(String name) {
        return new LoadedFile(new FilePreview(name, Status.INVALID), null);
    }

    @Override
    public void close() {
        textureIds.forEach(Minecraft.getInstance().getTextureManager()::release);
        textureIds.clear();
    }
}
