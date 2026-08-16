package top.csituka.youzaiworldcore.highlightitem;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;

/**
 * 高亮物品功能配置单例（参考 HighLightItem，适配 YouzaiWorldCore 26.2）。
 * <p>
 * 存放位置：{@code yzwc/client/global_settings.json} 的 {@code highlight_item_module} 分节。
 * 所有运行时状态（开关、颜色、比较模式、通知偏好）均持久化于此。
 */
public class Configurator {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/HighlightItem");

    public static boolean TOGGLE;
    public static int COLOR;
    public static ItemComparator.Comparators COMPARATOR;
    public static NotificationPreference NOTIFICATION_PREFERENCE;

    public Configurator() throws IOException {
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

    /** 分节内的配置键 */
    public enum Config {
        COLOR("color"),
        TOGGLE("toggle"),
        COMPARATOR("comparator"),
        NOTIFICATION_PREFERENCE("notif_preference");

        private final String key;

        Config(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    // ===== 默认值 =====

    private static final boolean DEFAULT_TOGGLE = true;
    private static final ItemComparator.Comparators DEFAULT_COMPARATOR = ItemComparator.Comparators.ITEM_ONLY;
    private static final NotificationPreference DEFAULT_NOTIFICATION_PREFERENCE = NotificationPreference.NONE;

    /** @return 该模块的配置分节 */
    private static ConfigSection section() {
        return ClientGlobalSettings.section(ClientGlobalSettings.HIGHLIGHT_ITEM_MODULE);
    }

    /**
     * 从 {@code highlight_item_module} 分节加载；分节缺失时写入默认值。
     *
     * @throws IOException 保留签名以兼容调用方的异常处理；实际错误走配置崩溃流程
     */
    public void loadOrGenerateConfig() throws IOException {
        ConfigSection section = section();
        if (section.isEmpty()) {
            writeDefaults();
            DebugLogger.info("HighlightItem", "highlight_item_module 分节不存在，已写入默认配置");
            return;
        }

        TOGGLE = section.getBoolean(Config.TOGGLE.getKey(), DEFAULT_TOGGLE);

        JsonObject jsonColor = section.getObject(Config.COLOR.getKey());
        float[] colors;
        if (jsonColor == null) {
            colors = Colors.HighLightColor.DEFAULT.getShaderColor();
        } else if (jsonColor.has("default")) {
            colors = Colors.HighLightColor.fromJson(jsonColor).getShaderColor();
        } else {
            colors = Colors.customFromJson(jsonColor);
        }
        applyShaderColor(colors);

        COMPARATOR = section.getEnum(Config.COMPARATOR.getKey(), DEFAULT_COMPARATOR,
                ItemComparator.Comparators.class);
        NOTIFICATION_PREFERENCE = section.getEnum(Config.NOTIFICATION_PREFERENCE.getKey(),
                DEFAULT_NOTIFICATION_PREFERENCE, NotificationPreference.class);

        DebugLogger.stateChange("HighlightItem", "config", "toggle", TOGGLE);
        DebugLogger.stateChange("HighlightItem", "config", "color", COLOR);
        DebugLogger.stateChange("HighlightItem", "config", "comparator", COMPARATOR.name());
        DebugLogger.stateChange("HighlightItem", "config", "notif", NOTIFICATION_PREFERENCE.name());
    }

    /** 重置为默认值并写入 {@code highlight_item_module} 分节（首次安装 / 坏文件恢复用） */
    public static void writeDefaults() {
        TOGGLE = DEFAULT_TOGGLE;
        COMPARATOR = DEFAULT_COMPARATOR;
        NOTIFICATION_PREFERENCE = DEFAULT_NOTIFICATION_PREFERENCE;
        applyShaderColor(Colors.HighLightColor.DEFAULT.getShaderColor());

        ConfigSection section = section();
        section.set(Config.TOGGLE.getKey(), DEFAULT_TOGGLE);
        section.set(Config.COLOR.getKey(), Colors.HighLightColor.DEFAULT.json());
        section.set(Config.COMPARATOR.getKey(), DEFAULT_COMPARATOR);
        section.set(Config.NOTIFICATION_PREFERENCE.getKey(), DEFAULT_NOTIFICATION_PREFERENCE);
        ClientGlobalSettings.save();
    }

    /**
     * 写入单项配置并落盘。
     *
     * @param config 配置键
     * @param value  取值；{@link Config#COLOR} 传 JSON 文本，{@link Config#TOGGLE} 传 "true"/"false"
     * @throws IOException 保留签名以兼容调用方的异常处理；实际错误走配置崩溃流程
     */
    public void updateConfig(Config config, String value) throws IOException {
        ConfigSection section = section();
        switch (config) {
            case COLOR -> section.set(config.getKey(), JsonParser.parseString(value));
            case TOGGLE -> section.set(config.getKey(), Boolean.parseBoolean(value));
            default -> section.set(config.getKey(), value);
        }
        ClientGlobalSettings.save();
    }

    /** 把着色器色值（rgba，0~1）应用到运行时的 {@link #COLOR} */
    private static void applyShaderColor(float[] rgba) {
        COLOR = ARGB.color(
                (int) (rgba[3] * 255),
                (int) (rgba[0] * 255),
                (int) (rgba[1] * 255),
                (int) (rgba[2] * 255));
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

    @SuppressWarnings("null")
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
        applyShaderColor(rgba);
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
    @SuppressWarnings("null")
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
    @SuppressWarnings("null")
    private void notifyToast(Component title, Component desc) {
        Minecraft.getInstance().gui.toastManager()
                .addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, desc));
    }
}
