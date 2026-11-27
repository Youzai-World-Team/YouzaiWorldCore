package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.config.ChatFormatSettings;

/** 只替换原版成就广播的文本，保留完成判定、奖励及游戏规则对广播的控制。 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsChatMixin {
        @SuppressWarnings("null")
        @Redirect(method = "lambda$award$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementType;createAnnouncement(Lnet/minecraft/advancements/AdvancementHolder;Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/network/chat/MutableComponent;"))
        private MutableComponent youzaiworldcore$formatAdvancement(AdvancementType type, AdvancementHolder holder,
                        ServerPlayer player) {
                return ChatFormatSettings.isEnabled() ? ChatFormatHelper.formatAdvancement(player, type, holder).copy()
                                : type.createAnnouncement(holder, player);
        }
}
