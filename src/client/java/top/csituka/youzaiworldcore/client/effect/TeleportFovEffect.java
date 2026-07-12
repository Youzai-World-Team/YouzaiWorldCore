package top.csituka.youzaiworldcore.client.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.network.TeleportAnchorTeleportPayload;

/**
 * 传送锚点传送时的 FOV 缩放动画管理器。
 * <p>
 * 流程：点击传送 → 视野快速放大（ZOOMING_IN）→ 放大到顶（ZOOMED_IN）
 * → 发送传送数据包 → 视野快速缩小（ZOOMING_OUT）→ 恢复正常（IDLE）
 * <p>
 * 缓动策略：
 * <ul>
 *   <li>放大（ZOOMING_IN）：五次方 ease-in（从慢到快）—— 起步柔和，越放越快</li>
 *   <li>缩小（ZOOMING_OUT）：五次方 ease-out（从快到慢）—— 迅速恢复，末尾轻柔归位</li>
 * </ul>
 * <p>
 * 渲染时使用 partialTick 进行逐帧插值，消除阶跃感，在任何帧率下都保持丝滑。
 */
public final class TeleportFovEffect {

    // ===== 枚举：动画阶段 =====
    public enum Phase {
        IDLE,
        ZOOMING_IN,
        ZOOMED_IN,
        ZOOMING_OUT
    }

    // ===== 动画参数 =====
    /** 放大阶段持续刻数（10 tick = 0.5 秒） */
    private static final int ZOOM_IN_TICKS = 10;
    /** 放大到顶后的保持刻数 */
    private static final int ZOOMED_HOLD_TICKS = 3;
    /** 缩小阶段持续刻数（12 tick = 0.6 秒） */
    private static final int ZOOM_OUT_TICKS = 12;

    /** 最小时的 FOV 倍率（0.08 = 极端的放大效果） */
    private static final float MIN_FOV_MODIFIER = 0.08f;

    // ===== 运行时状态 =====
    private static Phase phase = Phase.IDLE;
    /** 当前阶段已完成的完整刻数 */
    private static int tickCounter = 0;

    // 保存传送目标
    private static BlockPos targetPos;
    private static ResourceKey<Level> targetDim;

    // ===== 公开 API =====

    /**
     * 启动传送 FOV 动画。
     * <p>
     * 由 TeleportAnchorScreen 在点击传送按钮时调用。
     */
    public static void startTeleport(BlockPos pos, ResourceKey<Level> dim) {
        targetPos = pos;
        targetDim = dim;
        phase = Phase.ZOOMING_IN;
        tickCounter = 0;
    }

    /**
     * 每客户端刻更新一次动画状态（推进阶段计数器）。
     */
    public static void tick() {
        if (phase == Phase.IDLE) {
            return;
        }

        tickCounter++;

        switch (phase) {
            case ZOOMING_IN -> {
                if (tickCounter >= ZOOM_IN_TICKS) {
                    tickCounter = ZOOM_IN_TICKS; // 锁定在终点
                    phase = Phase.ZOOMED_IN;
                    tickCounter = 0;

                    // 放大到顶 → 发送传送数据包
                    sendTeleportPacket();
                }
            }

            case ZOOMED_IN -> {
                if (tickCounter >= ZOOMED_HOLD_TICKS) {
                    phase = Phase.ZOOMING_OUT;
                    tickCounter = 0;
                }
            }

            case ZOOMING_OUT -> {
                if (tickCounter >= ZOOM_OUT_TICKS) {
                    tickCounter = ZOOM_OUT_TICKS; // 锁定在终点
                    phase = Phase.IDLE;
                    tickCounter = 0;
                    targetPos = null;
                    targetDim = null;
                }
            }
        }
    }

    /**
     * 获取当前 FOV 倍率（无 partialTick 插值版本，仅用于 isActive 等检查用途）。
     * <p>
     * 渲染相关调用请使用 {@link #getFovModifier(float)} 以获得逐帧平滑效果。
     */
    public static float getFovModifier() {
        return computeFovModifier(0.0f);
    }

    /**
     * 获取当前 FOV 倍率（带 partialTick 插值）。
     * <p>
     * 由渲染层每帧调用，传入 partialTick 实现 sub-tick 精度的平滑插值，
     * 消除 20Hz 更新带来的阶跃感。
     *
     * @param partialTick 当前刻内的部分时间进度（0.0 ~ 1.0）
     * @return FOV 倍率（1.0 = 正常，越小越放大）
     */
    public static float getFovModifier(float partialTick) {
        return computeFovModifier(partialTick);
    }

    /**
     * 是否处于动画激活状态。
     */
    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    /**
     * 强制重置动画状态（例如切换维度/世界时）。
     */
    public static void reset() {
        phase = Phase.IDLE;
        tickCounter = 0;
        targetPos = null;
        targetDim = null;
    }

    /**
     * 获取当前动画阶段（仅供调试用）。
     */
    public static Phase getPhase() {
        return phase;
    }

    // ===== 核心计算 =====

    /**
     * 根据阶段和 sub-tick 进度计算 FOV 倍率。
     */
    private static float computeFovModifier(float partialTick) {
        if (phase == Phase.IDLE) return 1.0f;

        switch (phase) {
            case ZOOMING_IN -> {
                // 带 sub-tick 精度的进度（如 tickCounter=5, partialTick=0.5 → 5.5/10）
                float rawProgress = (tickCounter + partialTick) / ZOOM_IN_TICKS;
                float progress = Math.min(1.0f, rawProgress);
                // ease-in quintic：从慢到快，起步柔和
                float eased = quinticEaseIn(progress);
                return 1.0f - (1.0f - MIN_FOV_MODIFIER) * eased;
            }

            case ZOOMED_IN -> {
                return MIN_FOV_MODIFIER;
            }

            case ZOOMING_OUT -> {
                // 带 sub-tick 精度的进度
                float rawProgress = (tickCounter + partialTick) / ZOOM_OUT_TICKS;
                float progress = Math.min(1.0f, rawProgress);
                // ease-out quintic：从快到慢，末尾轻柔归位
                float eased = quinticEaseOut(progress);
                return MIN_FOV_MODIFIER + (1.0f - MIN_FOV_MODIFIER) * eased;
            }

            default -> {
                return 1.0f;
            }
        }
    }

    // ===== 缓动函数 =====

    /**
     * 五次方 ease-in：从慢到快。
     * <pre>
     * f(t) = t⁵
     * </pre>
     */
    private static float quinticEaseIn(float t) {
        return t * t * t * t * t;
    }

    /**
     * 五次方 ease-out：从快到慢。
     * <pre>
     * f(t) = 1 - (1 - t)⁵
     * </pre>
     */
    private static float quinticEaseOut(float t) {
        float f = 1.0f - t;
        return 1.0f - f * f * f * f * f;
    }

    // ===== 内部方法 =====

    /**
     * 发送传送数据包到服务端。
     */
    private static void sendTeleportPacket() {
        if (targetPos == null || targetDim == null) return;

        // 关闭屏幕（如果屏幕还开着 — 安全兜底）
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.screen() != null) {
            mc.setScreenAndShow(null);
        }

        ClientPlayNetworking.send(new TeleportAnchorTeleportPayload(targetPos, targetDim));
    }
}
