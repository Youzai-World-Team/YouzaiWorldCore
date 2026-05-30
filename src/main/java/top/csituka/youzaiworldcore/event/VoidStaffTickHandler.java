package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.VoidStaffItem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 凭虚法杖（Void Staff）的 Tick 事件处理器。
 * <p>
 * 负责每游戏刻检查正在使用凭虚法杖飞行的玩家，处理以下逻辑：
 * <ul>
 *     <li>每秒检查玩家是否手持凭虚法杖，若未手持则关闭飞行并清除相关状态</li>
 *     <li>每刻（实际每 5 秒）消耗玩家的饥饿值或饱和度来维持飞行，耗尽时强制关闭飞行</li>
 *     <li>当法杖耐久耗尽时自动销毁并关闭飞行</li>
 *     <li>玩家首次通过法杖获得飞行能力时授予成就</li>
 *     <li>玩家断开连接或加入时清理残留的 active 状态组件</li>
 * </ul>
 * 实现 Fabric API 的 {@link ServerTickEvents.StartTick} 接口。
 */
public class VoidStaffTickHandler implements ServerTickEvents.StartTick {

    // 单例实例
    private static final VoidStaffTickHandler INSTANCE = new VoidStaffTickHandler();

    // 通用计数器（用于每秒执行一次的手持检查与耐久消耗）
    private static int tickCounter = 0;

    // 饥饿消耗专用计数器（用于每 5 秒执行一次饥饿值扣除）
    private static int hungerTickCounter = 0;

    // 每秒对应的游戏刻数（20 tick = 1 秒）
    private static final int TICKS_PER_SECOND = 20;

    // 每次扣除饥饿值的间隔刻数（100 tick = 5 秒）
    private static final int TICKS_PER_HUNGER = 100;

    // 记录已经授予过“使用凭虚法杖”成就的玩家，避免重复授予
    private static final Set<UUID> grantedAchievementPlayers = new HashSet<>();

    // 私有构造，确保单例
    private VoidStaffTickHandler() {
    }

    /**
     * 每个游戏刻开始时触发。实际逻辑按 TICKS_PER_SECOND 和 TICKS_PER_HUNGER 间隔执行。
     *
     * @param server Minecraft 服务器实例
     */
    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        tickCounter++;
        hungerTickCounter++;

