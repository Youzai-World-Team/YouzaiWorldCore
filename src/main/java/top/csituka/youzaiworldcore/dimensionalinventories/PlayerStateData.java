package top.csituka.youzaiworldcore.dimensionalinventories;

import eu.pb4.trinkets.impl.LivingEntityTrinketAttachment;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import top.csituka.youzaiworldcore.util.LogValues;

import com.mojang.datafixers.DataFixer;
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
import top.csituka.youzaiworldcore.mixin.TrinketAttachmentPoolAccessor;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.ItemStackNbtUpgrader;

import java.util.*;

/**
 * 玩家在某个维度池中的完整状态快照。
 * <p>
 * 使用 Minecraft 的 DataComponent + CODEC 系统进行 ItemStack 序列化，
 * NBT 数据以 SNBT 字符串形式存储以确保 Gson 兼容性。
 * 文件同时记录写出时的数据版本，读取时先用原版 DataFixer 把物品升级到当前版本再解析，
 * 避免 Minecraft 更新物品组件格式后旧背包中的物品解析失败而丢失。
 * 包含：物品栏、盔甲、副手、末影箱、生命、饥饿、经验、分数、状态效果、
 * 以及离开时的维度与坐标（用于传送返回）。
 */
@SuppressWarnings("null")
public final class PlayerStateData {

    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PlayerStateData");

    // ===== 数据版本（0 表示引入本字段之前写出的文件，按 26.2 处理） =====
    private int dataVersion;

    // ===== 位置信息 =====
    private String dimension;
    private double x, y, z;
    private float yRot, xRot;
    private String gameMode;

    // ===== 物品栏（使用 SNBT 字符串存储，确保 Gson 兼容） =====
    private List<String> mainInventory;
    private List<String> armorInventory;
    private String offhandItem;
    private String bodyItem;
    private String saddleItem;
    private String trinkets;
    private int selectedSlot;
    private List<String> enderChestInventory;

    // ===== 状态 =====
    private float health;
    private float absorption;
    private int foodLevel;
    private float saturation;
    private float exhaustion;
    private int experienceLevel;
    private float experienceProgress;
    private int totalExperience;
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

    static PlayerStateData initialState(ServerPlayer player) {
        var state = new PlayerStateData();
        state.health = player.getMaxHealth();
        return state;
    }

    // ===== 从玩家保存状态 =====

    public static PlayerStateData fromPlayer(ServerPlayer player) {
        DebugLogger.SERVER.entering("PlayerState", "fromPlayer", "player=" + player.getName().getString());
        PlayerStateData data = new PlayerStateData();
        data.dataVersion = ItemStackNbtUpgrader.currentDataVersion();

        data.dimension = player.level().dimension().identifier().toString();
        Vec3 pos = player.position();
        data.x = pos.x;
        data.y = pos.y;
        data.z = pos.z;
        data.yRot = player.getYRot();
        data.xRot = player.getXRot();
        data.gameMode = player.gameMode().getName();

        HolderLookup.Provider lookup = getLookup(player);
        data.mainInventory = serializeItemList(player.getInventory().getNonEquipmentItems(), lookup);
        data.armorInventory = serializeArmor(player, lookup);
        data.offhandItem = serializeItemStack(player.getItemBySlot(EquipmentSlot.OFFHAND), lookup);
        data.bodyItem = serializeItemStack(player.getItemBySlot(EquipmentSlot.BODY), lookup);
        data.saddleItem = serializeItemStack(player.getItemBySlot(EquipmentSlot.SADDLE), lookup);
        data.trinkets = captureTrinkets(player).toString();
        data.selectedSlot = player.getInventory().getSelectedSlot();
        data.enderChestInventory = serializeItemList(player.getEnderChestInventory().getItems(), lookup);

        data.health = player.getHealth();
        data.absorption = player.getAbsorptionAmount();
        data.foodLevel = player.getFoodData().getFoodLevel();
        data.saturation = player.getFoodData().getSaturationLevel();
        data.exhaustion = getExhaustion(player);
        data.experienceLevel = player.experienceLevel;
        data.experienceProgress = player.experienceProgress;
        data.totalExperience = player.totalExperience;
        data.score = player.getScore();

        data.effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            data.effects.add(SavedEffect.fromEffect(effect));
        }

