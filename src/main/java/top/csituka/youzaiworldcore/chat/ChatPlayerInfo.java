package top.csituka.youzaiworldcore.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.skill.PlayerLevelStorage;
import top.csituka.youzaiworldcore.title.TitleManager;

/** 服务端生成聊天玩家名和资料悬停卡，使用发送消息时的内存数据，不查询外部服务。 */
@SuppressWarnings("null")
public final class ChatPlayerInfo {
    private ChatPlayerInfo() {
    }

    /** 玩家名携带资料悬停；后续纯色或渐变处理会保留该交互。 */
    public static Component name(ServerPlayer player) {
        return Component.literal(player.getScoreboardName())
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(tooltip(player))));
    }

    /** 玩家命令来源附加资料；控制台和非玩家实体继续使用原来的来源名称。 */
    public static Component sourceName(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? Component.literal(source.getTextName()) : name(player);
    }

    /** 资料按客户端语言翻译，称号保留原色；未初始化的等级数据使用一级、零经验。 */
    public static Component tooltip(ServerPlayer player) {
        var data = PlayerLevelStorage.get(player.getUUID());
        int level = data == null ? 1 : data.getLevel();
        int currentExp = data == null ? 0 : data.getCurrentLevelExp();
        int neededExp = data == null ? AdventureLevelManager.expForNextLevel(level) : data.getExpForNextLevel();
        Component title = TitleManager.getEquippedComponent(player);
        if (title.getString().isBlank()) {
            title = Component.translatableWithFallback("chat.youzaiworldcore.hover.no_title", "未佩戴");
        }
        return Component.empty()
                .append(Component.translatableWithFallback("chat.youzaiworldcore.hover.player", "玩家：%s", player.getScoreboardName()))
                .append("\n")
                .append(Component.translatableWithFallback("chat.youzaiworldcore.hover.level", "冒险等级：Lv.%s", level))
                .append("\n")
                .append(Component.translatableWithFallback("chat.youzaiworldcore.hover.experience", "升级经验：%s / %s", currentExp, neededExp))
                .append("\n")
                .append(Component.translatableWithFallback("chat.youzaiworldcore.hover.title", "佩戴称号：%s", title.copy()))
                .append("\n")
                .append(Component.translatableWithFallback("chat.youzaiworldcore.hover.game_mode", "游戏模式：%s",
                        player.gameMode().getShortDisplayName()))
                .withStyle(style -> style.withColor(0xF3EFEF));
    }
}
