package top.csituka.youzaiworldcore.highlightitem;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 高亮物品功能配置单例（参考 HighLightItem，适配 YouzaiWorldCore 26.2）。
 * <p>
 * 配置文件位于 {@code config/youzaiworldcore/highlight_item.properties}。
 * 所有运行时状态（开关、颜色、比较模式、通知偏好）均持久化于此。
 */
public class Configurator {
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/HighlightItem");

    public static boolean TOGGLE;
    public static int COLOR;
    public static ItemComparator.Comparators COMPARATOR;
    public static NotificationPreference NOTIFICATION_PREFERENCE;

    private final Path currentDirectory;
    private final Properties properties = new Properties();
    private final String CONFIG = "highlight_item";

    public Configurator() throws IOException {
        currentDirectory = FabricLoader.getInstance().getConfigDir().resolve("youzaiworldcore");
        Files.createDirectories(currentDirectory);
        DebugLogger.info("HighlightItem", "配置目录: %s", currentDirectory);
        loadOrGenerateConfig();
    }

    /** 通知上下文：决定通知走 Toast / 聊天 / 叠加层 / 静默。 */
    public enum NotificationContext {
        NONE,
        ON_SCREEN,
        SENDING_COMMAND,
        IN_GAME
    }

    /** 通知偏好（与 Mod 设置中的选项对应）。 */
    public enum NotificationPreference implements OptionEnum {
        NONE,
        TOAST,
        CHAT,
        OVERLAY;

        @Override
        public int getId() {
            return ordinal();
        }

        @Override
        public String getKey() {
            return "youzaiworldcore.highlight.notif." + name().toLowerCase();
        }
    }

    public enum Config {
        COLOR("color", Colors.HighLightColor.DEFAULT.json().toString()),
        TOGGLE("toggle", "true"),
        COMPARATOR("comparator", ItemComparator.Comparators.ITEM_ONLY.name()),
        NOTIFICATION_PREFERENCE("notif-preference", NotificationPreference.NONE.name());

        private final String key;
        private final String def;

        Config(String key, String def) {
            this.key = key;
            this.def = def;
        }

        public String getKey() {
            return key;
        }

        public String getDefault() {
            return def;
        }
    }

    public void loadOrGenerateConfig() throws IOException {
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try (InputStream input = new FileInputStream(configPath.toString())) {
                properties.load(input);
            }
            DebugLogger.info("HighlightItem", "已加载配置文件: %s", configPath);
        } else {
            try (var stream = new FileOutputStream(configPath.toString())) {
                for (Config value : Config.values()) {
                    properties.setProperty(value.getKey(), value.getDefault());
                }
                properties.store(stream, null);
            }
            DebugLogger.info("HighlightItem", "已生成默认配置文件: %s", configPath);
        }

        TOGGLE = Boolean.parseBoolean(properties.getProperty(Config.TOGGLE.getKey(), Config.TOGGLE.getDefault()));

        float[] colors;
        if (properties.containsKey("color")) {
            var jsonColor = JsonParser.parseString(properties.getProperty(Config.COLOR.getKey())).getAsJsonObject();
            if (jsonColor.has("default")) {
                colors = Colors.HighLightColor.fromJson(jsonColor).getShaderColor();
            } else {
                colors = Colors.customFromJson(jsonColor);
            }
        } else {
            var highlightColor = Colors.HighLightColor.valueOf(
                    properties.getProperty("highlight-color", Colors.HighLightColor.DEFAULT.name()));
            colors = highlightColor.getShaderColor();
            removeFromConfig("highlight-color"); // 颜色系统已变更
            updateConfig(Config.COLOR, highlightColor.json().toString());
        }

        COLOR = ARGB.color(
                (int) (colors[3] * 255),
                (int) (colors[0] * 255),
                (int) (colors[1] * 255),
                (int) (colors[2] * 255));
        COMPARATOR = ItemComparator.Comparators.valueOf(
                properties.getProperty(Config.COMPARATOR.getKey(), Config.COMPARATOR.getDefault()));
        NOTIFICATION_PREFERENCE = NotificationPreference.valueOf(
                properties.getProperty(Config.NOTIFICATION_PREFERENCE.getKey(), Config.NOTIFICATION_PREFERENCE.getDefault()));

