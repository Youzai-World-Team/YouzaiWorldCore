package top.csituka.youzaiworldcore.pet;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.*;

/**
 * 宠物事件核心处理逻辑 — 由各 Mixin 和 Fabric 事件处理器调用。
 * <p>
 * 所有操作均以全局注册表为唯一真实数据源，确保数据一致性。
 * 方法按设计文档中定义的 8 个全局事件组织。
 * </p>
 */
public final class PetEventHandler {

    private static final String MODULE = "PetEventHandler";

    private PetEventHandler() {
    }

    // ============================
    // 事件 1：驯服时自动注册
    // ============================

    /**
     * 当狼被驯服时调用。
     * <p>
     * 生成唯一内部名称，写入全局注册表，设置显示名称。
     * </p>
     *
     * @param wolf   被驯服的狼
     * @param player 驯服该狼的玩家
     * @param level  服务端世界
     */
    public static void onTame(Wolf wolf, ServerPlayer player, ServerLevel level) {
        DebugLogger.entering(MODULE, "onTame", "wolf=" + wolf.getUUID() + ", player=" + player.getName().getString());

        PetGlobalState state = PetGlobalState.get(level.getServer());

        // 生成唯一内部名称
        String internalName = PetUtils.generateUniqueInternalName(level.getServer());

        // 创建宠物条目
        PetEntry entry = PetEntry.createNew(internalName, wolf.getUUID(), player.getUUID());

        // 写入全局注册表
        try {
            state.addPet(entry);
        } catch (IllegalArgumentException e) {
            DebugLogger.error(MODULE, "添加宠物到注册表失败: %s", e.getMessage());
            return;
        }

        // 设置显示名称
        wolf.setCustomName(Component.literal(internalName));
        wolf.setCustomNameVisible(true);

        // 用实体标签标记为宠物（用于实体加载时快速识别）
        wolf.addTag(PetInternalTags.TAG_PET_MARKER);
        wolf.addTag(PetInternalTags.internalNameTag(internalName));

        DebugLogger.info(MODULE, "宠物驯服注册成功: [%s] 主人=%s", internalName, player.getName().getString());
        DebugLogger.exiting(MODULE, "onTame");
    }

    // ============================
    // 事件 2：繁殖继承
    // ============================