        // ========== 1. 每秒执行：检查手持状态与耐久消耗 ==========
        if (tickCounter >= TICKS_PER_SECOND) {
            tickCounter = 0; // 重置计数器，进入下一秒周期

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // 创造模式玩家无需消耗耐久，也不受飞行限制
                if (player.isCreative()) {
                    continue;
                }

                UUID playerId = player.getUUID();

                // 仅处理当前被标记为“正在使用凭虚法杖飞行”的玩家
                if (VoidStaffItem.isFlying(playerId)) {
                    // 情况1：玩家不再手持凭虚法杖
                    if (!VoidStaffItem.hasVoidStaffInHand(player)) {
                        // 关闭飞行标记
                        VoidStaffItem.setFlying(playerId, false);
                        // 清除背包中所有凭虚法杖的 active 组件
                        clearAllVoidStaffActiveState(player);
                        // 如果玩家同时处于飞行信标的飞行范围内，则恢复普通飞行（由信标接管）
                        if (FlyBeaconTickHandler.isBeaconFlying(playerId)) {
                            VoidStaffItem.disableFlight(player);
                        }
                        // 发送动作栏提示：法杖已禁用
                        sendActionBar(player,
                                Component.translatable("item.youzaiworldcore.void_staff.disabled")
                                        .withStyle(ChatFormatting.RED)
                        );
                    }
                    // 情况2：玩家手持法杖且正在飞行，且未站在地面上（即真正在飞）
                    else if (player.getAbilities().flying && !player.onGround()) {
                        ItemStack flyCore = VoidStaffItem.getVoidStaffInHand(player);
                        if (flyCore != null) {
                            // 增加 1 点耐久损耗
                            int newDamage = flyCore.getDamageValue() + 1;
                            if (newDamage >= flyCore.getMaxDamage()) {
                                // 耐久耗尽：销毁当前法杖，关闭飞行
                                flyCore.shrink(1);
                                VoidStaffItem.setFlying(playerId, false);
                                clearAllVoidStaffActiveState(player);
                                if (FlyBeaconTickHandler.isBeaconFlying(playerId)) {
                                    VoidStaffItem.disableFlight(player);
                                }
                                sendActionBar(player,
                                        Component.translatable("item.youzaiworldcore.void_staff.disabled")
                                                .withStyle(ChatFormatting.RED)
                                );
                            } else {
                                // 未耗尽：仅增加损伤值
                                flyCore.setDamageValue(newDamage);
                            }
                        }
                    }

                    // ***** 新增：首次获得飞行能力时授予成就 *****
                    // 玩家正在飞行且标记为 true，并且尚未授予过成就
                    if (player.getAbilities().flying && !grantedAchievementPlayers.contains(playerId)) {
                        grantUsedVoidStaffAdvancementViaCommand(player, server);
                        grantedAchievementPlayers.add(playerId);
                    }
                }
            }
        }

        // ========== 2. 每 5 秒执行：消耗饥饿值/饱和度 ==========
        if (hungerTickCounter >= TICKS_PER_HUNGER) {
            hungerTickCounter = 0; // 重置计数器，进入下一个 5 秒周期

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isCreative()) {
                    continue;
                }

                UUID playerId = player.getUUID();

                // 仅对正在飞行且真正在空中的玩家扣除饥饿
                if (VoidStaffItem.isFlying(playerId) && player.getAbilities().flying && !player.onGround()) {
                    float saturation = player.getFoodData().getSaturationLevel();
                    int food = player.getFoodData().getFoodLevel();

                    // 优先扣除饱和度
                    if (saturation > 0) {
                        player.getFoodData().setSaturation(Math.max(0, saturation - 1));
                    }
                    // 没有饱和度时扣除一点食物值
                    else if (food > 0) {
                        player.getFoodData().setFoodLevel(food - 1);
                    }
                    // 既无饱和度也无食物值：无法维持飞行，强制关闭
                    else {
                        VoidStaffItem.setFlying(playerId, false);
                        clearAllVoidStaffActiveState(player);
                        if (FlyBeaconTickHandler.isBeaconFlying(playerId)) {
                            VoidStaffItem.disableFlight(player);
                        }
                        sendActionBar(player,
                                Component.translatable("item.youzaiworldcore.void_staff.no_hunger")
                                        .withStyle(ChatFormatting.RED)
                        );
                    }
                }
            }
        }
    }

    /**
     * 清除玩家背包中所有凭虚法杖物品上的 {@code VOID_STAFF_ACTIVE} 组件。
     * 通常在飞行结束时调用，确保所有法杖不再保持 active 状态。
     *
     * @param player 目标玩家
     */
    private void clearAllVoidStaffActiveState(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == ModItems.VOID_STAFF && stack.has(ModDataComponents.VOID_STAFF_ACTIVE)) {
                stack.remove(ModDataComponents.VOID_STAFF_ACTIVE);
            }
        }
    }

    /**
     * 向玩家发送动作栏消息（位于屏幕底部）。
     *
     * @param player  目标玩家
     * @param message 要显示的文本组件
     */
    private void sendActionBar(ServerPlayer player, Component message) {
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(message);
        player.connection.send(packet);
    }

    /**
     * 通过执行服务器指令授予玩家“使用凭虚法杖”成就。
     *
     * @param player 目标玩家
     * @param server Minecraft 服务器实例
     */
    private void grantUsedVoidStaffAdvancementViaCommand(ServerPlayer player, MinecraftServer server) {
        String command = "advancement grant " + player.getName().getString() + " only youzaiworldcore:youzaiworld/used_void_staff";
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(),
                command
        );
    }

    /**
     * 向 Fabric 事件总线注册本 tick 处理器及相关连接事件监听器。
     * <ul>
     *     <li>注册服务器 start tick 事件</li>
     *     <li>注册玩家断开连接事件：清除该玩家的飞行标记，并移除成就记录</li>
     *     <li>注册玩家加入事件：清除背包中所有凭虚法杖的 active 组件（防止残留状态）</li>
     * </ul>
     */
    public static void register() {
        // 注册 tick 处理器
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);

        // 玩家断开连接时清除飞行标记，避免内存泄漏
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.player.getUUID();
            VoidStaffItem.setFlying(playerId, false);
            grantedAchievementPlayers.remove(playerId);
        });

        // 玩家加入时清理背包中凭虚法杖的 active 组件（以防上次异常断线残留）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() == ModItems.VOID_STAFF && stack.has(ModDataComponents.VOID_STAFF_ACTIVE)) {
                    stack.remove(ModDataComponents.VOID_STAFF_ACTIVE);
                }
            }
        });
    }
}