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
 * 按玩家到猫中点的距离线性计算音量。
 * </p>
 * <p>
 * 距离语义（v2 修复）：0~16 格从 1 平滑降到 0.001（≈-60dB，人耳不可闻）；超过 16 格
 * 钳制在 0.001 而非 0。实例<b>不再因距离而停止</b>——玩家走远→音量近静音，靠近→音量自动
 * 恢复。原因（26.2 字节码实测）：{@code SoundEngine.play} 对「0 音量且
 * {@code canStartSilent()==false}」的实例直接跳过（"volume was zero"），且原「>32 格
 * stop」会永久销毁实例，两者都会导致靠近后无法恢复声音。
 * </p>
 * <p>
 * 实例仅在两只猫实体不存在时停止（由 {@link LaowuMemeClientState} 创建/停止）。
 * </p>
 */
@SuppressWarnings("null")
public class LaowuSoundInstance extends AbstractTickableSoundInstance {

    /** 静音下限：>16 格时音量钳到该值（≈-60dB）。不能返回 0——见类注释 */
    private static final float MIN_VOLUME = 0.001f;

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
        // 手动平滑距离衰减：0~16 格线性从 1 降到 0.001，超过 16 格钳制在 0.001（近静音，不归零）
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
