package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.render.PingDisplayRender;

/**
 * 在实体名字牌（nametag）上追加显示延迟（ping）。
 * <p>
 * 在 {@code EntityRenderer.extractRenderState} 的 TAIL 注入，
 * 若实体为 AbstractClientPlayer 且名字牌非空，
 * 则在 {@code state.nameTag} 末尾追加形如 " (45ms)" 的延迟文本。
 * </p>
 * <p>
 * ping 段的组件构造与缓存放在 {@link PingDisplayRender}——本注入点每帧每玩家都会执行，
 * 缓存能省下字符串拼接、组件分配与捕获型 lambda 三项开销。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    /**
     * 在 {@code extractRenderState} 尾部注入，若玩家头顶名字牌非空则在末尾追加 ping 文字。
     *
     * <p>26.2 签名已在真实 jar 中验证：</p>
     * <pre>
     *     extractRenderState(Entity, EntityRenderState, float partialTicks)
     * </pre>
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL"))
    private <T extends Entity, S extends EntityRenderState> void yzwc$appendPingToNametag(
            T entity, S state, float partialTicks, CallbackInfo ci) {

        if (!(entity instanceof AbstractClientPlayer player)) return;
        if (state.nameTag == null) return;

        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;

        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        if (info == null) return;

        int ping = info.getLatency();

        // 在原始名字牌后追加 " (ping)"；名字文本和 ping 未变时复用整条组件。
        state.nameTag = PingDisplayRender.getNameTagComponent(player.getUUID(), state.nameTag, ping);
    }
}
