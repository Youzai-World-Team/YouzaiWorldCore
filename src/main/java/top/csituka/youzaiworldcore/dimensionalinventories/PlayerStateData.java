package top.csituka.youzaiworldcore.dimensionalinventories;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.*;

/**
 * 玩家在某个维度池中的完整状态快照。
 * <p>
 * 使用 Minecraft 26.2 的 DataComponent + CODEC 系统进行 ItemStack 序列化，
 * NBT 数据以 SNBT 字符串形式存储以确保 Gson 兼容性。
 * 包含：物品栏、盔甲、副手、末影箱、生命、饥饿、经验、分数、状态效果、
 * 以及离开时的维度与坐标（用于传送返回）。
 */
@SuppressWarnings("null")
public final class PlayerStateData {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/PlayerStateData");

    // ===== 位置信息 =====
    private String dimension;
    private double x, y, z;
    private float yRot, xRot;

    // ===== 物品栏（使用 SNBT 字符串存储，确保 Gson 兼容） =====
    private List<String> mainInventory;
    private List<String> armorInventory;
    private String offhandItem;
    private List<String> enderChestInventory;

    // ===== 状态 =====
    private float health;
    private int foodLevel;
    private float saturation;
    private float exhaustion;
    private int experienceLevel;
    private float experienceProgress;
    private int score;

    private List<SavedEffect> effects;

    public PlayerStateData() {
        this.mainInventory = new ArrayList<>();
        this.armorInventory = new ArrayList<>();
        this.enderChestInventory = new ArrayList<>();
        this.effects = new ArrayList<>();
        this.health = 20.0f;
        this.foodLevel = 20;
        this.saturation = 5.0f;
    }

    // ===== 从玩家保存状态 =====

    public static PlayerStateData fromPlayer(ServerPlayer player) {
        DebugLogger.entering("PlayerState", "fromPlayer", "player=" + player.getName().getString());
        PlayerStateData data = new PlayerStateData();

        // 位置
        data.dimension = player.level().dimension().identifier().toString();
        Vec3 pos = player.position();
        data.x = pos.x;
        data.y = pos.y;
        data.z = pos.z;
        data.yRot = player.getYRot();
        data.xRot = player.getXRot();

        // 物品栏 — 使用 26.2 API
        HolderLookup.Provider lookup = getLookup(player);
        data.mainInventory = serializeItemList(player.getInventory().getNonEquipmentItems(), lookup);
        data.armorInventory = serializeArmor(player, lookup);
        data.offhandItem = serializeItemStack(player.getItemBySlot(EquipmentSlot.OFFHAND), lookup);
        data.enderChestInventory = serializeItemList(player.getEnderChestInventory().getItems(), lookup);

        // 状态
        data.health = player.getHealth();
        data.foodLevel = player.getFoodData().getFoodLevel();
        data.saturation = player.getFoodData().getSaturationLevel();
        data.exhaustion = getExhaustion(player);
        data.experienceLevel = player.experienceLevel;
        data.experienceProgress = player.experienceProgress;
        data.score = player.getScore();

        // 状态效果
        data.effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            data.effects.add(SavedEffect.fromEffect(effect));
        }

