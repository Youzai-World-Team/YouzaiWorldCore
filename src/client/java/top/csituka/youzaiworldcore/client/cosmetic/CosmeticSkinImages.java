package top.csituka.youzaiworldcore.client.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;

/** 预览与实际外观共用的旧版皮肤布局处理，保留现代自定义 PNG 的透明效果。 */
public final class CosmeticSkinImages {
    private CosmeticSkinImages() {
    }

    /** 将 64×32 旧版皮肤展开为 64×64；成功时接管并按需释放传入图像。 */
    public static NativeImage prepare(NativeImage image) {
        if (image.getHeight() != 32) return image;
        NativeImage expanded = new NativeImage(64, 64, true);
        try {
            expanded.copyFrom(image);
            expanded.fillRect(0, 32, 64, 32, 0);
            expanded.copyRect(4, 16, 16, 32, 4, 4, true, false);
            expanded.copyRect(8, 16, 16, 32, 4, 4, true, false);
            expanded.copyRect(0, 20, 24, 32, 4, 12, true, false);
            expanded.copyRect(4, 20, 16, 32, 4, 12, true, false);
            expanded.copyRect(8, 20, 8, 32, 4, 12, true, false);
            expanded.copyRect(12, 20, 16, 32, 4, 12, true, false);
            expanded.copyRect(44, 16, -8, 32, 4, 4, true, false);
            expanded.copyRect(48, 16, -8, 32, 4, 4, true, false);
            expanded.copyRect(40, 20, 0, 32, 4, 12, true, false);
            expanded.copyRect(44, 20, -8, 32, 4, 12, true, false);
            expanded.copyRect(48, 20, -16, 32, 4, 12, true, false);
            expanded.copyRect(52, 20, -8, 32, 4, 12, true, false);
            opaque(expanded, 0, 0, 32, 16);
            clearOpaqueOverlay(expanded);
            opaque(expanded, 0, 16, 64, 32);
            opaque(expanded, 16, 48, 48, 64);
            image.close();
            return expanded;
        } catch (RuntimeException e) {
            expanded.close();
            throw e;
        }
    }

    private static void clearOpaqueOverlay(NativeImage image) {
        for (int x = 32; x < 64; x++) {
            for (int y = 0; y < 32; y++) {
                if ((image.getPixel(x, y) >>> 24) < 128) return;
            }
        }
        for (int x = 32; x < 64; x++) {
            for (int y = 0; y < 32; y++) {
                image.setPixel(x, y, image.getPixel(x, y) & 0xFFFFFF);
            }
        }
    }

    private static void opaque(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
            }
        }
    }
}
