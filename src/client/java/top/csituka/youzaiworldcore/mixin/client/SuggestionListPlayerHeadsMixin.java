package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.accessor.CommandSuggestionPlayerLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 为当前列表中的玩家行绘制皮肤头像，沿用原版滚动与点击区域。 */
@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class SuggestionListPlayerHeadsMixin {
    @Unique
    private static final int PLAYER_ICON_SIZE = PlayerFaceExtractor.SKIN_HEAD_WIDTH;
    @Unique
    private Map<String, PlayerInfo> youzaiworldcore$players = Map.of();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void youzaiworldcore$collectPlayers(CommandSuggestions owner, int x, int y, int width,
            List<Suggestion> suggestions, boolean narrate, CallbackInfo ci) {
        var lookup = (CommandSuggestionPlayerLookup) owner;
        var players = new HashMap<String, PlayerInfo>();
        for (Suggestion suggestion : suggestions) {
            String text = suggestion.getText();
            var player = lookup.youzaiworldcore$findSuggestionPlayer(text);
            if (player != null) {
                players.put(text, player);
            }
        }
        youzaiworldcore$players = players;
    }

    @SuppressWarnings("null")
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void youzaiworldcore$drawPlayerSuggestion(GuiGraphicsExtractor graphics, Font font, String text,
            int x, int y, int color, Operation<Void> original) {
        var player = youzaiworldcore$players.get(text);
        if (player != null) {
            PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, y, PLAYER_ICON_SIZE);
            x += CommandSuggestionPlayerLookup.PLAYER_ICON_INSET;
        }
        original.call(graphics, font, text, x, y, color);
    }
}