        DebugLogger.info("PlayerState", "保存玩家状态: %s @ %s [%.1f, %.1f, %.1f] 生命=%.1f 饥饿=%d",
                player.getName().getString(), data.dimension, data.x, data.y, data.z, data.health, data.foodLevel);
        DebugLogger.exiting("PlayerState", "fromPlayer");
        return data;
    }

    // ===== 应用到玩家 =====

    public void applyToPlayer(ServerPlayer player) {
        DebugLogger.entering("PlayerState", "applyToPlayer", "player=" + player.getName().getString());
        HolderLookup.Provider lookup = getLookup(player);

        // — 物品栏 —
        DebugLogger.branch("PlayerState", "mainInventory empty", this.mainInventory == null || this.mainInventory.isEmpty());
        if (this.mainInventory != null && !this.mainInventory.isEmpty()) {
            deserializeToMainInventory(player, this.mainInventory, lookup);
        }
        DebugLogger.branch("PlayerState", "armorInventory empty", this.armorInventory == null || this.armorInventory.isEmpty());
        if (this.armorInventory != null && !this.armorInventory.isEmpty()) {
            deserializeArmor(player, this.armorInventory, lookup);
        }
        boolean hasOffhand = this.offhandItem != null && !this.offhandItem.isEmpty();
        DebugLogger.branch("PlayerState", "hasOffhandItem", hasOffhand);
        player.setItemSlot(EquipmentSlot.OFFHAND,
                hasOffhand
                        ? deserializeItemStack(this.offhandItem, lookup)
                        : ItemStack.EMPTY);
        DebugLogger.branch("PlayerState", "enderChestInventory empty", this.enderChestInventory == null || this.enderChestInventory.isEmpty());
        if (this.enderChestInventory != null && !this.enderChestInventory.isEmpty()) {
            deserializeToItemList(player.getEnderChestInventory().getItems(), this.enderChestInventory, lookup);
        }

        // — 状态 —
        DebugLogger.stateChange("PlayerState", player.getName().getString(), "health", this.health);
        player.setHealth(this.health);
        DebugLogger.stateChange("PlayerState", player.getName().getString(), "foodLevel", this.foodLevel);
        player.getFoodData().setFoodLevel(this.foodLevel);
        DebugLogger.stateChange("PlayerState", player.getName().getString(), "saturation", this.saturation);
        player.getFoodData().setSaturation(this.saturation);
        DebugLogger.stateChange("PlayerState", player.getName().getString(), "exhaustion", this.exhaustion);
        setExhaustion(player, this.exhaustion);
        DebugLogger.stateChange("PlayerState", player.getName().getString(), "experienceLevel", this.experienceLevel);
        player.experienceLevel = this.experienceLevel;
        DebugLogger.stateChange("PlayerState", player.getName().getString(), "experienceProgress", this.experienceProgress);
        player.experienceProgress = this.experienceProgress;
        DebugLogger.stateChange("PlayerState", player.getName().getString(), "score", this.score);
        player.setScore(this.score);

        // 清除所有现有效果再应用保存的效果
        player.removeAllEffects();
        boolean hasEffects = this.effects != null && !this.effects.isEmpty();
        DebugLogger.branch("PlayerState", "hasSavedEffects", hasEffects);
        if (this.effects != null) {
            for (SavedEffect saved : this.effects) {
                MobEffectInstance effect = saved.toEffect();
                if (effect != null) {
                    player.addEffect(effect);
                }
            }
        }

        DebugLogger.info("PlayerState", "应用玩家状态: %s 生命=%.1f 饥饿=%d 经验=%d",
                player.getName().getString(), this.health, this.foodLevel, this.experienceLevel);
        DebugLogger.exiting("PlayerState", "applyToPlayer");
    }

    /** 清空玩家背包 */
    public static void clearPlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    // ===== 位置 =====

    public String getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYRot() { return yRot; }
    public float getXRot() { return xRot; }

    // ===== ItemStack 序列化（SNBT 字符串方式，确保 Gson 兼容） =====

    private static CompoundTag encodeStack(ItemStack stack, HolderLookup.Provider lookup) {
        if (stack.isEmpty()) return new CompoundTag();
        var result = ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), stack);
        return (CompoundTag) result.getOrThrow();
    }

    private static ItemStack decodeStack(CompoundTag tag, HolderLookup.Provider lookup) {
        if (tag == null || tag.isEmpty()) return ItemStack.EMPTY;
        var result = ItemStack.CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), tag);
        return result.result().orElse(ItemStack.EMPTY);
    }

    private static String tagToString(CompoundTag tag) {
        return tag.isEmpty() ? "" : tag.toString();
    }

    private static CompoundTag stringToTag(String str) {
        if (str == null || str.isEmpty()) return new CompoundTag();
        try {
            return TagParser.parseCompoundFully(str);
        } catch (Exception e) {
            return new CompoundTag();
        }
    }

    private static String serializeItemStack(ItemStack stack, HolderLookup.Provider lookup) {
        return tagToString(encodeStack(stack, lookup));
    }

    private static ItemStack deserializeItemStack(String str, HolderLookup.Provider lookup) {
        return decodeStack(stringToTag(str), lookup);
    }

    private static List<String> serializeItemList(NonNullList<ItemStack> items, HolderLookup.Provider lookup) {
        List<String> list = new ArrayList<>();
        for (ItemStack stack : items) {
            list.add(serializeItemStack(stack, lookup));
        }
        return list;
    }

    private static List<String> serializeArmor(ServerPlayer player, HolderLookup.Provider lookup) {
        return List.of(
                serializeItemStack(player.getItemBySlot(EquipmentSlot.FEET), lookup),
                serializeItemStack(player.getItemBySlot(EquipmentSlot.LEGS), lookup),
                serializeItemStack(player.getItemBySlot(EquipmentSlot.CHEST), lookup),
                serializeItemStack(player.getItemBySlot(EquipmentSlot.HEAD), lookup)
        );
    }

    private static void deserializeToItemList(NonNullList<ItemStack> target, List<String> source,
                                               HolderLookup.Provider lookup) {
        for (int i = 0; i < Math.min(target.size(), source.size()); i++) {
            target.set(i, deserializeItemStack(source.get(i), lookup));
        }
    }

    private static void deserializeToMainInventory(ServerPlayer player, List<String> source,
                                                    HolderLookup.Provider lookup) {
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(items.size(), source.size()); i++) {
            items.set(i, deserializeItemStack(source.get(i), lookup));
        }
    }

    private static void deserializeArmor(ServerPlayer player, List<String> source,
                                          HolderLookup.Provider lookup) {
        if (source.size() >= 4) {
            player.setItemSlot(EquipmentSlot.FEET, deserializeItemStack(source.get(0), lookup));
            player.setItemSlot(EquipmentSlot.LEGS, deserializeItemStack(source.get(1), lookup));
            player.setItemSlot(EquipmentSlot.CHEST, deserializeItemStack(source.get(2), lookup));
            player.setItemSlot(EquipmentSlot.HEAD, deserializeItemStack(source.get(3), lookup));
        }
    }

    // ===== HolderLookup.Provider 获取 =====

    private static HolderLookup.Provider getLookup(ServerPlayer player) {
        return player.level().getServer().registryAccess();
    }

    // ===== 饥饿值 exhaustion 辅助 =====

    private static float getExhaustion(ServerPlayer player) {
        try {
            var field = player.getFoodData().getClass().getDeclaredField("exhaustionLevel");
            field.setAccessible(true);
            return field.getFloat(player.getFoodData());
        } catch (Exception e) {
            LOGGER.warn("无法获取 exhaustion 值: {}", e.getMessage());
            return 0.0f;
        }
    }

    private static void setExhaustion(ServerPlayer player, float exhaustion) {
        try {
            var field = player.getFoodData().getClass().getDeclaredField("exhaustionLevel");
            field.setAccessible(true);
            field.setFloat(player.getFoodData(), exhaustion);
        } catch (Exception e) {
            LOGGER.warn("无法设置 exhaustion 值: {}", e.getMessage());
        }
    }

    // ===== 保存的状态效果 =====

    public static final class SavedEffect {
        private String effectId;
        private int amplifier;
        private int duration;
        private boolean ambient;
        private boolean showParticles;
        private boolean showIcon;

        public static SavedEffect fromEffect(MobEffectInstance effect) {
            SavedEffect saved = new SavedEffect();
            saved.effectId = effect.getEffect().getRegisteredName();
            saved.amplifier = effect.getAmplifier();
            saved.duration = effect.getDuration();
            saved.ambient = effect.isAmbient();
            saved.showParticles = effect.isVisible();
            saved.showIcon = effect.showIcon();
            return saved;
        }

        public MobEffectInstance toEffect() {
            Identifier id = Identifier.parse(this.effectId);
            var effectOpt = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(id);
            if (effectOpt.isEmpty()) return null;
            return new MobEffectInstance(Holder.direct(effectOpt.get()), this.duration, this.amplifier,
                    this.ambient, this.showParticles, this.showIcon);
        }
    }
}
