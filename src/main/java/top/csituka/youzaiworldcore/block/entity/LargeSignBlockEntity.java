package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.LargeSignTextRules;
import top.csituka.youzaiworldcore.sign.FlashingSign;

import java.util.UUID;

/**
 * 大字牌方块实体：保存牌面上的单个大字及其显示样式。
 * <p>
 * 持久化字段：
 * <ul>
 *   <li>{@code Text}    — 牌面文本（1 个全角或 2 个半角字符，见 {@link LargeSignTextRules}）；</li>
 *   <li>{@code Color}   — 文字染色，默认 {@link DyeColor#WHITE}（与原版告示牌默认黑色不同）；</li>
 *   <li>{@code Glowing} — 是否发光（荧光墨囊效果），默认 false；</li>
 *   <li>{@code Waxed}   — 是否已涂蜡，涂蜡后不可再编辑 / 染色 / 改发光。</li>
 * </ul>
 * {@code playerWhoMayEdit} 不持久化：与原版告示牌一致，仅在本次「打开编辑界面 → 提交文本」
 * 的往返过程中有效，用于防止其他玩家伪造 C2S 包改写别人正在编辑的字牌。
 *
 * @see top.csituka.youzaiworldcore.block.LargeSignBlock
 */
@SuppressWarnings("null")
public class LargeSignBlockEntity extends BlockEntity implements FlashingSign {

    /** 允许编辑的最远距离（平方值），与原版告示牌一致（8 格）。 */
    private static final double MAX_EDIT_DISTANCE_SQR = 64.0;

    private static final String MODULE = "LargeSignBlockEntity";

    private String text = "";
    private DyeColor color = DyeColor.WHITE;
    private boolean glowing;
    private boolean waxed;
    private boolean flashing;

    /** 当前被允许提交文本的玩家；不持久化，随方块实体卸载而失效。 */
    @Nullable
    private UUID playerWhoMayEdit;

