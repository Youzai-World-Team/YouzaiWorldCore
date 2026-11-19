/*
 * Adapted from Higher Chat (https://github.com/MDLC01/higher-chat-mc)
 * Original author: MDLC01
 * Original license: Unlicense (Public Domain)
 *
 * This file is part of YouzaiWorldCore.
 * Licensed under Apache-2.0.
 */
package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;
import top.csituka.youzaiworldcore.client.chat.ChatMessageRenderer;
import top.csituka.youzaiworldcore.client.chat.ChatRepeatTracker;
import top.csituka.youzaiworldcore.client.higherchat.SharedStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 保留聊天栏 HUD 避让，并将连续重复消息合并为带 [!n] 的单条显示。
 * 原版 allMessages 保留每条原文和签名，只有 trimmedMessages 显示队列被合并。
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    @Final
    private List<GuiMessage> allMessages;
    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;
    @Shadow
    private int chatScrollbarPos;
    @Unique
    private final ChatRepeatTracker youzaiworldcore$repeats = new ChatRepeatTracker();

    /** 本机模组提示也进入统一排版；服务端已排版的消息按结构识别。 */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true)
    private Component youzaiworldcore$formatClientMessage(Component message, Component original,
            MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
        return ChatMessageStyle.system(message, null);
    }

    @ModifyVariable(method = "addMessageToDisplayQueue", at = @At("HEAD"), argsOnly = true)
    private GuiMessage youzaiworldcore$collapseRepeatedMessage(GuiMessage message) {
        var result = youzaiworldcore$repeats.accept(message, ChatMessageRenderer.render(message.content()));
        if (result.replaced() != null) {
            int before = trimmedMessages.size();
            trimmedMessages.removeIf(line -> line.parent() == result.replaced());
            int removed = before - trimmedMessages.size();
            if (chatScrollbarPos > 0)
                chatScrollbarPos = Math.max(0, chatScrollbarPos - removed);
        }
        return result.displayed();
    }

    @Inject(method = "addMessageToQueue", at = @At("RETURN"))
    private void youzaiworldcore$retainRepeatHistory(GuiMessage message, CallbackInfo ci) {
        youzaiworldcore$repeats.retainHistory(allMessages);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void youzaiworldcore$clearRepeatState(boolean clearInputHistory, CallbackInfo ci) {
        youzaiworldcore$repeats.reset();
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void youzaiworldcore$beginRepeatReplay(CallbackInfo ci) {
        youzaiworldcore$repeats.beginReplay();
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("RETURN"))
    private void youzaiworldcore$endRepeatReplay(CallbackInfo ci) {
        youzaiworldcore$repeats.endReplay();
    }

    @SuppressWarnings("null")
    @Inject(method = "createDeletedMarker", at = @At("RETURN"), cancellable = true)
    private static void youzaiworldcore$formatDeletedMarker(GuiMessage original,
            CallbackInfoReturnable<GuiMessage> cir) {
        GuiMessage deleted = cir.getReturnValue();
        if (ChatMessageStyle.isFormatted(deleted.content()))
            return;
        cir.setReturnValue(new GuiMessage(deleted.addedTime(), ChatMessageStyle.game("Server", deleted.content()),
                deleted.signature(), deleted.source(), deleted.tag()));
    }

    @ModifyConstant(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", constant = @Constant(intValue = 40))
    private int adjustBottomMarginInRender(int bottomMargin) {
        return Math.max(bottomMargin, SharedStorage.getOptimalChatMargin());
    }
}