    /**
     * 当两只宠物狼繁殖时调用。
     *
     * @param parent1   父母方 1
     * @param parent2   父母方 2
     * @param baby      幼狼
     * @param breeder   触发繁殖的玩家
     * @param level     服务端世界
     */
    public static void onBreed(Wolf parent1, Wolf parent2, Wolf baby,
                                ServerPlayer breeder, ServerLevel level) {
        DebugLogger.entering(MODULE, "onBreed",
                "父母=" + parent1.getUUID() + "/" + parent2.getUUID()
                        + ", 触发玩家=" + breeder.getName().getString());

        PetGlobalState state = PetGlobalState.get(level.getServer());

        // 1. 检查父母双方是否均为宠物
        Optional<PetEntry> p1Opt = state.findByEntityUUID(parent1.getUUID());
        Optional<PetEntry> p2Opt = state.findByEntityUUID(parent2.getUUID());

        if (p1Opt.isEmpty() || p2Opt.isEmpty()) {
            // 一方为野生狼 → 取消繁殖（由预拦截处理，此处不应到达）
            DebugLogger.warn(MODULE, "繁殖校验未通过：父母之一非宠物");
            DebugLogger.exiting(MODULE, "onBreed", "cancelled");
            return;
        }

        PetEntry p1 = p1Opt.get();
        PetEntry p2 = p2Opt.get();

        // 2. 检查触发玩家是否在双亲的有效信任集中
        Set<UUID> effectiveTrust = new HashSet<>();
        effectiveTrust.addAll(p1.getEffectiveTrustSet());
        effectiveTrust.addAll(p2.getEffectiveTrustSet());

        if (!effectiveTrust.contains(breeder.getUUID())) {
            breeder.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.pet.breed.not_trusted"));
            DebugLogger.info(MODULE, "繁殖被拒：玩家 %s 不在双亲信任集中", breeder.getName().getString());
            DebugLogger.exiting(MODULE, "onBreed", "not_trusted");
            return;
        }

        // 3. 通过校验 → 创建幼狼条目
        String internalName = PetUtils.generateUniqueInternalName(level.getServer());

        // 信任列表并集（不含主人自身）
        Set<UUID> unionTrust = new HashSet<>(p1.trustedPlayers());
        unionTrust.addAll(p2.trustedPlayers());

        // 若信任列表并集过大（>50），仅保留双亲主人
        Set<UUID> initialTrust;
        if (unionTrust.size() > 50) {
            initialTrust = new HashSet<>();
            initialTrust.add(p1.ownerUUID());
            initialTrust.add(p2.ownerUUID());
            initialTrust.remove(breeder.getUUID()); // 主人不记录在信任列表中
        } else {
            initialTrust = unionTrust;
        }

        // 幼狼主人 = 触发繁殖的玩家
        PetEntry babyEntry = new PetEntry(
                internalName,
                baby.getUUID(),
                breeder.getUUID(),
                internalName,
                initialTrust,
                PetMode.COMPANIONSHIP,
                System.currentTimeMillis()
        );

        try {
            state.addPet(babyEntry);
        } catch (IllegalArgumentException e) {
            DebugLogger.error(MODULE, "繁殖：添加幼狼到注册表失败: %s", e.getMessage());
            return;
        }

        // 设置显示名称
        baby.setCustomName(Component.literal(internalName));
        baby.setCustomNameVisible(true);
        baby.addTag(PetInternalTags.TAG_PET_MARKER);
        baby.addTag(PetInternalTags.internalNameTag(internalName));

        breeder.sendSystemMessage(Component.translatable(
                "youzaiworldcore.message.pet.breed.success", internalName));

        DebugLogger.info(MODULE, "繁殖成功: 幼狼 [%s], 主人=%s, 信任=%d 人",
                internalName, breeder.getName().getString(), initialTrust.size());
        DebugLogger.exiting(MODULE, "onBreed");
    }

    // ============================
    // 事件 3：死亡自动移除
    // ============================

    /**
     * 当宠物狼死亡时调用 — 从全局注册表删除。
     *
     * @param wolf  死亡的狼
     * @param level 服务端世界
     */
    public static void onDeath(Wolf wolf, ServerLevel level) {
        DebugLogger.entering(MODULE, "onDeath", "wolf=" + wolf.getUUID());

        PetGlobalState state = PetGlobalState.get(level.getServer());
        Optional<PetEntry> removed = state.removePet(wolf.getUUID());

        if (removed.isPresent()) {
            DebugLogger.info(MODULE, "宠物死亡已移除: [%s]", removed.get().internalName());
        }

        DebugLogger.exiting(MODULE, "onDeath");
    }

    // ============================
    // 事件 5：信任玩家交互
    // ============================

    /**
     * 处理右键点击狼的交互。
     *
     * @param player 执行交互的玩家
     * @param wolf   被交互的狼
     * @return true 如果交互已处理（取消原版行为）
     */
    public static boolean onInteract(Player player, Wolf wolf) {
        if (player.level().isClientSide()) {
            return false;
        }

        ServerLevel level = (ServerLevel) player.level();
        PetGlobalState state = PetGlobalState.get(level.getServer());

        Optional<PetEntry> optEntry = state.findByEntityUUID(wolf.getUUID());
        if (optEntry.isEmpty()) {
            return false; // 不是宠物，放行原版行为
        }

        PetEntry entry = optEntry.get();

        // 检查玩家是否在有效信任集中
        if (!entry.isTrustedOrOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.pet.interact.not_trusted"));
            return true; // 取消交互
        }

        // 信任玩家 → 放行原版交互（坐下/站起、喂食、治疗）
        return false;
    }

