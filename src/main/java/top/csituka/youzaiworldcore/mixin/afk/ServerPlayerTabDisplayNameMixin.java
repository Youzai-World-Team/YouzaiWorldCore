package top.csituka.youzaiworldcore.mixin.afk;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.afk.AfkManager;
import top.csituka.youzaiworldcore.config.AfkConfig;
import top.csituka.youzaiworldcore.title.TitleManager;

/**
 * 为 AFK 玩家的 Tab 列表显示名添加 {@code [AFK]} 前缀。
 * <p>
 * 26.2 的 {@code ServerPlayer} 已移除 {@code setTabListDisplayName()}（仅剩
 * getter），服务端无法直接修改显示名。故在此拦截
 * {@code getTabListDisplayName()}：AFK 时返回
 * {@code "§7[AFK] " + 原显示名}。状态变化时由 {@link AfkManager} 广播
 * {@code ClientboundPlayerInfoUpdatePacket(UPDATE_DISPLAY_NAME, player)}
 * （构造时内部读取本方法），客户端 Tab 列表随即刷新。
 * </p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabDisplayNameMixin {

    @Inject(
            method = "getTabListDisplayName()Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void youzaiworldcore$appendAfkPrefix(CallbackInfoReturnable<Component> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Component original = cir.getReturnValue();
        if (original == null) original = self.getDisplayName();
        Component result = original;
        if (AfkConfig.isTabPrefixEnabled() && AfkManager.isAfk(self)) {
            result = Component.literal("§7[AFK] ").append(result);
        }
        Component title = TitleManager.getEquippedComponent(self);
        if (!title.getString().isBlank()) {
            result = title.copy().append(Component.literal(" ")).append(result);
        }
        if (result != original) cir.setReturnValue(result);
    }
}
