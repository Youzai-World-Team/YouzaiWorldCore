package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.commands.TagCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;

@Mixin(TagCommand.class)
public abstract class TagCommandPoolMixin {
    @SuppressWarnings("null")
    @WrapOperation(method = { "lambda$register$0", "lambda$register$1", "lambda$register$2",
            "lambda$register$3" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/arguments/EntityArgument;getEntities(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/String;)Ljava/util/Collection;"))
    private static Collection<? extends Entity> youzaiworldcore$entitiesInPool(
            CommandContext<CommandSourceStack> context,
            String argument, Operation<Collection<? extends Entity>> original) throws CommandSyntaxException {
        String pool = DimensionPoolSettings.statePoolId(context.getSource().getLevel().dimension());
        List<? extends Entity> targets = original.call(context, argument).stream()
                .filter(entity -> pool.equals(DimensionPoolSettings.statePoolId(entity.level().dimension())))
                .toList();
        if (targets.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }
        return targets;
    }
}
