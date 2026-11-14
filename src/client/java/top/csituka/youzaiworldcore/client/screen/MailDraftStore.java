package top.csituka.youzaiworldcore.client.screen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 本机草稿按服务器、玩家和邮件隔离，复用客户端唯一配置文件。 */
final class MailDraftStore {
    private MailDraftStore() { }
    static String key(UUID mailId) {
        var client = Minecraft.getInstance();
        if (client.player == null) return null;
        String server = client.getCurrentServer() != null ? client.getCurrentServer().ip
                : client.getSingleplayerServer() != null
                ? client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString() : null;
        if (server == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (server + "\n" + client.player.getUUID() + "\n" + mailId).getBytes(StandardCharsets.UTF_8));
            return "draft_" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String read(String key) {
        return key == null ? "" : ClientGlobalSettings.section(ClientGlobalSettings.MAIL_MODULE).getString(key, "");
    }
    static void save(String key, String snapshot) {
        if (key == null) throw new IllegalStateException("草稿未关联服务器与玩家");
        ClientGlobalSettings.section(ClientGlobalSettings.MAIL_MODULE).set(key, snapshot);
        ClientGlobalSettings.save();
        DebugLogger.info("MailDraft", "已保存本机草稿");
    }
    static void delete(String key) {
        if (key == null || read(key).isBlank()) return;
        ClientGlobalSettings.section(ClientGlobalSettings.MAIL_MODULE).remove(key);
        ClientGlobalSettings.save();
        DebugLogger.info("MailDraft", "已清除本机草稿");
    }
}
