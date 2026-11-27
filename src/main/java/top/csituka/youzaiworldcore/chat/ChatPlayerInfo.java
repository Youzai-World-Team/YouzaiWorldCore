package top.csituka.youzaiworldcore.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ResolvableProfile;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.skill.PlayerLevelStorage;
import top.csituka.youzaiworldcore.title.TitleManager;

import java.util.function.UnaryOperator;

/** 服务端生成聊天玩家名、皮肤头像和发送时的资料悬停卡。 */
@SuppressWarnings("null")
public final class ChatPlayerInfo {
    private ChatPlayerInfo() {
    }

    /** 头像与名称共用资料悬停，头像以空文本回退，保留消息的纯文本内容。 */
    public static Component name(ServerPlayer player) {
        var profile = ResolvableProfile.createResolved(player.getGameProfile());
        Component head = Component.object(new PlayerSprite(profile, true), Component.empty())
                .withStyle(style -> style.withColor(0xFFFFFF));
        return Component.empty()
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(tooltip(player))))
                .append(head)
                .append(player.getScoreboardName());
    }

    /** 名字换色时只处理头像右侧的文本，也适用于普通文本来源。 */
    public static Component colorName(Component name, UnaryOperator<Component> colorText) {
        var parts = name.getSiblings();
        if (parts.size() == 2
                && name.getContents().equals(Component.empty().getContents())
                && parts.getFirst().getContents() instanceof ObjectContents object
                && object.contents() instanceof PlayerSprite) {
            return MutableComponent.create(name.getContents()).setStyle(name.getStyle())
                    .append(parts.getFirst())
                    .append(colorText.apply(parts.getLast()));
        }
        return colorText.apply(name);
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