        DebugLogger.SERVER.debug("PlayerState", "保存玩家状态: {} @ {} [{}, {}, {}] 生命={} 饥饿={}", player.getName().getString(), data.dimension, LogValues.decimal(data.x, 1), LogValues.decimal(data.y, 1), LogValues.decimal(data.z, 1), LogValues.decimal(data.health, 1), data.foodLevel);
        DebugLogger.SERVER.exiting("PlayerState", "fromPlayer");
        return data;
    }

    // ===== 应用到玩家 =====

    public void applyToPlayer(ServerPlayer player) {
        applyToPlayer(player, false);
    }

    /** 主动切换时遇到无法读取的物品应中止，并由调用方恢复源池状态。 */
    void applyToPlayer(ServerPlayer player, boolean strictItems) {
        DebugLogger.SERVER.entering("PlayerState", "applyToPlayer", "player=" + player.getName().getString());
        ItemReader lookup = new ItemReader(getLookup(player), player.level().getServer().getFixerUpper(),
                this.dataVersion > 0 ? this.dataVersion : ItemStackNbtUpgrader.LEGACY_DATA_VERSION, strictItems);
        if (strictItems) {
            validateInventorySizes(player);
        }

        // — 物品栏 —
        DebugLogger.SERVER.branch("PlayerState", "mainInventory empty", this.mainInventory == null || this.mainInventory.isEmpty());
        if (this.mainInventory != null && !this.mainInventory.isEmpty()) {
            deserializeToMainInventory(player, this.mainInventory, lookup);
        }
        DebugLogger.SERVER.branch("PlayerState", "armorInventory empty", this.armorInventory == null || this.armorInventory.isEmpty());
        if (this.armorInventory != null && !this.armorInventory.isEmpty()) {
            deserializeArmor(player, this.armorInventory, lookup);
        }
        boolean hasOffhand = this.offhandItem != null && !this.offhandItem.isEmpty();
        DebugLogger.SERVER.branch("PlayerState", "hasOffhandItem", hasOffhand);
        player.setItemSlot(EquipmentSlot.OFFHAND,
                hasOffhand
                        ? deserializeItemStack(this.offhandItem, lookup)
                        : ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.BODY, deserializeItemStack(bodyItem, lookup));
        player.setItemSlot(EquipmentSlot.SADDLE, deserializeItemStack(saddleItem, lookup));
        player.getInventory().setSelectedSlot(Math.clamp(selectedSlot, 0, 8));
        applyTrinkets(player, stringToTag(trinkets, strictItems));
        DebugLogger.SERVER.branch("PlayerState", "enderChestInventory empty", this.enderChestInventory == null || this.enderChestInventory.isEmpty());
        if (this.enderChestInventory != null && !this.enderChestInventory.isEmpty()) {
            deserializeToItemList(player.getEnderChestInventory().getItems(), this.enderChestInventory, lookup);
        }

        // — 状态 —
        DebugLogger.SERVER.debugStateChange("PlayerState", player.getName().getString(), "health", this.health);
        player.setHealth(this.health > 0 ? this.health : player.getMaxHealth());
        player.setAbsorptionAmount(this.absorption);
        DebugLogger.SERVER.debugStateChange("PlayerState", player.getName().getString(), "foodLevel", this.foodLevel);
        player.getFoodData().setFoodLevel(this.foodLevel);
        DebugLogger.SERVER.debugStateChange("PlayerState", player.getName().getString(), "saturation", this.saturation);
        player.getFoodData().setSaturation(this.saturation);
        DebugLogger.SERVER.debugStateChange("PlayerState", player.getName().getString(), "exhaustion", this.exhaustion);
        setExhaustion(player, this.exhaustion);
        DebugLogger.SERVER.debugStateChange("PlayerState", player.getName().getString(), "experienceLevel", this.experienceLevel);
        player.experienceLevel = this.experienceLevel;
        DebugLogger.SERVER.debugStateChange("PlayerState", player.getName().getString(), "experienceProgress", this.experienceProgress);
        player.experienceProgress = this.experienceProgress;
        player.totalExperience = this.totalExperience;
        DebugLogger.SERVER.debugStateChange("PlayerState", player.getName().getString(), "score", this.score);
        player.setScore(this.score);

        player.removeAllEffects();
        boolean hasEffects = this.effects != null && !this.effects.isEmpty();
        DebugLogger.SERVER.branch("PlayerState", "hasSavedEffects", hasEffects);
        if (this.effects != null) {
            for (SavedEffect saved : this.effects) {
                MobEffectInstance effect = saved.toEffect();
                if (effect != null) {
                    player.addEffect(effect);
                }
            }
        }

        DebugLogger.SERVER.debug("PlayerState", "应用玩家状态: {} 生命={} 饥饿={} 经验={}", player.getName().getString(), LogValues.decimal(this.health, 1), this.foodLevel, this.experienceLevel);
        DebugLogger.SERVER.exiting("PlayerState", "applyToPlayer");
    }

    private void validateInventorySizes(ServerPlayer player) {
        if ((mainInventory != null && mainInventory.size() > player.getInventory().getNonEquipmentItems().size())
                || (armorInventory != null && !armorInventory.isEmpty() && armorInventory.size() != 4)
                || (enderChestInventory != null && enderChestInventory.size() > player.getEnderChestInventory().getContainerSize())) {
            throw new IllegalArgumentException("维度池存档中的物品栏大小无效");
        }
    }

    public static void clearPlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    static CompoundTag captureTrinkets(ServerPlayer player) {
        var problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, getLookup(player));
        var attachment = LivingEntityTrinketAttachment.get(player);
        checkTrinketOverflow(attachment);
        attachment.writeData(output);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(problems.getReport());
        }
        return output.buildResult();
    }

    static void applyTrinkets(ServerPlayer player, CompoundTag data) {
        if (data.isEmpty()) {
            // 旧维度池存档和新池都从空饰品栏开始。
            data = new CompoundTag();
            data.putInt("__version", 1);
        }
        if (data.getIntOr("__version", 0) != 1) {
            throw new IllegalArgumentException("维度池存档中的饰品格式无效");
        }
        var problems = new ProblemReporter.Collector();
        var attachment = LivingEntityTrinketAttachment.get(player);
        for (var inventory : attachment.getInventories().values()) {
            if (inventory.hasCosmeticItems()) {
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    inventory.setCosmeticItem(slot, ItemStack.EMPTY);
                }
            }
        }
        attachment.readData(TagValueInput.create(problems, getLookup(player), data));
        if (!problems.isEmpty()) {
            throw new IllegalStateException(problems.getReport());
        }
        checkTrinketOverflow(attachment);
    }

    private static void checkTrinketOverflow(LivingEntityTrinketAttachment attachment) {
        var pending = ((TrinketAttachmentPoolAccessor) attachment).youzaiworldcore$getDelayedDropped();
        if (pending != null && pending.stream().anyMatch(stack -> !stack.isEmpty())) {
            throw new IllegalStateException("部分饰品尚未进入玩家物品栏或对应饰品槽不可用");
        }
    }

    GameType savedGameMode(GameType fallback) {
        if (gameMode == null) {
            return fallback;
        }
        GameType mode = GameType.byName(gameMode, null);
        if (mode == null) {
            throw new IllegalStateException("玩家存档中的游戏模式无效：" + gameMode);
        }
        return mode;
    }

    // ===== 位置 =====

    public String getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYRot() { return yRot; }
    public float getXRot() { return xRot; }

    boolean hasValidPosition() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yRot) && Float.isFinite(xRot);
    }

    // ===== ItemStack 序列化（SNBT 字符串方式，确保 Gson 兼容） =====

    private static CompoundTag encodeStack(ItemStack stack, HolderLookup.Provider lookup) {
        if (stack.isEmpty()) return new CompoundTag();
        var result = ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), stack);
        return (CompoundTag) result.getOrThrow();
    }

    private static ItemStack decodeStack(CompoundTag tag, ItemReader reader) {
        if (tag == null || tag.isEmpty()) return ItemStack.EMPTY;
        CompoundTag upgraded = ItemStackNbtUpgrader.upgrade(reader.fixer(), tag, reader.dataVersion());
        var result = ItemStack.CODEC.parse(reader.lookup().createSerializationContext(NbtOps.INSTANCE), upgraded);
        if (reader.strictItems()) {
            return result.getOrThrow();
        }
        result.error().ifPresent(error -> LOGGER.warn("维度池中保存的物品无法解析，已跳过：{}；原始数据：{}",
                error.message(), tag));
        return result.result().orElse(ItemStack.EMPTY);
    }

    private static String tagToString(CompoundTag tag) {
        return tag.isEmpty() ? "" : tag.toString();
    }

    private static CompoundTag stringToTag(String str, boolean strict) {
        if (str == null || str.isEmpty()) return new CompoundTag();
        try {
            return TagParser.parseCompoundFully(str);
        } catch (Exception e) {
            if (strict) {
                throw new IllegalArgumentException("维度池存档中的物品无法解析", e);
            }
            return new CompoundTag();
        }
    }

    private static String serializeItemStack(ItemStack stack, HolderLookup.Provider lookup) {
        return tagToString(encodeStack(stack, lookup));
    }

    private static ItemStack deserializeItemStack(String str, ItemReader reader) {
        return decodeStack(stringToTag(str, reader.strictItems()), reader);
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
                                               ItemReader lookup) {
        for (int i = 0; i < Math.min(target.size(), source.size()); i++) {
            target.set(i, deserializeItemStack(source.get(i), lookup));
        }
    }

    private static void deserializeToMainInventory(ServerPlayer player, List<String> source,
                                                    ItemReader lookup) {
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(items.size(), source.size()); i++) {
            items.set(i, deserializeItemStack(source.get(i), lookup));
        }
    }

    private static void deserializeArmor(ServerPlayer player, List<String> source,
                                          ItemReader lookup) {
        if (source.size() >= 4) {
            player.setItemSlot(EquipmentSlot.FEET, deserializeItemStack(source.get(0), lookup));
            player.setItemSlot(EquipmentSlot.LEGS, deserializeItemStack(source.get(1), lookup));
            player.setItemSlot(EquipmentSlot.CHEST, deserializeItemStack(source.get(2), lookup));
            player.setItemSlot(EquipmentSlot.HEAD, deserializeItemStack(source.get(3), lookup));
        }
    }

    /** 读取物品所需的注册表、DataFixer 与数据写出时的版本。 */
    private record ItemReader(HolderLookup.Provider lookup, DataFixer fixer, int dataVersion, boolean strictItems) {
    }

    // ===== HolderLookup.Provider 获取 =====

    private static HolderLookup.Provider getLookup(ServerPlayer player) {
        return player.level().getServer().registryAccess();
    }

    // ===== 饥饿值 exhaustion 辅助 =====

    static float getExhaustion(ServerPlayer player) {
        try {
            var field = player.getFoodData().getClass().getDeclaredField("exhaustionLevel");
            field.setAccessible(true);
            return field.getFloat(player.getFoodData());
        } catch (Exception e) {
            LOGGER.warn("无法获取 exhaustion 值: {}", e.getMessage());
            return 0.0f;
        }
    }

    static void setExhaustion(ServerPlayer player, float exhaustion) {
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
