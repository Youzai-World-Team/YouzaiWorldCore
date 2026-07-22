package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 监守者战利品掉落与经验发放事件处理器。
 * <p>
 * 监听 {@link ServerLivingEntityEvents#AFTER_DEATH}。
 * 监守者被玩家击杀时直接发放 300 经验并掉落 bundle 战利品，
 * 取代数据包中基于 tick 扫描掉落物的脆弱方式。
 *
 * @see DragonElytraDropHandler
 */
@SuppressWarnings("null")
public class WardenDeathHandler {

    private static final int SEARCH_RADIUS = 30;
    private static final int XP_AMOUNT = 300;
    private static final float SECOND_BUNDLE_BASE_CHANCE = 0.15f;
    private static final float LOOTING_BONUS_PER_LEVEL = 0.05f;
    private static final Identifier LIGHT_BLUE_BUNDLE_ID = Identifier.parse("minecraft:light_blue_bundle");

    private static final WardenDeathHandler INSTANCE = new WardenDeathHandler();
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/WardenDeath");

    private Item lightBlueBundle;

    private WardenDeathHandler() {
    }

    // ========================================================================
    // 回调
    // ========================================================================

    private void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity.level().isClientSide())
            return;
        if (!(entity instanceof Warden warden))
            return;

        LOGGER.info("监守者被击杀，触发战利品逻辑");

        Optional<Player> killerOpt = findAttributionPlayer(warden, damageSource);
        if (killerOpt.isEmpty()) {
            LOGGER.info("非玩家击杀，跳过");
            return;
        }
        Player killer = killerOpt.get();
        ServerLevel level = (ServerLevel) entity.level();
        BlockPos pos = entity.blockPosition();
        RandomSource random = level.getRandom();
        RegistryAccess registries = level.registryAccess();

        // 1) 300 经验直接给击杀者
        grantXp(killer, level, pos);

        // 2) 第 1 个 bundle（保证）
        dropItemSafely(level, pos, createWardenBundle(random));

        // 3) 第 2 个 bundle（概率 + looting 加成）
        float chance = SECOND_BUNDLE_BASE_CHANCE
                + getLootingLevel(killer, registries) * LOOTING_BONUS_PER_LEVEL;
        if (random.nextFloat() < Math.min(chance, 1.0f)) {
            dropItemSafely(level, pos, createWardenBundle(random));
            LOGGER.debug("额外 bundle 掉落 (chance={})", chance);
        }

        // 4) 远古城市风格物品
        dropAncientCityLoot(level, pos, random);

        // 5) 附魔书
        dropItemSafely(level, pos, createEnchantedBook(registries, random));

        LOGGER.info("监守者战利品发放完成");
    }

    // ========================================================================
    // 经验
    // ========================================================================

    private static void grantXp(Player killer, ServerLevel level, BlockPos pos) {
        if (killer instanceof ServerPlayer sp) {
            sp.giveExperiencePoints(XP_AMOUNT);
        } else {
            level.addFreshEntity(new ExperienceOrb(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, XP_AMOUNT));
        }
    }

    // ========================================================================
    // 抢夺等级
    // ========================================================================

    private static int getLootingLevel(Player player, RegistryAccess registries) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty())
            return 0;
        var enchRegistry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var lootingKey = ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.parse("minecraft:looting"));
        var holder = enchRegistry.get(lootingKey);
        return holder.map(h -> weapon.getEnchantments().getLevel(h)).orElse(0);
    }

    // ========================================================================
    // Bundle 生成
    // ========================================================================

    private ItemStack createWardenBundle(RandomSource random) {
        if (lightBlueBundle == null) {
            lightBlueBundle = BuiltInRegistries.ITEM.get(LIGHT_BLUE_BUNDLE_ID)
                    .map(Holder::value).orElse(Items.AIR);
            if (lightBlueBundle == Items.AIR)
                lightBlueBundle = Items.BUNDLE;
        }
        ItemStack stack = new ItemStack(lightBlueBundle, 1);
        List<ItemStackTemplate> templates = new ArrayList<>();

        // Pool 1: 尖啸催生体 (1/10 发光)
        if (random.nextFloat() < 0.1f) {
            ItemStack s = new ItemStack(Items.SCULK_SHRIEKER);
            s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            s.set(DataComponents.RARITY, Rarity.RARE);
            templates.add(ItemStackTemplate.fromStack(s));
        } else {
            templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.SCULK_SHRIEKER)));
        }

        // Pool 2: 4 次抽取（下界合金碎片 w1 / 钻石 w4 / 金锭 w2 / 铁锭 w2 / 空 w10）
        for (int i = 0; i < 4; i++) {
            int r = random.nextInt(19);
            if (r < 1)
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.NETHERITE_SCRAP, 1 + random.nextInt(2))));
            else if (r < 5)
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.DIAMOND, 1 + random.nextInt(6))));
            else if (r < 7)
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(5))));
            else if (r < 9)
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(9))));
        }

        // Pool 3: 回声碎片 4-8 (4/5) vs 追溯指针 (1/5)
        if (random.nextInt(5) < 4)
            templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.ECHO_SHARD, 4 + random.nextInt(5))));
        else
            templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.RECOVERY_COMPASS)));

        // Pool 4: 唱片碎片 4-8 (4/5) vs 唱片 5 (1/5)
        if (random.nextInt(5) < 4)
            templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.DISC_FRAGMENT_5, 4 + random.nextInt(5))));
        else
            templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.MUSIC_DISC_5)));

        // Pool 5: 3 次——附魔金苹果 (1/4) vs 金苹果 1-3 (3/4)
        for (int i = 0; i < 3; i++) {
            if (random.nextInt(4) < 1)
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
            else
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(3))));
        }

        // Pool 6: 3 次——守卫盔纹 (2/5) vs 沉寂盔纹 (1/5) vs 空 (2/5)
        for (int i = 0; i < 3; i++) {
            int r = random.nextInt(5);
            if (r < 2)
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE)));
            else if (r < 3)
                templates.add(ItemStackTemplate.fromStack(new ItemStack(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)));
        }

        stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(templates));
        return stack;
    }

    // ========================================================================
    // 远古城市风格物品
    // ========================================================================

    private static void dropAncientCityLoot(ServerLevel level, BlockPos pos, RandomSource random) {
        int count = 3 + random.nextInt(4); // 3-6 件
        for (int i = 0; i < count; i++) {
            ItemStack s = pickCityItem(random);
            if (!s.isEmpty())
                dropItemSafely(level, pos, s);
        }
    }

    private static ItemStack pickCityItem(RandomSource random) {
        return switch (random.nextInt(25)) {
            case 0, 1 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            case 2, 3 -> new ItemStack(Items.DISC_FRAGMENT_5, 1 + random.nextInt(3));
            case 4, 5 -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(4));
            case 6, 7 -> new ItemStack(Items.ECHO_SHARD, 1 + random.nextInt(3));
            case 8, 9 -> new ItemStack(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE);
            case 10 -> new ItemStack(Items.MUSIC_DISC_OTHERSIDE);
            case 11 -> new ItemStack(Items.MUSIC_DISC_5);
            case 12 -> new ItemStack(Items.RECOVERY_COMPASS);
            case 13, 14 -> new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2));
            case 15 -> new ItemStack(Items.NETHERITE_SCRAP, 1 + random.nextInt(2));
            case 16 -> new ItemStack(Items.SCULK_CATALYST, 1 + random.nextInt(2));
            case 17 -> new ItemStack(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);
            case 18 -> new ItemStack(Items.CHAINMAIL_HELMET);
            case 19 -> new ItemStack(Items.CHAINMAIL_CHESTPLATE);
            case 20 -> new ItemStack(Items.CHAINMAIL_LEGGINGS);
            case 21 -> new ItemStack(Items.CHAINMAIL_BOOTS);
            case 22 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 2 + random.nextInt(3));
            default -> ItemStack.EMPTY;
        };
    }

    // ========================================================================
    // 附魔书
    // ========================================================================

    /**
     * 50% 迅捷潜行 I-III，50% 灵魂疾行 I-III。
     * 均通过 registryAccess 获取 Holder 构建，避免 API 版本兼容问题。
     */
    private static ItemStack createEnchantedBook(RegistryAccess registries, RandomSource random) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var enchRegistry = registries.lookupOrThrow(Registries.ENCHANTMENT);

        String enchId = random.nextBoolean()
                ? "minecraft:swift_sneak"
                : "minecraft:soul_speed";
        var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(enchId));

        enchRegistry.get(key).ifPresent(holder -> {
            int level = 1 + random.nextInt(3);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(holder, level);
            book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        });

        return book;
    }

    // ========================================================================
    // 归属查找
    // ========================================================================

    private static Optional<Player> findAttributionPlayer(Warden warden, DamageSource ds) {
        Entity e = ds.getEntity();
        if (e instanceof Player p)
            return Optional.of(p);
        if (e instanceof Projectile pr) {
            Entity owner = pr.getOwner();
            if (owner instanceof Player p)
                return Optional.of(p);
        }
        BlockPos wp = warden.blockPosition();
        AABB box = new AABB(
                wp.getX() - SEARCH_RADIUS, wp.getY() - SEARCH_RADIUS, wp.getZ() - SEARCH_RADIUS,
                wp.getX() + SEARCH_RADIUS, wp.getY() + SEARCH_RADIUS, wp.getZ() + SEARCH_RADIUS);
        List<Player> nearby = warden.level().getEntitiesOfClass(Player.class, box);
        if (nearby.isEmpty())
            return Optional.empty();
        nearby.sort((a, b) -> Double.compare(a.distanceToSqr(warden), b.distanceToSqr(warden)));
        return Optional.of(nearby.getFirst());
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private static void dropItemSafely(Level level, BlockPos pos, ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return;
        level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                stack.copy()));
    }

    // ========================================================================
    // 注册
    // ========================================================================

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(INSTANCE::onEntityDeath);
        LOGGER.info("监守者战利品发放事件处理器已注册");
    }
}
