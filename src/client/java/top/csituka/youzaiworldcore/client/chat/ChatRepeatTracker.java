package top.csituka.youzaiworldcore.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;

import java.util.Objects;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** 只合并显示队列中的连续重复消息，原始历史及每条消息的签名始终保留。 */
@SuppressWarnings("null")
public final class ChatRepeatTracker {
    private GuiMessage lastOriginal;
    private GuiMessage lastDisplayed;
    private Component lastContent;
    private int count;
    // 只保留原版历史仍持有的对象，保存被历史上限裁掉的同组消息计数。
    private final IdentityHashMap<GuiMessage, Integer> ordinals = new IdentityHashMap<>();
    private boolean firstReplayMessage;

    public record Result(GuiMessage displayed, GuiMessage replaced) {
    }

    public Result accept(GuiMessage message) {
        return accept(message, message.content());
    }

    /** 原始对象用于分组及历史序号，配色副本只用于显示，避免换色影响计数。 */
    public Result accept(GuiMessage message, Component styledContent) {
        // 发送确认是独立反馈，既不增加私信计数，也不打断正在累计的正文组。
        if (ChatMessageStyle.isPrivateReceipt(message.content())) {
            return new Result(displayed(message, styledContent), null);
        }
        boolean repeated = lastOriginal != null
                && lastOriginal.source() == message.source()
                && Objects.equals(lastOriginal.tag(), message.tag())
                && lastContent.equals(message.content());
        GuiMessage replaced = repeated ? lastDisplayed : null;
        count = firstReplayMessage ? ordinals.getOrDefault(message, 1)
                : repeated ? (int) Math.min((long) count + 1, Integer.MAX_VALUE) : 1;
        firstReplayMessage = false;
        ordinals.put(message, count);
        Component display = ChatMessageStyle.repeated(styledContent, count);
        GuiMessage shown = displayed(message, display);
        lastOriginal = message;
        lastContent = message.content().copy();
        lastDisplayed = shown;
        if (repeated) ChatMessageStyle.logRepeat(count);
        return new Result(shown, replaced);
    }

    private static GuiMessage displayed(GuiMessage message, Component content) {
        return content == message.content() ? message : new GuiMessage(
                message.addedTime(), content, message.signature(), message.source(), message.tag());
    }

    public void reset() {
        clearDisplayState();
        ordinals.clear();
    }

    /** 窗口重排、可见过滤或删除标记触发重建时，按原始消息重新分组。 */
    public void beginReplay() {
        clearDisplayState();
        firstReplayMessage = true;
    }

    public void endReplay() {
        firstReplayMessage = false;
    }

    public void retainHistory(List<GuiMessage> history) {
        var retained = Collections.newSetFromMap(new IdentityHashMap<GuiMessage, Boolean>());
        retained.addAll(history);
        ordinals.keySet().removeIf(message -> !retained.contains(message));
    }

    private void clearDisplayState() {
        lastOriginal = null;
        lastDisplayed = null;
        lastContent = null;
        count = 0;
        firstReplayMessage = false;
    }
}
