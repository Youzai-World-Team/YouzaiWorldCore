package top.csituka.youzaiworldcore.client.startup;

import top.csituka.youzaiworldcore.util.DebugLogger;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * 启动窗口使用的独立 8-bit 加载动画组件。
 * <p>
 * 动画由 16 个方形像素块组成，每次显示连续的 8 个方块，
 * 以一秒一周的节奏顺时针追逐。
 * </p>
 */
@SuppressWarnings("serial")
public final class StartupLoadingIndicator extends JComponent {

    private static final int COMPONENT_SIZE = 42;
    private static final int BLOCK_SIZE = 6;
    private static final int BLOCK_COUNT = 16;
    private static final int VISIBLE_BLOCK_COUNT = 8;
    private static final int REPAINT_INTERVAL_MILLIS = 16;
    private static final long FRAME_DURATION_NANOS = 62_500_000L;

    private static final int[] BLOCK_X = {
            18, 24, 30, 36,
            36, 36, 30, 24,
            18, 12, 6, 0,
            0, 0, 6, 12
    };
    private static final int[] BLOCK_Y = {
            0, 0, 6, 12,
            18, 24, 30, 36,
            36, 36, 30, 24,
            18, 12, 6, 0
    };

    private final Timer repaintTimer;
    private long animationStartedAtNanos;

    /**
     * 创建指定颜色的像素风加载动画。
     *
     * @param color 方形像素块颜色
     */
    public StartupLoadingIndicator(Color color) {
        Dimension size = new Dimension(COMPONENT_SIZE, COMPONENT_SIZE);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        setForeground(color);
        setOpaque(false);
        setFocusable(false);

        this.repaintTimer = new Timer(REPAINT_INTERVAL_MILLIS, event -> onAnimationFrame());
        this.repaintTimer.setCoalesce(true);
        this.repaintTimer.setRepeats(true);
    }

    /**
     * 从第一帧开始播放加载动画；重复调用不会创建额外计时器。
     */
    public void startAnimation() {
        if (this.repaintTimer.isRunning()) {
            return;
        }

        this.animationStartedAtNanos = System.nanoTime();
        this.repaintTimer.start();
        repaint();
        DebugLogger.info("StartupLoadingIndicator", "启动窗口加载动画已开始");
    }

    /**
     * 停止动画并取消后续重绘任务。
     */
    public void stopAnimation() {
        if (!this.repaintTimer.isRunning()) {
            return;
        }

        this.repaintTimer.stop();
        DebugLogger.info("StartupLoadingIndicator", "启动窗口加载动画已停止");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNotify() {
        stopAnimation();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        long elapsedNanos = this.repaintTimer.isRunning()
                ? Math.max(0L, System.nanoTime() - this.animationStartedAtNanos)
                : 0L;
        int firstVisibleBlock = (8 + (int) (elapsedNanos / FRAME_DURATION_NANOS)) % BLOCK_COUNT;

        graphics.setColor(getForeground());
        for (int offset = 0; offset < VISIBLE_BLOCK_COUNT; offset++) {
            int blockIndex = (firstVisibleBlock + offset) % BLOCK_COUNT;
            graphics.fillRect(
                    BLOCK_X[blockIndex],
                    BLOCK_Y[blockIndex],
                    BLOCK_SIZE,
                    BLOCK_SIZE
            );
        }
    }

    private void onAnimationFrame() {
        if (!isDisplayable() || !isShowing()) {
            stopAnimation();
            return;
        }
        repaint();
    }
}
