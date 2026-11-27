package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.accessor.CommandSuggestionPlayerLookup;

/** 让原版补全弹窗在计算宽度和屏幕位置时计入玩家头像。 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionPlayerHeadsMixin implements CommandSuggestionPlayerLookup {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private EditBox input;
    @Shadow
    @Nullable
    private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow
    private boolean currentParseIsCommand;

    @SuppressWarnings("null")
    @Override
    @Nullable
    public PlayerInfo youzaiworldcore$findSuggestionPlayer(String text) {
        if (!currentParseIsCommand || currentParse == null) {
            return null;
        }
        @SuppressWarnings("null")
        var context = currentParse.getContext();
        int cursor = input.getCursorPosition();
        if (cursor < context.getRange().getStart()) {
            return null;
        }
        var parent = context.findSuggestionContext(cursor).parent;
        if (parent.getChild(text) instanceof LiteralCommandNode<?>) {
            return null;
        }
        boolean selectsPlayers = parent.getChildren().stream()
                .anyMatch(node -> node instanceof ArgumentCommandNode<?, ?> argument
                        && (argument.getType() instanceof EntityArgument
                                || argument.getType() instanceof GameProfileArgument
                                || argument.getType() instanceof ScoreHolderArgument));
        var connection = minecraft.getConnection();
        return selectsPlayers && connection != null ? connection.getPlayerInfo(text) : null;
    }

    @WrapOperation(method = "showSuggestions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int youzaiworldcore$measurePlayerSuggestion(Font font, String text, Operation<Integer> original) {
        int width = original.call(font, text);
        return youzaiworldcore$findSuggestionPlayer(text) == null ? width : width + PLAYER_ICON_INSET;
    }
}
