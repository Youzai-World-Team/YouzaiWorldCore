package top.csituka.youzaiworldcore.dimensionalinventories;

import com.mojang.serialization.Codec;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.pet.PetGlobalState;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 跨池收起的狼由原池存档保管，原版区块写盘确认后才移除回收用的实体数据。 */
@SuppressWarnings("null")
public final class PoolPets {
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PoolPets");
    private static final Identifier RETURN_ID = Identifier.fromNamespaceAndPath("youzaiworldcore", "pool_pet_return");
    private static final AttachmentType<String> RETURN_TOKEN = AttachmentRegistry.createPersistent(RETURN_ID, Codec.STRING);
    private static final Map<MinecraftServer, Map<UUID, String>> UNSAVED_RETURNS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, String>> WAITING_FOR_CHUNK = new WeakHashMap<>();

    private PoolPets() {}

    public static void initialize() {
        // 在读取任何实体存档之前注册持久化附件。
    }

    static List<Wolf> departingPets(ServerPlayer player, String pool) {
        var server = player.level().getServer();
        var wolves = new ArrayList<Wolf>();
        for (var pet : PetGlobalState.get(server).findByOwner(player.getUUID())) {
            for (ServerLevel level : server.getAllLevels()) {
                if (pool.equals(DimensionPoolSettings.statePoolId(level.dimension()))
                        && level.getEntity(pet.entityUUID()) instanceof Wolf wolf && canFollow(wolf)) {
                    wolves.add(wolf);
                    break;
                }
            }
        }
        return wolves;
    }

    public static boolean canFollow(Wolf wolf) {
        return wolf.isAlive() && !wolf.isOrderedToSit() && !wolf.isInSittingPose();
    }

    static void afterTransfer(ServerPlayer player, String originPool, List<Wolf> departing) {
        var server = player.level().getServer();
        for (Wolf wolf : departing) {
            if (canFollow(wolf) && originPool.equals(DimensionPoolSettings.statePoolId(wolf.level().dimension()))) {
                try {
                    park(server, originPool, wolf);
                } catch (IOException | RuntimeException error) {
                    LOGGER.error("无法收起宠物 {}，留在原处", wolf.getUUID(), error);
                }
            }
        }
        restore(player);
    }

    private static void park(MinecraftServer server, String pool, Wolf wolf) throws IOException {
        UUID id = wolf.getUUID();
        // 先核对原档，避免用当前实体覆盖一个无法解析的回收档。
        StoredPet previous = read(server, id);
        if (previous != null && !previous.token.equals(returnToken(wolf))) {
            throw new IOException("实体与宠物档案不一致：" + id);
        }
        var problems = new ProblemReporter.Collector(wolf.problemPath());
        var output = TagValueOutput.createWithContext(problems, wolf.registryAccess());
        wolf.saveWithoutId(output);
        if (!problems.isEmpty()) {
            throw new IOException(problems.getReport());
        }
        CompoundTag body = output.buildResult();
        body.remove("Passengers");
        write(server, id, new StoredPet(pool, wolf.level().dimension().identifier().toString(), newToken(), body));
        wolf.stopRiding();
        wolf.ejectPassengers();
        wolf.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        var unsaved = UNSAVED_RETURNS.get(server);
        if (unsaved != null) {
            unsaved.remove(id);
        }
    }

    static void restore(ServerPlayer player) {
        if (!player.isAlive() || AuthPlayerHelper.shouldBlockActions(player)) {
            return;
        }
        var level = player.level();
        var server = level.getServer();
        String pool = DimensionPoolSettings.statePoolId(level.dimension());
        if (!isChunkLoaded(level, ChunkPos.containing(player.blockPosition()))) {
            if (!PetGlobalState.get(server).findByOwner(player.getUUID()).isEmpty()) {
                WAITING_FOR_CHUNK.computeIfAbsent(server, ignored -> new HashMap<>()).put(player.getUUID(), pool);
            }
            return;
        }
        var waiting = WAITING_FOR_CHUNK.get(server);
        if (waiting != null) {
            waiting.remove(player.getUUID());
        }
        restoreInLoadedChunk(player, pool);
    }

