package top.csituka.youzaiworldcore.pet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.pet.*;
import top.csituka.youzaiworldcore.pet.config.PetModuleConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 宠物管理命令 — {@code /yzwc pet ...}
 * <p>
 * 采用 Brigadier 框架实现，所有权限检查通过 {@link LuckPermsHelper} 统一管理。
 * 命令执行前置硬性规则：所有涉及 {@code <内部名称>} 参数的命令，
 * 第一步必须通过全局注册表进行名称匹配。
 * </p>
 */
@SuppressWarnings("null")
public class PetCommand {

    private static final String MODULE = "PetCommand";

    private PetCommand() {
    }

    // ===== 权限节点 =====
    public static final String PERMISSION_PET_LIST = "youzaiworldcore.command.pet.list";
    public static final String PERMISSION_PET_SET = "youzaiworldcore.command.pet.set";
    public static final String PERMISSION_PET_HIGHLIGHT = "youzaiworldcore.command.pet.highlight";
    public static final String PERMISSION_PET_ADMIN = "youzaiworldcore.command.pet.admin";

    /**
     * 向命令调度器注册宠物管理命令。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        DebugLogger.entering(MODULE, "register");

        // 建议提供者：列出玩家所有的宠物内部名称
        SuggestionProvider<CommandSourceStack> petNameSuggestions = (ctx, builder) -> {
            CommandSourceStack source = ctx.getSource();
            if (!source.isPlayer()) {
                return builder.buildFuture();
            }
            ServerPlayer player = source.getPlayerOrException();
            PetGlobalState state = PetGlobalState.get(player.level().getServer());
            List<PetEntry> pets = state.findByOwner(player.getUUID());
            for (PetEntry pet : pets) {
                builder.suggest(pet.internalName());
            }
            return builder.buildFuture();
        };

        dispatcher.register(Commands.literal("yzwc")
                .then(Commands.literal("pet")
                        // ===== list =====
                        .then(Commands.literal("list")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                        src, PERMISSION_PET_LIST, Commands.LEVEL_ALL))
                                .executes(PetCommand::executeList)
                        )
                        // ===== set <内部名称> ... =====
                        .then(Commands.literal("set")
                                .then(Commands.argument("internalName", StringArgumentType.word())
                                        .suggests(petNameSuggestions)
                                        // === rename ===
                                        .then(Commands.literal("rename")
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                        src, PERMISSION_PET_SET, Commands.LEVEL_ALL))
                                                .then(Commands.argument("newDisplayName", StringArgumentType.string())
                                                        .executes(PetCommand::executeRename)
                                                )
                                        )
                                        // === mode ===
                                        .then(Commands.literal("mode")
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                        src, PERMISSION_PET_SET, Commands.LEVEL_ALL))
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                                new String[]{"hunting", "companionship", "attack", "guard"}, builder))
                                                        .executes(PetCommand::executeSetMode)
                                                )
                                        )
                                        // === trust ===
                                        .then(Commands.literal("trust")
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                        src, PERMISSION_PET_SET, Commands.LEVEL_ALL))
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                                .executes(PetCommand::executeTrustAdd)
                                                        )
                                                )
                                                .then(Commands.literal("remove")
                                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                                .executes(PetCommand::executeTrustRemove)
                                                        )
                                                )
                                                .then(Commands.literal("list")
                                                        .executes(PetCommand::executeTrustList)
                                                )
                                        )
                                        // === release_life [force] ===
                                        .then(Commands.literal("release_life")
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                        src, PERMISSION_PET_SET, Commands.LEVEL_ALL))
                                                .executes(ctx -> executeReleaseLife(ctx, false))
                                                .then(Commands.argument("force", BoolArgumentType.bool())
                                                        .executes(ctx -> executeReleaseLife(
                                                                ctx, BoolArgumentType.getBool(ctx, "force")))
                                                )
                                        )
                                        // === transfer ===
                                        .then(Commands.literal("transfer")
                                                .requires(src -> LuckPermsHelper.checkPermission(
                                                        src, PERMISSION_PET_SET, Commands.LEVEL_ALL))
                                                .then(Commands.argument("newOwner", GameProfileArgument.gameProfile())
                                                        .executes(PetCommand::executeTransfer)
                                                )
                                        )
                                )
                        )
                        // ===== highlight <内部名称> =====
                        .then(Commands.literal("highlight")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                        src, PERMISSION_PET_HIGHLIGHT, Commands.LEVEL_ALL))
                                .then(Commands.argument("internalName", StringArgumentType.word())
                                        .suggests(petNameSuggestions)
                                        .executes(PetCommand::executeHighlight)
                                )
                        )
                        // ===== admin =====
                        .then(Commands.literal("admin")
                                .requires(src -> LuckPermsHelper.checkPermission(
                                        src, PERMISSION_PET_ADMIN, Commands.LEVEL_ADMINS))
                                .then(Commands.literal("restore")
                                        .executes(PetCommand::executeAdminRestore)
                                )
                                .then(Commands.literal("backup_list")
                                        .executes(PetCommand::executeAdminBackupList)
                                )
                                .then(Commands.literal("backup_interval")
                                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(60, 3600))
                                                .executes(PetCommand::executeAdminBackupInterval)
                                        )
                                )
                        )
                )
        );

        DebugLogger.exiting(MODULE, "register");
    }

    // ==================== 命令执行方法 ====================

    // ===== 1. 查看宠物列表 =====

    private static int executeList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeList");
        CommandSourceStack source = ctx.getSource();

        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.command.player_only"));
            DebugLogger.exiting(MODULE, "executeList", "0 (console)");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        // 获取该玩家作为主人的宠物
        List<PetEntry> ownedPets = state.findByOwner(player.getUUID());

        // 获取其他玩家分配给他（他在信任列表中）的宠物
        List<PetEntry> trustedPets = state.getAllPets().stream()
                .filter(p -> p.trustedPlayers().contains(player.getUUID()))
                .collect(Collectors.toList());

        if (ownedPets.isEmpty() && trustedPets.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.pet.list.empty"), false);
            DebugLogger.exiting(MODULE, "executeList", "0 (empty)");
            return 0;
        }

        // 显示格式：
        // 若查看者 == 主人: [内部名称] 显示名称
        // 若查看者 ∈ 信任列表: [内部名称] <主人>/显示名称
        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.list.header",
                ownedPets.size() + trustedPets.size()), false);

        for (PetEntry pet : ownedPets) {
            source.sendSuccess(() -> Component.literal(
                    String.format(" §7[%s] §f%s §7(%s) §8%s",
                            pet.internalName(),
                            pet.displayName(),
                            pet.mode().getSerializedName(),
                            pet.formattedTameTime())), false);
        }

        for (PetEntry pet : trustedPets) {
            String ownerName = player.level().getServer().getPlayerList()
                    .getPlayer(pet.ownerUUID()) != null
                    ? player.level().getServer().getPlayerList().getPlayer(pet.ownerUUID()).getName().getString()
                    : pet.ownerUUID().toString().substring(0, 8) + "...";
            final String owner = ownerName;
            source.sendSuccess(() -> Component.literal(
                    String.format(" §7[%s] §7<%s>/§f%s §7(%s) §8%s",
                            pet.internalName(),
                            owner,
                            pet.displayName(),
                            pet.mode().getSerializedName(),
                            pet.formattedTameTime())), false);
        }

        DebugLogger.exiting(MODULE, "executeList", "1");
        return 1;
    }

    // ===== 2. 命令辅助：通过内部名称查找宠物 =====

    /**
     * 根据内部名称查找宠物，并进行权限验证。
     *
     * @param ctx          命令上下文
     * @param internalName 内部名称
     * @return Optional 包含 PetEntry 和验证结果
     */
    private static ValidationResult validatePetAccess(CommandContext<CommandSourceStack> ctx,
                                                       String internalName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!source.isPlayer()) {
            return new ValidationResult(null, "youzaiworldcore.message.command.player_only");
        }
        ServerPlayer player = source.getPlayerOrException();
        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        // 严格优先匹配内部名称
        Optional<PetEntry> optEntry = state.findByInternalName(internalName.toUpperCase(Locale.ROOT));
        if (optEntry.isEmpty()) {
            return new ValidationResult(null,
                    "youzaiworldcore.message.pet.not_found", internalName);
        }