    // ============================
    // 事件 6：命名牌拦截
    // ============================

    /**
     * 检查并拦截命名牌使用。
     *
     * @param player 使用命名牌的玩家
     * @param wolf   被使用的狼
     * @return true 如果拦截了命名牌使用
     */
    public static boolean onNameTagUse(Player player, Wolf wolf) {
        if (player.level().isClientSide()) {
            return false;
        }

        ServerLevel level = (ServerLevel) player.level();
        PetGlobalState state = PetGlobalState.get(level.getServer());

        if (!state.isPet(wolf.getUUID())) {
            return false; // 野生狼，放行
        }

        // 拦截命名牌
        player.sendSystemMessage(Component.translatable(
                "youzaiworldcore.message.pet.nametag.blocked"));
        DebugLogger.info(MODULE, "拦截命名牌: 玩家=%s, 宠物=%s",
                player.getName().getString(), wolf.getUUID());

        return true; // 取消事件
    }

    // ============================
    // 事件 7：实体加载同步与垃圾回收
    // ============================

    /**
     * 实体加载时执行同步与垃圾回收。
     *
     * @param wolf  加载的狼实体
     * @param level 服务端世界
     */
    public static void onEntityLoad(Wolf wolf, ServerLevel level) {
        DebugLogger.debug(MODULE, "实体加载: wolf=%s", wolf.getUUID());

        PetGlobalState state = PetGlobalState.get(level.getServer());

        Optional<PetEntry> optEntry = state.findByEntityUUID(wolf.getUUID());

        if (optEntry.isPresent()) {
            // 注册表存在 → 强制同步到实体
            PetEntry entry = optEntry.get();
            syncRegistryToEntity(wolf, entry);
            DebugLogger.debug(MODULE, "实体同步: [%s] 显示名=%s", entry.internalName(), entry.displayName());
        } else if (wolf.entityTags().contains(PetInternalTags.TAG_PET_MARKER)) {
            // 注册表不存在但实体有标记 → 垃圾回收：清理残留数据
            cleanupEntityData(wolf);
            DebugLogger.info(MODULE, "垃圾回收: 清理孤儿实体 %s", wolf.getUUID());
        }
    }

    /**
     * 将注册表数据同步到实体对象。
     */
    private static void syncRegistryToEntity(Wolf wolf, PetEntry entry) {
        // 同步显示名称
        Component displayName = Component.literal(entry.displayName());
        if (!displayName.equals(wolf.getCustomName())) {
            wolf.setCustomName(displayName);
        }

        // 确保宠物标记和内部名称标签存在
        if (!wolf.entityTags().contains(PetInternalTags.TAG_PET_MARKER)) {
            wolf.addTag(PetInternalTags.TAG_PET_MARKER);
        }

        String expectedTag = PetInternalTags.internalNameTag(entry.internalName());
        boolean hasNameTag = wolf.entityTags().stream()
                .anyMatch(t -> t.startsWith(PetInternalTags.TAG_INTERNAL_NAME_PREFIX));
        if (!hasNameTag) {
            wolf.addTag(expectedTag);
        }
    }

    /**
     * 清理实体上的宠物残留数据。
     */
    private static void cleanupEntityData(Wolf wolf) {
        // 移除宠物标记
        wolf.removeTag(PetInternalTags.TAG_PET_MARKER);

        // 移除所有内部名称标签
        new HashSet<>(wolf.entityTags()).stream()
                .filter(t -> t.startsWith(PetInternalTags.TAG_INTERNAL_NAME_PREFIX))
                .forEach(wolf::removeTag);

        // 重置显示名称
        wolf.setCustomName(null);
        wolf.setCustomNameVisible(false);
    }

    // ============================
    // 事件 8：伤害拦截
    // ============================

