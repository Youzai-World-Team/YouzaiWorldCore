package top.csituka.youzaiworldcore.client.laowumeme;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * 启用/禁用状态持久化：以 Properties 格式写到
 * {@code config/youzaiworldcore/laowu_meme/enabled.properties}。
 * <p>
 * Key 用 {@code "builtin:<soundName>"} 或 {@code "imported:<真名>"}（不用 hex，保持配置文件可读）。
 * 缺省视为启用（只记录显式禁用的条目，文件小、可读性高）。
 * </p>
 */
public final class LaowuEnabledConfig {

    private static final String FILE_NAME = "enabled.properties";

    private LaowuEnabledConfig() {
    }

    public static File getFile() {
        return new File(LaowuAudioPool.getConfigDir(), FILE_NAME);
    }

    public static void load(Map<String, Boolean> enabled) {
        File f = getFile();
        if (!f.isFile()) {
            return;
        }
        Properties p = new Properties();
        try (FileReader r = new FileReader(f)) {
            p.load(r);
        } catch (IOException ignored) {
            return;
        }
        for (String key : p.stringPropertyNames()) {
            String v = p.getProperty(key);
            enabled.put(key, !"false".equalsIgnoreCase(v) && !"0".equals(v));
        }
    }

    public static void save(Map<String, Boolean> enabled) {
        File f = getFile();
        try {
            f.getParentFile().mkdirs();
            Properties p = new Properties();
            // 只保存显式 disabled 的（默认 true，文件小、可读性高）
            for (Map.Entry<String, Boolean> e : enabled.entrySet()) {
                if (Boolean.FALSE.equals(e.getValue())) {
                    p.setProperty(e.getKey(), "false");
                }
            }
            try (FileWriter w = new FileWriter(f)) {
                p.store(w, "laowu_meme enabled audio map (仅记录禁用的；缺省=启用)");
            }
        } catch (IOException ignored) {
        }
    }
}