        PetEntry entry = optEntry.get();

        // 验证是否为该宠物的主人
        if (!entry.isOwner(player.getUUID())) {
            return new ValidationResult(null,
                    "youzaiworldcore.message.pet.not_owner");
        }

        return new ValidationResult(entry, null);
    }

    private record ValidationResult(PetEntry entry, String errorKey, Object... errorArgs) {
        ValidationResult(PetEntry entry, String errorKey, Object... errorArgs) {
            this.entry = entry;
            this.errorKey = errorKey;
            this.errorArgs = errorArgs;
        }

        boolean hasError() {
            return errorKey != null;
        }
    }

    // ===== 3. 重命名 =====

    private static int executeRename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeRename");
        CommandSourceStack source = ctx.getSource();

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);
        String newDisplayName = StringArgumentType.getString(ctx, "newDisplayName");

        ValidationResult vr = validatePetAccess(ctx, internalName);
        if (vr.hasError()) {
            source.sendFailure(Component.translatable(vr.errorKey(), vr.errorArgs()));
            DebugLogger.exiting(MODULE, "executeRename", "0 (access denied)");
            return 0;
        }

        PetEntry entry = vr.entry();
        ServerPlayer player = source.getPlayerOrException();
        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        // 自我变更豁免：新名与当前显示名相同 → 视为取消
        if (newDisplayName.equals(entry.displayName())) {
            source.sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.pet.rename.no_change"), false);
            DebugLogger.exiting(MODULE, "executeRename", "1 (no change)");
            return 1;
        }

        // 检查其他宠物是否已使用该显示名
        List<PetEntry> ownedPets = state.findByOwner(entry.ownerUUID());
        boolean nameTaken = ownedPets.stream()
                .anyMatch(p -> !p.entityUUID().equals(entry.entityUUID())
                        && p.displayName().equals(newDisplayName));
        if (nameTaken) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.rename.duplicate", newDisplayName));
            DebugLogger.exiting(MODULE, "executeRename", "0 (duplicate)");
            return 0;
        }

        // 更新注册表
        PetEntry updated = entry.withDisplayName(newDisplayName);
        state.updatePet(updated);

        // 如果实体已加载，同步修改
        ServerLevel level = player.level();
        Entity entity = level.getEntity(entry.entityUUID());
        if (entity instanceof Wolf wolf) {
            wolf.setCustomName(Component.literal(newDisplayName));
        }

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.rename.success",
                internalName, newDisplayName), false);

        DebugLogger.info(MODULE, "重命名: [%s] -> %s", internalName, newDisplayName);
        DebugLogger.exiting(MODULE, "executeRename", "1");
        return 1;
    }

    // ===== 4. 设置模式 =====

    private static int executeSetMode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeSetMode");
        CommandSourceStack source = ctx.getSource();

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);
        String modeStr = StringArgumentType.getString(ctx, "mode");

        PetMode newMode;
        try {
            newMode = PetMode.fromString(modeStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.mode.invalid", modeStr));
            DebugLogger.exiting(MODULE, "executeSetMode", "0 (invalid mode)");
            return 0;
        }

        ValidationResult vr = validatePetAccess(ctx, internalName);
        if (vr.hasError()) {
            source.sendFailure(Component.translatable(vr.errorKey(), vr.errorArgs()));
            DebugLogger.exiting(MODULE, "executeSetMode", "0 (access denied)");
            return 0;
        }

        PetEntry entry = vr.entry();
        ServerPlayer player = source.getPlayerOrException();
        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        // 纯元数据修改：更新注册表
        PetEntry updated = entry.withMode(newMode);
        state.updatePet(updated);

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.mode.set",
                internalName, newMode.getSerializedName()), false);

        DebugLogger.info(MODULE, "设置模式: [%s] -> %s", internalName, newMode);
        DebugLogger.exiting(MODULE, "executeSetMode", "1");
        return 1;
    }

    // ===== 5. 管理信任列表 =====

    private static int executeTrustAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeTrustAdd");
        CommandSourceStack source = ctx.getSource();

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);

        ValidationResult vr = validatePetAccess(ctx, internalName);
        if (vr.hasError()) {
            source.sendFailure(Component.translatable(vr.errorKey(), vr.errorArgs()));
            DebugLogger.exiting(MODULE, "executeTrustAdd", "0 (access denied)");
            return 0;
        }

        PetEntry entry = vr.entry();
        ServerPlayer player = source.getPlayerOrException();

        // 解析目标玩家
        Collection<NameAndId> profiles;
        try {
            profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        } catch (Exception e) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.player_not_found"));
            DebugLogger.exiting(MODULE, "executeTrustAdd", "0 (player not found)");
            return 0;
        }

        if (profiles.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.player_not_found"));
            DebugLogger.exiting(MODULE, "executeTrustAdd", "0 (no player)");
            return 0;
        }

        NameAndId targetProfile = profiles.iterator().next();
        UUID targetUUID = targetProfile.id();

        // 禁止添加自己
        if (targetUUID.equals(entry.ownerUUID())) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.add_owner"));
            DebugLogger.exiting(MODULE, "executeTrustAdd", "0 (add owner)");
            return 0;
        }

        // 检查是否已存在
        if (entry.trustedPlayers().contains(targetUUID)) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.already_added"));
            DebugLogger.exiting(MODULE, "executeTrustAdd", "0 (already added)");
            return 0;
        }

        // 更新信任列表
        Set<UUID> newTrust = new HashSet<>(entry.trustedPlayers());
        newTrust.add(targetUUID);
        PetEntry updated = entry.withTrustedPlayers(newTrust);
        PetGlobalState state = PetGlobalState.get(player.level().getServer());
        state.updatePet(updated);

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.trust.added",
                targetProfile.name(), internalName), false);

        DebugLogger.info(MODULE, "信任添加: [%s] +%s", internalName, targetUUID);
        DebugLogger.exiting(MODULE, "executeTrustAdd", "1");
        return 1;
    }

    private static int executeTrustRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeTrustRemove");
        CommandSourceStack source = ctx.getSource();

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);

        ValidationResult vr = validatePetAccess(ctx, internalName);
        if (vr.hasError()) {
            source.sendFailure(Component.translatable(vr.errorKey(), vr.errorArgs()));
            DebugLogger.exiting(MODULE, "executeTrustRemove", "0 (access denied)");
            return 0;
        }

        PetEntry entry = vr.entry();
        ServerPlayer player = source.getPlayerOrException();

        Collection<NameAndId> profiles;
        try {
            profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        } catch (Exception e) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.player_not_found"));
            DebugLogger.exiting(MODULE, "executeTrustRemove", "0 (player not found)");
            return 0;
        }

        if (profiles.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.player_not_found"));
            DebugLogger.exiting(MODULE, "executeTrustRemove", "0 (no player)");
            return 0;
        }

        NameAndId targetProfile = profiles.iterator().next();
        UUID targetUUID = targetProfile.id();

        // 禁止移除主人
        if (targetUUID.equals(entry.ownerUUID())) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.remove_owner"));
            DebugLogger.exiting(MODULE, "executeTrustRemove", "0 (remove owner)");
            return 0;
        }

        // 检查是否在信任列表中
        if (!entry.trustedPlayers().contains(targetUUID)) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.not_found"));
            DebugLogger.exiting(MODULE, "executeTrustRemove", "0 (not found)");
            return 0;
        }

        // 更新信任列表
        Set<UUID> newTrust = new HashSet<>(entry.trustedPlayers());
        newTrust.remove(targetUUID);
        PetEntry updated = entry.withTrustedPlayers(newTrust);
        PetGlobalState state = PetGlobalState.get(player.level().getServer());
        state.updatePet(updated);

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.trust.removed",
                targetProfile.name(), internalName), false);

        DebugLogger.info(MODULE, "信任移除: [%s] -%s", internalName, targetUUID);
        DebugLogger.exiting(MODULE, "executeTrustRemove", "1");
        return 1;
    }

    private static int executeTrustList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeTrustList");
        CommandSourceStack source = ctx.getSource();

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);

        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.command.player_only"));
            DebugLogger.exiting(MODULE, "executeTrustList", "0 (console)");
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        Optional<PetEntry> optEntry = state.findByInternalName(internalName);
        if (optEntry.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.not_found", internalName));
            DebugLogger.exiting(MODULE, "executeTrustList", "0 (not found)");
            return 0;
        }

        PetEntry entry = optEntry.get();

        // 只有主人和信任玩家可以查看信任列表
        if (!entry.isTrustedOrOwner(player.getUUID())) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.not_owner"));
            DebugLogger.exiting(MODULE, "executeTrustList", "0 (no permission)");
            return 0;
        }

        Set<UUID> trusted = entry.trustedPlayers();
        if (trusted.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.pet.trust.empty", internalName), false);
            DebugLogger.exiting(MODULE, "executeTrustList", "1 (empty)");
            return 1;
        }

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.trust.list_header", internalName, trusted.size()), false);

        MinecraftServer server = player.level().getServer();
        int index = 1;
        for (UUID uuid : trusted) {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
            String name = (onlinePlayer != null)
                    ? onlinePlayer.getName().getString()
                    : uuid.toString().substring(0, 8) + "...";
            final int idx = index;
            final String playerName = name;
            source.sendSuccess(() -> Component.literal(
                    String.format(" %d. §e%s", idx, playerName)), false);
            index++;
        }

        DebugLogger.exiting(MODULE, "executeTrustList", "1");
        return 1;
    }

    // ===== 6. 高亮追踪 =====

    private static int executeHighlight(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeHighlight");
        CommandSourceStack source = ctx.getSource();

        if (!source.isPlayer()) {
            source.sendFailure(Component.translatable("youzaiworldcore.message.command.player_only"));
            DebugLogger.exiting(MODULE, "executeHighlight", "0 (console)");
            return 0;
        }

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);
        ServerPlayer player = source.getPlayerOrException();
        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        Optional<PetEntry> optEntry = state.findByInternalName(internalName);
        if (optEntry.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.not_found", internalName));
            DebugLogger.exiting(MODULE, "executeHighlight", "0 (not found)");
            return 0;
        }

        PetEntry entry = optEntry.get();

        // 权限校验：信任玩家及以上可用
        if (!entry.isTrustedOrOwner(player.getUUID())) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.not_trusted_or_owner"));
            DebugLogger.exiting(MODULE, "executeHighlight", "0 (no permission)");
            return 0;
        }

        // 实体交互类命令：检查实体是否已加载
        ServerLevel level = player.level();
        Entity entity = level.getEntity(entry.entityUUID());
        if (!(entity instanceof Wolf wolf)) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.not_loaded"));
            DebugLogger.exiting(MODULE, "executeHighlight", "0 (not loaded)");
            return 0;
        }

        // 施加发光效果，持续 5 秒（100 ticks）
        wolf.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING, 100, 0, false, false));

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.highlight.success", internalName), false);

        DebugLogger.info(MODULE, "高亮: [%s]", internalName);
        DebugLogger.exiting(MODULE, "executeHighlight", "1");
        return 1;
    }

    // ===== 7. 放生 =====

    private static int executeReleaseLife(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeReleaseLife", "force=" + force);
        CommandSourceStack source = ctx.getSource();

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);

        ValidationResult vr = validatePetAccess(ctx, internalName);
        if (vr.hasError()) {
            source.sendFailure(Component.translatable(vr.errorKey(), vr.errorArgs()));
            DebugLogger.exiting(MODULE, "executeReleaseLife", "0 (access denied)");
            return 0;
        }

        PetEntry entry = vr.entry();
        ServerPlayer player = source.getPlayerOrException();
        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        if (!force) {
            // 需要确认
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.release.confirm", internalName));
            DebugLogger.exiting(MODULE, "executeReleaseLife", "0 (need confirm)");
            return 0;
        }

        // 检查实体是否已加载
        ServerLevel level = player.level();
        Entity entity = level.getEntity(entry.entityUUID());
        boolean entityLoaded = entity instanceof Wolf;

        if (entityLoaded) {
            Wolf wolf = (Wolf) entity;
            // 从注册表删除
            state.removePet(entry.entityUUID());
            // 清空实体 PersistentData
            wolf.removeTag(PetInternalTags.TAG_PET_MARKER);
            new HashSet<>(wolf.entityTags()).stream()
                    .filter(t -> t.startsWith(PetInternalTags.TAG_INTERNAL_NAME_PREFIX))
                    .forEach(wolf::removeTag);
            // 重置显示名称
            wolf.setCustomName(null);
            wolf.setCustomNameVisible(false);
            // 重置愤怒等级
            wolf.setPersistentAngerEndTime(0);
            if (wolf.getPersistentAngerTarget() != null) {
                wolf.setPersistentAngerTarget(null);
            }
            // ==== 将驯服狗还原为野生狼 ====
            // 取消坐下状态
            wolf.setOrderedToSit(false);
            // 取消驯服标记并重置属性（setTame(false, true) 会调用 applyTamingSideEffects，
            // 将最大生命值从 40 降回野生狼的 8）
            wolf.setTame(false, true);
        } else {
            // 实体未加载：仅从注册表删除
            state.removePet(entry.entityUUID());
            // 记录运维日志
            DebugLogger.info(MODULE, "离线放生: [%s] UUID=%s (实体未加载)",
                    entry.internalName(), entry.entityUUID());
        }

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.release.success", internalName), true);

        DebugLogger.info(MODULE, "放生: [%s], force=%s, entityLoaded=%s",
                internalName, force, entityLoaded);
        DebugLogger.exiting(MODULE, "executeReleaseLife", "1");
        return 1;
    }

    // ===== 8. 转让所有权 =====

    private static int executeTransfer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeTransfer");
        CommandSourceStack source = ctx.getSource();

        String internalName = StringArgumentType.getString(ctx, "internalName").toUpperCase(Locale.ROOT);

        ValidationResult vr = validatePetAccess(ctx, internalName);
        if (vr.hasError()) {
            source.sendFailure(Component.translatable(vr.errorKey(), vr.errorArgs()));
            DebugLogger.exiting(MODULE, "executeTransfer", "0 (access denied)");
            return 0;
        }

        PetEntry entry = vr.entry();
        ServerPlayer player = source.getPlayerOrException();

        // 解析新主人
        Collection<NameAndId> newOwnerProfiles;
        try {
            newOwnerProfiles = GameProfileArgument.getGameProfiles(ctx, "newOwner");
        } catch (Exception e) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.player_not_found"));
            DebugLogger.exiting(MODULE, "executeTransfer", "0 (player not found)");
            return 0;
        }

        if (newOwnerProfiles.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.trust.player_not_found"));
            DebugLogger.exiting(MODULE, "executeTransfer", "0 (no player)");
            return 0;
        }

        NameAndId newOwnerProfile = newOwnerProfiles.iterator().next();
        UUID newOwnerUUID = newOwnerProfile.id();

        // 不能转让给自己
        if (newOwnerUUID.equals(entry.ownerUUID())) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.transfer.to_self"));
            DebugLogger.exiting(MODULE, "executeTransfer", "0 (to self)");
            return 0;
        }

        PetGlobalState state = PetGlobalState.get(player.level().getServer());

        // 执行转让：
        // 1. 更新主人
        // 2. 清空信任列表
        // 3. 将原主人加入信任列表
        Set<UUID> newTrust = new HashSet<>();
        newTrust.add(entry.ownerUUID()); // 原主人成为信任成员
        PetEntry updated = entry.withOwner(newOwnerUUID, newTrust);
        state.updatePet(updated);

        // 如果实体已加载，同步修改（EntityReference 无法在运行时直接修改）
        // 下次实体加载时会从注册表同步

        // 发送通知
        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.transfer.success",
                internalName, newOwnerProfile.name()), true);

        // 通知新主人
        ServerPlayer newOwner = player.level().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner != null && !newOwner.equals(player)) {
            newOwner.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.pet.transfer.notify",
                    internalName, player.getName().getString()));
        }

        DebugLogger.info(MODULE, "转让: [%s] 原主人=%s -> 新主人=%s",
                internalName, entry.ownerUUID(), newOwnerUUID);
        DebugLogger.exiting(MODULE, "executeTransfer", "1");
        return 1;
    }

    // ===== 管理命令 =====

    private static int executeAdminRestore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DebugLogger.entering(MODULE, "executeAdminRestore");
        CommandSourceStack source = ctx.getSource();

        List<Path> backups = PetBackupManager.listBackups();
        if (backups.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.admin.no_backups"));
            DebugLogger.exiting(MODULE, "executeAdminRestore", "0 (no backups)");
            return 0;
        }

        // 使用最新的备份
        Path latest = backups.get(backups.size() - 1);
        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.admin.restoring", latest.getFileName().toString()), false);

        Map<UUID, PetEntry> restored = PetBackupManager.restoreFromBackup(latest);
        if (restored.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "youzaiworldcore.message.pet.admin.restore_failed"));
            DebugLogger.exiting(MODULE, "executeAdminRestore", "0 (restore failed)");
            return 0;
        }

        PetGlobalState state = PetGlobalState.get(source.getServer());
        state.restoreFromSnapshot(restored);

        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.admin.restore_success",
                restored.size()), true);

        DebugLogger.info(MODULE, "管理员从备份恢复: %d 条记录", restored.size());
        DebugLogger.exiting(MODULE, "executeAdminRestore", "1");
        return 1;
    }

    private static int executeAdminBackupList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();

        List<Path> backups = PetBackupManager.listBackups();
        if (backups.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "youzaiworldcore.message.pet.admin.no_backups"), false);
            return 0;
        }

        for (Path backup : backups) {
            try {
                long size = Files.size(backup);
                source.sendSuccess(() -> Component.literal(
                        String.format(" §7- %s §8(%d bytes)", backup.getFileName().toString(), size)), false);
            } catch (IOException e) {
                source.sendSuccess(() -> Component.literal(
                        String.format(" §7- %s", backup.getFileName().toString())), false);
            }
        }

        return 1;
    }

    private static int executeAdminBackupInterval(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");

        PetModuleConfig.setBackupIntervalSeconds(seconds);
        source.sendSuccess(() -> Component.translatable(
                "youzaiworldcore.message.pet.admin.backup_interval_set", seconds), true);

        return 1;
    }
}
