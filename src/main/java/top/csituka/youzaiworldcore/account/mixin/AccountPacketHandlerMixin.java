package top.csituka.youzaiworldcore.account.mixin;

import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ServerGamePacketListenerImpl Mixin — 拦截未认证玩家的操作
 *
 * 功能：
 * - 阻止移动（传送回原位）
 * - 阻止破坏方块、交互
 * - 阻止物品使用、背包操作
 * - 阻止聊天（/yzwc account 相关命令除外）
 * - 阻止攻击实体
 * - 隐身、无敌
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AccountPacketHandlerMixin {

    @Shadow
    public ServerPlayer player;

    @Unique
    private static final long TELEPORT_COOLDOWN_NS = 200_000_000L; // 200ms

    @Unique
    private static final Map<UUID, Long> lastTeleportTime = new ConcurrentHashMap<>();

    /**
     * 阻止未认证玩家的移动（传送回原位）
     */
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void onMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (!shouldBlock()) return;

        UUID playerUuid = player.getUUID();
        long now = System.nanoTime();
        Long lastTime = lastTeleportTime.get(playerUuid);

        if (lastTime == null || now >= lastTime + TELEPORT_COOLDOWN_NS) {
            // 传送回原位
            ((ServerGamePacketListenerImpl) (Object) this).teleport(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            );
            lastTeleportTime.put(playerUuid, now);
        }
        ci.cancel();
    }

    /**
     * 阻止未认证玩家的方块放置/交互
     */
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void onUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            sendAuthMessage();
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的物品使用
     */
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void onUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            sendAuthMessage();
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的方块动作（挖掘、丢弃等）
     */
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void onPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            sendAuthMessage();
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的背包操作
     */
    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void onContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的创造模式物品栏操作
     */
    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void onCreativeInventoryAction(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的手持物品切换
     */
    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void onSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的攻击实体
     */
    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void onAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            sendAuthMessage();
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的实体交互
     */
    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void onInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            sendAuthMessage();
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的聊天（允许 /yzwc 命令）
     */
    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void onChat(ServerboundChatPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            sendAuthMessage();
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的聊天命令（允许 /yzwc account 命令）
     */
    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void onChatCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            String command = packet.command();
            // 允许 yzwc 相关命令通过（不取消，让命令调度器处理）
            if (command.startsWith("yzwc")) {
                return;
            }
            sendAuthMessage();
            ci.cancel();
        }
    }

    /**
     * 阻止未认证玩家的揮動（swing）
     */
    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void onSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (shouldBlock()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean shouldBlock() {
        if (player == null) return false;
        return AuthPlayerHelper.shouldBlockActions(player);
    }

    @Unique
    private void sendAuthMessage() {
        PlayerAccount account = AuthPlayerHelper.getAccount(player);
        if (account != null && account.isRegistered()) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c请先使用 §6/yzwc account login <密码> §c登录！"));
        } else {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c请先使用 §6/yzwc account register <密码> <确认密码> §c注册！"));
        }
    }
}
