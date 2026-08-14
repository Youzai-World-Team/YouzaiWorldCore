package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.config.ChatFormatSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 加入服务器消息替换（仿 Styled Chat 的 PlayerManagerMixin）。
 * <p>
 * {@code placeNewPlayer} 里唯一的 {@code broadcastSystemMessage} 调用是「加入广播」，
 * 通过 {@code LEAVE_GAME} 统计判断首次加入，通过翻译参数个数判断是否改名，
 * 分别套用首次 / 普通 / 改名三种模板。
 * </p>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    private static final String MODULE = "ChatMixin.PlayerList";

    @Unique
    private ServerPlayer youzaiworldcore$tempPlayer = null;

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void youzaiworldcore$storePlayer(Connection connection, ServerPlayer player,
                                             CommonListenerCookie clientData, CallbackInfo ci) {
        this.youzaiworldcore$tempPlayer = player;
    }

    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void youzaiworldcore$clearStoredPlayer(Connection connection, ServerPlayer player,
                                                   CommonListenerCookie clientData, CallbackInfo ci) {
        this.youzaiworldcore$tempPlayer = null;
    }

    @ModifyArg(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private Component youzaiworldcore$replaceJoinMessage(Component text) {
        if (!ChatFormatSettings.isEnabled() || this.youzaiworldcore$tempPlayer == null) {
            return text;
        }
        ServerPlayer player = this.youzaiworldcore$tempPlayer;

        // 从未离开过服务器（LEAVE_GAME 统计为 0）→ 首次加入
        if (player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == 0) {
            DebugLogger.trace(MODULE, "replaceJoin first_time player={}", player.getName().getString());
            return ChatFormatHelper.formatJoinFirstTime(player);
        }

        Object[] args = ((TranslatableContents) text.getContents()).getArgs();
        if (args.length == 1) {
            DebugLogger.trace(MODULE, "replaceJoin normal player={}", player.getName().getString());
            return ChatFormatHelper.formatJoin(player);
        } else {
            DebugLogger.trace(MODULE, "replaceJoin renamed player={}", player.getName().getString());
            return ChatFormatHelper.formatJoinRenamed(player, (String) args[1]);
        }
    }
}
