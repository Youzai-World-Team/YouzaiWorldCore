package top.csituka.youzaiworldcore.client.accessor;

import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jspecify.annotations.Nullable;

/** 按当前补全参数查找真实的在线玩家条目。 */
public interface CommandSuggestionPlayerLookup {
    int PLAYER_ICON_INSET = PlayerFaceExtractor.SKIN_HEAD_WIDTH + 2;

    @Nullable
    PlayerInfo youzaiworldcore$findSuggestionPlayer(String text);
}
