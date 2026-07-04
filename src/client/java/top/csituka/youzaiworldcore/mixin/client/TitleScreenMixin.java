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
import top.csituka.youzaiworldcore.client.TestScreen;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 修改Minecraft标题界面：
 * - 「单人游戏」替换为「YouzaiWorldCore 测试...」，打开空白测试页
 * - 隐藏其余所有按钮（含模组添加的）
 * - 下方添加「加入服务器」按钮，直连 play.mcyzw.top
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
     * 1. 移除所有按钮，仅保留选项/退出
     * 2. 添加「YouzaiWorldCore 测试...」按钮（替换单人游戏）
     * 3. 添加「加入服务器」按钮
     * 4. 将所有按钮居中排列（选项和退出在同一行）
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void youzaiworldcore$reworkTitleButtons(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        int width = screen.width;
        int height = screen.height;

        ScreenAccessor accessor = (ScreenAccessor) screen;

        // ============ 1. 收集需要保留的按钮 ============
        AbstractWidget optionsBtn = null;
        AbstractWidget quitBtn = null;
        List<GuiEventListener> toRemove = new ArrayList<>();

        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                String key = extractTranslationKey(widget.getMessage());
                if ("menu.options".equals(key)) {
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

        // ============ 3. 添加新按钮 ============
        Minecraft minecraft = accessor.youzaiworldcore$getMinecraft();
        Button testButton = null;

        // 3a. YouzaiWorldCore 测试... 按钮（开发者模式启用时显示）
        boolean showTest = ClientExternalSettings.isDevModeEnabled();
        if (showTest) {
            testButton = Button.builder(
                    Component.translatable("title.youzaiworldcore.test_page"),
                    button -> minecraft.gui.setScreen(new TestScreen(
                            Component.translatable("title.youzaiworldcore.test_page")))
            ).bounds(
                    width / 2 - BUTTON_WIDTH / 2, 0, BUTTON_WIDTH, BUTTON_HEIGHT
            ).build();
            childrenList.add(testButton);
            renderables.add(testButton);
            narratables.add(testButton);
        }

        // 3b. 加入服务器按钮
        Button joinButton = createJoinServerButton(width, screen, accessor);
        childrenList.add(joinButton);
        renderables.add(joinButton);
        narratables.add(joinButton);

        // ============ 4. 居中排列所有按钮 ============
        // 根据开发者模式是否启用，动态调整行数
        boolean hasTestButton = testButton != null;
        // 布局：
        //   行0: [ YouzaiWorldCore 测试... ]  (仅开发者模式)
        //   行1: [       加入服务器         ]
        //   行2: [   选项... ] [   退出游戏   ]
        int rows = hasTestButton ? 3 : 2;
        int totalHeight = rows * BUTTON_HEIGHT + (rows - 1) * BUTTON_GAP;
        int centerX = width / 2;

        int startY = Math.min(
                height / 4 + 48,
                (height - totalHeight) / 2
        );
        startY = Math.min(startY, height - totalHeight - 40);
        startY = Math.max(startY, 60);

        int currentY = startY;

        // 行 0：YouzaiWorldCore 测试...（可选）
        if (hasTestButton) {
            testButton.setX(centerX - BUTTON_WIDTH / 2);
            testButton.setY(currentY);
            testButton.setWidth(BUTTON_WIDTH);
            testButton.setHeight(BUTTON_HEIGHT);
            currentY += BUTTON_HEIGHT + BUTTON_GAP;
        }

        // 行 1/0：加入服务器
        joinButton.setX(centerX - BUTTON_WIDTH / 2);
        joinButton.setY(currentY);
        joinButton.setWidth(BUTTON_WIDTH);
        joinButton.setHeight(BUTTON_HEIGHT);
        currentY += BUTTON_HEIGHT + BUTTON_GAP;

        // 行 2/1：选项 + 退出（同一行）
        if (optionsBtn != null) {
            optionsBtn.setX(centerX - BUTTON_WIDTH / 2);
            optionsBtn.setY(currentY);
            optionsBtn.setWidth(HALF_BUTTON_WIDTH);
            optionsBtn.setHeight(BUTTON_HEIGHT);
        }
        if (quitBtn != null) {
            quitBtn.setX(centerX + BUTTON_WIDTH / 2 - HALF_BUTTON_WIDTH);
            quitBtn.setY(currentY);
            quitBtn.setWidth(HALF_BUTTON_WIDTH);
            quitBtn.setHeight(BUTTON_HEIGHT);
        }
    }

    /**
     * 创建「加入服务器」按钮，直连 play.mcyzw.top
     */
    private static Button createJoinServerButton(int width, TitleScreen screen, ScreenAccessor accessor) {
        Minecraft minecraft = Objects.requireNonNull(accessor.youzaiworldcore$getMinecraft());
        TitleScreen safeScreen = Objects.requireNonNull(screen);

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
                            safeScreen,
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

    private static String extractTranslationKey(Component component) {
        if (component == null) return null;
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return null;
    }
}
