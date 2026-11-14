package top.csituka.youzaiworldcore.client.skill;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.network.AttributeUpgradePayload;
import top.csituka.youzaiworldcore.network.AttributeUpgradeResultPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 单次加点等待服务端确认；请求序号与连接共同隔离超时、迟到和重连响应。 */
public final class AttributeUpgradeRequests {
    private static long sequence, pending, deadline;
    private static Object connection;
    private static Component status = Component.empty();

    private AttributeUpgradeRequests() { }

    public static boolean waiting() {
        if (connection != Minecraft.getInstance().getConnection()) {
            pending = 0; connection = null; status = Component.empty();
        } else if (pending != 0 && System.currentTimeMillis() >= deadline) {
            pending = 0;
            status = text("timeout");
        }
        return pending != 0;
    }

    public static Component status() { waiting(); return status; }

    public static void submit(String key) {
        if (waiting() || Minecraft.getInstance().getConnection() == null) return;
        connection = Minecraft.getInstance().getConnection();
        pending = ++sequence;
        deadline = System.currentTimeMillis() + 15000;
        status = text("waiting");
        ClientPlayNetworking.send(new AttributeUpgradePayload(key, pending));
        DebugLogger.info("AttributeUpgrade", "已提交加点请求 %d：%s", pending, key);
    }

    public static void receive(AttributeUpgradeResultPayload result, Object sourceConnection) {
        if (!waiting() || sourceConnection != connection || result.requestId() != pending) return;
        pending = 0;
        status = text(result.success() ? "success" : "rejected");
        DebugLogger.info("AttributeUpgrade", "加点结果 %d：%s", result.requestId(), result.success());
    }

    private static Component text(String key) {
        return Component.translatable("screen.youzaiworldcore.upgrade." + key);
    }
}
