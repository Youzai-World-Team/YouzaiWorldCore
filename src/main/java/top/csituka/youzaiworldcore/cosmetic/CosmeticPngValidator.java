package top.csituka.youzaiworldcore.cosmetic;

/**
 * 自定义皮肤与披风 PNG 的轻量校验器。
 * <p>
 * 服务端不解码纹理，只校验 PNG 签名、IHDR 尺寸与文件大小；客户端收到数据后还会通过
 * {@code NativeImage.read(byte[])} 做第二次完整解码。
 * </p>
 */
public final class CosmeticPngValidator {

    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private CosmeticPngValidator() {
    }

    /**
     * 校验一份皮肤 PNG，允许 64x32 或 64x64。
     *
     * @param data 文件字节
     * @param maxFileBytes 允许的最大字节数
     * @return 校验结果
     */
    public static Validation validateSkin(byte[] data, int maxFileBytes) {
        return validate(data, maxFileBytes, false);
    }

    /**
     * 校验一份披风 PNG，披风固定为 64x32。
     *
     * @param data 文件字节
     * @param maxFileBytes 允许的最大字节数
     * @return 校验结果
     */
    public static Validation validateCloak(byte[] data, int maxFileBytes) {
        return validate(data, maxFileBytes, true);
    }

    private static Validation validate(byte[] data, int maxFileBytes, boolean cloak) {
        if (data == null || data.length == 0) {
            return Validation.invalid("文件为空");
        }
        if (data.length > maxFileBytes) {
            return Validation.invalid("文件大小 " + data.length + " 字节超过上限 " + maxFileBytes + " 字节");
        }
        if (data.length < 24) {
            return Validation.invalid("文件过短，无法包含完整 PNG 头");
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (data[i] != PNG_MAGIC[i]) {
                return Validation.invalid("PNG 文件签名不正确");
            }
        }
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            return Validation.invalid("PNG 首个数据块不是 IHDR");
        }

        int width = readInt(data, 16);
        int height = readInt(data, 20);
        if (width != 64 || (cloak ? height != 32 : height != 32 && height != 64)) {
            String expected = cloak ? "64x32" : "64x32 或 64x64";
            return Validation.invalid("图片尺寸必须为 " + expected + "，实际为 " + width + "x" + height);
        }
        return new Validation(true, "", width, height);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24
                | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8
                | data[offset + 3] & 0xFF;
    }

    /** PNG 校验结果。 */
    public record Validation(boolean valid, String reason, int width, int height) {

        private static Validation invalid(String reason) {
            return new Validation(false, reason, 0, 0);
        }
    }
}
