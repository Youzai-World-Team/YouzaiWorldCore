package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * 导入音频的循环播放实例：绕过资源系统，直接从磁盘
 * {@code config/youzaiworldcore/laowu_meme/sounds/<名>.ogg} 读取字节流，
 * 由 {@code SoundBufferLibraryLaowuMixin} 在 {@code getStream} 拦截
 * {@code youzaiworldcore:sounds/imported/<hex名>.ogg} 时提供 JOrbis 解码流。
 * <p>
 * 文件名经 {@link LaowuSoundIdCodec} hex 编码进 Identifier，规避
 * {@code [a-z0-9/._-]} 限制（中文/空格文件名曾导致崩溃）。行为与
 * {@link LaowuSoundInstance} 一致：循环、跟随两只猫中点、过远静音、猫消失即停。
 * </p>
 * <p>
 * 关键坑：{@code AbstractSoundInstance} 的 {@code getVolume()/getPitch()} 读的是超类
 * {@code protected} {@code sound} 字段，该字段由 {@code resolve()} 在内部填充（默认实现
 * {@code this.sound = events.getSound(random)}）。因此本类不能覆盖 {@code getSound()}，
 * 且必须在 {@code resolve()} 里把 {@code this.sound} 填上，否则 SoundEngine.play
 * 调 {@code getVolume()} 时 {@code this.sound} 为 null 直接 NPE（网络协议错误断连）。
 * </p>
 */
public class LaowuImportedSoundInstance extends AbstractTickableSoundInstance {

    /** 静音下限：>16 格时音量钳到该值（≈-60dB）。不能返回 0——SoundEngine.play 会跳过 0 音量实例 */
    private static final float MIN_VOLUME = 0.001f;

    private final WeighedSoundEvents events;
    private final int catAId, catBId;

    public LaowuImportedSoundInstance(String baseName, int catAId, int catBId) {
        // 用 LAOWU2 仅作构造载体；真正播放的声音由下方 disk Sound 提供
        // （location=imported/<hex>，经 Sound.getPath() 后变为 sounds/imported/<hex>.ogg，
        // 被 mixin 拦截读盘）。
        super(LaowuModSounds.LAOWU2, SoundSource.NEUTRAL, RandomSource.create());
        Sound sound = new Sound(
                Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID,
                        "imported/" + LaowuSoundIdCodec.encode(baseName)),
                (RandomSource r) -> 1.0f,   // volume
                (RandomSource r) -> 1.0f,   // pitch
                1,
                Sound.Type.SOUND_EVENT,
                true,   // stream：走 SoundBufferLibrary.getStream（被 mixin 拦截）
                false,  // preload
                16);    // 衰减距离
        this.events = new WeighedSoundEvents(getIdentifier(), null);
        this.events.addSound(sound);
        this.catAId = catAId;
        this.catBId = catBId;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        // 关闭 MC 自带衰减，改由下方 getVolume() 手动平滑计算，统一所有音频的 16 格衰退
        // （磁盘读取的导入音频在 MC 里常不自带头衰减，表现为远离猫骤然消失）
        this.attenuation = SoundInstance.Attenuation.NONE;
        updatePos();
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        // 必须填充超类 this.sound 字段（仿默认实现），否则 getVolume() 在 SoundEngine.play 里 NPE
        this.sound = this.events.getSound(this.random);
        return this.events;
    }

    @Override
    public float getVolume() {
        // 手动平滑距离衰减：0~16 格线性从 1 降到 0.001，超过 16 格钳制在 0.001（近静音，不归零）
        // 与 LaowuSoundInstance 一致，保证导入音频也有自然衰退且靠近后能恢复音量
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return this.volume;
        }
        double dist = Math.sqrt(mc.player.distanceToSqr(this.x, this.y, this.z));
        float f = (float) (1.0 - dist / 16.0);
        if (f < MIN_VOLUME) {
            f = MIN_VOLUME;
        }
        return this.volume * f;
    }

    /** 允许 0/近零音量开始播放（配合 MIN_VOLUME 双保险）：实时音量由 getVolume() 按距离动态计算 */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (!updatePos()) {
            stop();
        }
    }

    /** 更新到两只猫中点；返回 false 表示猫已不存在，应停止。
     *  注意：不再按距离停止——距离只影响 getVolume()，玩家走远再靠近声音自然恢复。 */
    private boolean updatePos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        Entity a = mc.level.getEntity(catAId);
        Entity b = mc.level.getEntity(catBId);
        if (a == null || b == null) {
            return false;
        }
        this.x = (a.getX() + b.getX()) / 2.0;
        this.y = (a.getY() + b.getY()) / 2.0;
        this.z = (a.getZ() + b.getZ()) / 2.0;
        return true;
    }
}
