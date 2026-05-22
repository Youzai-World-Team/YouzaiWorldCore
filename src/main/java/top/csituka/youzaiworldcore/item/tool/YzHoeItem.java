package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public class YzHoeItem extends HoeItem {

    public YzHoeItem(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline, Properties settings) {
        super(material, attackDamageBaseline, attackSpeedBaseline, settings);
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> tooltip, @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.yz_hoe.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override
    @NonNull
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return till3x3(context);
        }
        return super.useOn(context);
    }

    private InteractionResult till3x3(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos centerPos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        boolean tilledAny = false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = centerPos.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);

                if (isTillable(level, pos, state)) {
                    level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
                    if (state.getBlock() == Blocks.GRASS_BLOCK || state.getBlock() == Blocks.DIRT) {
                        level.destroyBlock(pos.above(), false);
                    }
                    tilledAny = true;
                }
            }
        }

        if (tilledAny && player != null) {
            stack.hurtAndBreak(1, player, context.getHand());
        }

        return tilledAny ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private boolean isTillable(Level level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)) {
            BlockState aboveState = level.getBlockState(pos.above());
            return aboveState.isAir() || aboveState.canBeReplaced();
        }
        return false;
    }
}
