package top.csituka.youzaiworldcore.pet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 宠物元数据条目 — 全局注册表中的核心数据实体。
 * <p>
 * 每个被驯服的狼对应一个 {@link PetEntry}，记录其内部名称、所有者、
 * 信任列表、模式及驯服时间戳。此条目为系统的唯一真实数据源（Source of Truth），
 * 所有只读操作均基于此数据，避免直接访问实体对象。
 * </p>
 *
 * @param internalName  内部名称（如 {@code DOGAB3F9}），系统生成，永久不变
 * @param entityUUID    宠物狼的实体 UUID
 * @param ownerUUID     主人玩家的 UUID
 * @param displayName   显示名称（可自定义），默认为内部名称
 * @param trustedPlayers 信任玩家 UUID 集合
 * @param mode          当前战斗模式
 * @param tameTimestamp 驯服时间戳（毫秒）
 */
public record PetEntry(
        @NotNull String internalName,
        @NotNull UUID entityUUID,
        @NotNull UUID ownerUUID,
        @NotNull String displayName,
        @NotNull Set<UUID> trustedPlayers,
        @NotNull PetMode mode,
        long tameTimestamp
) {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final Codec<Set<UUID>> UUID_SET_CODEC = Codec.list(Codec.STRING)
            .xmap(
                    list -> {
                        Set<UUID> set = new HashSet<>();
                        for (String s : list) {
                            try {
                                set.add(UUID.fromString(s));
                            } catch (IllegalArgumentException ignored) {
                                // 跳过无法解析的 UUID
                            }
                        }
                        return set;
                    },
                    set -> set.stream().map(uuid -> uuid.toString()).toList()
            );

    @SuppressWarnings("null")
    public static final Codec<PetEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("internalName").forGetter(e -> e.internalName()),
            Codec.STRING.xmap(UUID::fromString, uuid -> uuid.toString()).fieldOf("entityUUID").forGetter(e -> e.entityUUID()),
            Codec.STRING.xmap(UUID::fromString, uuid -> uuid.toString()).fieldOf("ownerUUID").forGetter(e -> e.ownerUUID()),
            Codec.STRING.fieldOf("displayName").forGetter(e -> e.displayName()),
            UUID_SET_CODEC.fieldOf("trustedPlayers").forGetter(e -> e.trustedPlayers()),
            PetMode.CODEC.fieldOf("mode").forGetter(e -> e.mode()),
            Codec.LONG.fieldOf("tameTimestamp").forGetter(e -> e.tameTimestamp())
    ).apply(instance, PetEntry::new));

    /**
     * 创建一个新的 PetEntry（驯服时使用）。
     *
     * @param internalName 生成的内部名称
     * @param entityUUID   实体 UUID
     * @param ownerUUID    主人 UUID
     * @return 初始化的 PetEntry，默认模式为 COMPANIONSHIP
     */
    public static PetEntry createNew(String internalName, UUID entityUUID, UUID ownerUUID) {
        return new PetEntry(
                internalName,
                entityUUID,
                ownerUUID,
                internalName, // 默认显示名 = 内部名称
                new HashSet<>(),
                PetMode.COMPANIONSHIP,
                System.currentTimeMillis()
        );
    }

    /**
     * 获取格式化的驯服时间字符串。
     *
     * @return 格式为 "yyyy-MM-dd HH:mm" 的字符串
     */
    public String formattedTameTime() {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(tameTimestamp));
    }

    /**
     * 获取有效信任集（主人 ∪ 信任列表），用于权限判定。
     *
     * @return 包含主人 UUID 和所有信任玩家 UUID 的不可修改集合
     */
    public Set<UUID> getEffectiveTrustSet() {
        Set<UUID> result = new HashSet<>(trustedPlayers);
        result.add(ownerUUID);
        return Collections.unmodifiableSet(result);
    }

    /**
     * 判断指定玩家是否在有效信任集中（是主人或受信任的玩家）。
     *
     * @param playerUUID 玩家 UUID
     * @return true 如果该玩家在有效信任集中
     */
    public boolean isTrustedOrOwner(UUID playerUUID) {
        return ownerUUID.equals(playerUUID) || trustedPlayers.contains(playerUUID);
    }

    /**
     * 判断指定玩家是否是主人。
     *
     * @param playerUUID 玩家 UUID
     * @return true 如果该玩家是主人
     */
    public boolean isOwner(UUID playerUUID) {
        return ownerUUID.equals(playerUUID);
    }

    /**
     * 生成一个包含变更后的新 PetEntry（不可变模式）。
     *
     * @param ownerUUID 新的主人 UUID
     * @param trustedPlayers 新的信任列表
     * @return 新的 PetEntry 实例
     */
    public PetEntry withOwner(UUID ownerUUID, Set<UUID> trustedPlayers) {
        return new PetEntry(internalName, entityUUID, ownerUUID, displayName,
                new HashSet<>(trustedPlayers), mode, tameTimestamp);
    }

    /**
     * 生成一个包含变更后的新 PetEntry（不可变模式）。
     *
     * @param displayName 新的显示名称
     * @return 新的 PetEntry 实例
     */
    public PetEntry withDisplayName(String displayName) {
        return new PetEntry(internalName, entityUUID, ownerUUID, displayName,
                new HashSet<>(trustedPlayers), mode, tameTimestamp);
    }

    /**
     * 生成一个包含变更后的新 PetEntry（不可变模式）。
     *
     * @param mode 新的模式
     * @return 新的 PetEntry 实例
     */
    public PetEntry withMode(PetMode mode) {
        return new PetEntry(internalName, entityUUID, ownerUUID, displayName,
                new HashSet<>(trustedPlayers), mode, tameTimestamp);
    }

    /**
     * 生成一个包含变更后的新 PetEntry（不可变模式）。
     *
     * @param trustedPlayers 新的信任列表
     * @return 新的 PetEntry 实例
     */
    public PetEntry withTrustedPlayers(Set<UUID> trustedPlayers) {
        return new PetEntry(internalName, entityUUID, ownerUUID, displayName,
                new HashSet<>(trustedPlayers), mode, tameTimestamp);
    }
}
