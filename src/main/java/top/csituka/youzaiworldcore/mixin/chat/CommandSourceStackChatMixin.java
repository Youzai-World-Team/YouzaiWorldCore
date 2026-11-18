package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;

/** 命令成功、失败和管理员广播保留实际执行者，模组命令反馈标记发送模组。 */
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackChatMixin {
    @ModifyArg(method = {"sendSystemMessage", "sendSuccess", "sendFailure"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/CommandSource;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"), index = 0)
    private Component youzaiworldcore$formatCommandOutput(Component message) {
        return ChatMessageStyle.system(message, (CommandSourceStack) (Object) this);
    }

    @ModifyArg(method = "sendSystemMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"), index = 0)
    private Component youzaiworldcore$formatCommandOutputToPlayer(Component message) {
        return ChatMessageStyle.system(message, (CommandSourceStack) (Object) this);
    }

    @Redirect(method = "broadcastToAdmins", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent youzaiworldcore$replaceAdminOutput(String key, Object[] args) {
        if ("chat.type.admin".equals(key) && args.length == 2 && args[1] instanceof Component message) {
            return ChatMessageStyle.system(message, (CommandSourceStack) (Object) this).copy();
        }
        return Component.translatable(key, args);
    }
}
