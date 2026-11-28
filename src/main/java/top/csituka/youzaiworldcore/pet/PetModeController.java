package top.csituka.youzaiworldcore.pet;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.jetbrains.annotations.Nullable;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;

/** 按注册表模式响应主人、信任玩家和狼自身的近期战斗。 */
public final class PetModeController extends TargetGoal {
    private static final double HUNTING_RANGE_SQUARED = 10 * 10;
    private static final int ATTACK_MEMORY_TICKS = 100;
    private static final TargetingConditions SELF_DEFENSE = TargetingConditions.forCombat().ignoreLineOfSight()
            .ignoreInvisibilityTesting();

    private final Wolf wolf;
    private final Map<LivingEntity, Integer> handledAttacks = new WeakHashMap<>();
    private final Map<LivingEntity, Integer> handledHits = new WeakHashMap<>();
    private @Nullable Response response;

    @SuppressWarnings("null")
    public PetModeController(Wolf wolf) {
        super(wolf, true);
        this.wolf = wolf;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    public static void stopCombat(Wolf wolf) {
        wolf.setTarget(null);
        wolf.setPersistentAngerTarget(null);
        wolf.setPersistentAngerEndTime(0);
        wolf.getNavigation().stop();
    }

    public static boolean allowsTarget(Wolf wolf, LivingEntity target) {
        if (!wolf.entityTags().contains(PetInternalTags.TAG_PET_MARKER)) {
            return true;
        }
        PetEntry entry = entry(wolf);
        return entry == null || (entry.mode() != PetMode.COMPANIONSHIP && !entry.isTrustedOrOwner(target.getUUID()));
    }

    private static @Nullable PetEntry entry(Wolf wolf) {
        if (!(wolf.level() instanceof ServerLevel level)) {
            return null;
        }
        return PetGlobalState.get(level.getServer()).findByEntityUUID(wolf.getUUID()).orElse(null);
    }

    @SuppressWarnings("null")
    @Override
    public boolean canUse() {
        PetEntry entry = entry(wolf);
        if (entry == null || entry.mode() == PetMode.COMPANIONSHIP || wolf.isOrderedToSit()) {
            return false;
        }

        var players = ((ServerLevel) wolf.level()).getServer().getPlayerList();
        response = playerResponse(entry.mode(), players.getPlayer(entry.ownerUUID()));
        if (response == null) {
            for (UUID id : entry.trustedPlayers()) {
                response = playerResponse(entry.mode(), players.getPlayer(id));
                if (response != null) {
                    break;
                }
            }
        }
        if (response == null) {
            response = findResponse(wolf, entry.mode(), true);
        }
        return response != null;
    }

    private @Nullable Response playerResponse(PetMode mode, @Nullable ServerPlayer player) {
        if (player == null || player.level() != wolf.level() || player.isSpectator()
                || AuthPlayerHelper.shouldBlockActions(player)) {
            return null;
        }
        Response retaliation = mode == PetMode.HUNTING ? null : findResponse(player, mode, true);
        if (retaliation != null || mode == PetMode.GUARD) {
            return retaliation;
        }
        return findResponse(player, mode, false);
    }

    @SuppressWarnings("null")
    private @Nullable Response findResponse(LivingEntity actor, PetMode mode, boolean defense) {
        LivingEntity target = defense ? actor.getLastHurtByMob() : actor.getLastHurtMob();
        int timestamp = defense ? actor.getLastHurtByMobTimestamp() : actor.getLastHurtMobTimestamp();
        var handled = defense ? handledHits : handledAttacks;
        double followRange = getFollowDistance();
        if (target == null || target.level() != wolf.level() || !target.isAlive()
                || actor.tickCount - timestamp > ATTACK_MEMORY_TICKS
                || handled.getOrDefault(actor, Integer.MIN_VALUE) == timestamp
                || wolf.distanceToSqr(target) > followRange * followRange) {
            return null;
        }
        boolean selfDefense = actor == wolf;
        if (!withinHuntingRange(mode, actor, target, defense)
                || !canAttack(target, selfDefense ? SELF_DEFENSE : TargetingConditions.DEFAULT)
                || (!selfDefense && !wolf.wantsToAttack(target, actor))) {
            return null;
        }
        return new Response(actor, target, timestamp, defense, mode);
    }

    @SuppressWarnings("null")
    private static boolean withinHuntingRange(PetMode mode, LivingEntity actor, LivingEntity target, boolean defense) {
        return mode != PetMode.HUNTING || defense || actor.distanceToSqr(target) <= HUNTING_RANGE_SQUARED;
    }

    @Override
    public void start() {
        Response chosen = response;
        if (chosen == null) {
            return;
        }
        (chosen.defense() ? handledHits : handledAttacks).put(chosen.actor(), chosen.timestamp());
        targetMob = chosen.target();
        wolf.setTarget(chosen.target());
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        Response active = response;
        PetEntry entry = entry(wolf);
        return active != null && entry != null && entry.mode() == active.mode() && !wolf.isOrderedToSit()
                && active.actor().level() == wolf.level() && active.target().level() == wolf.level()
                && (active.actor() == wolf || (!active.actor().isRemoved()
                        && entry.isTrustedOrOwner(active.actor().getUUID())))
                && wolf.getTarget() == active.target()
                && withinHuntingRange(active.mode(), active.actor(), active.target(), active.defense())
                && super.canContinueToUse();
    }

    @Override
    public void stop() {
        super.stop();
        response = null;
    }

    private record Response(LivingEntity actor, LivingEntity target, int timestamp, boolean defense, PetMode mode) {
    }
}
