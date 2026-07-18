package top.csituka.youzaiworldcore.highlightitem;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 高亮物品客户端命令（纯指令控制，无 GUI）。
 * <p>
 * 命令根：{@code /yzwc settings highlight_item}，子命令：{@code toggle} / {@code color}
 * / {@code mode}。
 * 枚举参数的解析使用 {@link StringArgumentType} 按名称匹配，避免自定义 ArgumentType 注册。
 */
public class HighLightCommands {

    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                literal("yzwc")
                        .then(literal("settings")
                                .then(literal("highlight_item")
                                        .then(literal("toggle").executes(cmdContext -> {
                                            HighlightItem.configurator.updateToggle(
                                                    cmdContext.getSource().getPlayer(),
                                                    Configurator.NotificationContext.SENDING_COMMAND);
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                        .then(literal("color")
                                                .then(literal("custom")
                                                        .then(argument("red", IntegerArgumentType.integer(0, 255))
                                                                .then(argument("green", IntegerArgumentType.integer(0, 255))
                                                                        .then(argument("blue", IntegerArgumentType.integer(0, 255))
                                                                                .then(argument("alpha", FloatArgumentType.floatArg(0.0f, 1.0f))
                                                                                        .executes(cmdContext -> {
                                                                                            float[] rgba = new float[]{
                                                                                                    cmdContext.getArgument("red", Integer.class) / 255.0f,
                                                                                                    cmdContext.getArgument("green", Integer.class) / 255.0f,
                                                                                                    cmdContext.getArgument("blue", Integer.class) / 255.0f,
                                                                                                    cmdContext.getArgument("alpha", Float.class)
                                                                                            };
                                                                                            HighlightItem.configurator.updateColor(rgba, cmdContext.getSource().getPlayer());
                                                                                            return Command.SINGLE_SUCCESS;
                                                                                        }))))))
                                                .then(argument("color", StringArgumentType.word())
                                                        .executes(cmdContext -> {
                                                            String name = cmdContext.getArgument("color", String.class).toUpperCase();
                                                            try {
                                                                Colors.HighLightColor color = Colors.HighLightColor.valueOf(name);
                                                                float[] colors = color.getShaderColor();
                                                                Configurator.COLOR = ARGB.color(
                                                                        (int) (colors[3] * 255),
                                                                        (int) (colors[0] * 255),
                                                                        (int) (colors[1] * 255),
                                                                        (int) (colors[2] * 255));
                                                                HighlightItem.configurator.updateConfig(Configurator.Config.COLOR, color.json().toString());
                                                                cmdContext.getSource().sendFeedback(
                                                                        Component.translatable("youzaiworldcore.highlight.color"));
                                                            } catch (IllegalArgumentException | IOException e) {
                                                                cmdContext.getSource().sendError(
                                                                        Component.translatable("youzaiworldcore.highlight.config.update.fail"));
                                                                DebugLogger.exception("HighlightItem", "命令 color 解析失败: " + name, e);
                                                            }
                                                            return Command.SINGLE_SUCCESS;
                                                        })))
                                        .then(literal("mode")
                                                .then(argument("mode", StringArgumentType.word())
                                                        .executes(cmdContext -> {
                                                            String name = cmdContext.getArgument("mode", String.class).toUpperCase();
                                                            try {
                                                                ItemComparator.Comparators mode = ItemComparator.Comparators.valueOf(name);
                                                                HighlightItem.configurator.updateMode(
                                                                        mode,
                                                                        cmdContext.getSource().getPlayer(),
                                                                        Configurator.NotificationContext.SENDING_COMMAND);
                                                            } catch (IllegalArgumentException e) {
                                                                cmdContext.getSource().sendError(
                                                                        Component.translatable("youzaiworldcore.highlight.config.update.fail"));
                                                                DebugLogger.exception("HighlightItem", "命令 mode 解析失败: " + name, e);
                                                            }
                                                            return Command.SINGLE_SUCCESS;
                                                        })))
                                )
                        )
        ));
    }
}
