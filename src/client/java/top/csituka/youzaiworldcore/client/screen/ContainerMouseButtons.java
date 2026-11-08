package top.csituka.youzaiworldcore.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 鼠标事件按键编号到容器点击协议编号的换算。
 * <p>
 * 26.3 起 {@link MouseButtonEvent#button()} 是 SDL 编号（左 1 / 中 2 / 右 3），
 * 而容器点击协议仍使用左 0 / 右 1 / 中 2。需要把按键传给槽位逻辑或发往服务端时必须先换算，
 * 规则与原版 {@code AbstractContainerScreen.getContainerClickButton} 一致。
 */
public final class ContainerMouseButtons {

    private ContainerMouseButtons() {
    }

    /** 返回容器点击协议使用的按键编号。 */
    public static int of(MouseButtonEvent event) {
        return switch (event.button()) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            default -> event.button();
        };
    }
}
