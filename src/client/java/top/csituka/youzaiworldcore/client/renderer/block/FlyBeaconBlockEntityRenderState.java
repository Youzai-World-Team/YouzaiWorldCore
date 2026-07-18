package top.csituka.youzaiworldcore.client.renderer.block;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;

/**
 * 飞行信标 BlockEntity 的渲染状态。
 * 在每帧渲染前从 FlyBeaconBlockEntity 提取所需数据。
 */
public class FlyBeaconBlockEntityRenderState extends BlockEntityRenderState {
    private boolean active;
    private BlockPos pos;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }
}
