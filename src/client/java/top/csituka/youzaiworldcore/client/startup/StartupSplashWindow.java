package top.csituka.youzaiworldcore.client.startup;

import top.csituka.youzaiworldcore.util.DebugLogger;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import java.awt.AlphaComposite;
import java.awt.AWTError;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minecraft 主窗口显示前使用的轻量启动窗口。
 * <p>
 * 窗口固定为浅绿色圆角无边框样式，左上角显示悠哉世界标志，
 * 左下角显示 8-bit 加载动画与“加载中”文字。
 * 所有 Swing 生命周期操作都在 AWT 事件调度线程执行。
 * </p>
 */
public final class StartupSplashWindow {

    private static final int WINDOW_WIDTH = 520;
    private static final int WINDOW_HEIGHT = 300;
    private static final int CORNER_ARC = 32;
    private static final int LOGO_WIDTH = 320;
    private static final int LOGO_LEFT_MARGIN = 24;
    private static final float LOADING_FONT_SIZE = 16.0F;
    private static final String LOGO_RESOURCE =
            "/assets/youzaiworldcore/textures/gui/startup/logo.png";
    private static final String LOADING_FONT_RESOURCE =
            "/assets/youzaiworldcore/font/opposans_bold.ttf";
    private static final Color BACKGROUND_COLOR = new Color(0xDDF2E1);
    private static final Color TEXT_COLOR = new Color(0x385246);
    private static final AtomicBoolean SHOW_REQUESTED = new AtomicBoolean(false);
    private static final AtomicBoolean CLOSE_REQUESTED = new AtomicBoolean(false);

    private static volatile JWindow window;
    private static volatile StartupLoadingIndicator loadingIndicator;

    private StartupSplashWindow() {}

