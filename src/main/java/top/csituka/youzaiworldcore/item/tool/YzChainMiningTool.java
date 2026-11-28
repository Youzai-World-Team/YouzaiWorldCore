package top.csituka.youzaiworldcore.item.tool;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.LoadedChunks;

@SuppressWarnings("null")
public class YzChainMiningTool {

    private static final int CHAIN_RANGE = 6;

    public static boolean isChainMiningTool(ItemStack stack) {
        DebugLogger.SERVER.entering("YzChainMiningTool", "isChainMiningTool",
                "item=" + (stack.isEmpty() ? "empty" : stack.getItem().toString()));
        if (stack.isEmpty()) {
            DebugLogger.SERVER.branch("YzChainMiningTool", "stack is empty", true);
            DebugLogger.SERVER.exiting("YzChainMiningTool", "isChainMiningTool", "false (empty)");
            return false;
        }
        Item item = stack.getItem();
        boolean result = item instanceof YzShovelItem || item instanceof YzPickaxeItem;
        DebugLogger.SERVER.branch("YzChainMiningTool", "is chain tool", result, "item=" + item.getClass().getSimpleName());
        DebugLogger.SERVER.exiting("YzChainMiningTool", "isChainMiningTool", String.valueOf(result));
        return result;
    }

    public static void registerChainMiningEvent() {
        DebugLogger.SERVER.entering("YzChainMiningTool", "registerChainMiningEvent");
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            DebugLogger.SERVER.entering("YzChainMiningTool", "lambda:onBlockBreak",
                    "player=" + player.getName().getString() + ", pos=" + pos + ", block=" + state.getBlock());
            if (world.isClientSide()) {
                DebugLogger.SERVER.branch("YzChainMiningTool", "client side", true);
                DebugLogger.SERVER.exiting("YzChainMiningTool", "lambda:onBlockBreak", "skipped (client)");
                return;
            }
            DebugLogger.SERVER.branch("YzChainMiningTool", "server side", false);
            if (!player.isShiftKeyDown()) {
                DebugLogger.SERVER.branch("YzChainMiningTool", "not sneaking", true);
                DebugLogger.SERVER.exiting("YzChainMiningTool", "lambda:onBlockBreak", "skipped (not sneaking)");
                return;
            }
            DebugLogger.SERVER.branch("YzChainMiningTool", "is sneaking", false);

            ItemStack mainHand = player.getMainHandItem();
            if (!isChainMiningTool(mainHand)) {
                DebugLogger.SERVER.branch("YzChainMiningTool", "not chain mining tool", true,
                        "item=" + mainHand.getItem());
                DebugLogger.SERVER.exiting("YzChainMiningTool", "lambda:onBlockBreak", "skipped (wrong tool)");
                return;
            }
            DebugLogger.SERVER.branch("YzChainMiningTool", "is chain mining tool", false);

            Direction lookDirection = getPlayerLookDirection(player);
            chainMine((ServerLevel) world, player, pos, state.getBlock(), lookDirection);
            DebugLogger.SERVER.exiting("YzChainMiningTool", "lambda:onBlockBreak", "chain mine executed");
        });
        DebugLogger.SERVER.exiting("YzChainMiningTool", "registerChainMiningEvent");
    }

    private static Direction getPlayerLookDirection(Player player) {
        DebugLogger.SERVER.entering("YzChainMiningTool", "getPlayerLookDirection",
                "player=" + player.getName().getString());
        Vec3 look = player.getLookAngle();
        double absX = Math.abs(look.x);
        double absY = Math.abs(look.y);
        double absZ = Math.abs(look.z);

        Direction result;
        if (absY > absX && absY > absZ) {
            result = look.y > 0 ? Direction.UP : Direction.DOWN;
            DebugLogger.SERVER.branch("YzChainMiningTool", "vertical look direction", true,
                    "result=" + result);
        } else if (absX > absZ) {
            result = look.x > 0 ? Direction.EAST : Direction.WEST;
            DebugLogger.SERVER.branch("YzChainMiningTool", "X-axis look direction", true,
                    "result=" + result);
        } else {
            result = look.z > 0 ? Direction.SOUTH : Direction.NORTH;
            DebugLogger.SERVER.branch("YzChainMiningTool", "Z-axis look direction", false,
                    "result=" + result);
        }
        DebugLogger.SERVER.exiting("YzChainMiningTool", "getPlayerLookDirection", result.toString());
        return result;
    }

    private static void chainMine(ServerLevel world, Player player, BlockPos origin, Block targetType, Direction direction) {
        DebugLogger.SERVER.entering("YzChainMiningTool", "chainMine",
                "origin=" + origin + ", targetType=" + targetType + ", direction=" + direction);
        for (int i = 1; i <= CHAIN_RANGE; i++) {
            BlockPos targetPos = origin.relative(direction, i);
            var chunk = LoadedChunks.get(world, targetPos);
            if (chunk == null || !LoadedChunks.covers(world, targetPos, targetPos, 1)) {
                break;
            }
            BlockState targetState = chunk.getBlockState(targetPos);

            if (!targetState.is(targetType)) {
                DebugLogger.SERVER.branch("YzChainMiningTool", "block type mismatch", true,
                        "pos=" + targetPos + ", expected=" + targetType + ", actual=" + targetState.getBlock());
                break;
            }
            DebugLogger.SERVER.branch("YzChainMiningTool", "mining block", false,
                    "pos=" + targetPos + ", i=" + i);

            world.destroyBlock(targetPos, true, player);
        }
        DebugLogger.SERVER.exiting("YzChainMiningTool", "chainMine");
    }
}
