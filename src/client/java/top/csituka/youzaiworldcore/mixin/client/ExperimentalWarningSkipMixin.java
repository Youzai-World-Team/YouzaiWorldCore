package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 当 {@code autoSkipExperimentalWarning} 启用时，自动跳过以下两个屏幕：
 * <ul>
 * <li><b>创建世界：</b>{@code confirmWorldCreation} 中"此世界使用实验性设置"的确认弹窗</li>
 * <li><b>打开世界：</b>{@code askForBackup} 中"使用实验性设置的世界不受支持"的备份确认屏</li>
 * </ul>
 * 跳过行为由 {@code experimentalWarningSkipAction} 控制：
 * {@code "skip"} = "我知道我在做什么"（不备份），{@code "backup"} = "创建备份并进入"。
 */
@Mixin(WorldOpenFlows.class)
public abstract class ExperimentalWarningSkipMixin {

    /**
     * 拦截 {@link WorldOpenFlows#confirmWorldCreation}。
     * <p>
     * 当世界创建的 lifecycle 为 experimental 且自动跳过开关启用时，
     * 直接调用 callback（即确认进入），不再弹出确认屏。
     */
    @Inject(method = "confirmWorldCreation", at = @At("HEAD"), cancellable = true)
    private static void skipConfirmWorldCreation(
            Minecraft minecraft,
            CreateWorldScreen createWorldScreen,
            Lifecycle lifecycle,
            Runnable runnable,
            boolean bypassWarnings,
            CallbackInfo ci) {
        if (!ClientExternalSettings.isAutoSkipExperimentalWarning()) {
            return;
        }
        // 仅在生命周期为 experimental（非 stable 且非 deprecated）时跳过
        if (lifecycle == Lifecycle.experimental()) {
            DebugLogger.info("ExperimentalWarningSkip",
                    "自动跳过创建世界的实验性设置确认屏");
            runnable.run();
            ci.cancel();
        }
    }

    /**
     * 拦截 {@link WorldOpenFlows#askForBackup}。
     * <p>
     * 当打开已有世界检测到实验性设置（isOldCustomized == false）且自动跳过开关启用时，
     * 根据 {@code experimentalWarningSkipAction} 执行对应操作：
     * <ul>
     *   <li>{@code "skip"}：直接调用 onProceed（等效于"我知道我在做什么"）</li>
     *   <li>{@code "backup"}：先创建备份，完成后调用 onProceed（等效于"创建备份并进入"）</li>
     * </ul>
     * 自定义旧世界（isOldCustomized == true）不受影响，仍正常弹出。
     */
    @Inject(method = "askForBackup", at = @At("HEAD"), cancellable = true)
    private void skipAskForBackup(
            LevelStorageSource.LevelStorageAccess access,
            boolean isOldCustomized,
            Runnable onProceed,
            Runnable onCancel,
            CallbackInfo ci) {
        if (!ClientExternalSettings.isAutoSkipExperimentalWarning()) {
            return;
        }
        // 仅跳过实验性设置导致的备份确认，不跳过旧自定义世界
        if (!isOldCustomized) {
            if (ClientExternalSettings.isExperimentalWarningSkipBackup()) {
                DebugLogger.info("ExperimentalWarningSkip",
                        "自动跳过实验性设置备份确认屏（创建备份并进入）");
                EditWorldScreen.conditionallyMakeBackupAndShowToast(true, access)
                        .thenAcceptAsync(v -> onProceed.run(), Minecraft.getInstance());
            } else {
                DebugLogger.info("ExperimentalWarningSkip",
                        "自动跳过实验性设置备份确认屏（不备份，直接进入）");
                onProceed.run();
            }
            ci.cancel();
        }
    }
}
