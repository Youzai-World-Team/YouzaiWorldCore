package top.csituka.youzaiworldcore.account.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 密码哈希工具
 * 使用 PBKDF2WithHmacSHA256（Java 内置，无需额外依赖）
 *
 * 格式：algorithm:iterations:base64(salt):base64(hash)
 * 示例：PBKDF2:600000:abc123...:def456...
 */
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000; // OWASP 推荐值 (2023+)
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 对密码进行哈希
     */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);

        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();

            return formatHash(salt, hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("密码哈希失败", e);
        }
    }

    /**
     * 验证密码
     */
    public static boolean verify(String password, String storedHash) {
        try {
            HashComponents components = parseHash(storedHash);
            if (components == null) return false;

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), components.salt, components.iterations, components.keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] computedHash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();

            return constantTimeEquals(computedHash, components.hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return false;
        }
    }

    private static String formatHash(byte[] salt, byte[] hash) {
        return String.format("PBKDF2:%d:%s:%s",
                ITERATIONS,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash));
    }

    private static HashComponents parseHash(String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 4 || !"PBKDF2".equals(parts[0])) return null;

            return new HashComponents(
                    Integer.parseInt(parts[1]),
                    Base64.getDecoder().decode(parts[2]),
                    Base64.getDecoder().decode(parts[3]),
                    KEY_LENGTH
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 常量时间比较，防止时序攻击
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private record HashComponents(int iterations, byte[] salt, byte[] hash, int keyLength) {}
}
