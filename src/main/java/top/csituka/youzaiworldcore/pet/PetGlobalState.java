package top.csituka.youzaiworldcore.pet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 宠物全局注册表 — PersistentState 数据驱动。
 * <p>
 * 存储于主世界（Overworld）的 {@link SavedData} 中，是宠物模块的<b>唯一真实数据源</b>（Source of Truth）。
 * 维护两个索引：
 * <ul>
 *   <li>{@code entityMap} — 按实体 UUID 索引的宠物条目</li>
 *   <li>{@code nameIndex} — 按内部名称快速查找实体 UUID</li>
 * </ul>
 * 所有修改操作后立即调用 {@link #setDirty()}，确保服务器关闭时不丢失数据。
 * 此类的所有方法应在游戏主线程中调用，无需额外同步。
 * </p>
 */
public class PetGlobalState extends SavedData {

    private static final String MODULE = "PetGlobalState";

    /** 数据存储 ID */
    @SuppressWarnings("null")
    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "pet_registry");

    /** 序列化 Codec */
    @SuppressWarnings("null")
    private static final Codec<PetGlobalState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(
                    Codec.STRING.xmap(UUID::fromString, uuid -> uuid.toString()),
                    PetEntry.CODEC
            ).fieldOf("pets").forGetter(state -> state.entityMap)
    ).apply(instance, PetGlobalState::new));

    /** SavedDataType 描述符，用于 {@code ServerLevel.getDataStorage().computeIfAbsent(TYPE)} */
    @SuppressWarnings("null")
    public static final SavedDataType<PetGlobalState> TYPE = new SavedDataType<>(
            DATA_ID,
            (Supplier<PetGlobalState>) PetGlobalState::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    // ===== 运行时数据 =====

    /** 实体 UUID → PetEntry 的映射（主索引） */
    private final Map<UUID, PetEntry> entityMap;

    /** 内部名称 → 实体 UUID 的快速查找索引 */
    private final Map<String, UUID> nameIndex;

    /** 所有者 UUID → 该主人的所有宠物实体 UUID 集合（反向索引，未持久化） */
    private final Map<UUID, Set<UUID>> ownerIndex;

    // ===== 构造 =====

    private PetGlobalState() {
        this(new HashMap<>());
    }

    private PetGlobalState(Map<UUID, PetEntry> entityMap) {
        this.entityMap = new ConcurrentHashMap<>(entityMap);
        this.nameIndex = new ConcurrentHashMap<>();
        this.ownerIndex = new ConcurrentHashMap<>();
        rebuildIndexes();
        DebugLogger.info(MODULE, "PetGlobalState 初始化完成: %d 条宠物记录", this.entityMap.size());
    }

    // ===== 索引重建 =====

    /** 从 entityMap 重建 nameIndex 和 ownerIndex */
    private void rebuildIndexes() {
        nameIndex.clear();
        ownerIndex.clear();
        for (Map.Entry<UUID, PetEntry> entry : entityMap.entrySet()) {
            UUID entityUUID = entry.getKey();
            PetEntry pet = entry.getValue();
            nameIndex.put(pet.internalName(), entityUUID);

            ownerIndex.computeIfAbsent(pet.ownerUUID(), k -> ConcurrentHashMap.newKeySet())
                    .add(entityUUID);
        }
        DebugLogger.debug(MODULE, "索引重建完成: %d 个名称索引, %d 个所有者索引",
                nameIndex.size(), ownerIndex.size());
    }

    // ===== 访问方法 =====

    /**
     * 获取或创建 PetGlobalState 实例（从主世界数据存储）。
     *
     * @param server 当前 Minecraft 服务器实例
     * @return PetGlobalState 实例
     * @throws IllegalStateException 如果主世界不可用
     */
    @NotNull
    @SuppressWarnings("null")
    public static PetGlobalState get(@NotNull MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("主世界不可用，无法获取宠物注册表");
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * 通过内部名称查找宠物条目。
     *
     * @param internalName 内部名称（如 {@code DOGAB3F9}）
     * @return Optional 包含 PetEntry，若不存在则为空
     */
    public Optional<PetEntry> findByInternalName(@NotNull String internalName) {
        UUID entityUUID = nameIndex.get(internalName);
        if (entityUUID == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityMap.get(entityUUID));
    }

    /**
     * 通过实体 UUID 查找宠物条目。
     *
     * @param entityUUID 实体 UUID
     * @return Optional 包含 PetEntry，若不存在则为空
     */
    public Optional<PetEntry> findByEntityUUID(@NotNull UUID entityUUID) {
        return Optional.ofNullable(entityMap.get(entityUUID));
    }

    /**
     * 获取指定主人名下的所有宠物。
     *
     * @param ownerUUID 主人 UUID
     * @return 该主人的所有宠物条目列表（不可变）
     */
    @SuppressWarnings("null")
    public List<PetEntry> findByOwner(@NotNull UUID ownerUUID) {
        Set<UUID> uuids = ownerIndex.getOrDefault(ownerUUID, Collections.emptySet());
        return uuids.stream()
                .map(entityMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 获取所有宠物的列表。
     *
     * @return 所有宠物条目的不可变列表
     */
    public List<PetEntry> getAllPets() {
        return List.copyOf(entityMap.values());
    }

    /**
     * 获取宠物总数。
     *
     * @return 宠物数量
     */
    public int getPetCount() {
        return entityMap.size();
    }

    /**
     * 检查内部名称是否已被占用。
     *
     * @param internalName 内部名称
     * @return true 如果该名称已被使用
     */
    public boolean isNameTaken(@NotNull String internalName) {
        return nameIndex.containsKey(internalName);
    }

    // ===== 修改方法 =====

    /**
     * 添加一个新宠物到注册表。
     *
     * @param entry 宠物条目
     * @throws IllegalArgumentException 如果该实体 UUID 或内部名称已存在
     */
    public synchronized void addPet(@NotNull PetEntry entry) {
        DebugLogger.entering(MODULE, "addPet", "internalName=" + entry.internalName()
                + ", entityUUID=" + entry.entityUUID());

        if (entityMap.containsKey(entry.entityUUID())) {
            throw new IllegalArgumentException("实体 " + entry.entityUUID() + " 已存在于宠物注册表中");
        }
        if (nameIndex.containsKey(entry.internalName())) {
            throw new IllegalArgumentException("内部名称 " + entry.internalName() + " 已被占用");
        }

        entityMap.put(entry.entityUUID(), entry);
        nameIndex.put(entry.internalName(), entry.entityUUID());
        ownerIndex.computeIfAbsent(entry.ownerUUID(), k -> ConcurrentHashMap.newKeySet())
                .add(entry.entityUUID());

        setDirty();
        DebugLogger.info(MODULE, "宠物已注册: [%s] %s (主人=%s)",
                entry.internalName(), entry.displayName(), entry.ownerUUID());
        DebugLogger.exiting(MODULE, "addPet");
    }

    /**
     * 更新注册表中的宠物条目。
     *
     * @param entry 更新后的宠物条目
     * @throws IllegalArgumentException 如果实体 UUID 不存在于注册表中
     */
    public synchronized void updatePet(@NotNull PetEntry entry) {
        DebugLogger.entering(MODULE, "updatePet", "internalName=" + entry.internalName());

        PetEntry existing = entityMap.get(entry.entityUUID());
        if (existing == null) {
            throw new IllegalArgumentException("宠物 " + entry.entityUUID() + " 不存在于注册表中");
        }

        // 如果内部名称变了，更新索引
        if (!existing.internalName().equals(entry.internalName())) {
            nameIndex.remove(existing.internalName());
            nameIndex.put(entry.internalName(), entry.entityUUID());
        }

        // 如果主人变了，更新所有者索引
        if (!existing.ownerUUID().equals(entry.ownerUUID())) {
            Set<UUID> oldOwnerPets = ownerIndex.get(existing.ownerUUID());
            if (oldOwnerPets != null) {
                oldOwnerPets.remove(entry.entityUUID());
            }
            ownerIndex.computeIfAbsent(entry.ownerUUID(), k -> ConcurrentHashMap.newKeySet())
                    .add(entry.entityUUID());
        }

        entityMap.put(entry.entityUUID(), entry);

        setDirty();
        DebugLogger.info(MODULE, "宠物已更新: [%s] %s", entry.internalName(), entry.displayName());
        DebugLogger.exiting(MODULE, "updatePet");
    }

    /**
     * 从注册表中删除一个宠物。
     *
     * @param entityUUID 实体的 UUID
     * @return Optional 包含被删除的 PetEntry，若不存在则为空
     */
    public synchronized Optional<PetEntry> removePet(@NotNull UUID entityUUID) {
        DebugLogger.entering(MODULE, "removePet", "entityUUID=" + entityUUID);

        PetEntry removed = entityMap.remove(entityUUID);
        if (removed == null) {
            DebugLogger.exiting(MODULE, "removePet", "not_found");
            return Optional.empty();
        }

        nameIndex.remove(removed.internalName());

        Set<UUID> ownerPets = ownerIndex.get(removed.ownerUUID());
        if (ownerPets != null) {
            ownerPets.remove(entityUUID);
            if (ownerPets.isEmpty()) {
                ownerIndex.remove(removed.ownerUUID());
            }
        }

        setDirty();
        DebugLogger.info(MODULE, "宠物已移除: [%s] %s", removed.internalName(), removed.displayName());
        DebugLogger.exiting(MODULE, "removePet", "success");
        return Optional.of(removed);
    }

    /**
     * 检查一个实体 UUID 是否为注册表中的宠物。
     *
     * @param entityUUID 实体 UUID
     * @return true 如果该实体是已注册的宠物
     */
    public boolean isPet(@NotNull UUID entityUUID) {
        return entityMap.containsKey(entityUUID);
    }

    /**
     * 获取深拷贝的条目映射（用于备份快照）。
     *
     * @return 实体 UUID → PetEntry 的新 HashMap
     */
    public Map<UUID, PetEntry> getSnapshot() {
        return new HashMap<>(entityMap);
    }

    /**
     * 从快照恢复注册表数据（用于备份恢复）。
     *
     * @param snapshot 备份快照
     */
    public synchronized void restoreFromSnapshot(Map<UUID, PetEntry> snapshot) {
        DebugLogger.info(MODULE, "正在从快照恢复 %d 条宠物记录...", snapshot.size());
        this.entityMap.clear();
        this.entityMap.putAll(snapshot);
        rebuildIndexes();
        setDirty();
        DebugLogger.info(MODULE, "从快照恢复完成");
    }
}
