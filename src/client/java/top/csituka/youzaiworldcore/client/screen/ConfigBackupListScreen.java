package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.config.ConfigIOManager;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Android 端导入列表界面 — 显示 {@code config_backups/} 下的全部 ZIP 文件。
 * <p>
 * 按修改时间倒序排列，点击任意条目直接触发导入流程。
 * 无备份文件时显示空状态提示。
 * </p>
 */
@SuppressWarnings("null")
public class ConfigBackupListScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ConfigBackupListScreen");
    private static final String LOG_MODULE = "ConfigBackupListScreen";

    private static final int LIST_START_Y = 60;
    private static final int ENTRY_HEIGHT = 28;
    private static final int LIST_WIDTH = 640;
    private int page;

    private final Screen parentScreen;
    private final File gameDir;

    private List<File> backupFiles = new ArrayList<>();
    private List<TransparentButton> entryButtons = new ArrayList<>();
    private TransparentButton backButton;

    private volatile boolean importInProgress = false;

    public ConfigBackupListScreen(Screen parentScreen, File gameDir) {
        super(Component.translatable("screen.youzaiworldcore.config_io.backup_list_title"));
        this.parentScreen = parentScreen;
        this.gameDir = gameDir;
    }

    @Override
    protected void init() {
        super.init();
        DebugLogger.entering(LOG_MODULE, "init");
        scanBackupFiles();
        rebuildEntryWidgets();
    }

    private void scanBackupFiles() {
        Path backupDir = gameDir.toPath().resolve("config_backups");
        File dir = backupDir.toFile();
        if (!dir.isDirectory()) {
            backupFiles = new ArrayList<>();
            DebugLogger.debug(LOG_MODULE, "config_backups 目录不存在");
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.startsWith("config_export_") && name.endsWith(".zip"));
        if (files == null) {
            backupFiles = new ArrayList<>();
            return;
        }

        backupFiles = new ArrayList<>(Arrays.asList(files));
        // 按修改时间倒序
        backupFiles.sort(Comparator.comparingLong(File::lastModified).reversed());
        DebugLogger.debug(LOG_MODULE, "扫描到 %d 个备份文件", backupFiles.size());
    }

    private void rebuildEntryWidgets() {
        clearWidgets();
        entryButtons.clear();
        int listWidth = Math.min(LIST_WIDTH, width - 64);
        int listX = (width - listWidth) / 2;
        int pageSize = Math.max(1, (height - 130) / (ENTRY_HEIGHT + 6));
        int pageCount = Math.max(1, (backupFiles.size() + pageSize - 1) / pageSize);
        page = Math.clamp(page, 0, pageCount - 1);
        int first = page * pageSize, last = Math.min(backupFiles.size(), first + pageSize);
        for (int index = first; index < last; index++) {
            File file = backupFiles.get(index);
            String display = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(file.lastModified()))
                    + "   " + file.getName();
            TransparentButton button = new TransparentButton(listX, LIST_START_Y + (index - first) * (ENTRY_HEIGHT + 6),
                    listWidth, ENTRY_HEIGHT, Component.literal(display), () -> onEntryClick(file.toPath()));
            button.setTextLeftAligned(true);
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(file.getName())));
            button.active = !importInProgress;
            entryButtons.add(button);
            addRenderableWidget(button);
        }
        backButton = new TransparentButton((width - 124) / 2, height - 42, 124, 26,
                Component.translatable("screen.youzaiworldcore.config_io.backup_list_back"), this::onBack);
        addRenderableWidget(backButton);
        if (pageCount > 1) {
            TransparentButton previous = new TransparentButton(listX, height - 42, 72, 26,
                    Component.literal("←"), () -> { page--; rebuildEntryWidgets(); });
            previous.active = page > 0 && !importInProgress;
            addRenderableWidget(previous);
            TransparentButton next = new TransparentButton(listX + listWidth - 72, height - 42, 72, 26,
                    Component.literal("→"), () -> { page++; rebuildEntryWidgets(); });
            next.active = page < pageCount - 1 && !importInProgress;
            addRenderableWidget(next);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int listWidth = Math.min(LIST_WIDTH, width - 64);
        YzuiTheme.label(guiGraphics, font, title, (width - listWidth) / 2, 24, listWidth, YzuiTheme.text(), false);
        if (backupFiles.isEmpty()) {
            YzuiTheme.wrapped(guiGraphics, font, Component.translatable("screen.youzaiworldcore.config_io.backup_list_empty"),
                    (width - listWidth) / 2, LIST_START_Y + 20, listWidth, 4, YzuiTheme.textMuted());
        }
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        int pageSize = Math.max(1, (height - 130) / (ENTRY_HEIGHT + 6));
        int pageCount = Math.max(1, (backupFiles.size() + pageSize - 1) / pageSize);
        if (pageCount > 1) {
            YzuiTheme.label(guiGraphics, font, Component.literal((page + 1) + " / " + pageCount),
                    (width - listWidth) / 2, height - 64, listWidth, YzuiTheme.textMuted(), true);
        }
        if (importInProgress) {
            guiGraphics.nextStratum();
            guiGraphics.fill(0, 0, width, height, YzuiTheme.scrim());
            YzuiTheme.card(guiGraphics, width / 2 - 180, height / 2 - 32, 360, 64);
            YzuiTheme.wrapped(guiGraphics, font, Component.translatable("screen.youzaiworldcore.config_io.importing_hint"),
                    width / 2 - 160, height / 2 - 8, 320, 3, YzuiTheme.text());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 背景由共用屏幕入口在内容变换之前绘制，避免重复模糊与叠加遮罩。
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (importInProgress) return true;
        if (keyEvent.key() == 256) { // ESC
            onBack();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 由 keyPressed 处理
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 回调 ==========

    private void onBack() {
        if (importInProgress) return;
        DebugLogger.debug(LOG_MODULE, "返回上级菜单");
        Minecraft.getInstance().setScreenAndShow(parentScreen);
    }

    private void onEntryClick(Path zipPath) {
        if (importInProgress) return;
        DebugLogger.info(LOG_MODULE, "用户选择导入: %s", zipPath);
        LOGGER.info("开始从 {} 导入配置", zipPath);

        importInProgress = true;

        ConfigIOManager.importConfig(zipPath, gameDir, (processed, total, phase) -> {
            // 进
        }).thenRun(() -> {
            importInProgress = false;
            Minecraft.getInstance().execute(() -> {
                DebugLogger.info(LOG_MODULE, "导入成功，显示重启弹窗");
                Minecraft.getInstance().setScreenAndShow(new ConfigImportSuccessScreen());
            });
        }).exceptionally(ex -> {
            importInProgress = false;
            Minecraft.getInstance().execute(() -> {
                String errorMsg = Component.translatable("message.youzaiworldcore.config_io.import_failed_generic").getString();
                // 若为 IO 异常可能包含更具体描述
                String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                if (detail != null && detail.contains("被占用")) {
                    errorMsg = Component.translatable("message.youzaiworldcore.config_io.import_failed_occupied").getString();
                } else if (detail != null && (detail.contains("ZIP 炸弹") || detail.contains("损坏"))) {
                    errorMsg = Component.translatable("message.youzaiworldcore.config_io.import_failed_corrupt").getString();
                } else if (detail != null && detail.contains("磁盘空间")) {
                    errorMsg = Component.translatable("message.youzaiworldcore.config_io.import_failed_disk").getString();
                }
                String finalMsg = errorMsg;
                // 显示错误 Toast
                var errorToast = new net.minecraft.client.gui.components.toasts.SystemToast(
                        new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(),
                        Component.translatable("message.youzaiworldcore.config_io.import_failed_generic"),
                        Component.literal("§e" + finalMsg)
                );
                Minecraft.getInstance().gui.toastManager().addToast(errorToast);
                // 刷新列表
                scanBackupFiles();
                rebuildEntryWidgets();
            });
            return null;
        });
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return importInProgress || super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() { onBack(); }
}
