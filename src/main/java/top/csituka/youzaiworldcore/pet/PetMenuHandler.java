package top.csituka.youzaiworldcore.pet;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.network.PetMenuRequestPayload;
import top.csituka.youzaiworldcore.network.PetMenuStatePayload;
import top.csituka.youzaiworldcore.pet.command.PetCommand;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 菜单复用既有命令树的权限与实体校验；仅开放列表、改名、模式和高亮。 */
@SuppressWarnings("null")
public final class PetMenuHandler {
    private PetMenuHandler() { }
    public static void handle(ServerPlayer player, PetMenuRequestPayload request) {
        if (player.hasDisconnected()) return;
        if (top.csituka.youzaiworldcore.account.util.AuthPlayerHelper.shouldBlockActions(player)) {
            ServerPlayNetworking.send(player, new PetMenuStatePayload(request.requestId(), false,
                    Component.translatable("screen.youzaiworldcore.pets.login_required"), List.of()));
            return;
        }
        var server = player.level().getServer();
        var source = player.createCommandSourceStack();
        if (!LuckPermsHelper.checkPermission(source, PetCommand.PERMISSION_PET_LIST, Commands.LEVEL_ALL)) {
            ServerPlayNetworking.send(player, new PetMenuStatePayload(request.requestId(), false,
                    Component.translatable("screen.youzaiworldcore.pets.denied"), List.of()));
            return;
        }
        Component[] feedback = { Component.empty() };
        boolean success = true;
        if (!"list".equals(request.action())) {
            var entry = PetGlobalState.get(server).findByInternalName(request.petId()).orElse(null);
            if (entry == null || !entry.isOwner(player.getUUID())) success = false;
            else {
                String command = switch (request.action()) {
                    case "rename" -> "set " + entry.internalName() + " rename " + quote(request.value());
                    case "mode" -> java.util.List.of("companionship", "hunting", "attack", "guard").contains(request.value())
                            ? "set " + entry.internalName() + " mode " + request.value() : null;
                    case "highlight" -> "highlight " + entry.internalName();
                    default -> null;
                };
                if (command == null) success = false;
                else try {
                    CommandSource collector = new CommandSource() {
                        public void sendSystemMessage(Component message) { feedback[0] = message; }
                        public boolean acceptsSuccess() { return true; }
                        public boolean acceptsFailure() { return true; }
                        public boolean shouldInformAdmins() { return false; }
                    };
                    success = server.getCommands().getDispatcher().execute("yzwc pet " + command, source.withSource(collector)) > 0;
                } catch (Exception e) {
                    success = false; feedback[0] = Component.translatable("screen.youzaiworldcore.pets.failed");
                    DebugLogger.exception("PetMenu", "execute", e);
                }
            }
        }
        if (!success && (feedback[0] == null || feedback[0].getString().isBlank())) feedback[0] = Component.translatable("screen.youzaiworldcore.pets.failed");
        List<PetMenuStatePayload.Entry> pets = PetGlobalState.get(server).findByOwner(player.getUUID()).stream()
                .sorted(java.util.Comparator.comparing(PetEntry::internalName)).limit(10000)
                .map(p -> new PetMenuStatePayload.Entry(p.internalName(), p.displayName(), p.mode().name().toLowerCase(java.util.Locale.ROOT))).toList();
        ServerPlayNetworking.send(player, new PetMenuStatePayload(request.requestId(), success, feedback[0], pets));
        DebugLogger.info("PetMenu", "菜单操作 %s：%s", request.action(), success);
    }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
