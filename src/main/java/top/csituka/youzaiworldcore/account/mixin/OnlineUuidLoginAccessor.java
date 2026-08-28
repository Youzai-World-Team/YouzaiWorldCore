package top.csituka.youzaiworldcore.account.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 登录阶段替换原版已认证档案，使后续 ServerPlayer 使用 Mojang UUID。 */
@Mixin(ServerLoginPacketListenerImpl.class)
public interface OnlineUuidLoginAccessor {
    @Accessor("authenticatedProfile")
    void yzwc$setAuthenticatedProfile(GameProfile profile);
}
