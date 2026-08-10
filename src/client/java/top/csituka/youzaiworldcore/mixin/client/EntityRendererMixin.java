package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.render.PingDisplayRender;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 在实体名字牌（nametag）上追加显示延迟（ping）。
 * <p>
 * 在 {@code EntityRenderer.extractRenderState} 的 TAIL 注入，
 * 若实体为 AbstractClientPlayer 且名字牌非空，
 * 则在 {@code state.nameTag} 末尾追加形如 " (45ms)" 的延迟文本。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    /** 缓存的括号组件，避免每帧每实体重新分配。 */
    private static final Component PING_PREFIX = Component.literal(" (").withStyle(style -> style.withColor(0xAAAAAA));
    private static final Component PING_SUFFIX = Component.literal(")").withStyle(style -> style.withColor(0xAAAAAA));

    /**
     * 每个玩家的「已着色 ping 组件」缓存。
     * <p>
     * 本注入点每帧、每个可见玩家各跑一次。原实现其中三项开销是纯浪费：
     * {@code getPingText()} 拼出的 {@code "45ms"} 字符串、包装它的
     * {@code Component.literal}、以及 {@code withStyle(style -> ...)} 里那个
     * <b>捕获了 {@code color} 的 lambda 实例</b>（捕获型 lambda 每次调用都要新建对象）。
     * 而 ping 值大约每秒才变一次，这三者在两次变化之间完全可以复用。
     * </p>
     * <p>
     * <b>为什么只缓存 ping 组件、不缓存整条名字牌：</b>
     * {@code state.nameTag} 由原版每帧通过 {@code getDisplayName()} 重新构造
     * （队伍前缀、称号等都在其中），对象身份逐帧变化，整条缓存永远命不中，
     * 反而会白白多出一次查表与一次装配。因此只缓存真正稳定的那一段。
     * </p>
     */
    @Unique
    private static final Map<UUID, PingComponent> YZWC$PING_CACHE = new HashMap<>();

    /** 缓存条目：ping 数值 + 对应的已着色组件。 */
    @Unique
    private record PingComponent(int ping, Component component) {
    }

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

        UUID uuid = player.getUUID();
        PlayerInfo info = connection.getPlayerInfo(uuid);
        if (info == null) return;

        int ping = info.getLatency();

        // 在原始名字牌后追加 " (ping)"，括号与 ping 段均复用缓存组件
        state.nameTag = Component.literal(state.nameTag.getString())
                .append(PING_PREFIX)
                .append(yzwc$pingComponent(uuid, ping))
                .append(PING_SUFFIX);
    }

    /**
     * 取得该玩家当前 ping 对应的已着色组件，ping 未变时复用上次结果。
     */
    @Unique
    private static Component yzwc$pingComponent(UUID uuid, int ping) {
        PingComponent cached = YZWC$PING_CACHE.get(uuid);
        if (cached != null && cached.ping() == ping) {
            return cached.component();
        }

        // 玩家进出世界会不断引入新 UUID，加一道上限避免 Map 无界增长；
        // 清空后下一帧自然重建，无功能影响。
        if (YZWC$PING_CACHE.size() > 256) {
            YZWC$PING_CACHE.clear();
        }

        int color = PingDisplayRender.getPingColor(ping);
        // withColor(int) 直接写入 Style，避免 withStyle(UnaryOperator) 的捕获型 lambda 分配
        Component built = Component.literal(PingDisplayRender.getPingText(ping)).withColor(color);
        YZWC$PING_CACHE.put(uuid, new PingComponent(ping, built));
        return built;
    }
}
