package top.csituka.youzaiworldcore.client.animation;

/** 帧率无关的轻微悬停状态层；低特效模式仍保留即时反馈。 */
public final class YzuiHover {
    private long lastFrame;
    private float value;

    public float sample(boolean hovered) {
        long now = System.nanoTime();
        float target = hovered ? 1f : 0f;
        if (lastFrame == 0 || !GuiAnimationController.isEnabled()) {
            value = target;
        } else {
            double elapsed = Math.clamp((now - lastFrame) / 1_000_000.0, 0, 100);
            value += (target - value) * (float) (1.0 - Math.exp(-elapsed / 40.0));
            if (Math.abs(value - target) < 0.002f) value = target;
        }
        lastFrame = now;
        return value;
    }
}
