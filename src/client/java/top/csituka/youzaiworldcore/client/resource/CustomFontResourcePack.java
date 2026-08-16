package top.csituka.youzaiworldcore.client.resource;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 管理模组内置的自定义字体资源包。
 * <p>
 * 资源包位于 {@code resourcepacks/custom_font/}，通过 Fabric 内置资源包 API 注册；
 * 启用状态由 {@code yzwc/client/global_settings.json} 的
 * {@code core_module.custom_font_enabled} 控制。切换后同步原版资源包列表并触发资源重载。
 * </p>
 */
@SuppressWarnings("null")
public final class CustomFontResourcePack {

    private static final String MODULE = "CustomFontResourcePack";
    private static final Identifier PACK_IDENTIFIER =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "custom_font");
    private static final String PACK_ID = PACK_IDENTIFIER.toString();

    private static boolean registered;

    private CustomFontResourcePack() {
    }

    /**
     * 注册内置字体资源包，并在客户端首次资源加载完成后校准配置所要求的启用状态。
     */
    public static void register() {
        if (registered) {
            return;
        }

        DebugLogger.entering(MODULE, "register");
        var container = FabricLoader.getInstance().getModContainer(YouzaiworldCore.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("无法取得 YouzaiWorldCore 模组容器"));
        PackActivationType activationType = ClientExternalSettings.isCustomFontEnabled()
                ? PackActivationType.DEFAULT_ENABLED
                : PackActivationType.NORMAL;

        registered = ResourceLoader.registerBuiltinPack(
                PACK_IDENTIFIER,
                container,
                Component.translatable("resourcepack.youzaiworldcore.custom_font.name"),
                activationType);
        if (!registered) {
            DebugLogger.error(MODULE, "内置字体资源包注册失败：resourcepacks/custom_font");
            DebugLogger.exiting(MODULE, "register", "failed");
            return;
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                applySelection(client, ClientExternalSettings.isCustomFontEnabled()));
        DebugLogger.info(MODULE, "内置字体资源包已注册：id=%s, defaultEnabled=%s",
                PACK_ID, activationType.isEnabledByDefault());
        DebugLogger.exiting(MODULE, "register");
    }

    /**
     * 在运行时切换字体资源包；资源包列表发生变化时由原版选项系统保存并重载资源。
     *
     * @param enabled 是否启用自定义字体
     */
    public static void setEnabled(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> applySelection(client, enabled));
    }

    private static void applySelection(Minecraft client, boolean enabled) {
        PackRepository repository = client.getResourcePackRepository();
        if (!repository.isAvailable(PACK_ID)) {
            repository.reload();
        }
        if (!repository.isAvailable(PACK_ID)) {
            DebugLogger.error(MODULE, "字体资源包不可用，无法切换：%s", PACK_ID);
            return;
        }

        boolean selected = repository.getSelectedIds().contains(PACK_ID);
        if (selected == enabled) {
            DebugLogger.debug(MODULE, "字体资源包状态已同步：enabled=%s", enabled);
            return;
        }

        boolean changed = enabled ? repository.addPack(PACK_ID) : repository.removePack(PACK_ID);
        if (!changed) {
            DebugLogger.warn(MODULE, "字体资源包状态切换失败：enabled=%s, selected=%s", enabled, selected);
            return;
        }

        client.options.updateResourcePacks(repository);
        DebugLogger.stateChange(MODULE, PACK_ID, "enabled", selected, enabled);
    }
}
