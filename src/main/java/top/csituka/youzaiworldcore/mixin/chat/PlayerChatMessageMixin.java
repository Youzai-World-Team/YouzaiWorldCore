package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.SignedMessageBody;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.chat.ExtPlayerChatMessage;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 为 {@link PlayerChatMessage} 附加 {@code override} 等参数（仿 Styled Chat）。
 * <p>
 * 由于 {@code withUnsignedContent} / {@code filter} / {@code removeUnsignedContent}
 * 都会返回<b>新的</b> PlayerChatMessage，必须把附加参数同步拷贝到新对象，
 * 否则发送阶段的 {@code OutgoingChatMessage.create} 会读不到 override。
 * </p>
 */
@Mixin(PlayerChatMessage.class)
public abstract class PlayerChatMessageMixin implements ExtPlayerChatMessage {

    private static final String MODULE = "ChatMixin.PlayerChatMessage";

    @Shadow
    @Final
    private SignedMessageBody signedBody;

    @Unique
    private final Map<String, net.minecraft.network.chat.Component> youzaiworldcore_args = new HashMap<>();

    @Override
    public String youzaiworldcore_getOriginal() {
        return this.signedBody.content();
    }

    @Override
    public void youzaiworldcore_setArg(String name, net.minecraft.network.chat.Component arg) {
        this.youzaiworldcore_args.put(name, arg);
    }

    @Override
    public net.minecraft.network.chat.Component youzaiworldcore_getArg(String name) {
        return this.youzaiworldcore_args.getOrDefault(name, ChatFormatHelper.EMPTY_TEXT);
    }

    @Inject(method = "withUnsignedContent", at = @At("RETURN"))
    private void youzaiworldcore$copyData1(net.minecraft.network.chat.Component unsignedContent,
                                           CallbackInfoReturnable<PlayerChatMessage> cir) {
        this.youzaiworldcore$copyData(cir.getReturnValue());
    }

    @Inject(method = "filter(Lnet/minecraft/network/chat/FilterMask;)Lnet/minecraft/network/chat/PlayerChatMessage;",
            at = @At("RETURN"))
    private void youzaiworldcore$copyData2(FilterMask filterMask, CallbackInfoReturnable<PlayerChatMessage> cir) {
        this.youzaiworldcore$copyData(cir.getReturnValue());
    }

    @Inject(method = "removeUnsignedContent", at = @At("RETURN"))
    private void youzaiworldcore$copyData3(CallbackInfoReturnable<PlayerChatMessage> cir) {
        this.youzaiworldcore$copyData(cir.getReturnValue());
    }

    @Inject(method = "filter(Z)Lnet/minecraft/network/chat/PlayerChatMessage;", at = @At("RETURN"))
    private void youzaiworldcore$copyData4(boolean enabled, CallbackInfoReturnable<PlayerChatMessage> cir) {
        this.youzaiworldcore$copyData(cir.getReturnValue());
    }

    @Unique
    private void youzaiworldcore$copyData(PlayerChatMessage returnValue) {
        if (returnValue == null || returnValue == (Object) this) {
            return;
        }
        var mixin = (PlayerChatMessageMixin) (Object) returnValue;
        mixin.youzaiworldcore_args.putAll(this.youzaiworldcore_args);
        DebugLogger.trace(MODULE, "copied {} args to new PlayerChatMessage", this.youzaiworldcore_args.size());
    }
}
