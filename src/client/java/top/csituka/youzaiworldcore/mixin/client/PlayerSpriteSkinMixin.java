package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.cosmetic.CosmeticClientManager;

import java.util.function.Supplier;

/** 让原版行内玩家头像使用客户端已同步的自定义皮肤。 */
@Mixin(targets = "net.minecraft.client.gui.font.PlayerGlyphProvider$1")
public abstract class PlayerSpriteSkinMixin {
    @SuppressWarnings("null")
    @WrapOperation(method = "load(Lnet/minecraft/network/chat/FontDescription$PlayerSprite;)Lnet/minecraft/client/gui/GlyphSource;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PlayerSkinRenderCache;createLookup(Lnet/minecraft/world/item/component/ResolvableProfile;)Ljava/util/function/Supplier;"))
    private Supplier<PlayerSkinRenderCache.RenderInfo> youzaiworldcore$useCosmeticSkin(PlayerSkinRenderCache cache,
            ResolvableProfile profile, Operation<Supplier<PlayerSkinRenderCache.RenderInfo>> original) {
        var vanilla = original.call(cache, profile);
        if (!profile.skinPatch().equals(PlayerSkin.Patch.EMPTY)) {
            return vanilla;
        }
        var owner = profile.partialProfile().id();
        var cached = new PlayerSkinRenderCache.RenderInfo[1];
        return () -> {
            var base = vanilla.get();
            var skin = CosmeticClientManager.apply(owner, base.playerSkin());
            if (skin == base.playerSkin()) {
                cached[0] = null;
                return base;
            }
            // 外观重新同步时纹理可能沿用同一标识，换肤对象后重新取得纹理视图。
            if (cached[0] == null || cached[0].playerSkin() != skin) {
                cached[0] = cache.new RenderInfo(base.gameProfile(), skin, PlayerSkin.Patch.EMPTY);
            }
            return cached[0];
        };
    }
}
