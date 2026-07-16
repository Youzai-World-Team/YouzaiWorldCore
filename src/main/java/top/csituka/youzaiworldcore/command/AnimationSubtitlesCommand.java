package top.csituka.youzaiworldcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.entity.animation_subtitle.AnimationSubtitleEntity;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 动画字幕命令 {@code /yzwc function animation_subtitles}。
 *
 * <h2>子命令</h2>
 * <ul>
 *   <li>{@code set pos <pos> <rot1> <rot2> <text> [time]} — 在指定坐标以指定朝向生成字幕</li>
 *   <li>{@code set player_location <text> [time] [player]} — 在目标玩家前方 2 格生成，自动匹配朝向</li>
 * </ul>
 *
 * <h2>文本格式化</h2>
 * 支持以下格式化代码（在文本中以 {@code &} 或 {@code §} 开头）：
 * <ul>
 *   <li>{@code &[0-9a-f]} — 传统颜色码</li>
 *   <li>{@code &l} — 加粗</li>
 *   <li>{@code &n} — 下划线</li>
 *   <li>{@code &o} — 斜体</li>
 *   <li>{@code &r} — 重置格式</li>
 *   <li>{@code <#RRGGBB>} — 16进制颜色</li>
 *   <li>{@code <size:N>} — 字体大小 (1-128，基准 10)</li>
 *   <li>{@code <bold>} / {@code </bold>} — 加粗控制</li>
 *   <li>{@code <br>} — 换行</li>
 *   <li>{@code <reset>} — 重置所有样式</li>
 * </ul>
 *
 * <p>
 * {@code time} 参数缺省值为 5.0 秒，{@code player} 参数缺省值为命令执行者自己。
 * </p>
 */
@SuppressWarnings("null")
public class AnimationSubtitlesCommand {

    /** 默认停留时间（秒） */
    private static final float DEFAULT_HOLD_SECONDS = 5.0F;

    /** 默认字幕缩放 */
    private static final float DEFAULT_SCALE = 1.0F;

    /** 玩家前方距离 */
    private static final double PLAYER_FRONT_DISTANCE = 2.0;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("function")
                        .then(Commands.literal("animation_subtitles")
                                .requires(source -> LuckPermsHelper.checkPermission(
                                        source,
                                        "youzaiworldcore.command.animation_subtitles",
                                        Commands.LEVEL_ADMINS
                                ))
                                .then(Commands.literal("set")
                                        // === set pos <pos> <rot1> <rot2> <text> [time] ===
                                        .then(Commands.literal("pos")
                                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                                        .then(Commands.argument("rot1", FloatArgumentType.floatArg(-360f, 360f))
                                                                .then(Commands.argument("rot2", FloatArgumentType.floatArg(-360f, 360f))
                                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                                .executes(ctx -> executeSetPos(
                                                                                        ctx,
                                                                                        FloatArgumentType.getFloat(ctx, "rot1"),
                                                                                        FloatArgumentType.getFloat(ctx, "rot2"),
                                                                                        StringArgumentType.getString(ctx, "text"),
                                                                                        DEFAULT_HOLD_SECONDS
                                                                                ))
                                                                                .then(Commands.argument("time", FloatArgumentType.floatArg(0.1f, 3600f))
                                                                                        .executes(ctx -> executeSetPos(
                                                                                                ctx,
                                                                                                FloatArgumentType.getFloat(ctx, "rot1"),
                                                                                                FloatArgumentType.getFloat(ctx, "rot2"),
                                                                                                StringArgumentType.getString(ctx, "text"),
                                                                                                FloatArgumentType.getFloat(ctx, "time")
                                                                                        ))
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                        // === set player_location <text> [time] [player] ===
                                        .then(Commands.literal("player_location")
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(ctx -> executeSetPlayerLocation(
                                                                ctx,
                                                                StringArgumentType.getString(ctx, "text"),
                                                                DEFAULT_HOLD_SECONDS,
                                                                ctx.getSource().getPlayerOrException()
                                                        ))
                                                        .then(Commands.argument("time", FloatArgumentType.floatArg(0.1f, 3600f))
                                                                .executes(ctx -> executeSetPlayerLocation(
                                                                        ctx,
                                                                        StringArgumentType.getString(ctx, "text"),
                                                                        FloatArgumentType.getFloat(ctx, "time"),
                                                                        ctx.getSource().getPlayerOrException()
                                                                ))
                                                                .then(Commands.argument("player", EntityArgument.player())
                                                                        .executes(ctx -> executeSetPlayerLocation(
                                                                                ctx,
                                                                                StringArgumentType.getString(ctx, "text"),
                                                                                FloatArgumentType.getFloat(ctx, "time"),
                                                                                EntityArgument.getPlayer(ctx, "player")
                                                                        ))
                                                                )
                                                        )
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> executeSetPlayerLocation(
                                                                        ctx,
                                                                        StringArgumentType.getString(ctx, "text"),
                                                                        DEFAULT_HOLD_SECONDS,
                                                                        EntityArgument.getPlayer(ctx, "player")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    // ======================== 命令执行 ========================

    /**
     * 在指定坐标以指定朝向生成字幕。
     */
    private static int executeSetPos(
            CommandContext<CommandSourceStack> ctx,
            float rot1, float rot2,
            String rawText, float holdSeconds
    ) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        // 解析文本中的停留时间覆盖（文本末尾空格后跟数字）
        String text = rawText;
        float finalHoldSeconds = holdSeconds;
        String trimmed = rawText.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace > 0 && lastSpace < trimmed.length() - 1) {
            String potentialTime = trimmed.substring(lastSpace + 1);
            try {
                finalHoldSeconds = Float.parseFloat(potentialTime);
                text = rawText.substring(0, rawText.lastIndexOf(potentialTime)).trim();
            } catch (NumberFormatException ignored) {
                // 不是数字，保持原文本
            }
        }

        // 转换 & 格式码为 §
        text = convertAmpersandCodes(text);

        Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
        int holdTicks = Math.round(finalHoldSeconds * 20);

        DebugLogger.info("AnimationSubtitlesCommand",
                "生成位置字幕: pos=%s, rot1=%s, rot2=%s, text=%s, time=%ss",
                pos, rot1, rot2, text, finalHoldSeconds);

        AnimationSubtitleEntity entity = AnimationSubtitleEntity.createMain(
                level, pos, rot1, text, DEFAULT_SCALE, holdTicks
        );
        level.addFreshEntity(entity);

        source.sendSuccess(
                () -> Component.translatable("youzaiworldcore.message.command.animation_subtitles.success"),
                true
        );
        return 1;
    }

    /**
     * 在目标玩家前方生成字幕，自动匹配朝向。
     */
    private static int executeSetPlayerLocation(
            CommandContext<CommandSourceStack> ctx,
            String rawText, float holdSeconds,
            ServerPlayer targetPlayer
    ) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = (ServerLevel) targetPlayer.level();

        // 解析文本中的停留时间覆盖
        String text = rawText;
        float finalHoldSeconds = holdSeconds;
        String trimmed = rawText.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace > 0 && lastSpace < trimmed.length() - 1) {
            String potentialTime = trimmed.substring(lastSpace + 1);
            try {
                finalHoldSeconds = Float.parseFloat(potentialTime);
                text = rawText.substring(0, rawText.lastIndexOf(potentialTime)).trim();
            } catch (NumberFormatException ignored) {
            }
        }

        // 转换 & 格式码为 §
        text = convertAmpersandCodes(text);

        // 计算玩家前方位置
        Vec3 lookVec = targetPlayer.getLookAngle();
        Vec3 playerPos = targetPlayer.position();
        Vec3 spawnPos = playerPos.add(
                lookVec.x * PLAYER_FRONT_DISTANCE,
                lookVec.y * PLAYER_FRONT_DISTANCE + 1.0, // 稍微抬高，在视线中心
                lookVec.z * PLAYER_FRONT_DISTANCE
        );

        float yRot = targetPlayer.getYRot() + 180f; // 面向玩家
        if (yRot > 180f) yRot -= 360f;
        if (yRot < -180f) yRot += 360f;

        int holdTicks = Math.round(finalHoldSeconds * 20);

        DebugLogger.info("AnimationSubtitlesCommand",
                "生成玩家字幕: player=%s, pos=%s, yRot=%s, text=%s, time=%ss",
                targetPlayer.getName().getString(), spawnPos, yRot, text, finalHoldSeconds);

        AnimationSubtitleEntity entity = AnimationSubtitleEntity.createMain(
                level, spawnPos, yRot, text, DEFAULT_SCALE, holdTicks
        );
        level.addFreshEntity(entity);

        source.sendSuccess(
                () -> Component.translatable("youzaiworldcore.message.command.animation_subtitles.success"),
                true
        );
        return 1;
    }

    // ======================== 辅助方法 ========================

    /**
     * 将 {@code &} 格式码转换为 Minecraft {@code §} 格式码。
     */
    private static String convertAmpersandCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f')
                        || next == 'k' || next == 'l' || next == 'm'
                        || next == 'n' || next == 'o' || next == 'r') {
                    result.append('\u00a7');
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }
}
