package top.csituka.youzaiworldcore.dimensionalinventories;

import com.mojang.serialization.Codec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.mixin.pet.PetShoulderAccessor;
import top.csituka.youzaiworldcore.pet.PetEntry;
import top.csituka.youzaiworldcore.pet.PetEventHandler;
import top.csituka.youzaiworldcore.pet.PetGlobalState;
import top.csituka.youzaiworldcore.pet.PetStationaryAccess;
import top.csituka.youzaiworldcore.pet.PetUtils;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.LoadedChunks;

/** 跨池收起的狼由原池存档保管，原版区块写盘确认后才移除回收用的实体数据。 */
@SuppressWarnings("null")
public final class PoolPets {
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PoolPets");
    private static final Identifier RETURN_ID = Identifier.fromNamespaceAndPath("youzaiworldcore", "pool_pet_return");
    private static final Identifier NAUTILUS = Identifier.withDefaultNamespace("nautilus");
    private static final AttachmentType<String> RETURN_TOKEN = AttachmentRegistry.createPersistent(RETURN_ID, Codec.STRING);
    private static final Map<MinecraftServer, Map<UUID, String>> UNSAVED_RETURNS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Set<UUID>> PENDING_RESTORES = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, ReleaseLocation>> PENDING_RELEASES = new WeakHashMap<>();

