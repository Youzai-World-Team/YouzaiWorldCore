package top.csituka.youzaiworldcore.skill;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.network.AttributeSyncPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

/**
 * 属性加点管理器。
 * 负责：属性模组应用/移除、加点请求处理、客户端同步、升级时发放技能点。
 */
@SuppressWarnings("null")
public class AttributeManager {

    /** 所有 AttributeModifier 共享的命名空间前缀 */
    private static final String MODIFIER_NS = "youzaiworldcore_attr_";

    public static void initialize() {
        DebugLogger.entering("AttributeManager", "initialize");

        // 玩家加入时应用属性并同步
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 在 tick 末尾执行，确保玩家完全加载
            server.execute(() -> {
                ServerPlayer player = handler.getPlayer();
                applyAllAttributes(player);
                syncToClient(player);
            });
        });

        DebugLogger.exiting("AttributeManager", "initialize");
    }

    // ==================== 加点处理 ====================

    /**
     * 处理玩家加点请求（由 C2S 数据包处理器调用）。
     *
     * @return true 表示加点成功
     */
    public static boolean handleUpgrade(ServerPlayer player, String attributeKey) {
        UUID uuid = player.getUUID();
        PlayerAttributeData data = PlayerAttributeStorage.getOrCreate(uuid, player.getName().getString());
        int playerLevel = AdventureLevelManager.getLevelFromExp(
                PlayerLevelStorage.getOrCreate(uuid, player.getName().getString()).totalExp);

        // 检查可用技能点
        if (data.skillPointsAvailable <= 0) return false;

        // 特殊约束：未满 20 级只能加抗性
        if (playerLevel < 20 && !"damageResistance".equals(attributeKey)) return false;

        // 执行加点
        int current = data.get(attributeKey);
        data.set(attributeKey, current + 1);
        data.skillPointsAvailable--;
        PlayerAttributeStorage.markDirty(uuid);

        // 应用到玩家
        applySingleAttribute(player, attributeKey, data.get(attributeKey));
        syncToClient(player);

        DebugLogger.info("AttributeManager",
                "玩家 %s 属性 %s: %d → %d (剩余技能点 %d)",
                player.getName().getString(), attributeKey, current, current + 1, data.skillPointsAvailable);
        return true;
    }

    // ==================== 应用属性到玩家 ====================

    /**
     * 将玩家的所有属性数据应用到 Minecraft 属性系统。
     */
    public static void applyAllAttributes(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAttributeData data = PlayerAttributeStorage.get(uuid);
        if (data == null) return;

        for (String key : PlayerAttributeData.ATTRIBUTE_KEYS) {
            applySingleAttribute(player, key, data.get(key));
        }
    }

    /**
     * 应用单项属性修饰符。先移除旧的，再添加新的。
     */
    private static void applySingleAttribute(ServerPlayer player, String key, int points) {
        if (points <= 0) return;

        switch (key) {
            case "maxHealth" -> applyModifier(player, Attributes.MAX_HEALTH, "max_health",
                    points * 1.0, AttributeModifier.Operation.ADD_VALUE);
            case "miningSpeed" -> applyModifier(player, Attributes.BLOCK_BREAK_SPEED, "mining_speed",
                    points * 0.01, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            case "movementSpeed" -> applyModifier(player, Attributes.MOVEMENT_SPEED, "movement_speed",
                    points * 0.01, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            case "jumpAmplitude" -> applyModifier(player, Attributes.JUMP_STRENGTH, "jump_strength",
                    points * 0.01, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            case "luck" -> applyModifier(player, Attributes.LUCK, "luck",
                    points * 1.0, AttributeModifier.Operation.ADD_VALUE);
            case "meleeDamage" -> applyModifier(player, Attributes.ATTACK_DAMAGE, "melee_damage",
                    points * 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            // healingAmplification, rangedDamage, damageResistance 由 Mixin 处理
        }

        // 最大生命值变更后同步实际血量
        if ("maxHealth".equals(key)) {
            float oldMax = player.getMaxHealth();
            float newMax = 20f + points;
            double ratio = newMax / oldMax;
            player.setHealth(player.getHealth() * (float) ratio);
        }
    }

    /**
     * 辅助：为玩家添加/替换单个属性修饰符。
     */
    private static void applyModifier(ServerPlayer player, Holder<Attribute> attribute,
                                       String suffix, double amount, AttributeModifier.Operation operation) {
        Identifier id = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, MODIFIER_NS + suffix);
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        // 移除旧修饰符
        instance.removeModifier(id);

        // 添加新修饰符（仅当点数 > 0）
        if (amount != 0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }

    // ==================== 客户端同步 ====================

    /**
     * 向客户端发送当前属性数据。
     */
    public static void syncToClient(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAttributeData data = PlayerAttributeStorage.getOrCreate(uuid, player.getName().getString());
        int playerLevel = AdventureLevelManager.getLevelFromExp(
                PlayerLevelStorage.getOrCreate(uuid, player.getName().getString()).totalExp);

        ServerPlayNetworking.send(player, new AttributeSyncPayload(
                data.skillPointsAvailable,
                data.maxHealth,
                data.healingAmplification,
                data.miningSpeed,
                data.movementSpeed,
                data.jumpAmplitude,
                data.luck,
                data.meleeDamage,
                data.rangedDamage,
                data.damageResistance,
                playerLevel
        ));
    }

    // ==================== 升级时发放技能点 ====================

    /**
     * 玩家升级时调用，发放 1 技能点。
     */
    public static void grantSkillPoint(UUID uuid, String username) {
        PlayerAttributeData data = PlayerAttributeStorage.getOrCreate(uuid, username);
        data.skillPointsAvailable++;
        PlayerAttributeStorage.markDirty(uuid);
    }

    // ==================== 获取属性加成值（供 Mixin 使用） ====================

    /**
     * 获取玩家某属性的当前点数（供 Mixin 读取，运行在服务端）。
     */
    public static int getAttributePoints(UUID uuid, String key) {
        PlayerAttributeData data = PlayerAttributeStorage.get(uuid);
        if (data == null) return 0;
        return data.get(key);
    }

    /** 获取玩家生命恢复加成倍率（1 + 点数 * 0.01） */
    public static float getHealingMultiplier(UUID uuid) {
        return 1.0f + getAttributePoints(uuid, "healingAmplification") * 0.01f;
    }

    /** 获取玩家远程伤害加成倍率（1 + 点数 * 0.02） */
    public static float getRangedDamageMultiplier(UUID uuid) {
        return 1.0f + getAttributePoints(uuid, "rangedDamage") * 0.02f;
    }

    /** 获取玩家伤害减免比例（点数 * 0.02，上限 0.8） */
    public static float getDamageReduction(UUID uuid) {
        return Math.min(0.8f, getAttributePoints(uuid, "damageResistance") * 0.02f);
    }
}
