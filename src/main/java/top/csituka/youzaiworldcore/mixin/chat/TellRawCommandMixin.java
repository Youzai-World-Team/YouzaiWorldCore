package top.csituka.youzaiworldcore.mixin.chat;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;

/** /tellraw 保留 JSON 正文的文字与交互组件，并使用命令执行者作为游戏消息来源。 */
@Mixin(TellRawCommand.class)
public abstract class TellRawCommandMixin {
    @SuppressWarnings("null")
    @Redirect(method = "lambda$register$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/arguments/ComponentArgument;getResolvedComponent(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;"))
    private static Component youzaiworldcore$formatTellRawMessage(CommandContext<CommandSourceStack> context,
            String name, Entity target) throws CommandSyntaxException {
        Component message = ComponentArgument.getResolvedComponent(context, name, target);
        return ChatMessageStyle.game(context.getSource().getTextName(), message);
    }
}