    /**
     * 同步等待启动窗口完成创建并显示，重复调用不会创建多个窗口。
     */
    public static void show() {
        DebugLogger.entering("StartupSplash", "show");

        boolean firstRequest = SHOW_REQUESTED.compareAndSet(false, true);
        DebugLogger.branch("StartupSplash", "首次请求显示启动窗口", firstRequest);
        if (!firstRequest || CLOSE_REQUESTED.get()) {
            DebugLogger.exiting("StartupSplash", "show", "已显示或已请求关闭");
            return;
        }

        Thread startupThread = Thread.currentThread();
        try {
            if (GraphicsEnvironment.isHeadless()) {
                DebugLogger.warn("StartupSplash", "当前图形环境不支持窗口，已跳过启动窗口");
                DebugLogger.exiting("StartupSplash", "show", "无图形环境");
                return;
            }

            if (EventQueue.isDispatchThread()) {
                createAndShow();
            } else {
                EventQueue.invokeAndWait(StartupSplashWindow::createAndShow);
            }

            if (window != null) {
                startCleanupGuard(startupThread);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DebugLogger.exception("StartupSplash", "等待启动窗口显示", e);
            close();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            DebugLogger.exception("StartupSplash", "创建启动窗口", cause);
            close();
        } catch (RuntimeException | AWTError | LinkageError e) {
            // DISPLAY 无效、桌面模块不可用等情况只跳过启动窗口，不影响 Minecraft。
            DebugLogger.exception("StartupSplash", "显示启动窗口", e);
            close();
        }

        DebugLogger.exiting("StartupSplash", "show");
    }

    /**
     * 异步关闭并释放启动窗口，允许从 Minecraft 渲染线程安全调用。
     */
    public static void close() {
        DebugLogger.entering("StartupSplash", "close");
        CLOSE_REQUESTED.set(true);

        if (window == null) {
            DebugLogger.exiting("StartupSplash", "close", "窗口尚未创建或已经关闭");
            return;
        }

        try {
            if (EventQueue.isDispatchThread()) {
                disposeOnEventThread();
            } else {
                EventQueue.invokeLater(StartupSplashWindow::disposeOnEventThread);
            }
        } catch (RuntimeException | AWTError | LinkageError e) {
            DebugLogger.exception("StartupSplash", "调度启动窗口关闭", e);
        }

        DebugLogger.exiting("StartupSplash", "close");
    }

    // ===== 窗口创建与清理 =====

    private static void createAndShow() {
        if (CLOSE_REQUESTED.get() || window != null) {
            return;
        }

        JWindow splash = createTranslucencyAwareWindow();
        StartupLoadingIndicator indicator = null;
        try {
            splash.setFocusableWindowState(false);
            splash.setAutoRequestFocus(false);

            boolean antialiasedCorners = enableAntialiasedCorners(splash);
            JPanel content;
            if (antialiasedCorners) {
                content = new AntialiasedRoundedPanel();
            } else {
                splash.setBackground(BACKGROUND_COLOR);
                content = new JPanel(new BorderLayout());
                content.setBackground(BACKGROUND_COLOR);
            }

            JLabel logoLabel = createLogoLabel();
            if (logoLabel != null) {
                content.add(logoLabel, BorderLayout.PAGE_START);
            }

            indicator = new StartupLoadingIndicator(TEXT_COLOR);
            indicator.setAlignmentY(Component.CENTER_ALIGNMENT);

            JLabel loadingLabel = new JLabel("加载中", SwingConstants.LEFT);
            loadingLabel.setForeground(TEXT_COLOR);
            loadingLabel.setFont(createLoadingFont());
            loadingLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

            JPanel loadingRow = new JPanel();
            loadingRow.setOpaque(false);
            loadingRow.setLayout(new BoxLayout(loadingRow, BoxLayout.X_AXIS));
            loadingRow.setBorder(BorderFactory.createEmptyBorder(0, 28, 24, 0));
            loadingRow.add(indicator);
            loadingRow.add(Box.createHorizontalStrut(8));
            loadingRow.add(loadingLabel);
            content.add(loadingRow, BorderLayout.PAGE_END);

            splash.setContentPane(content);
            splash.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
            splash.setLocationRelativeTo(null);
            if (!antialiasedCorners) {
                applyNativeRoundedShape(splash);
            }

            if (CLOSE_REQUESTED.get()) {
                splash.dispose();
                return;
            }

            window = splash;
            loadingIndicator = indicator;
            splash.setVisible(true);
            indicator.startAnimation();
            DebugLogger.info("StartupSplash", "启动加载窗口已显示");

            if (CLOSE_REQUESTED.get()) {
                disposeOnEventThread();
            }
        } catch (RuntimeException | AWTError | LinkageError e) {
            if (indicator != null) {
                indicator.stopAnimation();
            }
            try {
                splash.dispose();
            } catch (RuntimeException | AWTError disposeError) {
                DebugLogger.exception("StartupSplash", "清理创建失败的启动窗口", disposeError);
            } finally {
                if (window == splash) {
                    window = null;
                }
                if (loadingIndicator == indicator) {
                    loadingIndicator = null;
                }
            }
            throw e;
        }
    }

    private static JWindow createTranslucencyAwareWindow() {
        try {
            GraphicsDevice device = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            if (device.isWindowTranslucencySupported(
                    GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                for (GraphicsConfiguration configuration : device.getConfigurations()) {
                    if (configuration.isTranslucencyCapable()) {
                        return new JWindow(configuration);
                    }
                }
            }
        } catch (RuntimeException | AWTError e) {
            DebugLogger.exception("StartupSplash", "选择逐像素透明图形配置", e);
        }
        return new JWindow();
    }

    private static boolean enableAntialiasedCorners(JWindow splash) {
        try {
            GraphicsConfiguration configuration = splash.getGraphicsConfiguration();
            GraphicsDevice device = configuration.getDevice();
            if (!configuration.isTranslucencyCapable()
                    || !device.isWindowTranslucencySupported(
                            GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                DebugLogger.warn("StartupSplash", "当前窗口系统不支持抗锯齿圆角，已使用原生圆角");
                return false;
            }

            splash.setBackground(new Color(0, 0, 0, 0));
            splash.getRootPane().setOpaque(false);
            splash.getLayeredPane().setOpaque(false);
            DebugLogger.info("StartupSplash", "启动窗口已启用逐像素透明抗锯齿圆角");
            return true;
        } catch (RuntimeException | AWTError e) {
            DebugLogger.exception("StartupSplash", "启用启动窗口抗锯齿圆角", e);
            return false;
        }
    }

    private static Font createLoadingFont() {
        try (InputStream stream = StartupSplashWindow.class.getResourceAsStream(LOADING_FONT_RESOURCE)) {
            if (stream == null) {
                DebugLogger.warn("StartupSplash", "启动窗口字体资源未找到：" + LOADING_FONT_RESOURCE);
                return createFallbackLoadingFont();
            }

            Font loadingFont = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(LOADING_FONT_SIZE);
            DebugLogger.info("StartupSplash", "启动窗口已加载 OPPOSans-Bold 字体");
            return loadingFont;
        } catch (IOException | FontFormatException | RuntimeException e) {
            DebugLogger.exception("StartupSplash", "读取启动窗口字体资源", e);
            return createFallbackLoadingFont();
        }
    }

    private static Font createFallbackLoadingFont() {
        return new Font(Font.SANS_SERIF, Font.BOLD, Math.round(LOADING_FONT_SIZE));
    }

    private static JLabel createLogoLabel() {
        try (InputStream stream = StartupSplashWindow.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (stream == null) {
                DebugLogger.warn("StartupSplash", "启动窗口标志资源未找到：" + LOGO_RESOURCE);
                return null;
            }

            BufferedImage sourceImage = ImageIO.read(stream);
            if (sourceImage == null || sourceImage.getWidth() <= 0 || sourceImage.getHeight() <= 0) {
                DebugLogger.warn("StartupSplash", "启动窗口标志资源无法解码：" + LOGO_RESOURCE);
                return null;
            }

            int logoHeight = Math.max(1, (int) Math.round(
                    LOGO_WIDTH * (double) sourceImage.getHeight() / sourceImage.getWidth()
            ));
            Image scaledImage = sourceImage.getScaledInstance(LOGO_WIDTH, logoHeight, Image.SCALE_SMOOTH);

            JLabel logoLabel = new JLabel(new ImageIcon(scaledImage));
            logoLabel.setHorizontalAlignment(SwingConstants.LEFT);
            logoLabel.setVerticalAlignment(SwingConstants.TOP);
            logoLabel.setBorder(BorderFactory.createEmptyBorder(24, LOGO_LEFT_MARGIN, 0, 0));
            return logoLabel;
        } catch (IOException e) {
            DebugLogger.exception("StartupSplash", "读取启动窗口标志资源", e);
            return null;
        }
    }

    private static void applyNativeRoundedShape(JWindow splash) {
        GraphicsDevice device = splash.getGraphicsConfiguration().getDevice();
        if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
            DebugLogger.warn("StartupSplash", "当前窗口系统不支持圆角窗口，已使用矩形外观");
            return;
        }

        try {
            splash.setShape(new RoundRectangle2D.Double(
                    0,
                    0,
                    WINDOW_WIDTH,
                    WINDOW_HEIGHT,
                    CORNER_ARC,
                    CORNER_ARC
            ));
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            DebugLogger.exception("StartupSplash", "设置启动窗口圆角", e);
        }
    }

    /**
     * 使用逐像素透明度绘制带抗锯齿边缘的圆角背景。
     */
    private static final class AntialiasedRoundedPanel extends JPanel {

        private AntialiasedRoundedPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D roundedGraphics = (Graphics2D) graphics.create();
            try {
                roundedGraphics.setComposite(AlphaComposite.Clear);
                roundedGraphics.fillRect(0, 0, getWidth(), getHeight());
                roundedGraphics.setComposite(AlphaComposite.SrcOver);
                roundedGraphics.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );
                roundedGraphics.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY
                );
                roundedGraphics.setRenderingHint(
                        RenderingHints.KEY_ALPHA_INTERPOLATION,
                        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
                );
                roundedGraphics.setColor(BACKGROUND_COLOR);
                roundedGraphics.fill(new RoundRectangle2D.Double(
                        0.5,
                        0.5,
                        Math.max(0.0, getWidth() - 1.0),
                        Math.max(0.0, getHeight() - 1.0),
                        CORNER_ARC,
                        CORNER_ARC
                ));
            } finally {
                roundedGraphics.dispose();
            }
        }
    }

    private static void disposeOnEventThread() {
        StartupLoadingIndicator indicator = loadingIndicator;
        loadingIndicator = null;
        if (indicator != null) {
            indicator.stopAnimation();
        }

        JWindow splash = window;
        if (splash == null) {
            return;
        }

        try {
            splash.setVisible(false);
        } catch (RuntimeException | AWTError e) {
            DebugLogger.exception("StartupSplash", "隐藏启动窗口", e);
        }

        try {
            // 即使隐藏操作失败也必须继续释放，避免 AWT 窗口阻止进程自然退出。
            splash.dispose();
            DebugLogger.info("StartupSplash", "启动加载窗口已关闭");
        } catch (RuntimeException | AWTError e) {
            DebugLogger.exception("StartupSplash", "释放启动窗口", e);
        } finally {
            if (indicator != null) {
                indicator.stopAnimation();
            }
            window = null;
        }
    }

    private static void startCleanupGuard(Thread startupThread) {
        try {
            Thread cleanupGuard = new Thread(() -> {
                try {
                    startupThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    DebugLogger.exception("StartupSplash", "等待游戏启动线程结束", e);
                } finally {
                    close();
                }
            }, "YouzaiWorldCore-StartupSplashGuard");
            cleanupGuard.setDaemon(true);
            cleanupGuard.start();
        } catch (RuntimeException e) {
            DebugLogger.exception("StartupSplash", "创建启动窗口清理守护线程", e);
        }
    }
}
