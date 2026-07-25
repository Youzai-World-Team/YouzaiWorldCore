package top.csituka.youzaiworldcore.mixin.trialvault;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.trialvault.TrialVaultConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 试炼宝库无限领奖功能 Mixin。
 * <p>
 * 通过拦截 {@link VaultServerData#hasRewardedPlayer(ServerPlayer)}
 * 和 {@link VaultServerData#addToRewardedPlayers(ServerPlayer)}
 * 来移除宝库的每玩家一次领奖限制。
 * </p>
 * <p>
 * 当 {@link TrialVaultConfig#isEnabled()} 为 {@code true} 时：
 * <ul>
 *   <li>{@code hasRewardedPlayer} 恒返回 {@code false}，使宝库认为该玩家从未领过奖</li>
 *   <li>{@code addToRewardedPlayers} 被取消调用，玩家 UUID 不会被写入已领奖名单</li>
 * </ul>
 * 净效果：同一玩家可对同一宝库无限次插钥匙领奖。
 * </p>
 * <p>
 * 参考 trial-chamber-time-removal v2.0.0 的设计思路，改用精确方法注入而非通配 Redirect。
 * </p>
 */
@Mixin(VaultServerData.class)
public class VaultServerDataMixin {

    @Unique
    private static final String MODULE = "VaultServerDataMixin";

    /**
     * 拦截 {@code hasRewardedPlayer}，功能启用时恒返回 {@code false}。
     * <p>
     * 原版方法判断 {@code rewardedPlayers.contains(player.getUUID())}，
     * 本注入在方法入口处拦截：如果功能启用，直接返回 false（"未领过奖"），
     * 跳过原版 Set.contains 调用。
     * </p>
     */
    @Inject(method = "hasRewardedPlayer", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$bypassHasRewardedPlayer(Player player,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (!TrialVaultConfig.isEnabled()) {
            return;
        }
        DebugLogger.branch(MODULE, "bypassHasRewardedPlayer", true,
                "player=" + player.getScoreboardName() + ", enabled=true");
        cir.setReturnValue(false);
    }

    /**
     * 拦截 {@code addToRewardedPlayers}，功能启用时取消调用（不记录玩家 UUID）。
     * <p>
     * 原版方法执行 {@code rewardedPlayers.add(player.getUUID())}，
     * 本注入在方法入口将其取消，确保玩家的 UUID 永远不会被写入已领奖集合。
     * 即使仅拦截 {@code hasRewardedPlayer} 已足以实现无限领奖，
     * 额外拦截此方法可使 {@code rewardedPlayers} 集合保持更新前的状态，
     * 逻辑更清晰且与参考模组的行为一致。
     * </p>
     */
    @Inject(method = "addToRewardedPlayers", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$bypassAddToRewardedPlayers(Player player,
                                                             CallbackInfo ci) {
        if (!TrialVaultConfig.isEnabled()) {
            return;
        }
        DebugLogger.branch(MODULE, "bypassAddToRewardedPlayers", true,
                "player=" + player.getScoreboardName() + ", enabled=true");
        ci.cancel();
    }
}
