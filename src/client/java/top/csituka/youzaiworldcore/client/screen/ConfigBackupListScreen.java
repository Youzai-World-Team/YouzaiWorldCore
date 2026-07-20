package top.csituka.youzaiworldcore.client.screen;

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
    private static final int ENTRY_HEIGHT = 22;
    private static final int LIST_WIDTH = 300;

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

        // 返回按钮
        int backBtnX = (this.width - 100) / 2;
        this.backButton = new TransparentButton(
                backBtnX, this.height - 40, 100, 20,
                Component.translatable("screen.youzaiworldcore.config_io.backup_list_back"),
                this::onBack
        );
        this.backButton.setTextColor(0xFFFFFFFF);
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
        // 清除旧的按钮
        for (TransparentButton btn : entryButtons) {
            removeWidget(btn);
        }
        entryButtons.clear();

        int listX = (this.width - LIST_WIDTH) / 2;
        int y = LIST_START_Y;

        for (File file : backupFiles) {
            String display = file.getName();
            // 添加时间信息
            long time = file.lastModified();
            if (time > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                display = sdf.format(new java.util.Date(time)) + "  " + file.getName();
            }

            TransparentButton btn = new TransparentButton(
                    listX, y, LIST_WIDTH, ENTRY_HEIGHT,
                    Component.literal(display),
                    () -> onEntryClick(file.toPath())
            );
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundVisible(true);
            entryButtons.add(btn);
            addRenderableWidget(btn);
            y += ENTRY_HEIGHT + 2;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 背景
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        int cx = this.width / 2;

        // 标题
        String title = Component.translatable("screen.youzaiworldcore.config_io.backup_list_title").getString();
        int titleWidth = this.font.width(title);
        guiGraphics.text(this.font, title, cx - titleWidth / 2, 20, 0xFFFFFFFF, false);

        if (backupFiles.isEmpty()) {
            // 空状态
            String emptyText = Component.translatable("screen.youzaiworldcore.config_io.backup_list_empty").getString();
            int emptyWidth = this.font.width(emptyText);
            guiGraphics.text(this.font, emptyText, cx - emptyWidth / 2, LIST_START_Y, 0x80FFFFFF, false);
        } else {
            // 列表
            super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 返回按钮
        if (backButton != null) {
            backButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 导入进行中提示
        if (importInProgress) {
            String progressText = Component.translatable("screen.youzaiworldcore.config_io.importing_hint").getString();
            int pw = this.font.width(progressText);
            guiGraphics.text(this.font, progressText, cx - pw / 2, this.height / 2, 0xFFFFAA00, false);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
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
}
