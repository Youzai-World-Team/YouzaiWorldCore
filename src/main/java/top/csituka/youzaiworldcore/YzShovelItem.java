package top.csituka.youzaiworldcore;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class YzShovelItem extends ShovelItem {

    private static final int CHAIN_RANGE = 6;

    public YzShovelItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Properties settings) {
        super(material, attackDamageBaseline, attackSpeedBaseline, settings);
    }

    public static void registerChainMiningEvent() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) return;
            if (!player.isShiftKeyDown()) return;

            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof YzShovelItem)) return;

            Direction lookDirection = getPlayerLookDirection(player);
            chainMine((ServerLevel) world, player, pos, state.getBlock(), lookDirection);
        });
    }

    private static Direction getPlayerLookDirection(Player player) {
        Vec3 look = player.getLookAngle();
        double absX = Math.abs(look.x);
        double absY = Math.abs(look.y);
        double absZ = Math.abs(look.z);

        if (absY > absX && absY > absZ) {
            return look.y > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX > absZ) {
            return look.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return look.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static void chainMine(ServerLevel world, Player player, BlockPos origin, Block targetType, Direction direction) {
        for (int i = 1; i <= CHAIN_RANGE; i++) {
            BlockPos targetPos = origin.relative(direction, i);
            BlockState targetState = world.getBlockState(targetPos);

            if (!targetState.is(targetType)) break;

            world.destroyBlock(targetPos, true, player);
        }
    }
}
