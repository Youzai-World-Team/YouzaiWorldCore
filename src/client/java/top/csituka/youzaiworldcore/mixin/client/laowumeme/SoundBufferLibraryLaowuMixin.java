package top.csituka.youzaiworldcore.mixin.client.laowumeme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.laowumeme.LaowuAudioPool;
import top.csituka.youzaiworldcore.client.laowumeme.LaowuSoundIdCodec;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * 拦截 {@code youzaiworldcore:sounds/imported/<hex名>.ogg} 的资源读取，
 * hex 解码出真实文件名后直接从 {@code config/youzaiworldcore/laowu_meme/sounds/<名>.ogg}
 * 读取并用 JOrbis 解码，使导入音频无需进资源包即可播放。
 * <p>
 * 关键点：SoundEngine.play 调用 getStream(location, looping) 时，传入的 location 是
 * {@code Sound.getPath()} 的结果，格式为 {@code youzaiworldcore:sounds/imported/<hex>.ogg}
 * （带 {@code sounds/} 前缀和 {@code .ogg} 后缀）。因此 mixin 必须匹配 {@code sounds/imported/}。
 * </p>
 * <p>
 * 仅对 youzaiworldcore 命名空间 + {@code sounds/imported/} 路径生效，其余声音走原逻辑。
 * looping 分支完全照搬原版 getStream：用 {@code LoopingAudioStream} 包一层，
 * provider 每次从重置后的流重新建解码器，实现无缝循环。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryLaowuMixin {

    private static final String MODULE = "SoundBufferLibraryLaowuMixin";

    @Inject(method = "getStream(Lnet/minecraft/resources/Identifier;Z)"
            + "Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$laowuInterceptImportedStream(Identifier id, boolean looping,
                                                              CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!id.getNamespace().equals("youzaiworldcore")) {
            return;
        }
        String path = id.getPath();
        // SoundEngine.play 调用 getStream 时传入的是 Sound.getPath() 结果：
        // youzaiworldcore:sounds/imported/<hex>.ogg（带 sounds/ 前缀和 .ogg 后缀）
        if (!path.startsWith("sounds/imported/")) {
            return;
        }
        String enc = path.substring("sounds/imported/".length());  // 形如 <hex>.ogg
        if (enc.isEmpty()) {
            return;
        }
        String hex = enc.endsWith(".ogg") ? enc.substring(0, enc.length() - 4) : enc;
        String name = LaowuSoundIdCodec.decode(hex);
        if (name.isEmpty()) {
            return;
        }
        File f = new File(LaowuAudioPool.getSoundsDir(), name + ".ogg");
        if (!f.isFile()) {
            return;
        }
        try {
            InputStream in = Files.newInputStream(f.toPath());
            AudioStream stream;
            if (looping) {
                LoopingAudioStream.AudioStreamProvider provider =
                        (InputStream s) -> (AudioStream) (Object) new JOrbisAudioStream(s);
                stream = (AudioStream) (Object) new LoopingAudioStream(provider, in);
            } else {
                stream = (AudioStream) (Object) new JOrbisAudioStream(in);
            }
            cir.setReturnValue(CompletableFuture.completedFuture(stream));
        } catch (IOException | RuntimeException e) {
            // 读取/解码失败：放行给原逻辑（按缺失资源处理），不崩溃；给玩家提示便于排查
            DebugLogger.warn(MODULE, "导入音频解码失败（已忽略）：%s —— %s", name, e.getMessage());
            Minecraft.getInstance().gui.toastManager().addToast(
                    new SystemToast(
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("YouzaiWorldCore"),
                            Component.literal("导入音频解码失败：" + name)));
        }
    }
}
