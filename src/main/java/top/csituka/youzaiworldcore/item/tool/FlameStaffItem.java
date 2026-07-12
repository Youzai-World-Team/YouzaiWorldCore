package top.csituka.youzaiworldcore.item.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
public class FlameStaffItem extends Item {

    /** 火焰法杖消耗魔力 */
    public static final int MANA_COST = 10;

    /** 最大蓄力时间（tick），约 3 秒 */
    public static final int MAX_CHARGE_TICKS = 60;

    /** 基础伤害 */
    public static final float BASE_DAMAGE = 5.0f;

    /** 每 tick 蓄力增加的伤害 */
    public static final float DAMAGE_PER_TICK = 0.5f;

    /** 火焰激光最大射程 */
    public static final double LASER_RANGE = 30.0;

    /** 激光宽度（用于碰撞检测） */
    public static final double LASER_WIDTH = 0.5;

    public FlameStaffItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return MAX_CHARGE_TICKS;
    }

    @Override
    @NonNull
    public InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand usedHand) {
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }

        UUID playerId = player.getUUID();
        int mana = ManaManager.getInstance().getMana(playerId);
        if (mana < MANA_COST) {
            return InteractionResult.FAIL;
        }

        player.startUsingItem(usedHand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level, @NonNull LivingEntity livingEntity, int timeCharged) {
        if (level.isClientSide() || !(livingEntity instanceof Player player)) {
            return false;
        }

        UUID playerId = player.getUUID();
        int mana = ManaManager.getInstance().getMana(playerId);
        if (mana < MANA_COST) {
            return false;
        }

        // 计算实际蓄力时间（已使用的 tick 数 = getUseDuration - timeCharged）
        int chargeTicks = Math.min(MAX_CHARGE_TICKS, getUseDuration(stack, livingEntity) - timeCharged);
        if (chargeTicks < 3) {
            // 蓄力时间太短，取消释放
            return false;
        }

        // 扣除魔力
        ManaManager.getInstance().consumeMana(playerId, MANA_COST);

        // 计算伤害
        float damage = BASE_DAMAGE + (chargeTicks * DAMAGE_PER_TICK);

        // 发射火焰激光
        fireFlameLaser(level, player, damage);

        // 播放音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    /**
     * 发射火焰激光：对前方直线上的实体造成伤害并点燃。
     */
    private void fireFlameLaser(Level level, Player player, float damage) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookDir = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookDir.x * LASER_RANGE, lookDir.y * LASER_RANGE, lookDir.z * LASER_RANGE);

        // 逐步检测直线上的实体
        double step = 0.5;
        Vec3 currentPos = eyePos;
        double distance = 0;

        // 先做一次方块碰撞检测，找到激光被阻挡的位置
        net.minecraft.world.phys.BlockHitResult blockHit = level.clip(
                new net.minecraft.world.level.ClipContext(
                        eyePos, endPos,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        player
                )
        );
        if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            endPos = blockHit.getLocation();
        }

        double maxDist = eyePos.distanceTo(endPos);

        // 收集激光路径上的所有实体
        while (distance < maxDist) {
            AABB aabb = new AABB(
                    currentPos.x - LASER_WIDTH, currentPos.y - LASER_WIDTH, currentPos.z - LASER_WIDTH,
                    currentPos.x + LASER_WIDTH, currentPos.y + LASER_WIDTH, currentPos.z + LASER_WIDTH
            );

            List<Entity> entities = level.getEntities(player, aabb, e -> e instanceof LivingEntity && e != player);
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity target) {
                    // 造成伤害
                    target.hurt(level.damageSources().playerAttack(player), damage);
                    // 点燃 5 秒
                    target.setRemainingFireTicks(100);
                }
            }

            currentPos = currentPos.add(lookDir.x * step, lookDir.y * step, lookDir.z * step);
            distance += step;
        }

        // 在服务器端生成粒子效果（发送给所有玩家）
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            spawnLaserParticles(serverLevel, eyePos, endPos);
        }
    }

    private void spawnLaserParticles(net.minecraft.server.level.ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 dir = end.subtract(start).normalize();
        double dist = start.distanceTo(end);
        int particles = (int) (dist * 2);

        for (int i = 0; i < particles; i++) {
            double t = i / (double) particles;
            Vec3 pos = start.add(dir.x * dist * t, dir.y * dist * t, dir.z * dist * t);
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.FLAME,
                    pos.x, pos.y, pos.z,
                    1, 0.02, 0.02, 0.02, 0.01
            );
        }
    }


    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, Consumer<Component> tooltip, @NonNull TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.youzaiworldcore.flame_staff.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