    static void tickPendingRestores(MinecraftServer server) {
        var waiting = WAITING_FOR_CHUNK.get(server);
        if (waiting == null) {
            return;
        }
        waiting.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected() || !player.isAlive()
                    || !entry.getValue().equals(DimensionPoolSettings.statePoolId(player.level().dimension()))) {
                return true;
            }
            if (AuthPlayerHelper.shouldBlockActions(player)
                    || !isChunkLoaded(player.level(), ChunkPos.containing(player.blockPosition()))) {
                return false;
            }
            restoreInLoadedChunk(player, entry.getValue());
            return true;
        });
    }

    private static void restoreInLoadedChunk(ServerPlayer player, String pool) {
        var level = player.level();
        var server = level.getServer();
        for (var pet : PetGlobalState.get(server).findByOwner(player.getUUID())) {
            UUID id = pet.entityUUID();
            try {
                StoredPet stored = read(server, id);
                if (stored != null && stored.body != null && pool.equals(stored.pool)
                        && !UNSAVED_RETURNS.getOrDefault(server, Map.of()).containsKey(id)) {
                    materialize(level, stored, id, wolf -> {
                        wolf.setOwnerReference(EntityReference.of(player.getUUID()));
                        wolf.setDeltaMovement(Vec3.ZERO);
                        wolf.resetFallDistance();
                        wolf.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
                        if (isRecallAreaLoaded(player)) {
                            wolf.tryToTeleportToOwner();
                        }
                    });
                }
            } catch (IOException | RuntimeException error) {
                LOGGER.error("无法恢复宠物 {}，保留原档", id, error);
            }
        }
    }

    private static boolean isRecallAreaLoaded(ServerPlayer player) {
        // 原版候选点距主人最多三格，寻路和碰撞还会检查候选点周围的方块。
        var pos = player.blockPosition();
        var min = ChunkPos.containing(pos.offset(-5, 0, -5));
        var max = ChunkPos.containing(pos.offset(5, 0, 5));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                if (!isChunkLoaded(player.level(), new ChunkPos(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isChunkLoaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null;
    }

    /** 放生暂存的宠物时，先把实体放回原来的维度，再由命令将它恢复为野生狼。 */
    public static @Nullable Wolf releaseParked(MinecraftServer server, UUID id) throws IOException {
        StoredPet stored = read(server, id);
        if (stored == null || stored.body == null) {
            return null;
        }
        for (ServerLevel world : server.getAllLevels()) {
            if (world.getEntity(id) instanceof Wolf wolf) {
                return wolf;
            }
        }
        if (UNSAVED_RETURNS.getOrDefault(server, Map.of()).containsKey(id)) {
            throw new IOException("宠物仍在保存：" + id);
        }
        ServerLevel level = DimensionPoolManager.getLoadedLevel(server, stored.dimension);
        if (level == null) {
            throw new IOException("宠物原维度不可用：" + stored.dimension);
        }
        Wolf wolf = materialize(level, stored, id, entity -> {});
        if (wolf == null) {
            throw new IOException("无法在原维度恢复宠物：" + id);
        }
        return wolf;
    }

    private static @Nullable Wolf materialize(ServerLevel level, StoredPet stored, UUID id,
            java.util.function.Consumer<Wolf> prepare) throws IOException {
        MinecraftServer server = level.getServer();
        for (ServerLevel world : server.getAllLevels()) {
            if (world.getEntity(id) != null) {
                return null;
            }
        }
        Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.LOAD);
        if (wolf == null) {
            return null;
        }
        var problems = new ProblemReporter.Collector(wolf.problemPath());
        wolf.load(TagValueInput.create(problems, level.registryAccess(), stored.body));
        if (!problems.isEmpty() || !id.equals(wolf.getUUID())) {
            throw new IOException("宠物存档无效 " + id + "：" + problems.getReport());
        }
        String token = newToken();
        ((AttachmentTarget) wolf).setAttached(RETURN_TOKEN, token);
        prepare.accept(wolf);
        write(server, id, new StoredPet(stored.pool, stored.dimension, token, stored.body));
        if (level.addFreshEntity(wolf)) {
            UNSAVED_RETURNS.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, token);
            return wolf;
        }
        return null;
    }

    /** 比较区块里的副本与最近的一次收起或恢复，防止重启加载到旧区块中的狼。 */
    public static boolean canLoad(ServerLevel level, Wolf wolf) {
        try {
            var server = level.getServer();
            UUID id = wolf.getUUID();
            StoredPet stored = read(server, id);
            if (stored == null) {
                return true;
            }
            boolean current = stored.token.equals(returnToken(wolf));
            if (current && stored.body != null) {
                UNSAVED_RETURNS.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, stored.token);
            }
            return current;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("无法读取宠物 {} 的回收档，保留区块中的实体", wolf.getUUID(), error);
            return true;
        }
    }

    public static void afterEntityWrite(MinecraftServer server, CompoundTag chunk,
            CompletableFuture<Void> write, java.util.function.Supplier<CompletableFuture<Void>> flush) {
        var unsaved = UNSAVED_RETURNS.get(server);
        if (unsaved == null || unsaved.isEmpty()) {
            return;
        }
        var saved = new HashMap<UUID, String>();
        for (Tag tag : chunk.getListOrEmpty("Entities")) {
            if (tag instanceof CompoundTag entity) {
                collectSavedReturns(entity, unsaved, saved);
            }
        }
        if (!saved.isEmpty()) {
            write.thenCompose(ignored -> flush.get()).thenRun(() -> server.execute(() ->
                    saved.forEach((id, token) -> finishReturn(server, id, token))));
        }
    }

    private static void collectSavedReturns(CompoundTag entity, Map<UUID, String> unsaved, Map<UUID, String> saved) {
        String token = entity.getCompoundOrEmpty("fabric:attachments").getStringOr(RETURN_ID.toString(), "");
        if (!token.isEmpty()) {
            entity.read("UUID", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> {
                if (token.equals(unsaved.get(id))) {
                    saved.put(id, token);
                }
            });
        }
        for (Tag tag : entity.getListOrEmpty("Passengers")) {
            if (tag instanceof CompoundTag passenger) {
                collectSavedReturns(passenger, unsaved, saved);
            }
        }
    }

    private static void finishReturn(MinecraftServer server, UUID id, String token) {
        var unsaved = UNSAVED_RETURNS.get(server);
        if (unsaved == null || !token.equals(unsaved.get(id))) {
            return;
        }
        try {
            StoredPet stored = read(server, id);
            if (stored != null && stored.body != null && token.equals(stored.token)) {
                write(server, id, new StoredPet(stored.pool, stored.dimension, token, null));
            }
            unsaved.remove(id);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("无法结算宠物 {} 的恢复档，下次保存时重试", id, error);
        }
    }

    private static @Nullable String returnToken(Wolf wolf) {
        return ((AttachmentTarget) wolf).getAttached(RETURN_TOKEN);
    }

    private static String newToken() {
        return UUID.randomUUID().toString();
    }

    private static @Nullable StoredPet read(MinecraftServer server, UUID id) throws IOException {
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(ModPaths.parkedPet(server, id), NbtAccounter.uncompressedQuota());
        } catch (NoSuchFileException ignored) {
            return null;
        }
        String pool = root.getStringOr("pool", "");
        String dimension = root.getStringOr("dimension", "");
        String token = root.getStringOr("token", "");
        if (pool.isEmpty() || Identifier.tryParse(dimension) == null || dimension.isEmpty() || token.isEmpty()
                || (root.contains("wolf") && root.getCompound("wolf").isEmpty())) {
            throw new IOException("无效的宠物回收档：" + id);
        }
        return new StoredPet(pool, dimension, token, root.getCompound("wolf").orElse(null));
    }

    private static void write(MinecraftServer server, UUID id, StoredPet stored) throws IOException {
        var tag = new CompoundTag();
        tag.putString("pool", stored.pool);
        tag.putString("dimension", stored.dimension);
        tag.putString("token", stored.token);
        if (stored.body != null) {
            tag.put("wolf", stored.body);
        }
        DimensionPoolManager.writeAtomically(ModPaths.parkedPet(server, id), file -> NbtIo.writeCompressed(tag, file));
    }

    private record StoredPet(String pool, String dimension, String token, @Nullable CompoundTag body) {}
}