    /**
     * 处理宠物狼受伤事件 — 拦截主人/信任玩家的误伤。
     *
     * @param wolf   受伤的狼
     * @param source 伤害来源
     * @param level  服务端世界
     * @return true 如果伤害应被取消
     */
    public static boolean onDamage(Wolf wolf, net.minecraft.world.damagesource.DamageSource source,
                                    ServerLevel level) {
        // 非玩家伤害 → 不拦截
        if (!(source.getEntity() instanceof Player damager)) {
            return false;
        }

        PetGlobalState state = PetGlobalState.get(level.getServer());
        Optional<PetEntry> optEntry = state.findByEntityUUID(wolf.getUUID());
        if (optEntry.isEmpty()) {
            return false; // 不是宠物，放行
        }

        PetEntry entry = optEntry.get();

        // 检查伤害来源是否在有效信任集中
        if (entry.isOwner(damager.getUUID())) {
            // 主人误伤
            damager.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.pet.damage.protect_owner"));
            // 强制清空愤怒状态
            wolf.setPersistentAngerEndTime(0);
            if (wolf.getPersistentAngerTarget() != null) {
                wolf.setPersistentAngerTarget(null);
            }
            return true; // 取消伤害
        } else if (entry.isTrustedOrOwner(damager.getUUID())) {
            // 信任玩家误伤
            damager.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.pet.damage.protect_trusted"));
            return true; // 取消伤害
        }

        // 陌生人 → 放行
        return false;
    }

    // ============================
    // 事件 4：跨维度传送跟随
    // ============================

    /**
     * 当玩家切换维度时，传送已加载的宠物跟随。
     *
     * @param player      切换维度的玩家
     * @param destination 目标维度
     */
    public static void onChangeDimension(ServerPlayer player, ServerLevel destination) {
        DebugLogger.debug(MODULE, "维度变化: 玩家=%s, 目标=%s",
                player.getName().getString(), destination.dimension().identifier());

        PetGlobalState state = PetGlobalState.get(player.level().getServer());
        List<PetEntry> pets = state.findByOwner(player.getUUID());

        if (pets.isEmpty()) {
            return;
        }

        int teleported = 0;
        for (PetEntry pet : pets) {
            // 遍历所有维度查找已加载的宠物
            for (ServerLevel level : player.level().getServer().getAllLevels()) {
                net.minecraft.world.entity.Entity entity = level.getEntity(pet.entityUUID());
                if (entity instanceof Wolf wolf) {
                    // 跳过坐下的狼
                    if (wolf.isInSittingPose()) {
                        continue;
                    }
                    // 传送至玩家位置
                    wolf.teleportTo(destination,
                            player.getX(), player.getY(), player.getZ(),
                            java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    teleported++;
                    DebugLogger.debug(MODULE, "传送宠物 [%s] 至玩家位置", pet.internalName());
                    break;
                }
            }
        }

        if (teleported > 0) {
            DebugLogger.info(MODULE, "维度切换: 传送 %d 只宠物随玩家 %s",
                    teleported, player.getName().getString());
        }
    }

    /**
     * 同维度内远距离传送 — 仅传送未坐下的已加载宠物。
     *
     * @param player 传送的玩家
     * @param level  当前世界
     */
    public static void onTeleportWithinDimension(ServerPlayer player, ServerLevel level) {
        PetGlobalState state = PetGlobalState.get(player.level().getServer());
        List<PetEntry> pets = state.findByOwner(player.getUUID());

        for (PetEntry pet : pets) {
            net.minecraft.world.entity.Entity entity = level.getEntity(pet.entityUUID());
            if (entity instanceof Wolf wolf && !wolf.isInSittingPose()) {
                wolf.teleportTo(level,
                        player.getX(), player.getY(), player.getZ(),
                        java.util.Set.of(), player.getYRot(), player.getXRot(), true);
            }
        }
    }
}