    public LargeSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LARGE_SIGN, pos, state);
    }

    // ===== 只读访问 =====

    /**
     * @return 牌面文本，空串表示尚未编辑
     */
    @NonNull
    public String getText() {
        return text;
    }

    /**
     * @return 文字染色
     */
    @NonNull
    public DyeColor getColor() {
        return color;
    }

    /**
     * @return 文字是否发光
     */
    public boolean isGlowing() {
        return glowing;
    }

    /**
     * @return 是否已涂蜡（涂蜡后一切修改都被拒绝）
     */
    public boolean isWaxed() {
        return waxed;
    }

    /** @return 大字牌文字是否处于闪烁状态 */
    public boolean isFlashing() {
        return flashing;
    }

    @Override
    public boolean youzaiworldcore$isFlashing(boolean front) {
        return flashing;
    }

    @Override
    public boolean youzaiworldcore$setFlashing(boolean front, boolean newFlashing) {
        if (waxed || flashing == newFlashing) {
            return false;
        }
        boolean oldFlashing = flashing;
        flashing = newFlashing;
        markUpdated();
        DebugLogger.stateChange(MODULE, "largeSign@" + worldPosition.toShortString(),
                "flashing", oldFlashing, newFlashing);
        return true;
    }

    // ===== 状态变更（全部会同步到客户端） =====

    /**
     * 写入牌面文本。
     * <p>
     * 已涂蜡或文本不合法时不做任何改动。
     *
     * @param newText 新文本
     * @return 实际发生改动时返回 true
     */
    public boolean setText(String newText) {
        if (waxed) {
            DebugLogger.branch(MODULE, "setText 被拒绝：字牌已涂蜡", false);
            return false;
        }
        String value = newText == null ? "" : newText;
        if (!LargeSignTextRules.isValid(value)) {
            DebugLogger.warn(MODULE, "setText 被拒绝：文本不合法 text=%s", value);
            return false;
        }
        if (value.equals(text)) {
            return false;
        }

        String oldText = text;
        text = value;
        markUpdated();
        DebugLogger.stateChange(MODULE, "largeSign@" + worldPosition.toShortString(), "text", oldText, value);
        return true;
    }

    /**
     * 设置文字染色。
     *
     * @param newColor 新颜色
     * @return 实际发生改动时返回 true
     */
    public boolean setColor(DyeColor newColor) {
        if (waxed || newColor == null || newColor == color) {
            return false;
        }

        DyeColor oldColor = color;
        color = newColor;
        markUpdated();
        DebugLogger.stateChange(MODULE, "largeSign@" + worldPosition.toShortString(), "color", oldColor, newColor);
        return true;
    }

    /**
     * 设置文字是否发光。
     *
     * @param newGlowing 目标发光状态
     * @return 实际发生改动时返回 true
     */
    public boolean setGlowing(boolean newGlowing) {
        if (waxed || newGlowing == glowing) {
            return false;
        }

        glowing = newGlowing;
        markUpdated();
        DebugLogger.stateChange(MODULE, "largeSign@" + worldPosition.toShortString(),
                "glowing", !newGlowing, newGlowing);
        return true;
    }

    /**
     * 涂蜡 / 解除涂蜡。
     * <p>
     * 游戏内只会用蜜脾把它置为 true；解除需要破坏后重新放置。
     *
     * @param newWaxed 目标涂蜡状态
     * @return 实际发生改动时返回 true
     */
    public boolean setWaxed(boolean newWaxed) {
        if (newWaxed == waxed) {
            return false;
        }

        waxed = newWaxed;
        // 涂蜡后立即失效编辑授权，避免已打开的编辑界面还能提交
        playerWhoMayEdit = null;
        markUpdated();
        DebugLogger.stateChange(MODULE, "largeSign@" + worldPosition.toShortString(),
                "waxed", !newWaxed, newWaxed);
        return true;
    }

    // ===== 编辑授权 =====

    /**
     * 授权某位玩家提交本字牌的文本（在服务端下发编辑界面时调用）。
     *
     * @param playerUuid 玩家 UUID，传 null 表示撤销授权
     */
    public void setAllowedPlayerEditor(@Nullable UUID playerUuid) {
        playerWhoMayEdit = playerUuid;
    }

    /**
     * 校验玩家是否有权提交本字牌的文本。
     * <p>
     * 需要同时满足：未涂蜡、是当前被授权者、且距离字牌不超过 8 格。
     *
     * @param player 提交文本的玩家
     * @return 允许提交时返回 true
     */
    public boolean mayEdit(Player player) {
        if (waxed || player == null) {
            return false;
        }
        if (playerWhoMayEdit == null || !playerWhoMayEdit.equals(player.getUUID())) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= MAX_EDIT_DISTANCE_SQR;
    }

    /** 检查物品是否可以修改大字牌，避免覆盖另一名玩家正在编辑的牌面。 */
    public boolean mayApply(Player player) {
        if (waxed || player == null || !player.mayBuild()) {
            return false;
        }
        return playerWhoMayEdit == null
                || playerWhoMayEdit.equals(player.getUUID());
    }

    // ===== 同步与持久化 =====

    /**
     * 标记数据变更并把方块实体数据推送给所有跟踪该区块的客户端。
     */
    private void markUpdated() {
        setChanged();

        Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }

        BlockState state = getBlockState();
        currentLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putString("Text", text);
        output.putString("Color", color.getName());
        output.putBoolean("Glowing", glowing);
        output.putBoolean("Waxed", waxed);
        output.putBoolean("Flashing", flashing);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        String loadedText = input.getStringOr("Text", "");
        // 存档可能被外部工具改坏，读入时同样过一遍规则
        text = LargeSignTextRules.isValid(loadedText) ? loadedText : LargeSignTextRules.clamp(loadedText);
        color = DyeColor.byName(input.getStringOr("Color", DyeColor.WHITE.getName()), DyeColor.WHITE);
        glowing = input.getBooleanOr("Glowing", false);
        waxed = input.getBooleanOr("Waxed", false);
        flashing = input.getBooleanOr("Flashing", false);
        playerWhoMayEdit = null;
    }

    @Override
    @NonNull
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 直接复用 {@link #saveAdditional(ValueOutput)} 的结果（{@code saveCustomOnly}），
     * 保证「存档字段」与「同步字段」永远一致，新增字段时不会漏同步。
     */
    @Override
    @NonNull
    public CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }
}