    private PoolPets() {}

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(PoolPets::loadPendingReleases);
    }

    static List<Mob> departingPets(ServerPlayer player, String pool) {
        var server = player.level().getServer();
        var wolves = new ArrayList<Mob>();
        for (var pet : PetGlobalState.get(server).findByOwner(player.getUUID())) {
            if (pet.isStationary()) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (pool.equals(DimensionPoolSettings.statePoolId(level.dimension()))
                        && level.getEntity(pet.entityUUID()) instanceof Mob wolf && pet.entityType().equals(PetUtils.petType(wolf))
                        && canFollow(wolf)) {
                    wolves.add(wolf);
                    break;
                }
            }
        }
        return wolves;
    }

    public static boolean canFollow(Mob pet) {
        return pet.isAlive() && !((PetStationaryAccess) pet).yzwc$isStationary()
                && (!(pet instanceof TamableAnimal animal)
                || (!animal.isOrderedToSit() && !animal.isInSittingPose()));
    }

    static void afterTransfer(ServerPlayer player, String originPool, List<Mob> departing, ServerLevel origin,
            Vec3 originPosition) {
        var server = player.level().getServer();
        for (Mob wolf : departing) {
            if (canFollow(wolf) && originPool.equals(DimensionPoolSettings.statePoolId(wolf.level().dimension()))) {
                try {
                    park(server, originPool, wolf);
                } catch (IOException | RuntimeException error) {
                    LOGGER.error("无法收起宠物 {}，留在原处", wolf.getUUID(), error);
                }
            }
        }
        parkShoulderPets(player, originPool, origin, originPosition, null);
        restore(player);
    }

    public static void stationShoulderPet(ServerPlayer owner, UUID id) {
        var level = owner.level();
        if (parkShoulderPets(owner, DimensionPoolSettings.statePoolId(level.dimension()), level, owner.position(), id)) {
            restore(owner);
        }
    }

    private static boolean parkShoulderPets(ServerPlayer player, String pool, ServerLevel origin, Vec3 position,
            @Nullable UUID onlyPet) {
        var access = (PetShoulderAccessor) player;
        boolean left = parkShoulderPet(player.getShoulderEntityLeft(), pool, origin, position, onlyPet);
        if (left) access.yzwc$setPetShoulderLeft(new CompoundTag());
        boolean right = parkShoulderPet(player.getShoulderEntityRight(), pool, origin, position, onlyPet);
        if (right) access.yzwc$setPetShoulderRight(new CompoundTag());
        return left || right;
    }

    private static boolean parkShoulderPet(CompoundTag data, String pool, ServerLevel origin, Vec3 position,
            @Nullable UUID onlyPet) {
        var id = data.read("UUID", UUIDUtil.CODEC).orElse(null);
        if (id == null || (onlyPet != null && !id.equals(onlyPet))) {
            return false;
        }
        var server = origin.getServer();
        var pet = PetGlobalState.get(server).findByEntityUUID(id).orElse(null);
        if (pet == null || !pet.entityType().toString().equals(data.getStringOr("id", ""))) {
            return false;
        }
        try {
            var existing = read(server, id);
            var token = data.getCompoundOrEmpty("fabric:attachments").getStringOr(RETURN_ID.toString(), "");
            if (existing != null && !existing.token.equals(token)) {
                throw new IOException("肩载宠物与存档不一致：" + id);
            }
            var body = data.copy();
            body.store("Pos", Vec3.CODEC, position);
            write(server, id, new StoredPet(pool, origin.dimension().identifier().toString(), newToken(),
                    body, null, pet.entityType()));
            var unsaved = UNSAVED_RETURNS.get(server);
            if (unsaved != null) unsaved.remove(id);
            return true;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("无法收起肩载宠物 {}", id, error);
            return false;
        }
    }

    private static void park(MinecraftServer server, String pool, Mob wolf) throws IOException {
        UUID id = wolf.getUUID();
        // 先核对原档，避免用当前实体覆盖一个无法解析的回收档。
        StoredPet previous = read(server, id);
        if (previous != null && !previous.token.equals(returnToken(wolf))) {
            throw new IOException("实体与宠物档案不一致：" + id);
        }
        write(server, id, new StoredPet(pool, wolf.level().dimension().identifier().toString(), newToken(),
                snapshot(wolf), null, PetUtils.petType(wolf)));
        wolf.stopRiding();
        wolf.ejectPassengers();
        wolf.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        var unsaved = UNSAVED_RETURNS.get(server);
        if (unsaved != null) {
            unsaved.remove(id);
        }
    }

    private static CompoundTag snapshot(Mob wolf) throws IOException {
        var problems = new ProblemReporter.Collector(wolf.problemPath());
        var output = TagValueOutput.createWithContext(problems, wolf.registryAccess());
        wolf.saveWithoutId(output);
        if (!problems.isEmpty()) {
            throw new IOException(problems.getReport());
        }
        CompoundTag body = output.buildResult();
        body.remove("Passengers");
        return body;
    }

    static void restore(ServerPlayer player) {
        if (!player.isAlive() || player.hasDisconnected()) {
            return;
        }
        var server = player.level().getServer();
        if (!canRestore(player)) {
            if (!PetGlobalState.get(server).findByOwner(player.getUUID()).isEmpty()) {
                PENDING_RESTORES.computeIfAbsent(server, ignored -> new HashSet<>()).add(player.getUUID());
            }
            return;
        }
        var waiting = PENDING_RESTORES.get(server);
        if (waiting != null) {
            waiting.remove(player.getUUID());
        }
        if (restoreInLoadedChunk(player)) {
            PENDING_RESTORES.computeIfAbsent(server, ignored -> new HashSet<>()).add(player.getUUID());
        }
    }

    static void tickPendingRestores(MinecraftServer server) {
        var waiting = PENDING_RESTORES.get(server);
        if (waiting == null) {
            return;
        }
        waiting.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.hasDisconnected() || !player.isAlive()) {
                return true;
            }
            if (!canRestore(player)) {
                return false;
            }
            return !restoreInLoadedChunk(player);
        });
    }

    private static boolean canRestore(ServerPlayer player) {
        return !AuthPlayerHelper.shouldBlockActions(player)
                && !AuthPlayerHelper.LOGIN_HALL_KEY.equals(player.level().dimension())
                && isChunkLoaded(player.level(), ChunkPos.containing(player.blockPosition()));
    }

    /** 返回 true 表示还有宠物需要等待实体区块。 */
    private static boolean restoreInLoadedChunk(ServerPlayer player) {
        var level = player.level();
        var server = level.getServer();
        String pool = DimensionPoolSettings.statePoolId(level.dimension());
        boolean waiting = false;
        for (var pet : PetGlobalState.get(server).findByOwner(player.getUUID())) {
            UUID id = pet.entityUUID();
            try {
                StoredPet stored = read(server, id);
                if (stored == null || stored.release != null || stored.body == null || !pool.equals(stored.pool)
                        || !pet.entityType().equals(stored.type)
                        || UNSAVED_RETURNS.getOrDefault(server, Map.of()).containsKey(id)) {
                    continue;
                }
                if (pet.isStationary()) {
                    // 宠物已暂存时也可切换模式。静止宠物回到原地，等原区块自然就绪。
                    var location = releaseLocation(id, stored);
                    var origin = DimensionPoolManager.getLoadedLevel(server, location.dimension);
                    if (origin == null || !isEntityChunkReady(origin, location.chunk)) {
                        waiting = true;
                        continue;
                    }
                    materialize(origin, stored, id, animal -> PetEventHandler.syncRegistryToEntity(animal, pet));
                    continue;
                }
                var playerChunk = ChunkPos.containing(player.blockPosition());
                if (!pet.hasCombatMode() && !isEntityChunkReady(level, playerChunk)) {
                    waiting = true;
                    continue;
                }
                var destination = recallPosition(player, stored.type);
                if (destination == null) {
                    waiting = true;
                    continue;
                }
                materialize(level, stored, id, animal -> {
                    PetEventHandler.syncRegistryToEntity(animal, pet);
                    animal.setDeltaMovement(Vec3.ZERO);
                    animal.resetFallDistance();
                    animal.snapTo(destination, player.getYRot(), 0);
                    if (animal instanceof Wolf wolf && isRecallAreaLoaded(player)) {
                        wolf.tryToTeleportToOwner();
                    }
                });
            } catch (IOException | RuntimeException error) {
                LOGGER.error("无法恢复宠物 {}，保留原档", id, error);
            }
        }
        return waiting;
    }

    private static @Nullable Vec3 recallPosition(ServerPlayer player, Identifier type) {
        if (!NAUTILUS.equals(type)) {
            return player.position();
        }
        var level = player.level();
        for (BlockPos pos : BlockPos.withinManhattan(player.blockPosition(), 5)) {
            var chunk = LoadedChunks.get(level, pos);
            if (chunk != null && chunk.getBlockState(pos).is(Blocks.WATER)
                    && chunk.getBlockState(pos.above()).is(Blocks.WATER)
                    && isEntityChunkReady(level, ChunkPos.containing(pos))) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return null;
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

    private static boolean isEntityChunkReady(ServerLevel level, ChunkPos pos) {
        return isChunkLoaded(level, pos) && level.areEntitiesActuallyLoadedAndTicking(pos);
    }

    /** 在确认放生前记录意图，等待原区块的实体数据自然就绪。 */
    public static boolean releaseParked(MinecraftServer server, PetEntry pet, @Nullable Mob loaded) throws IOException {
        UUID id = pet.entityUUID();
        boolean awaitingSave = UNSAVED_RETURNS.getOrDefault(server, Map.of()).containsKey(id);
        if (loaded != null && !awaitingSave) {
            return false;
        }
        StoredPet stored = read(server, id);
        if (stored == null || stored.body == null) {
            return false;
        }
        if (!stored.type.equals(pet.entityType())) {
            throw new IOException("宠物存档类型不匹配：" + id);
        }
        if ((loaded == null && awaitingSave) || (loaded != null && !stored.token.equals(returnToken(loaded)))) {
            throw new IOException("宠物仍在保存：" + id);
        }
        Release release = new Release(pet.internalName(), pet.tameTimestamp());
        StoredPet released = loaded == null
                ? new StoredPet(stored.pool, stored.dimension, stored.token, stored.body, release, stored.type)
                : new StoredPet(DimensionPoolSettings.statePoolId(loaded.level().dimension()),
                        loaded.level().dimension().identifier().toString(), newToken(), snapshot(loaded), release, stored.type);
        ReleaseLocation location = releaseLocation(id, released);
        write(server, id, released);
        PetGlobalState.get(server).removePet(id);
        if (loaded != null) {
            ((AttachmentTarget) loaded).setAttached(RETURN_TOKEN, released.token);
            PetEventHandler.releasePet(loaded);
            UNSAVED_RETURNS.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, released.token);
        } else if (!tryRelease(server, id, location)) {
            PENDING_RELEASES.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, location);
        }
        return true;
    }

    private static void loadPendingReleases(MinecraftServer server) {
        try (var files = Files.newDirectoryStream(ModPaths.parkedPets(server), "*.dat")) {
            for (var file : files) {
                String name = file.getFileName().toString();
                UUID id;
                try {
                    id = UUID.fromString(name.substring(0, name.length() - 4));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                try {
                    StoredPet stored = read(server, id);
                    if (stored == null || stored.release == null) {
                        continue;
                    }
                    var state = PetGlobalState.get(server);
                    var pet = state.findByEntityUUID(id).orElse(null);
                    // 放生记录只对应当时的登记。野生狼再次驯服会建立新的登记。
                    if (pet != null) {
                        if (!stored.release.matches(pet)) {
                            if (stored.body != null) {
                                // 新登记已落盘而实体尚未落盘时，按新主人的普通召回流程恢复。
                                write(server, id, new StoredPet(stored.pool, stored.dimension, stored.token,
                                        stored.body, null, stored.type));
                            }
                            continue;
                        }
                        state.removePet(id);
                        Mob wolf = PetUtils.findLoadedPet(server, pet);
                        if (wolf != null) {
                            PetEventHandler.releasePet(wolf);
                        }
                    }
                    if (stored.body != null) {
                        PENDING_RELEASES.computeIfAbsent(server, ignored -> new HashMap<>())
                                .put(id, releaseLocation(id, stored));
                    }
                } catch (IOException | RuntimeException error) {
                    LOGGER.error("无法读取宠物 {} 的放生记录，保留原档", id, error);
                }
            }
        } catch (NoSuchFileException ignored) {
            // 当前存档没有暂存宠物。
        } catch (IOException | RuntimeException error) {
            LOGGER.error("无法读取宠物放生记录目录", error);
        }
    }

    static void tickPendingReleases(MinecraftServer server) {
        var pending = PENDING_RELEASES.get(server);
        if (pending != null) {
            pending.entrySet().removeIf(entry -> tryRelease(server, entry.getKey(), entry.getValue()));
        }
    }

    private static ReleaseLocation releaseLocation(UUID id, StoredPet stored) throws IOException {
        Vec3 pos = stored.body.read("Pos", Vec3.CODEC)
                .filter(value -> Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z))
                .orElseThrow(() -> new IOException("宠物存档没有有效的原坐标：" + id));
        return new ReleaseLocation(stored.dimension, ChunkPos.containing(BlockPos.containing(pos)));
    }

    private static boolean tryRelease(MinecraftServer server, UUID id, ReleaseLocation location) {
        ServerLevel level = DimensionPoolManager.getLoadedLevel(server, location.dimension);
        if (level == null || !isEntityChunkReady(level, location.chunk)) {
            return false;
        }
        try {
            StoredPet stored = read(server, id);
            if (stored == null || stored.release == null || stored.body == null) {
                return true;
            }
            if (stored.token.equals(UNSAVED_RETURNS.getOrDefault(server, Map.of()).get(id))) {
                return true; // 区块载入了上次已经写入的同版本实体。
            }
            return materialize(level, stored, id, PetEventHandler::releasePet) != null;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("无法在原区块放生宠物 {}，保留原档", id, error);
            return false;
        }
    }

    private static @Nullable Mob materialize(ServerLevel level, StoredPet stored, UUID id,
            java.util.function.Consumer<Mob> prepare) throws IOException {
        MinecraftServer server = level.getServer();
        for (ServerLevel world : server.getAllLevels()) {
            if (world.getEntity(id) != null) {
                return null;
            }
        }
        var type = BuiltInRegistries.ENTITY_TYPE.getValue(stored.type);
        if (type == null || !(type.create(level, EntitySpawnReason.LOAD) instanceof Mob wolf)
                || !stored.type.equals(PetUtils.petType(wolf))) {
            throw new IOException("无效的宠物实体类型：" + stored.type);
        }
        var problems = new ProblemReporter.Collector(wolf.problemPath());
        wolf.load(TagValueInput.create(problems, level.registryAccess(), stored.body));
        if (!problems.isEmpty() || !id.equals(wolf.getUUID())) {
            throw new IOException("宠物存档无效 " + id + "：" + problems.getReport());
        }
        String token = newToken();
        ((AttachmentTarget) wolf).setAttached(RETURN_TOKEN, token);
        prepare.accept(wolf);
        write(server, id, new StoredPet(stored.pool, stored.dimension, token, stored.body, stored.release, stored.type));
        if (level.addFreshEntity(wolf)) {
            UNSAVED_RETURNS.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, token);
            return wolf;
        }
        return null;
    }

    /** 比较区块里的副本与最近的一次收起或恢复，防止重启加载到旧区块中的狼。 */
    public static boolean canLoad(ServerLevel level, Mob wolf) {
        return canLoad(level, wolf.getUUID(), PetUtils.petType(wolf), returnToken(wolf));
    }

    public static boolean canLoadShoulder(ServerLevel level, CompoundTag data) {
        var id = data.read("UUID", UUIDUtil.CODEC).orElse(null);
        var type = Identifier.tryParse(data.getStringOr("id", ""));
        var token = data.getCompoundOrEmpty("fabric:attachments").getStringOr(RETURN_ID.toString(), "");
        return id == null || canLoad(level, id, type, token);
    }

    private static boolean canLoad(ServerLevel level, UUID id, @Nullable Identifier type, @Nullable String token) {
        try {
            var server = level.getServer();
            StoredPet stored = read(server, id);
            if (stored == null || !stored.type.equals(type)) {
                return true;
            }
            boolean current = stored.token.equals(token);
            if (current && stored.body != null) {
                UNSAVED_RETURNS.computeIfAbsent(server, ignored -> new HashMap<>()).put(id, stored.token);
            }
            return current;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("无法读取宠物 {} 的回收档，保留原版实体", id, error);
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
                write(server, id, new StoredPet(stored.pool, stored.dimension, token, null, stored.release, stored.type));
            }
            unsaved.remove(id);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("无法结算宠物 {} 的恢复档，下次保存时重试", id, error);
        }
    }

    private static @Nullable String returnToken(Mob wolf) {
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
        String bodyKey = root.contains("pet") ? "pet" : "wolf";
        var type = Identifier.tryParse(root.getStringOr("entity_type", PetEntry.WOLF_TYPE.toString()));
        if (pool.isEmpty() || Identifier.tryParse(dimension) == null || dimension.isEmpty() || token.isEmpty()
                || type == null || (root.contains(bodyKey) && root.getCompound(bodyKey).isEmpty())) {
            throw new IOException("无效的宠物回收档：" + id);
        }
        Release release = readRelease(root);
        if (release == null && root.contains("release")) {
            throw new IOException("无效的宠物放生记录：" + id);
        }
        return new StoredPet(pool, dimension, token, root.getCompound(bodyKey).orElse(null), release, type);
    }

    /** 显式还原登记时清除当前回收档中会撤销该登记的放生凭据，保留版本与实体。 */
    public static boolean clearRestoredRelease(CompoundTag root, PetEntry pet) {
        Release release = readRelease(root);
        if (release == null || !release.matches(pet)) {
            return false;
        }
        root.remove("release");
        return true;
    }

    private static @Nullable Release readRelease(CompoundTag root) {
        CompoundTag receipt = root.getCompound("release").orElse(null);
        if (receipt == null) {
            return null;
        }
        String name = receipt.getStringOr("name", "");
        var timestamp = receipt.getLong("tame_timestamp");
        return name.isEmpty() || timestamp.isEmpty() ? null : new Release(name, timestamp.get());
    }

    private static void write(MinecraftServer server, UUID id, StoredPet stored) throws IOException {
        var tag = new CompoundTag();
        tag.putString("pool", stored.pool);
        tag.putString("dimension", stored.dimension);
        tag.putString("token", stored.token);
        tag.putString("entity_type", stored.type.toString());
        if (stored.body != null) {
            tag.put("pet", stored.body);
        }
        if (stored.release != null) {
            var receipt = new CompoundTag();
            receipt.putString("name", stored.release.name);
            receipt.putLong("tame_timestamp", stored.release.tameTimestamp);
            tag.put("release", receipt);
        }
        DimensionPoolManager.writeAtomically(ModPaths.parkedPet(server, id), file -> NbtIo.writeCompressed(tag, file));
    }

    private record ReleaseLocation(String dimension, ChunkPos chunk) {}

    private record Release(String name, long tameTimestamp) {
        boolean matches(PetEntry pet) {
            return name.equals(pet.internalName()) && tameTimestamp == pet.tameTimestamp();
        }
    }

    private record StoredPet(String pool, String dimension, String token, @Nullable CompoundTag body,
            @Nullable Release release, Identifier type) {}
}
