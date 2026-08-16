package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.config.ChatFormatSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 死亡消息替换（仿 Styled Chat 的 ServerPlayerMixin）。
 * <p>
 * 重定向 {@code ServerPlayer.die} 中 {@code CombatTracker.getDeathMessage} 的调用，
 * 把原版死亡描述套上配置模板。
 * </p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerChatMixin {

    private static final String MODULE = "ChatMixin.ServerPlayer";

    @Redirect(
            method = "die",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()Lnet/minecraft/network/chat/Component;")
    )
    private Component youzaiworldcore$replaceDeathMessage(CombatTracker instance) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!ChatFormatSettings.isEnabled()) {
            return instance.getDeathMessage();
        }
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(MODULE, "replaceDeathMessage player={}", player.getName().getString());
        }
        return ChatFormatHelper.formatDeath(player, instance.getDeathMessage());
    }
}
