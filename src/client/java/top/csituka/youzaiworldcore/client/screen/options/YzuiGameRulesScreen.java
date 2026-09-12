package top.csituka.youzaiworldcore.client.screen.options;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.InWorldGameRulesScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.gamerules.GameRule;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 游戏规则使用独立布局，同时保留 InWorldGameRulesScreen 的协议身份。
 * 原版 ClientPacketListener 按此类型派发规则响应，因此不能把它包在普通 Screen 中。
 * 数值校验、只发送改动项、权限变化和放弃编辑确认继续由原版控制。
 */
@SuppressWarnings("null")
public final class YzuiGameRulesScreen extends InWorldGameRulesScreen {
    private final YzuiOptionsScreen parent;
    private SettingsFrame frame;
    private boolean installing;
    private boolean updatingPermissions;

    YzuiGameRulesScreen(YzuiOptionsScreen parent) {
        super(Minecraft.getInstance().getConnection(), ignored -> Minecraft.getInstance().gui.setScreen(parent), parent);
        this.parent = parent;
        DebugLogger.info("YzuiSettings", "创建游戏规则编辑页面");
    }

    YzuiOptionsScreen parent() { return parent; }

    @Override
    protected void init() {
        super.init();
        installFrame();
    }

    private void installFrame() {
        if (installing || doneButton == null) return;
        installing = true;
        try {
            AbstractWidget focused = SettingsWidgets.focused(this);
            String query = frame == null ? "" : frame.search.getValue();
            double scroll = frame == null ? 0 : frame.content.scrollAmount();
            List<SettingsList.Row> rows = new ArrayList<>();
            if (ruleList == null) {
                rows.add(SettingsList.Row.text(YzuiOptionsScreen.text("rules.loading"), Component.empty()));
            } else {
                for (var entry : ruleList.children()) {
                    if (entry.children().isEmpty()) {
                        rows.add(SettingsList.Row.heading(SettingsWidgets.read(entry, "label", Component.class)));
                        continue;
                    }
                    Component label = readLines(entry, "label");
                    Component description = readLines(entry, "tooltip");
                    AbstractWidget[] controls = entry.children().stream().filter(AbstractWidget.class::isInstance)
                            .map(AbstractWidget.class::cast).toArray(AbstractWidget[]::new);
                    for (AbstractWidget control : controls) {
                        if (!description.getString().isEmpty()) control.setTooltip(Tooltip.create(description));
                    }
                    rows.add(SettingsList.Row.controls(label, description, controls));
                }
            }
            clearFocus();
            clearWidgets();
            TransparentButton cancel = new TransparentButton(0, 0, 150, 24, Component.translatable("gui.cancel"), this::onClose);
            frame = new SettingsFrame(this, rows, List.of(cancel, doneButton), this::addRenderableWidget, query, scroll, true);
            frame.restoreFocus(focused);
        } finally {
            installing = false;
        }
    }

    private static Component readLines(Object entry, String field) {
        List<?> lines = SettingsWidgets.read(entry, field, List.class);
        if (lines == null) return Component.empty();
        StringBuilder text = new StringBuilder();
        for (Object line : lines) {
            if (!text.isEmpty()) text.append('\n');
            text.append(SettingsWidgets.plainText((FormattedCharSequence) line));
        }
        return Component.literal(text.toString());
    }

    @Override
    protected void repositionElements() {
        if (frame == null) super.repositionElements();
        else installFrame();
    }

    @Override
    public void onGameRuleValuesUpdated(Map<ResourceKey<GameRule<?>>, String> values) {
        super.onGameRuleValuesUpdated(values);
        installFrame();
        DebugLogger.info("YzuiSettings", "收到 %d 项服务端游戏规则", values.size());
    }

    @Override
    public void onGamemasterPermissionChanged(boolean value) {
        // 动画可能延迟切换屏幕，原版会向当前页再次转发权限事件，避免重复进入。
        if (updatingPermissions) return;
        updatingPermissions = true;
        try {
            super.onGamemasterPermissionChanged(value);
        } finally {
            updatingPermissions = false;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        frame.render(g);
        // 只绘制新列表和外壳注册的控件，原版规则列表保留为校验与提交模型。
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }
}