        DebugLogger.stateChange("HighlightItem", "config", "toggle", TOGGLE);
        DebugLogger.stateChange("HighlightItem", "config", "color", COLOR);
        DebugLogger.stateChange("HighlightItem", "config", "comparator", COMPARATOR.name());
        DebugLogger.stateChange("HighlightItem", "config", "notif", NOTIFICATION_PREFERENCE.name());
    }

    public Path getConfigPath() {
        return currentDirectory.resolve(CONFIG);
    }

    public void updateConfig(Config config, String value) throws IOException {
        try (var stream = new FileOutputStream(getConfigPath().toString())) {
            properties.setProperty(config.getKey(), value);
            properties.store(stream, null);
        }
    }

    public void removeFromConfig(String key) throws IOException {
        try (var stream = new FileOutputStream(getConfigPath().toString())) {
            properties.remove(key);
            properties.store(stream, null);
        }
    }

    /** 切换高亮总开关。 */
    public void updateToggle(LocalPlayer player, NotificationContext notification) {
        TOGGLE = !TOGGLE;
        try {
            updateConfig(Config.TOGGLE, "" + TOGGLE);
            notify(notification,
                    Component.translatable("youzaiworldcore.highlight.notification.update")
                            .append(Component.literal(" "))
                            .append(Component.translatable(TOGGLE
                                    ? "youzaiworldcore.highlight.activate"
                                    : "youzaiworldcore.highlight.deactivate"))
                            .withStyle(TOGGLE ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY),
                    player);
            DebugLogger.stateChange("HighlightItem", "toggle", "value", TOGGLE);
        } catch (IOException e) {
            notify(notification,
                    Component.translatable("youzaiworldcore.highlight.config.update.fail").withStyle(ChatFormatting.RED),
                    player);
            LOGGER.error("[HighlightItem] 无法更新配置文件", e);
        }
    }

    /** 在 7 种比较模式间循环切换。 */
    public void changeMode(LocalPlayer player, NotificationContext notification) {
        if (COMPARATOR.ordinal() == ItemComparator.Comparators.values().length - 1) {
            updateMode(ItemComparator.Comparators.ITEM_ONLY, player, notification);
        } else {
            for (ItemComparator.Comparators mode : ItemComparator.Comparators.values()) {
                if (mode.ordinal() == Math.min(COMPARATOR.ordinal() + 1,
                        ItemComparator.Comparators.values().length - 1)) {
                    updateMode(mode, player, notification);
                    break;
                }
            }
        }
    }

    public void updateMode(ItemComparator.Comparators mode, LocalPlayer player, NotificationContext notification) {
        COMPARATOR = mode;
        try {
            updateConfig(Config.COMPARATOR, mode.name());
            notify(notification,
                    Component.translatable("youzaiworldcore.highlight.comparator.change",
                                    Component.translatable(mode.translationKey())
                                            .append(Component.literal(" (" + mode.name() + ")")))
                            .withStyle(ChatFormatting.GRAY),
                    player);
            DebugLogger.stateChange("HighlightItem", "comparator", "value", mode.name());
        } catch (IOException e) {
            notify(notification,
                    Component.translatable("youzaiworldcore.highlight.config.update.fail").withStyle(ChatFormatting.RED),
                    player);
            LOGGER.error("[HighlightItem] 无法更新配置文件", e);
        }
    }

    /** 仅更新运行时颜色（不写文件），用于配置界面拖动滑块时的实时预览。 */
    public void setColorLive(float[] rgba) {
        COLOR = ARGB.color(
                (int) (rgba[3] * 255f),
                (int) (rgba[0] * 255f),
                (int) (rgba[1] * 255f),
                (int) (rgba[2] * 255f));
    }

    /** 更新颜色并持久化到配置文件。 */
    public void updateColor(float[] rgba, LocalPlayer player) {
        setColorLive(rgba);
        try {
            updateConfig(Config.COLOR, Colors.customToJson(rgba).toString());
            if (player != null) {
                player.sendSystemMessage(Component.translatable("youzaiworldcore.highlight.color").withStyle(ChatFormatting.GRAY));
            }
            DebugLogger.stateChange("HighlightItem", "color", "value", COLOR);
        } catch (IOException e) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("youzaiworldcore.highlight.config.update.fail")
                        .withStyle(ChatFormatting.RED));
            }
            LOGGER.error("[HighlightItem] 无法更新配置文件", e);
        }
    }

    public void updateNotificationPreference(NotificationPreference preference) {
        NOTIFICATION_PREFERENCE = preference;
        try {
            updateConfig(Config.NOTIFICATION_PREFERENCE, preference.name());
            DebugLogger.stateChange("HighlightItem", "notif", "value", preference.name());
        } catch (IOException e) {
            LOGGER.error("[HighlightItem] 无法更新通知偏好配置文件", e);
        }
    }

    /**
     * 切换/反馈消息统一以“唱片机”式顶部 Toast 弹出（与唱片机 now-playing 同属 Toast 家族、同屏幕位置）。
     * 仅当用户显式选择 CHAT / OVERLAY 偏好时，才改走聊天栏 / 叠加层。
     */
    private void notify(NotificationContext type, Component text, LocalPlayer player) {
        if (NOTIFICATION_PREFERENCE.equals(NotificationPreference.CHAT)) {
            if (player != null) {
                player.sendSystemMessage(text);
            }
            return;
        }
        if (NOTIFICATION_PREFERENCE.equals(NotificationPreference.OVERLAY)) {
            if (player != null) {
                player.sendOverlayMessage(text);
            }
            return;
        }
        // 默认 / TOAST / ON_SCREEN / SENDING_COMMAND / IN_GAME 均走 Toast（唱片机文本展示路径）
        notifyToast(text);
    }

    private void notifyToast(Component text) {
        notifyToast(Component.literal("YouzaiWorldCore"), text);
    }

    /** 弹出系统通知 Toast（简化版，不依赖对 SystemToast 字段的访问器）。 */
    private void notifyToast(Component title, Component desc) {
        Minecraft.getInstance().gui.toastManager()
                .addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, desc));
    }
}
