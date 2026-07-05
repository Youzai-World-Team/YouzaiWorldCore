package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractOptionSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 修改「选项」页面：
 * - 删除「在线选项...」（{@code options.online}）和「视场角选项」（{@code options.fov}）
 * - 将「遥测数据...」按钮替换为「已安装的模组...」，打开 ModMenu 主界面
 * <p>
 * 注意：替换按钮时也会将其注册到 {@link GridLayout} 的 children 列表中，
 * 确保窗口最大化/缩放时按钮能跟随 GridLayout 重新布局，避免位置偏移。
 */
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/OptionsScreenMixin");
    private static final String MODS_SCREEN_CLASS = "com.terraformersmc.modmenu.gui.ModsScreen";
    private static boolean modMenuChecked = false;
    private static Class<?> modsScreenClass = null;

    /**
     * 缓存反射 Field，避免每次 init() 都重复查找
     */
    private static Field optionsScreenLayoutField;
    private static Field optionsScreenInWorldField;
    private static Field headerFooterContentsField;
    private static Field headerFooterHeaderField;
    private static Field frameLayoutChildrenField;
    private static Field gridLayoutChildrenField;
    private static Field childWrapperChildField;
    private static Field gridChildRowField;
    private static Field gridChildColField;
    private static Field gridChildOccupiedRowsField;
    private static Field gridChildOccupiedColsField;
    private static Field gridChildSettingsField;
    private static Field linearLayoutWrappedField;
    private static Constructor<?> gridChildConstructor;
    private static boolean reflectionReady = false;

    private static final String[] KEYS_TO_REMOVE = {
            "options.online"
    };

    private static boolean isWidgetToRemove(AbstractWidget widget) {
        String key = extractTranslationKey(widget.getMessage());
        if (isKeyToRemove(key)) return true;
        if (containsTranslationKey(widget.getMessage(), "options.fov")) return true;
        return widget instanceof AbstractOptionSliderButton;
    }

    private static boolean containsTranslationKey(Component component, String key) {
        if (component == null) return false;
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents tc && key.equals(tc.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (containsTranslationKey(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    // ============ 反射初始化（layout 结构遍历） ============

    /**
     * 初始化所有需要用到的反射 Field 和 Constructor，只执行一次。
     */
    private static void initReflection() {
        if (reflectionReady) return;
        try {
            // 1. OptionsScreen.layout / inWorld → HeaderAndFooterLayout, boolean
            optionsScreenLayoutField = OptionsScreen.class.getDeclaredField("layout");
            optionsScreenLayoutField.setAccessible(true);
            optionsScreenInWorldField = OptionsScreen.class.getDeclaredField("inWorld");
            optionsScreenInWorldField.setAccessible(true);

            // 2. HeaderAndFooterLayout.headerFrame / contentsFrame → FrameLayout
            headerFooterHeaderField = HeaderAndFooterLayout.class.getDeclaredField("headerFrame");
            headerFooterHeaderField.setAccessible(true);
            headerFooterContentsField = HeaderAndFooterLayout.class.getDeclaredField("contentsFrame");
            headerFooterContentsField.setAccessible(true);

            // 3. FrameLayout.children → List<FrameLayout.ChildContainer>
            frameLayoutChildrenField = FrameLayout.class.getDeclaredField("children");
            frameLayoutChildrenField.setAccessible(true);

            // 4. GridLayout.children → List<GridLayout.ChildContainer>
            gridLayoutChildrenField = GridLayout.class.getDeclaredField("children");
            gridLayoutChildrenField.setAccessible(true);

            // 5. AbstractLayout.AbstractChildWrapper → 内部类是包级可见，通过 Class.forName 访问
            Class<?> childWrapperClass = Class.forName("net.minecraft.client.gui.layouts.AbstractLayout$AbstractChildWrapper");
            childWrapperChildField = childWrapperClass.getField("child");

            // 6. GridLayout.ChildContainer 的私有字段
            Class<?> childContainerClass = Class.forName("net.minecraft.client.gui.layouts.GridLayout$ChildContainer");
            gridChildRowField = childContainerClass.getDeclaredField("row");
            gridChildRowField.setAccessible(true);
            gridChildColField = childContainerClass.getDeclaredField("column");
            gridChildColField.setAccessible(true);
            gridChildOccupiedRowsField = childContainerClass.getDeclaredField("occupiedRows");
            gridChildOccupiedRowsField.setAccessible(true);
            gridChildOccupiedColsField = childContainerClass.getDeclaredField("occupiedColumns");
            gridChildOccupiedColsField.setAccessible(true);

            // 7. AbstractChildWrapper.layoutSettings → 复制到新 ChildContainer
            gridChildSettingsField = childWrapperClass.getField("layoutSettings");

            // 8. LinearLayout.wrapped → GridLayout（LinearLayout 内部包装了一个 GridLayout）
            linearLayoutWrappedField = LinearLayout.class.getDeclaredField("wrapped");
            linearLayoutWrappedField.setAccessible(true);

            // 9. 构造函数：GridLayout.ChildContainer(LayoutElement, int row, int col, int occupiedRows, int occupiedColumns, LayoutSettings)
            gridChildConstructor = childContainerClass.getDeclaredConstructor(
                    LayoutElement.class, int.class, int.class, int.class, int.class,
                    net.minecraft.client.gui.layouts.LayoutSettings.class
            );
            gridChildConstructor.setAccessible(true);

            reflectionReady = true;
            if (ClientExternalSettings.isLogToFile()) {
                LOGGER.info("Reflection fields initialized for GridLayout manipulation");
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to initialize reflection for GridLayout manipulation", e);
        }
    }

    /**
     * 从 {@link OptionsScreen} 的 layout 层级中遍历找到 {@link GridLayout} 实例。
     * 路径：OptionsScreen.layout → HeaderAndFooterLayout.contentsFrame → FrameLayout.children → GridLayout
     */
    @SuppressWarnings("unchecked")
    private static GridLayout findGridLayout(OptionsScreen screen) {
        try {
            initReflection();
            if (!reflectionReady) return null;

            HeaderAndFooterLayout headerFooter = (HeaderAndFooterLayout) optionsScreenLayoutField.get(screen);
            FrameLayout contentsFrame = (FrameLayout) headerFooterContentsField.get(headerFooter);
            List<?> frameChildren = (List<?>) frameLayoutChildrenField.get(contentsFrame);

            for (Object container : frameChildren) {
                LayoutElement child = (LayoutElement) childWrapperChildField.get(container);
                if (child instanceof GridLayout grid) {
                    return grid;
                }
            }
            LOGGER.warn("GridLayout not found in layout hierarchy");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to find GridLayout", e);
        }
        return null;
    }

    /**
     * 在 {@link GridLayout} 的 children 列表中寻找包裹 {@code targetWidget} 的 ChildContainer，
     * 将其替换为包裹 {@code newWidget} 的新容器，使新按钮参与 GridLayout 布局。
     *
     * @return 替换成功返回 true
     */
    @SuppressWarnings("unchecked")
    private static boolean replaceInGridLayout(GridLayout grid, AbstractWidget targetWidget, AbstractWidget newWidget) {
        try {
            initReflection();
            if (!reflectionReady) return false;

            List<?> gridChildren = (List<?>) gridLayoutChildrenField.get(grid);
            if (gridChildren == null) return false;

            for (int i = 0; i < gridChildren.size(); i++) {
                Object container = gridChildren.get(i);
                LayoutElement child = (LayoutElement) childWrapperChildField.get(container);
                if (child == targetWidget) {
                    // 读取原始 ChildContainer 的 grid 位置信息
                    int row = gridChildRowField.getInt(container);
                    int col = gridChildColField.getInt(container);
                    int occupiedRows = gridChildOccupiedRowsField.getInt(container);
                    int occupiedCols = gridChildOccupiedColsField.getInt(container);
                    Object layoutSettings = gridChildSettingsField.get(container);

                    // 创建新的 ChildContainer 包裹 modsButton
                    Object newContainer = gridChildConstructor.newInstance(
                            newWidget, row, col, occupiedRows, occupiedCols, layoutSettings
                    );

                    // 替换列表中的条目（先转型为原始 List 以突破通配符限制）
                    ((List) gridChildren).set(i, newContainer);

                    if (ClientExternalSettings.isLogToFile()) {
                        LOGGER.debug("Replaced telemetry button with mods button in GridLayout at (col={}, row={})", col, row);
                    }
                    return true;
                }
            }
            LOGGER.warn("Telemetry button not found in GridLayout children");
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to replace button in GridLayout", e);
        }
        return false;
    }

    // ============ 表头布局修复：展开「世界选项」按钮 ============

    /**
     * 遍历 OptionsScreen 的表头布局层级，在 HeaderAndFooterLayout 的 headerFrame 中
     * 找到水平 LinearLayout，移除其中所有 {@link #isWidgetToRemove} 匹配的子元素
     *（FOV 滑块和在线选项按钮），使剩余按钮独占整行宽度。
     * <p>
     * 路径：OptionsScreen.layout → HeaderAndFooterLayout.headerFrame → FrameLayout
     *   → 垂直 LinearLayout → .wrapped(GridLayout) → 子[1] = 水平 LinearLayout
     *   → .wrapped(GridLayout) → 遍历子元素移除不需要的
     *
     * @return 至少移除了一个元素返回 true
     */
    @SuppressWarnings("unchecked")
    private static boolean removeFovSliderFromHeader(OptionsScreen screen) {
        try {
            initReflection();
            if (!reflectionReady) return false;

            // 1. Get HeaderAndFooterLayout
            HeaderAndFooterLayout headerFooter = (HeaderAndFooterLayout) optionsScreenLayoutField.get(screen);
            // 2. Get headerFrame
            FrameLayout headerFrame = (FrameLayout) headerFooterHeaderField.get(headerFooter);
            // 3. Get FrameLayout's children
            List<?> frameChildren = (List<?>) frameLayoutChildrenField.get(headerFrame);
            if (frameChildren.isEmpty()) return false;

            // 4. First child: vertical LinearLayout
            Object verticalContainer = frameChildren.getFirst();
            LayoutElement verticalChild = (LayoutElement) childWrapperChildField.get(verticalContainer);
            if (!(verticalChild instanceof LinearLayout verticalLayout)) return false;

            // 5. Get vertical layout's wrapped GridLayout
            GridLayout verticalGrid = (GridLayout) linearLayoutWrappedField.get(verticalLayout);
            List<?> verticalChildren = (List<?>) gridLayoutChildrenField.get(verticalGrid);
            if (verticalChildren.size() < 2) return false;

            // 6. Second child: horizontal LinearLayout
            Object horizontalContainer = verticalChildren.get(1);
            LayoutElement horizontalChild = (LayoutElement) childWrapperChildField.get(horizontalContainer);
            if (!(horizontalChild instanceof LinearLayout horizontalLayout)) return false;

            // 7. Get horizontal layout's wrapped GridLayout
            GridLayout horizontalGrid = (GridLayout) linearLayoutWrappedField.get(horizontalLayout);
            List<?> horizontalChildren = (List<?>) gridLayoutChildrenField.get(horizontalGrid);
            if (horizontalChildren.isEmpty()) return false;

            // 8. 遍历移除所有 isWidgetToRemove 匹配的子元素（FOV 滑块、在线选项等）
            boolean removed = false;
            var it = horizontalChildren.iterator();
            while (it.hasNext()) {
                Object container = it.next();
                LayoutElement child = (LayoutElement) childWrapperChildField.get(container);
                    if (child instanceof AbstractWidget widget && isWidgetToRemove(widget)) {
                    it.remove();
                    removed = true;
                    if (ClientExternalSettings.isLogToFile()) {
                        LOGGER.debug("Removed widget from header horizontal layout: {}",
                                extractTranslationKey(widget.getMessage()));
                    }
                }
            }

            return removed;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to remove widgets from header layout", e);
        }
        return false;
    }

    // ============ 表头布局：添加「YouzaiWorldCore 设置」按钮 ============

    /**
     * 在 OptionsScreen 表头水平布局中添加「YouzaiWorldCore 设置...」按钮。
     * 仅适用于 {@code inWorld == false}（从标题屏幕打开选项）的情况。
     * <p>
     * 按钮点击后打开 {@link top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen}。
     *
     * @return 创建成功的 Button 实例，失败返回 null
     */
    @SuppressWarnings("unchecked")
    private static Button addSettingsButtonToHeader(OptionsScreen screen, Minecraft minecraft) {
        try {
            initReflection();
            if (!reflectionReady) return null;

            // 遍历到水平布局的 wrapped GridLayout（与 removeFovSliderFromHeader 相同路径）
            HeaderAndFooterLayout headerFooter = (HeaderAndFooterLayout) optionsScreenLayoutField.get(screen);
            FrameLayout headerFrame = (FrameLayout) headerFooterHeaderField.get(headerFooter);
            List<?> frameChildren = (List<?>) frameLayoutChildrenField.get(headerFrame);
            if (frameChildren.isEmpty()) return null;

            Object verticalContainer = frameChildren.getFirst();
            LayoutElement verticalChild = (LayoutElement) childWrapperChildField.get(verticalContainer);
            if (!(verticalChild instanceof LinearLayout verticalLayout)) return null;

            GridLayout verticalGrid = (GridLayout) linearLayoutWrappedField.get(verticalLayout);
            List<?> verticalChildren = (List<?>) gridLayoutChildrenField.get(verticalGrid);
            if (verticalChildren.size() < 2) return null;

            Object horizontalContainer = verticalChildren.get(1);
            LayoutElement horizontalChild = (LayoutElement) childWrapperChildField.get(horizontalContainer);
            if (!(horizontalChild instanceof LinearLayout horizontalLayout)) return null;

            GridLayout horizontalGrid = (GridLayout) linearLayoutWrappedField.get(horizontalLayout);

            // 创建按钮（打开 YouzaiWorldCore 设置页面）
            Button settingsButton = Button.builder(
                    Component.translatable("options.youzaiworldcore.settings"),
                    btn -> {
                        var settingsScreen = new top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen(screen);
                        minecraft.gui.setScreen(settingsScreen);
                    }
            ).build();

            // 添加到 GridLayout（列 0，行 0）
            horizontalGrid.addChild(settingsButton, 0, 0);

            if (ClientExternalSettings.isLogToFile()) {
                LOGGER.debug("Added YouzaiWorldCore settings button to header");
            }
            return settingsButton;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to add settings button to header", e);
        }
        return null;
    }

    // ============ Mixin 注入 ============

    @Inject(method = "init", at = @At("TAIL"))
    private void youzaiworldcore$reworkOptionsScreen(CallbackInfo ci) {
        OptionsScreen screen = (OptionsScreen) (Object) this;
        ScreenAccessor accessor = (ScreenAccessor) screen;
        List<Renderable> renderables = accessor.youzaiworldcore$getRenderables();
        List<NarratableEntry> narratables = accessor.youzaiworldcore$getNarratables();
        List<GuiEventListener> childrenList = accessor.youzaiworldcore$getChildren();

        // ============ 1. 删除不需要的按钮 ============
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                if (isWidgetToRemove(widget)) {
                    toRemove.add(child);
                }
            }
        }
        for (GuiEventListener child : toRemove) {
            childrenList.remove(child);
            renderables.remove((Object) child);
            narratables.remove((Object) child);
        }

        // ============ 2. 展开表头「世界选项」按钮（移除 FOV 滑块占位） ============
        // FOV 滑块虽从 Screen 渲染/事件列表中移除，但仍在表头 HorizontalLayout 中占位，
        // 导致「世界选项」按钮偏右。需要从布局中移除滑块以腾出整行宽度。
        boolean headerFixed = removeFovSliderFromHeader(screen);

        // ============ 3. 标题屏幕选项：添加「YouzaiWorldCore 设置」按钮 ============
        // 从标题屏幕打开选项时（inWorld == false），表头中 FOV 滑块和在线选项均被移除，
        // 整行留空。在此位置添加一个 YouzaiWorldCore 设置按钮。
        boolean settingsAdded = false;
        try {
            if (!optionsScreenInWorldField.getBoolean(screen)) {
                Minecraft mc = accessor.youzaiworldcore$getMinecraft();
                Button settingsBtn = addSettingsButtonToHeader(screen, mc);
                if (settingsBtn != null) {
                    childrenList.add(settingsBtn);
                    renderables.add(settingsBtn);
                    narratables.add(settingsBtn);
                    settingsAdded = true;
                }
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to check inWorld field", e);
        }

        // ============ 4. 替换遥测按钮为已安装的模组 ============
        AbstractWidget telemetryBtn = null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                String key = extractTranslationKey(widget.getMessage());
                if ("options.telemetry".equals(key)) {
                    telemetryBtn = widget;
                    break;
                }
            }
        }
        if (telemetryBtn == null) return;

        if (!ensureModMenuLoaded()) {
            LOGGER.warn("ModMenu not found, cannot replace telemetry button with mods button");
            return;
        }

        Minecraft minecraft = accessor.youzaiworldcore$getMinecraft();
        Button modsButton = Button.builder(
                Component.translatable("options.youzaiworldcore.installed_mods"),
                button -> openModsScreen(minecraft, screen)
        ).build();

        // ============ 5. 将新按钮注册到 GridLayout（使其参与布局） ============
        GridLayout grid = findGridLayout(screen);
        boolean gridReplaced = false;
        if (grid != null) {
            gridReplaced = replaceInGridLayout(grid, telemetryBtn, modsButton);
        }

        // ============ 5. 更新 Screen 的 children / renderables / narratables ============
        int oldIndex = childrenList.indexOf(telemetryBtn);
        childrenList.remove(telemetryBtn);
        renderables.remove((Object) telemetryBtn);
        narratables.remove((Object) telemetryBtn);

        if (oldIndex >= 0 && oldIndex <= childrenList.size()) {
            childrenList.add(oldIndex, modsButton);
        } else {
            childrenList.add(modsButton);
        }
        renderables.add(modsButton);
        narratables.add(modsButton);

        // ============ 6. 如果修改了布局，触发重新布局 ============
        if (gridReplaced || headerFixed || settingsAdded) {
            accessor.youzaiworldcore$repositionElements();
        }
    }

    private static boolean isKeyToRemove(String key) {
        for (String k : KEYS_TO_REMOVE) {
            if (k.equals(key)) return true;
        }
        return false;
    }

    private static boolean ensureModMenuLoaded() {
        if (modMenuChecked) return modsScreenClass != null;
        modMenuChecked = true;
        try {
            modsScreenClass = Class.forName(MODS_SCREEN_CLASS);
            modsScreenClass.getConstructor(Screen.class);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            LOGGER.warn("ModMenu not found: {}", e.getMessage());
            modsScreenClass = null;
            return false;
        }
    }

    private static void openModsScreen(Minecraft minecraft, Screen parent) {
        if (modsScreenClass == null) return;
        try {
            Screen modsScreen = (Screen) modsScreenClass.getConstructor(Screen.class)
                    .newInstance(parent);
            minecraft.gui.setScreen(modsScreen);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to open ModMenu ModsScreen", e);
        }
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
