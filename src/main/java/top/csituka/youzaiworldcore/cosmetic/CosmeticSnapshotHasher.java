package top.csituka.youzaiworldcore.cosmetic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 为三个外观文件生成客户端与服务端一致的快照哈希。 */
public final class CosmeticSnapshotHasher {

    private static final byte[] EMPTY = new byte[0];

    private CosmeticSnapshotHasher() {
    }

    /**
     * 计算包含槽位边界的 SHA-256，避免不同槽位内容拼接后产生相同输入。
     */
    public static String hash(byte[] skinWide, byte[] skinSlim, byte[] cloak) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateSlot(digest, skinWide);
            updateSlot(digest, skinSlim);
            updateSlot(digest, cloak);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", e);
        }
    }

    private static void updateSlot(MessageDigest digest, byte[] value) {
        byte[] data = value == null ? EMPTY : value;
        digest.update((byte) (data.length >>> 24));
        digest.update((byte) (data.length >>> 16));
        digest.update((byte) (data.length >>> 8));
        digest.update((byte) data.length);
        digest.update(data);
    }
}
