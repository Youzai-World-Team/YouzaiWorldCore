package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 修改Minecraft标题界面：
 * - 隐藏除「单人游戏」「选项」「退出游戏」外的所有按钮（含模组添加的）
 * - 在单人游戏下方添加「加入服务器」按钮，直连 play.mcyzw.top
 * - 【选项】和【退出游戏】放在同一行
 * - 所有按钮居中排列
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    /** 全宽按钮宽度 */
    private static final int BUTTON_WIDTH = 200;

    /** 半宽按钮宽度（选项/退出各占一半） */
    private static final int HALF_BUTTON_WIDTH = 98;

    /** 按钮高度 */
    private static final int BUTTON_HEIGHT = 20;

    /** 按钮间距 */
    private static final int BUTTON_GAP = 4;

    /**
     * 在 {@code TitleScreen.init()} 执行完毕后：
     * 1. 移除除单人游戏/选项/退出游戏之外的所有按钮
     * 2. 添加「加入服务器」按钮
     * 3. 将所有保留按钮居中排列（选项和退出在同一行）
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void youzaiworldcore$reworkTitleButtons(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int width = screen.width;
        int height = screen.height;

        ScreenAccessor accessor = (ScreenAccessor) screen;

        // ============ 1. 收集需要保留的按钮 ============
        AbstractWidget singleplayerBtn = null;
        AbstractWidget optionsBtn = null;
        AbstractWidget quitBtn = null;
        List<GuiEventListener> toRemove = new ArrayList<>();

        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                String key = extractTranslationKey(widget.getMessage());
                if ("menu.singleplayer".equals(key)) {
                    singleplayerBtn = widget;
                } else if ("menu.options".equals(key)) {
                    optionsBtn = widget;
                } else if ("menu.quit".equals(key)) {
                    quitBtn = widget;
                } else {
                    toRemove.add(child);
                }
            }
        }

        // ============ 2. 移除不需要的按钮 ============
        List<Renderable> renderables = accessor.youzaiworldcore$getRenderables();
        List<NarratableEntry> narratables = accessor.youzaiworldcore$getNarratables();
        List<GuiEventListener> childrenList = accessor.youzaiworldcore$getChildren();

        for (GuiEventListener child : toRemove) {
            childrenList.remove(child);
            if (child instanceof Renderable r) {
                renderables.remove(r);
            }
            if (child instanceof NarratableEntry n) {
                narratables.remove(n);
            }
        }

        // ============ 3. 添加「加入服务器」按钮 ============
        Button joinButton = createJoinServerButton(width, screen, accessor);
        childrenList.add(joinButton);
        renderables.add(joinButton);
        narratables.add(joinButton);

        // ============ 4. 居中排列所有按钮 ============
        // 布局：3 行
        //   行0: [      单人游戏      ]
        //   行1: [      加入服务器     ]
        //   行2: [   选项... ] [   退出游戏   ]
        int rows = 3;
        int totalHeight = rows * BUTTON_HEIGHT + (rows - 1) * BUTTON_GAP;
        int centerX = width / 2;

        int startY = Math.min(
                height / 4 + 48,                          // 传统标题界面起始位置
                (height - totalHeight) / 2                // 垂直居中
        );
        startY = Math.min(startY, height - totalHeight - 40);
        startY = Math.max(startY, 60);

        // 行 0：单人游戏
        if (singleplayerBtn != null) {
            singleplayerBtn.setX(centerX - BUTTON_WIDTH / 2);
            singleplayerBtn.setY(startY);
            singleplayerBtn.setWidth(BUTTON_WIDTH);
            singleplayerBtn.setHeight(BUTTON_HEIGHT);
        }

        // 行 1：加入服务器
        int row1Y = startY + BUTTON_HEIGHT + BUTTON_GAP;
        joinButton.setX(centerX - BUTTON_WIDTH / 2);
        joinButton.setY(row1Y);
        joinButton.setWidth(BUTTON_WIDTH);
        joinButton.setHeight(BUTTON_HEIGHT);

        // 行 2：选项 + 退出（同一行）
        int row2Y = row1Y + BUTTON_HEIGHT + BUTTON_GAP;
        if (optionsBtn != null) {
            optionsBtn.setX(centerX - BUTTON_WIDTH / 2);
            optionsBtn.setY(row2Y);
            optionsBtn.setWidth(HALF_BUTTON_WIDTH);
            optionsBtn.setHeight(BUTTON_HEIGHT);
        }
        if (quitBtn != null) {
            quitBtn.setX(centerX + BUTTON_WIDTH / 2 - HALF_BUTTON_WIDTH);
            quitBtn.setY(row2Y);
            quitBtn.setWidth(HALF_BUTTON_WIDTH);
            quitBtn.setHeight(BUTTON_HEIGHT);
        }
    }

    /**
     * 创建「加入服务器」按钮，直连 play.mcyzw.top
     */
    private static Button createJoinServerButton(int width, TitleScreen screen, ScreenAccessor accessor) {
        Minecraft minecraft = accessor.youzaiworldcore$getMinecraft();

        return Button.builder(
                Component.translatable("title.youzaiworldcore.join_server"),
                button -> {
                    ServerData serverData = new ServerData(
                            "Youzai World",
                            "play.mcyzw.top",
                            ServerData.Type.OTHER
                    );
                    ServerAddress address = ServerAddress.parseString("play.mcyzw.top");
                    ConnectScreen.startConnecting(
                            screen,
                            minecraft,
                            address,
                            serverData,
                            false,
                            null
                    );
                }
        ).bounds(width / 2 - BUTTON_WIDTH / 2, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
         .build();
    }

    /**
     * 从组件中提取翻译键。
     * 如果是可翻译组件，返回其翻译键；否则返回 {@code null}。
     */
    private static String extractTranslationKey(Component component) {
        if (component == null) return null;
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return null;
    }
}
