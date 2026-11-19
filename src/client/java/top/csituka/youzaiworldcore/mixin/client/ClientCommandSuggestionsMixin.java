package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.impl.command.client.ClientCommandInternals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.command.ChatStyleClientCommand;

/** 补齐同名 /yzwc 根下的聊天配色补全节点，覆盖单人游戏及远程服务端命令树。 */
@Mixin(value = ClientCommandInternals.class, remap = false)
public abstract class ClientCommandSuggestionsMixin {
    @Inject(method = "addCommands", at = @At("RETURN"), remap = false)
    private static void youzaiworldcore$restoreChatStyleSuggestions(
            CommandDispatcher<FabricClientCommandSource> dispatcher, FabricClientCommandSource source, CallbackInfo ci) {
        ChatStyleClientCommand.addCompletionNodes(dispatcher);
    }
}
