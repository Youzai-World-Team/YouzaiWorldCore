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

    public record Atlas(Identifier texture, int height) {
    }

    private record LoadedFile(FilePreview preview, ClientAsset.Texture texture, Atlas atlas) {
        private LoadedFile(FilePreview preview, ClientAsset.Texture texture) {
            this(preview, texture, null);
        }
    }

    private static final String MODULE = "CosmeticPreviewTextures";
    private final List<Identifier> textureIds = new ArrayList<>(2);
    private PlayerSkin skin;
    private FilePreview skinFile;
    private FilePreview cloakFile;
    private boolean skinConflict;
    private Atlas skinAtlas;
    private Atlas cloakAtlas;

    public CosmeticPreviewTextures(PlayerSkin fallback) {
        reload(fallback);
    }

    /** Builds a preview from the saved or staged snapshot, including the original PNG layouts. */
    public CosmeticPreviewTextures(PlayerSkin fallback, byte[] body, boolean slim, byte[] cape) {
        String scope = UUID.randomUUID().toString();
        setPreview(fallback, slim,
                loadBytes(slim ? "skin_slim.png" : "skin.png", body, false, scope, true),
                loadBytes("cloak.png", cape, true, scope, true));
    }

    public static Path directory() {
        return ModPaths.clientConfig(ClientGlobalSettings.COSMETIC_MODULE);
    }

    /** 只读取本地文件，不改变上传状态、玩家外观缓存或任何服务器数据。 */
    public void reload(PlayerSkin fallback) {
        close();
        Path directory = directory();
        boolean wideExists = Files.isRegularFile(directory.resolve("skin.png"));
        Path slimFile = directory.resolve("skin_slim.png");
        if (!Files.isRegularFile(slimFile)) {
            slimFile = directory.resolve("skin_silm.png");
        }
        boolean slimExists = Files.isRegularFile(slimFile);
        skinConflict = wideExists && slimExists;
        boolean useSlim = !wideExists && slimExists;
        String scope = UUID.randomUUID().toString();
        setPreview(fallback, useSlim,
                load(useSlim ? slimFile : directory.resolve("skin.png"), false, scope),
                load(directory.resolve("cloak.png"), true, scope));
        DebugLogger.CLIENT.debug(MODULE, "已重新读取本地预览：皮肤={}，披风={}，皮肤冲突={}", skinFile.status(), cloakFile.status(), skinConflict);
    }

    private void setPreview(PlayerSkin fallback, boolean slim, LoadedFile body, LoadedFile cloak) {
        skinFile = body.preview();
        cloakFile = cloak.preview();
        skinAtlas = body.atlas();
        cloakAtlas = cloak.atlas();
        PlayerModelType model = body.texture() == null ? fallback.model()
                : slim ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        skin = PlayerSkin.insecure(body.texture() == null ? fallback.body() : body.texture(),
                cloak.texture(), null, model);
    }

    public Atlas skinAtlas() { return skinAtlas; }
    public Atlas cloakAtlas() { return cloakAtlas; }

    public PlayerSkin skin() { return skin; }
    public FilePreview skinFile() { return skinFile; }
    public FilePreview cloakFile() { return cloakFile; }
    public boolean skinConflict() { return skinConflict; }
    public boolean hasCloak() { return cloakFile.status() == Status.LOADED; }

    private LoadedFile load(Path file, boolean cloak, String scope) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file)) return new LoadedFile(new FilePreview(name, Status.MISSING), null);
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES + 1);
            return bytes.length == 0 ? invalid(name) : loadBytes(name, bytes, cloak, scope, false);
        } catch (IOException | RuntimeException e) {
            DebugLogger.CLIENT.exception(MODULE, "读取预览文件 " + name, e);
            return invalid(name);
        }
    }

    private LoadedFile loadBytes(String name, byte[] bytes, boolean cloak, String scope, boolean includeAtlas) {
        if (bytes.length == 0) return new LoadedFile(new FilePreview(name, Status.MISSING), null);
        NativeImage image = null;
        int previousTextures = textureIds.size();
        try {
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
            Identifier identifier = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID,
                    "cosmetic_preview/" + scope + "/" + name);
            Atlas atlas = includeAtlas ? new Atlas(identifier, validation.height()) : null;
            if (!cloak && includeAtlas && validation.height() == 32) {
                Identifier raw = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID,
                        "cosmetic_preview/" + scope + "/original_" + name);
                NativeImage original = NativeImage.read(bytes);
                register("original " + name, raw, original);
                atlas = new Atlas(raw, validation.height());
            }
            if (!cloak) image = CosmeticSkinImages.prepare(image);
            NativeImage textureImage = image;
            image = null;
            register(name, identifier, textureImage);
            return new LoadedFile(new FilePreview(name, Status.LOADED), new CosmeticTexture(identifier, identifier), atlas);
        } catch (IOException | RuntimeException e) {
            while (textureIds.size() > previousTextures) {
                Minecraft.getInstance().getTextureManager().release(textureIds.removeLast());
            }
            DebugLogger.CLIENT.exception(MODULE, "读取预览文件 " + name, e);
            return invalid(name);
        } finally {
            if (image != null) image.close();
        }
    }

    /** The texture manager takes ownership of the image after registration. */
    private void register(String name, Identifier identifier, NativeImage image) {
        DynamicTexture texture = null;
        try {
            texture = new DynamicTexture(() -> "YouzaiWorldCore cosmetic preview " + name, image);
            Minecraft.getInstance().getTextureManager().register(identifier, texture);
            textureIds.add(identifier);
        } catch (RuntimeException e) {
            if (texture == null) image.close();
            else texture.close();
            throw e;
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
