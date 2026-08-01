package top.csituka.youzaiworldcore.client.laowumeme;

import java.nio.charset.StandardCharsets;

/**
 * 把导入音频的「真实文件名（可能含中文/空格）」编成合法的 Identifier path。
 * <p>
 * 背景：MC 的 Identifier 只允许 {@code [a-z0-9/._-]}，而用户导入的 .ogg 文件名常带中文/空格，
 * 直接塞进 Identifier 会抛 {@code IdentifierException}（曾实测导致单人触发即网络协议错误断连）。
 * 这里用 UTF-8 的 hex 编码（只含 0-9a-f，必然合法），mixin 侧再解码回真名去磁盘读文件。
 * </p>
 */
public final class LaowuSoundIdCodec {

    private LaowuSoundIdCodec() {
    }

    /** 真实文件名 -> 合法 hex 串（如 "MP3到OGG转换器" -> "4d5033e688b34f474730... "） */
    public static String encode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** hex 串 -> 真实文件名（与 encode 互逆） */
    public static String decode(String s) {
        int n = s.length() / 2;
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
