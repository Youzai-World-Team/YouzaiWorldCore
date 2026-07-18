package top.csituka.youzaiworldcore.client.pickup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.client.pickup.display.SubtitleDisplayEntry;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 声音字幕事件捕获处理器。
 * <p>
 * 实现 {@link SoundEventListener} 接口，监听引擎播放的每个声音事件。
 * 当系统设置中开启了声音字幕时，捕获字幕文本并创建 {@link SubtitleDisplayEntry}
 * 交给 {@link DrawEntriesHandler} 统一渲染，从而与拾取提示共享同一显示区域。
 * </p>
 */
@SuppressWarnings("null")
public final class SubtitleCaptureHandler implements SoundEventListener {

    /** 单例实例 */
    public static final SubtitleCaptureHandler INSTANCE = new SubtitleCaptureHandler();

    /** 字幕显示时间（毫秒，与原版默认 3000 一致） */
    private static final int DISPLAY_TIME_MS = 3000;

    /** 字幕显示时间（tick，DISPLAY_TIME_MS / 50） */
    private static final int DISPLAY_TIME_TICKS = DISPLAY_TIME_MS / 50;

    private boolean registered = false;

    private SubtitleCaptureHandler() {}

    /**
     * 注册到 {@link SoundManager}。
     */
    public void register(SoundManager soundManager) {
        if (!registered) {
            soundManager.addListener(this);
            registered = true;
            DebugLogger.info("SubtitleCaptureHandler", "已注册声音字幕监听器");
        }
    }

    /**
     * 从 {@link SoundManager} 注销。
     */
    public void unregister(SoundManager soundManager) {
        if (registered) {
            soundManager.removeListener(this);
            registered = false;
            DebugLogger.info("SubtitleCaptureHandler", "已注销声音字幕监听器");
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    @Override
    public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
        // 获取字幕文本
        Component text = soundEvent.getSubtitle();
        if (text == null || range <= 0.0f) return;

        DebugLogger.info("SubtitleCaptureHandler", "字幕事件: %s (range=%.1f)", text.getString(), range);

        // 获取声音位置
        Vec3 soundPos = new Vec3(sound.getX(), sound.getY(), sound.getZ());

        // 计算方向（相对于当前玩家视角）
        Minecraft client = Minecraft.getInstance();
        Direction dir = Direction.NONE;
        if (client.player != null) {
            dir = computeDirection(client.player.position(), client.player.getYRot(), soundPos);
        }

        // 创建字幕显示条目
        SubtitleDisplayEntry entry = new SubtitleDisplayEntry(text, dir, DISPLAY_TIME_TICKS);
        DrawEntriesHandler.INSTANCE.addEntry(entry.getKey(), entry);
    }

    /**
     * 计算声音相对于玩家视线方向。
     */
    private static Direction computeDirection(Vec3 playerPos, float playerYaw, Vec3 soundPos) {
        // 计算从玩家到声音的方向向量（水平面）
        double dx = soundPos.x - playerPos.x;
        double dz = soundPos.z - playerPos.z;
        if (dx * dx + dz * dz < 1.0) return Direction.NONE; // 太近，无方向指示

        // 计算声音相对于玩家的水平方位角
        double soundAngle = Math.atan2(dz, dx); // 弧度，相对于 X 正轴
        double playerAngle = Math.toRadians(playerYaw); // 玩家朝向（yaw）

        // 计算相对角度（-PI 到 PI）
        double relativeAngle = soundAngle - playerAngle;
        // 归一化到 (-PI, PI]
        while (relativeAngle > Math.PI) relativeAngle -= 2 * Math.PI;
        while (relativeAngle <= -Math.PI) relativeAngle += 2 * Math.PI;

        if (relativeAngle > Math.PI / 4 && relativeAngle <= Math.PI * 3 / 4) {
            return Direction.LEFT; // 声音在左侧
        } else if (relativeAngle > -Math.PI * 3 / 4 && relativeAngle <= -Math.PI / 4) {
            return Direction.RIGHT; // 声音在右侧
        } else if (relativeAngle > -Math.PI / 4 && relativeAngle <= Math.PI / 4) {
            return Direction.FORWARD; // 声音在前方（视野内）
        } else {
            return Direction.BEHIND; // 声音在后方
        }
    }

    /**
     * 声音方向枚举。
     */
    public enum Direction {
        NONE,
        FORWARD,
        LEFT,
        RIGHT,
        BEHIND
    }
}
