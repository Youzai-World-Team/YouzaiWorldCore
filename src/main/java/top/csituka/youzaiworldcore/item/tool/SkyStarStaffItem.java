package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.mana.ManaManager;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("null")
public class SkyStarStaffItem extends Item {

    /** 天星法杖消耗魔力 */
    public static final int MANA_COST = 60;

    /** 陨石影响范围半径 */
    public static final double METEOR_RADIUS = 10.0;

    /** 陨石坠落高度（从怪物上方多高落下） */
    public static final double METEOR_FALL_HEIGHT = 25.0;

    /** 陨石伤害 */
    public static final float METEOR_DAMAGE = 15.0f;

    /** 陨石爆炸威力 */
    public static final float METEOR_EXPLOSION_POWER = 2.0f;

    public SkyStarStaffItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    @NonNull
    public InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand usedHand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        UUID playerId = player.getUUID();
        int mana = ManaManager.getInstance().getMana(playerId);
        if (mana < MANA_COST) {
            return InteractionResult.FAIL;
        }

        // 扣除魔力
        if (!ManaManager.getInstance().consumeMana(playerId, MANA_COST)) {
            return InteractionResult.FAIL;
        }

        // 召唤陨石
        summonMeteors(level, player);

        // 播放音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.AMBIENT_CAVE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);

        return InteractionResult.SUCCESS;
    }

    /**
     * 在玩家周围范围内召唤陨石砸向怪物。
     */
    private void summonMeteors(Level level, Player player) {
        Vec3 playerPos = player.position();
        AABB searchArea = new AABB(
                playerPos.x - METEOR_RADIUS, playerPos.y - METEOR_RADIUS, playerPos.z - METEOR_RADIUS,
                playerPos.x + METEOR_RADIUS, playerPos.y + METEOR_RADIUS, playerPos.z + METEOR_RADIUS
        );

        List<Entity> targets = level.getEntities(player, searchArea, e -> {
            if (!(e instanceof Mob)) return false;
            // 确保在水平范围内（AABB 是立方体，需要额外检查水平距离）
            double dx = e.getX() - playerPos.x;
            double dz = e.getZ() - playerPos.z;
            return (dx * dx + dz * dz) <= (METEOR_RADIUS * METEOR_RADIUS);
        });

        if (targets.isEmpty()) {
            return;
        }

        for (Entity target : targets) {
            if (target instanceof LivingEntity livingTarget) {
                summonSingleMeteor(level, livingTarget, player);
            }
        }
    }

    /**
     * 对单个目标召唤一颗陨石。
     */
    private void summonSingleMeteor(Level level, LivingEntity target, Player player) {
        double meteorX = target.getX();
        double meteorY = target.getY() + METEOR_FALL_HEIGHT;
        double meteorZ = target.getZ();

        // 在目标位置创建小型爆炸（模拟陨石撞击）
        if (level instanceof ServerLevel serverLevel) {
            // 创建爆炸效果
            serverLevel.explode(
                    null,
                    meteorX, target.getY(), meteorZ,
                    METEOR_EXPLOSION_POWER,
                    Level.ExplosionInteraction.NONE
            );

            // 对目标造成伤害
            target.hurt(level.damageSources().playerAttack(player), METEOR_DAMAGE);

            // 点燃目标
            target.setRemainingFireTicks(100);

            // 在目标位置生成火焰粒子
            spawnMeteorParticles(serverLevel, meteorX, target.getY(), meteorZ);

            // 在陨石坠落轨迹上生成烟雾粒子
            spawnTrailParticles(serverLevel, meteorX, meteorY, meteorZ, target.getY());
        }
    }

    private void spawnMeteorParticles(ServerLevel level, double x, double y, double z) {
        // 大量火焰和烟雾粒子
        for (int i = 0; i < 30; i++) {
            double px = x + (level.getRandom().nextDouble() - 0.5) * 2.0;
            double py = y + level.getRandom().nextDouble() * 2.0;
            double pz = z + (level.getRandom().nextDouble() - 0.5) * 2.0;
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.LAVA,
                    px, py, pz,
                    1, 0.2, 0.2, 0.2, 0.05
            );
        }
        for (int i = 0; i < 20; i++) {
            double px = x + (level.getRandom().nextDouble() - 0.5) * 3.0;
            double py = y + level.getRandom().nextDouble() * 2.0;
            double pz = z + (level.getRandom().nextDouble() - 0.5) * 3.0;
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    px, py, pz,
                    1, 0.3, 0.3, 0.3, 0.02
            );
        }
        for (int i = 0; i < 15; i++) {
            double px = x + (level.getRandom().nextDouble() - 0.5) * 2.0;
            double py = y + level.getRandom().nextDouble() * 2.0;
            double pz = z + (level.getRandom().nextDouble() - 0.5) * 2.0;
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.FLAME,
                    px, py, pz,
                    1, 0.2, 0.2, 0.2, 0.05
            );
        }
    }

    private void spawnTrailParticles(ServerLevel level, double x, double startY, double z, double endY) {
        int steps = (int) ((startY - endY) * 2);
        for (int i = 0; i < steps; i++) {
            double t = i / (double) steps;
            double py = startY - (startY - endY) * t;
            if (level.getRandom().nextFloat() < 0.3f) {
                level.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SMOKE,
                        x, py, z,
                        1, 0.1, 0.1, 0.1, 0.01
                );
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> tooltip, @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.sky_star_staff.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
