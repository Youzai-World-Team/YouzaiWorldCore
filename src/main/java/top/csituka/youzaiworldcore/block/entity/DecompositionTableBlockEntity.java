package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;

/**
 * 分解台方块实体（BlockEntity）。
 * <p>
 * 负责管理分解台的物品栏（10 个槽位）、持久化存储以及与 GUI 菜单的交互。
 * 实现了 {@link Container} 接口以提供物品存储功能，同时实现 {@link MenuProvider}
 * 以便玩家右键时打开对应的 GUI 容器。
 * </p>
 */
public class DecompositionTableBlockEntity extends BlockEntity implements Container, MenuProvider {

    /**
     * 分解台的物品栏列表，大小为 10。
     * 使用 {@link NonNullList} 确保每个槽位都不会为 null，空槽位使用 {@link ItemStack#EMPTY} 表示。
     */
    private final NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);

    /**
     * 构造一个分解台方块实体实例。
     *
     * @param pos   方块在世界中的位置
     * @param state 方块状态
     */
    public DecompositionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECOMPOSITION_TABLE, pos, state);
    }

    // ==================== Container 接口实现 ====================

    /**
     * 返回物品栏的槽位总数。
     *
     * @return 10（分解台固定拥有 10 个槽位）
     */
    @Override
    public int getContainerSize() {
        return items.size();
    }

    /**
     * 检查物品栏是否为空（所有槽位均为空）。
     *
     * @return 如果所有槽位都为空则返回 true，否则 false
     */
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取指定槽位的物品堆叠。
     *
     * @param slot 槽位索引
     * @return 该槽位的 {@link ItemStack}，可能为 EMPTY
     */
    @Override
    @NonNull
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    /**
     * 从指定槽位移除指定数量的物品，并返回被移除的物品堆叠。
     *
     * @param slot   槽位索引
     * @param amount 要移除的数量
     * @return 移除的物品堆叠
     */
    @Override
    @NonNull
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    /**
     * 移除指定槽位的整个物品堆叠（不触发更新通知）。
     *
     * @param slot 槽位索引
     * @return 被移除的完整物品堆叠
     */
    @Override
    @NonNull
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    /**
     * 将物品放入指定槽位，并标记方块实体已更改（触发保存）。
     *
     * @param slot  槽位索引
     * @param stack 要放入的物品堆叠
     */
    @Override
    public void setItem(int slot, @NonNull ItemStack stack) {
        items.set(slot, stack);
        setChanged(); // 标记数据已修改，需要持久化
    }

    /**
     * 标记方块实体已更改，通常在物品栏内容变化时调用。
     * 重写此方法仅为了确保调用父类逻辑。
     */
    @Override
    public void setChanged() {
        super.setChanged();
    }

    /**
     * 检查玩家是否仍然有权与此容器交互（例如未离方块太远）。
     * 方块被破坏或玩家超出 8 格距离时返回 false。
     *
     * @param player 要检查的玩家
     * @return 如果玩家可以继续操作容器则返回 true
     */
    @Override
    public boolean stillValid(@NonNull Player player) {
        if (this.level == null) {
            return false;
        }
        // 检查当前方块实体是否仍与世界中存放的一致（防止被替换）
        if (this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        // 检查玩家与方块中心的距离是否 ≤ 8 格（64 平方距离）
        return player.distanceToSqr(this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    /**
     * 清空物品栏的所有内容。
     */
    @Override
    public void clearContent() {
        items.clear();
    }

    /**
     * 获取当前方块实体所代表的容器实例。
     * 用于需要直接传递 Container 的场合。
     *
     * @return 容器自身
     */
    public Container getInventory() {
        return this;
    }

    // ==================== 数据持久化 ====================

    /**
     * 将物品栏数据保存到 NBT 输出流中。
     * 在方块实体被保存到世界文件时自动调用。
     *
     * @param output 输出对象（用于写入 NBT 数据）
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items); // 保存物品栏所有槽位
    }

    /**
     * 从 NBT 输入流中恢复物品栏数据。
     * 在世界加载方块实体时自动调用。
     *
     * @param input 输入对象（包含 NBT 数据）
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items); // 加载物品栏所有槽位
    }

    // ==================== MenuProvider 接口实现 ====================

    /**
     * 返回 GUI 显示的名称（用于屏幕标题）。
     * 从语言文件获取本地化的“分解台”文本。
     *
     * @return 显示名称组件
     */
    @Override
    @NonNull
    public Component getDisplayName() {
        return Component.translatable("block.youzaiworldcore.decomposition_table");
    }

    /**
     * 创建与当前容器关联的菜单实例。
     * 当玩家右键方块时，此方法会被调用以创建服务端菜单。
     *
     * @param containerId       菜单的容器 ID（由游戏分配）
     * @param playerInventory   玩家的物品栏
     * @param player            玩家实体
     * @return 新的 {@link DecompositionTableMenu} 实例
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory playerInventory, @NonNull Player player) {
        return new DecompositionTableMenu(containerId, playerInventory, this);
    }
}