package top.csituka.youzaiworldcore.client.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 工具信息 HUD 叠加层。
 * <p>
 * 在动作栏（action bar）显示手持特殊物品的附加信息：
 * <ul>
 * <li><b>时钟</b>：当前游戏时间和天数</li>
 * <li><b>指南针</b>：当前面对方向（北/东/南/西等）</li>
 * <li><b>追溯指针</b>：上次死亡坐标</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("null")
public class ToolInfoOverlay {

    private static final String MODULE = "ToolInfoOverlay";
    private static final String[] DIRECTIONS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

    private static boolean registered = false;
    private static int tickCounter = 0;

    private ToolInfoOverlay() {
    }

    /**
     * 注册客户端 Tick 回调。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(ToolInfoOverlay::onClientTick);
        DebugLogger.info(MODULE, "工具信息 HUD 叠加层已注册");
    }

    private static void onClientTick(Minecraft client) {
        tickCounter++;

        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        // 每 10 tick 更新一次（0.5 秒）
        if (tickCounter % 10 != 0) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // 优先主手，其次副手
        ItemStack tool = !mainHand.isEmpty() ? mainHand : offHand;
        if (tool.isEmpty()) {
            return;
        }

        Component message = null;

        if (tool.is(Items.CLOCK)) {
            message = getClockInfo(player);
        } else if (tool.is(Items.COMPASS)) {
            message = getCompassInfo(player);
        } else if (tool.is(Items.RECOVERY_COMPASS)) {
            message = getRecoveryCompassInfo(player);
        }

        if (message != null) {
            player.sendOverlayMessage(message);
        }
    }

    /**
     * 获取时钟信息：游戏内时间和天数。
     */
    private static Component getClockInfo(LocalPlayer player) {
        long time = player.level().getLevelData().getGameTime();
        long day = time / 24000L;

        // 将游戏 tick 转换为游戏内小时（0-23999 → 6:00-5:59）
        long dayTime = time % 24000L;
        int hours = (int) ((dayTime / 1000L + 6) % 24);
        int minutes = (int) ((dayTime % 1000L) * 60 / 1000);

        return Component.translatable("youzaiworldcore.hud.clock",
                day, String.format("%02d:%02d", hours, minutes));
    }

    /**
     * 获取指南针信息：当前面对方向。
     */
    private static Component getCompassInfo(LocalPlayer player) {
        float yaw = player.getYRot();
        // 标准化到 0-360
        yaw = (yaw % 360.0f + 360.0f) % 360.0f;

        // 将 360 度映射到 8 个方向
        int index = Math.round(yaw / 45.0f) % 8;
        String dir = DIRECTIONS[index];

        return Component.translatable("youzaiworldcore.hud.compass", dir);
    }

    /**
     * 获取追溯指针信息：上次死亡坐标。
     */
    private static Component getRecoveryCompassInfo(LocalPlayer player) {
        var lastDeathPos = player.getLastDeathLocation();
        if (lastDeathPos.isEmpty()) {
            return Component.translatable("youzaiworldcore.hud.recovery_compass.no_death");
        }

        var pos = lastDeathPos.get().pos();
        String dimension = lastDeathPos.get().dimension().identifier().toString();

        return Component.translatable("youzaiworldcore.hud.recovery_compass",
                pos.getX(), pos.getY(), pos.getZ(), dimension);
    }
}
