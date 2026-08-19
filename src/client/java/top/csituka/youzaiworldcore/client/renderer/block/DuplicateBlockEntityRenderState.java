package top.csituka.youzaiworldcore.client.renderer.block;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * 复制方块的客户端渲染状态。
 */
public class DuplicateBlockEntityRenderState extends BlockEntityRenderState {

    private boolean active;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
