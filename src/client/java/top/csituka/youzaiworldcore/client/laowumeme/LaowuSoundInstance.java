package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * 循环播放的音频实例：跟随两只猫的中点位置，手动做 16 格平滑线性距离衰减。
 * <p>
 * 为什么手动算衰减：MC 对「流式(stream) / 磁盘读取」的音频其自带的 distance attenuation
 * 在某些情况下不生效（实测战吼与导入音频远离猫时音量不衰退、超过某距离骤然消失），
 * 而 laowu2/qiliang 偶发正常。为让所有音频（含战吼、导入）行为一致且自然衰退，
 * 这里关闭 MC 自带衰减（{@code attenuation=NONE}），改为在 {@link #getVolume()} 里
 * 按玩家到猫中点的距离线性计算音量：0~16 格从 1 平滑降到 0，超过 16 格保持静音，
 * tick() 在 32 格处彻底停止。由 {@link LaowuMemeClientState} 创建/停止。
 * </p>
 */
public class LaowuSoundInstance extends AbstractTickableSoundInstance {

    private final int catAId, catBId;

    public LaowuSoundInstance(SoundEvent sound, int catAId, int catBId) {
        super(sound, SoundSource.NEUTRAL, RandomSource.create());
        this.catAId = catAId;
        this.catBId = catBId;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        // 关闭 MC 自带衰减，改由下方 getVolume() 手动平滑计算，统一所有音频的 16 格衰退
        this.attenuation = SoundInstance.Attenuation.NONE;
        updatePos();
    }

    @Override
    public float getVolume() {
        // 手动平滑距离衰减：0~16 格线性从 1 降到 0，超过 16 格保持 0（静音但不突然停）
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return this.volume;
        }
        double dist = Math.sqrt(mc.player.distanceToSqr(this.x, this.y, this.z));
        float f = (float) (1.0 - dist / 16.0);
        if (f < 0.0f) {
            f = 0.0f;
        }
        if (f > 1.0f) {
            f = 1.0f;
        }
        return this.volume * f;
    }

    @Override
    public void tick() {
        if (!updatePos()) {
            stop();
        }
    }

    /** 更新到两只猫中点；返回 false 表示猫已不存在或玩家过远（>32格）应停止 */
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
        // 音量由 getVolume() 按距离平滑衰减，不在此处手动覆盖。
        // 玩家离中点超过 32 格时直接停止，避免极远距离仍占声音通道。
        if (mc.player != null && mc.player.distanceToSqr(this.x, this.y, this.z) > 32 * 32) {
            return false;
        }
        return true;
    }
}
