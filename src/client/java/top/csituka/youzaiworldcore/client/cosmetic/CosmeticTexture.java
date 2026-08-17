package top.csituka.youzaiworldcore.client.cosmetic;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/** 指向客户端动态注册纹理的外观资源。 */
public record CosmeticTexture(@SuppressWarnings("null") Identifier id, @SuppressWarnings("null") Identifier texturePath)
        implements ClientAsset.Texture {
}
