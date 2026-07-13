package top.csituka.youzaiworldcore.client.renderer.block;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * 传送锚点 BlockEntity 的渲染状态。
 * <p>
 * 在每帧渲染前从 TeleportAnchorBlockEntity 提取所需数据。
 * {@code activatedByMe} 表示当前客户端玩家是否已激活此锚点。
 */
public class TeleportAnchorBlockEntityRenderState extends BlockEntityRenderState {
    private boolean activatedByMe;

    public boolean isActivatedByMe() {
        return activatedByMe;
    }

    public void setActivatedByMe(boolean activatedByMe) {
        this.activatedByMe = activatedByMe;
    }
}
