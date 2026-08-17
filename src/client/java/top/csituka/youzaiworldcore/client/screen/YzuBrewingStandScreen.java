package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import top.csituka.youzaiworldcore.client.screen.brewing.BrewingGuideRecipe;
import top.csituka.youzaiworldcore.client.screen.brewing.BrewingGuideRecipes;
import top.csituka.youzaiworldcore.mixin.client.SlotPositionAccessor;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * YZUI 酿造台屏幕。
 * <p>
 * 主面板重新排列燃料、原料、三瓶药水和玩家背包槽位，并提供独立的烈焰粉储量条与酿造进度条。
 * 左侧酿造指南提供搜索、翻页、配方材料链和药水效果说明。所有槽位编号及服务端校验保持原样。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuBrewingStandScreen extends AbstractContainerScreen<BrewingStandMenu> {

    private static final String MODULE = "YzuBrewingStandScreen";

    // ===== 面板尺寸与颜色 =====

    private static final int MAIN_WIDTH = 176;
    private static final int MAIN_HEIGHT = 190;
    private static final int GUIDE_WIDTH = 136;
    private static final int GUIDE_GAP = 6;
    private static final int PANEL_RADIUS = 6;

    private static final int MAIN_BG = 0xA8FFFFFF;
    private static final int GUIDE_BG = 0xB8F6F4FA;
    private static final int DIVIDER_COLOR = 0x30405050;
    private static final int TITLE_COLOR = 0xFF315F62;
    private static final int LABEL_COLOR = 0xCC404040;
    private static final int MUTED_COLOR = 0xAA505050;
    private static final int ACCENT_COLOR = 0xC05BB4A8;
    private static final int GUIDE_ACCENT = 0xC07B6BA8;

    // ===== 槽位布局 =====

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3;
    private static final int POTION_SLOT_COLOR = 0x506FC8D0;
    private static final int INGREDIENT_SLOT_COLOR = 0x507AAE72;
    private static final int FUEL_SLOT_COLOR = 0x60E0A34B;
    private static final int INVENTORY_SLOT_COLOR = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x78FFFFFF;

    private static final int[] BOTTLE_X = { 49, 80, 111 };
    private static final int[] BOTTLE_Y = { 71, 75, 71 };
    private static final int INGREDIENT_X = 80;
    private static final int INGREDIENT_Y = 31;
    private static final int FUEL_SLOT_X = 18;
    private static final int FUEL_SLOT_Y = 41;
    private static final int INVENTORY_X = 7;
    private static final int INVENTORY_Y = 108;
    private static final int HOTBAR_Y = 166;

    // ===== 状态指示 =====

    private static final int FUEL_BAR_X = 8;
    private static final int FUEL_BAR_Y = 64;
    private static final int FUEL_BAR_W = 34;
    private static final int FUEL_BAR_H = 5;
    private static final int FUEL_BAR_BG = 0x30000000;
    private static final int FUEL_BAR_FILL = 0xE0E6A23C;

    private static final int BREW_BAR_X = 53;
    private static final int BREW_BAR_Y = 55;
    private static final int BREW_BAR_W = 70;
    private static final int BREW_BAR_H = 6;
    private static final int BREW_BAR_BG = 0x30000000;
    private static final int BREW_BAR_FILL = 0xE064B9AE;
    private static final int FLOW_LINE_COLOR = 0x8064A6A0;
    private static final float BREW_TIME_TICKS = 400.0F;

    // ===== 标题按钮 =====

    private static final int BUTTON_SIZE = 14;
    private static final int BUTTON_RADIUS = 4;
    private static final int BUTTON_TOP = 2;
    private static final int CLOSE_X = MAIN_WIDTH - 20;
    private static final int GUIDE_TOGGLE_X = MAIN_WIDTH - 39;
    private static final int BUTTON_BG = 0x40FFFFFF;
    private static final int BUTTON_BG_HOVER = 0x80FFFFFF;
    private static final int BUTTON_ICON = 0xCC404040;
    private static final int BUTTON_ICON_HOVER = 0xFF111111;
    private static final String CLOSE_GLYPH = "\u00d7";
    private static final ItemStack TITLE_ICON = new ItemStack(Items.BREWING_STAND);
    private static final ItemStack GUIDE_ICON = new ItemStack(Items.KNOWLEDGE_BOOK);

    // ===== 酿造指南 =====

    private static final int SEARCH_X = 6;
    private static final int SEARCH_Y = 23;
    private static final int SEARCH_W = GUIDE_WIDTH - 12;
    private static final int SEARCH_H = 16;
    private static final int LIST_X = 5;
    private static final int LIST_Y = 45;
    private static final int ROW_W = GUIDE_WIDTH - 10;
    private static final int ROW_H = 24;
    private static final int ROWS_PER_PAGE = 5;
    private static final int PAGE_Y = 169;
    private static final int PAGE_BUTTON_W = 16;
    private static final int PAGE_BUTTON_H = 14;
    private static final int ROW_BG = 0x28FFFFFF;
    private static final int ROW_HOVER_BG = 0x58FFFFFF;

    private final List<BrewingGuideRecipe> filteredRecipes = new ArrayList<>();

    private EditBox guideSearchBox;
    private boolean guideOpen = true;
    private boolean guideInitialized;
    private int guidePage;

    /** 创建酿造台 YZUI 屏幕。 */
    public YzuBrewingStandScreen(BrewingStandMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, MAIN_WIDTH, MAIN_HEIGHT);
        DebugLogger.info(MODULE, "创建 YZUI 酿造台屏幕: title=%s menuType=%s",
                title.getString(), menu.getType());
    }

    // ===== 初始化与布局 =====

    @Override
    protected void init() {
        super.init();

        if (!this.guideInitialized) {
            this.guideOpen = canFitGuide();
            this.guideInitialized = true;
        } else if (this.guideOpen && !canFitGuide()) {
            this.guideOpen = false;
        }

        positionMainPanel();
        positionSlots();
        initGuideSearchBox();
        rebuildFilteredRecipes();

        DebugLogger.debug(MODULE, "初始化酿造台布局: left=%d top=%d guideOpen=%s",
                this.leftPos, this.topPos, this.guideOpen);
    }

    private boolean canFitGuide() {
        return this.width >= MAIN_WIDTH + GUIDE_WIDTH + GUIDE_GAP + 2;
    }

    private void positionMainPanel() {
        this.topPos = (this.height - this.imageHeight) / 2;
        if (this.guideOpen) {
            int totalWidth = MAIN_WIDTH + GUIDE_WIDTH + GUIDE_GAP;
            int groupLeft = Math.max(1, (this.width - totalWidth) / 2);
            this.leftPos = groupLeft + GUIDE_WIDTH + GUIDE_GAP;
        } else {
            this.leftPos = (this.width - this.imageWidth) / 2;
        }
    }

    private void positionSlots() {
        for (int i = 0; i < 3; i++) {
            setSlotPosition(i, BOTTLE_X[i], BOTTLE_Y[i]);
        }
        setSlotPosition(3, INGREDIENT_X, INGREDIENT_Y);
        setSlotPosition(4, FUEL_SLOT_X, FUEL_SLOT_Y);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                setSlotPosition(5 + row * 9 + column,
                        INVENTORY_X + column * 18,
                        INVENTORY_Y + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            setSlotPosition(32 + column, INVENTORY_X + column * 18, HOTBAR_Y);
        }
    }

    private void setSlotPosition(int index, int x, int y) {
        if (index < 0 || index >= this.menu.slots.size()) {
            DebugLogger.warn(MODULE, "槽位索引越界，无法调整位置: index=%d size=%d",
                    index, this.menu.slots.size());
            return;
        }
        SlotPositionAccessor accessor = (SlotPositionAccessor) this.menu.slots.get(index);
        accessor.youzaiworldcore$setX(x);
        accessor.youzaiworldcore$setY(y);
    }

    private void initGuideSearchBox() {
        String previousQuery = this.guideSearchBox == null ? "" : this.guideSearchBox.getValue();
        int guideX = guideLeft();
        this.guideSearchBox = new EditBox(this.font,
                guideX + SEARCH_X, this.topPos + SEARCH_Y,
                SEARCH_W, SEARCH_H,
                Component.translatable("screen.youzaiworldcore.brewing.search"));
        this.guideSearchBox.setMaxLength(40);
        this.guideSearchBox.setBordered(false);
        this.guideSearchBox.setTextColor(0xFF303030);
        this.guideSearchBox.setHint(Component.translatable("screen.youzaiworldcore.brewing.search"));
        this.guideSearchBox.setResponder(query -> {
            this.guidePage = 0;
            rebuildFilteredRecipes();
        });
        this.guideSearchBox.setValue(previousQuery);
        this.guideSearchBox.setVisible(this.guideOpen);
        this.guideSearchBox.active = this.guideOpen;
        this.addRenderableWidget(this.guideSearchBox);
    }

    // ===== 渲染 =====

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawMainPanel(graphics);
        drawTitle(graphics);
        drawHeaderButtons(graphics, mouseX, mouseY);
        drawBrewingWorkspace(graphics, mouseX, mouseY);
        drawInventoryArea(graphics);
        if (this.guideOpen) {
            drawGuide(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // YZUI 面板在 extractRenderState 中绘制。
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 标题与背包标签由本屏幕统一绘制。
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawSlotBackgrounds(graphics, mouseX, mouseY);
        super.extractSlots(graphics, mouseX, mouseY);
    }

    private void drawMainPanel(GuiGraphicsExtractor graphics) {
        fillRounded(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                PANEL_RADIUS, MAIN_BG);
    }

    private void drawTitle(GuiGraphicsExtractor graphics) {
        int iconX = this.leftPos + 8;
        int iconY = this.topPos + 5;
        fillRounded(graphics, iconX, iconY, 12, 12, 3, POTION_SLOT_COLOR);

        graphics.pose().pushMatrix();
        graphics.pose().translate(iconX, iconY);
        graphics.pose().scale(0.75F, 0.75F);
        graphics.item(TITLE_ICON, 0, 0, 0);
        graphics.pose().popMatrix();

        int titleX = iconX + 16;
        int titleMaxWidth = this.leftPos + GUIDE_TOGGLE_X - titleX - 3;
        String titleText = ellipsize(this.title, titleMaxWidth);
        graphics.text(this.font, titleText, titleX, this.topPos + 5, TITLE_COLOR, false);
        fillRounded(graphics, titleX, this.topPos + 15,
                Math.min(this.font.width(titleText), 80), 2, 1, ACCENT_COLOR);
    }

    private void drawHeaderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int guideX = this.leftPos + GUIDE_TOGGLE_X;
        int buttonY = this.topPos + BUTTON_TOP;
        boolean guideHovered = isInside(mouseX, mouseY, guideX, buttonY, BUTTON_SIZE, BUTTON_SIZE);
        fillRounded(graphics, guideX, buttonY, BUTTON_SIZE, BUTTON_SIZE, BUTTON_RADIUS,
                guideHovered ? BUTTON_BG_HOVER : BUTTON_BG);
        graphics.pose().pushMatrix();
        graphics.pose().translate(guideX + 1, buttonY + 1);
        graphics.pose().scale(0.75F, 0.75F);
        graphics.item(GUIDE_ICON, 0, 0, 0);
        graphics.pose().popMatrix();
        if (guideHovered) {
            graphics.setTooltipForNextFrame(
                    Component.translatable("screen.youzaiworldcore.brewing.toggle_guide"), mouseX, mouseY);
        }

        int closeX = this.leftPos + CLOSE_X;
        boolean closeHovered = isInside(mouseX, mouseY, closeX, buttonY, BUTTON_SIZE, BUTTON_SIZE);
        fillRounded(graphics, closeX, buttonY, BUTTON_SIZE, BUTTON_SIZE, BUTTON_RADIUS,
                closeHovered ? BUTTON_BG_HOVER : BUTTON_BG);
        int textX = closeX + (BUTTON_SIZE - this.font.width(CLOSE_GLYPH)) / 2;
        int textY = buttonY + (BUTTON_SIZE - this.font.lineHeight) / 2;
        graphics.text(this.font, CLOSE_GLYPH, textX, textY,
                closeHovered ? BUTTON_ICON_HOVER : BUTTON_ICON, false);
    }

    private void drawBrewingWorkspace(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x + 45, y + 25, x + 46, y + 89, DIVIDER_COLOR);
        graphics.text(this.font, ellipsize(Component.translatable("screen.youzaiworldcore.brewing.fuel"), 35),
                x + 7, y + 27, MUTED_COLOR, false);

        int fuelWidth = Mth.clamp((FUEL_BAR_W * this.menu.getFuel() + 19) / 20, 0, FUEL_BAR_W);
        fillRounded(graphics, x + FUEL_BAR_X, y + FUEL_BAR_Y,
                FUEL_BAR_W, FUEL_BAR_H, 2, FUEL_BAR_BG);
        if (fuelWidth > 0) {
            fillRounded(graphics, x + FUEL_BAR_X, y + FUEL_BAR_Y,
                    fuelWidth, FUEL_BAR_H, 2, FUEL_BAR_FILL);
        }

        int ticks = this.menu.getBrewingTicks();
        int brewWidth = ticks <= 0 ? 0
                : Mth.clamp((int) (BREW_BAR_W * (1.0F - ticks / BREW_TIME_TICKS)), 0, BREW_BAR_W);
        fillRounded(graphics, x + BREW_BAR_X, y + BREW_BAR_Y,
                BREW_BAR_W, BREW_BAR_H, 3, BREW_BAR_BG);
        if (brewWidth > 0) {
            fillRounded(graphics, x + BREW_BAR_X, y + BREW_BAR_Y,
                    brewWidth, BREW_BAR_H, 3, BREW_BAR_FILL);
        }

        // 原料槽到三瓶药水槽的流向线。
        graphics.fill(x + 87, y + 47, x + 89, y + BREW_BAR_Y, FLOW_LINE_COLOR);
        graphics.fill(x + 87, y + BREW_BAR_Y + BREW_BAR_H,
                x + 89, y + 67, FLOW_LINE_COLOR);
        graphics.fill(x + 57, y + 66, x + 120, y + 68, FLOW_LINE_COLOR);
        for (int index = 0; index < BOTTLE_X.length; index++) {
            int centerX = x + BOTTLE_X[index] + 8;
            graphics.fill(centerX - 1, y + 67,
                    centerX + 1, y + BOTTLE_Y[index], FLOW_LINE_COLOR);
        }

        int brewPercent = ticks <= 0 ? 0 : Mth.clamp((int) (100.0F * (1.0F - ticks / BREW_TIME_TICKS)), 0, 100);
        Component progressText = Component.translatable(
                "screen.youzaiworldcore.brewing.brew_progress", brewPercent);
        graphics.text(this.font, ellipsize(progressText, 66),
                x + 103, y + 34, MUTED_COLOR, false);

        if (isInside(mouseX, mouseY, x + FUEL_BAR_X, y + FUEL_BAR_Y, FUEL_BAR_W, FUEL_BAR_H)
                || isInside(mouseX, mouseY, x + FUEL_SLOT_X, y + FUEL_SLOT_Y, SLOT_SIZE, SLOT_SIZE)) {
            graphics.setTooltipForNextFrame(
                    Component.translatable("screen.youzaiworldcore.brewing.fuel_level", this.menu.getFuel()),
                    mouseX, mouseY);
        } else if (isInside(mouseX, mouseY, x + BREW_BAR_X, y + BREW_BAR_Y, BREW_BAR_W, BREW_BAR_H)) {
            graphics.setTooltipForNextFrame(
                    Component.translatable("screen.youzaiworldcore.brewing.brew_progress", brewPercent),
                    mouseX, mouseY);
        }
    }

    private void drawInventoryArea(GuiGraphicsExtractor graphics) {
        graphics.fill(this.leftPos + 7, this.topPos + 102,
                this.leftPos + this.imageWidth - 7, this.topPos + 103, DIVIDER_COLOR);
        graphics.text(this.font, this.playerInventoryTitle,
                this.leftPos + 7, this.topPos + 94, LABEL_COLOR, false);
    }

    private void drawSlotBackgrounds(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int index = 0; index < this.menu.slots.size(); index++) {
            Slot slot = this.menu.slots.get(index);
            if (!slot.isActive()) {
                continue;
            }

            boolean hovered = isInside(mouseX, mouseY,
                    this.leftPos + slot.x, this.topPos + slot.y, SLOT_SIZE, SLOT_SIZE);
            int color;
            if (hovered) {
                color = SLOT_HOVER_COLOR;
            } else if (index <= 2) {
                color = POTION_SLOT_COLOR;
            } else if (index == 3) {
                color = INGREDIENT_SLOT_COLOR;
            } else if (index == 4) {
                color = FUEL_SLOT_COLOR;
            } else {
                color = INVENTORY_SLOT_COLOR;
            }
            fillRounded(graphics, slot.x, slot.y, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, color);
        }
    }

    // ===== 酿造指南 =====

    private void drawGuide(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = guideLeft();
        int y = this.topPos;
        fillRounded(graphics, x, y, GUIDE_WIDTH, this.imageHeight, PANEL_RADIUS, GUIDE_BG);

        graphics.text(this.font,
                ellipsize(Component.translatable("screen.youzaiworldcore.brewing.guide"), GUIDE_WIDTH - 14),
                x + 7, y + 7, TITLE_COLOR, false);
        fillRounded(graphics, x + 7, y + 17, GUIDE_WIDTH - 14, 2, 1, GUIDE_ACCENT);

        int start = this.guidePage * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, this.filteredRecipes.size());
        if (start >= end) {
            Component noRecipes = Component.translatable("gui.recipebook.noRecipes");
            graphics.centeredText(this.font, noRecipes, x + GUIDE_WIDTH / 2, y + 96, MUTED_COLOR);
        } else {
            for (int index = start; index < end; index++) {
                int row = index - start;
                drawGuideRow(graphics, this.filteredRecipes.get(index), row, mouseX, mouseY);
            }
        }

        drawPageControls(graphics, mouseX, mouseY);
    }

    private void drawGuideRow(GuiGraphicsExtractor graphics, BrewingGuideRecipe recipe,
            int row, int mouseX, int mouseY) {
        int x = guideLeft() + LIST_X;
        int y = this.topPos + LIST_Y + row * ROW_H;
        boolean hovered = isInside(mouseX, mouseY, x, y, ROW_W, ROW_H - 1);
        fillRounded(graphics, x, y, ROW_W, ROW_H - 1, 3, hovered ? ROW_HOVER_BG : ROW_BG);

        ItemStack input = recipe.inputStack();
        ItemStack ingredient = recipe.ingredientStack();
        ItemStack output = recipe.outputStack();
        graphics.item(input, x + 2, y + 3, 0);
        graphics.text(this.font, "+", x + 20, y + 7, MUTED_COLOR, false);
        graphics.item(ingredient, x + 27, y + 3, 0);
        graphics.text(this.font, ">", x + 45, y + 7, MUTED_COLOR, false);
        graphics.item(output, x + 52, y + 3, 0);

        int textX = x + 72;
        int textWidth = ROW_W - 75;
        graphics.text(this.font, ellipsize(recipe.outputName(), textWidth),
                textX, y + 3, TITLE_COLOR, false);
        graphics.text(this.font, ellipsize(effectSummary(recipe), textWidth),
                textX, y + 13, MUTED_COLOR, false);

        if (hovered) {
            graphics.setComponentTooltipForNextFrame(this.font, recipeTooltip(recipe), mouseX, mouseY);
        }
    }

    private void drawPageControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = guideLeft();
        int y = this.topPos + PAGE_Y;
        int pageCount = pageCount();

        boolean previousHovered = isInside(mouseX, mouseY, x + 5, y, PAGE_BUTTON_W, PAGE_BUTTON_H);
        boolean nextHovered = isInside(mouseX, mouseY,
                x + GUIDE_WIDTH - 5 - PAGE_BUTTON_W, y, PAGE_BUTTON_W, PAGE_BUTTON_H);
        fillRounded(graphics, x + 5, y, PAGE_BUTTON_W, PAGE_BUTTON_H, 4,
                previousHovered ? ROW_HOVER_BG : ROW_BG);
        fillRounded(graphics, x + GUIDE_WIDTH - 5 - PAGE_BUTTON_W, y,
                PAGE_BUTTON_W, PAGE_BUTTON_H, 4, nextHovered ? ROW_HOVER_BG : ROW_BG);
        graphics.centeredText(this.font, "<", x + 5 + PAGE_BUTTON_W / 2, y + 3, TITLE_COLOR);
        graphics.centeredText(this.font, ">", x + GUIDE_WIDTH - 5 - PAGE_BUTTON_W / 2, y + 3, TITLE_COLOR);
        graphics.centeredText(this.font,
                Component.translatable("screen.youzaiworldcore.brewing.page", this.guidePage + 1, pageCount),
                x + GUIDE_WIDTH / 2, y + 3, MUTED_COLOR);
    }

    private void rebuildFilteredRecipes() {
        String query = this.guideSearchBox == null
                ? ""
                : this.guideSearchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.filteredRecipes.clear();
        for (BrewingGuideRecipe recipe : BrewingGuideRecipes.all()) {
            if (query.isEmpty() || matchesQuery(recipe, query)) {
                this.filteredRecipes.add(recipe);
            }
        }
        this.guidePage = Mth.clamp(this.guidePage, 0, pageCount() - 1);
    }

    private boolean matchesQuery(BrewingGuideRecipe recipe, String query) {
        if (recipe.inputName().getString().toLowerCase(Locale.ROOT).contains(query)
                || recipe.ingredientStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || recipe.outputName().getString().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (MobEffectInstance effect : recipe.effects()) {
            if (effect.getEffect().value().getDisplayName().getString()
                    .toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private Component effectSummary(BrewingGuideRecipe recipe) {
        if (recipe.effects().isEmpty()) {
            return recipe.containerMix()
                    ? Component.translatable("screen.youzaiworldcore.brewing.effect_preserved")
                    : Component.translatable("screen.youzaiworldcore.brewing.no_effect");
        }
        Component first = describeEffect(recipe.effects().getFirst());
        if (recipe.effects().size() == 1) {
            return first;
        }
        return Component.literal(first.getString() + " +" + (recipe.effects().size() - 1));
    }

    private List<Component> recipeTooltip(BrewingGuideRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(recipe.outputName().getString())
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("screen.youzaiworldcore.brewing.input",
                recipe.inputName()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.youzaiworldcore.brewing.ingredient",
                recipe.ingredientStack().getHoverName()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.youzaiworldcore.brewing.output",
                recipe.outputName()).withStyle(ChatFormatting.GRAY));

        if (recipe.effects().isEmpty()) {
            lines.add(Component.translatable("screen.youzaiworldcore.brewing.effect",
                    recipe.containerMix()
                            ? Component.translatable("screen.youzaiworldcore.brewing.effect_preserved")
                            : Component.translatable("screen.youzaiworldcore.brewing.no_effect"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (int index = 0; index < recipe.effects().size(); index++) {
                Component effect = describeEffect(recipe.effects().get(index));
                lines.add(index == 0
                        ? Component.translatable("screen.youzaiworldcore.brewing.effect", effect)
                                .withStyle(ChatFormatting.GRAY)
                        : Component.literal("  ").append(effect).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    private Component describeEffect(MobEffectInstance effect) {
        MutableComponent name = Component.translatable(effect.getDescriptionId());
        if (effect.getAmplifier() > 0) {
            name.append(" ").append(Component.translatable("potion.potency." + effect.getAmplifier()));
        }
        if (!effect.getEffect().value().isInstantaneous() && effect.getDuration() > 20) {
            return Component.translatable("potion.withDuration", name,
                    Component.literal(StringUtil.formatTickDuration(effect.getDuration(), 20.0F)));
        }
        return name;
    }

    private int pageCount() {
        return Math.max(1, (this.filteredRecipes.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private void changePage(int delta) {
        int oldPage = this.guidePage;
        this.guidePage = Mth.clamp(this.guidePage + delta, 0, pageCount() - 1);
        if (oldPage != this.guidePage) {
            DebugLogger.debug(MODULE, "切换酿造指南页码: %d -> %d", oldPage + 1, this.guidePage + 1);
        }
    }

    // ===== 输入处理 =====

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isActuallyClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (event.button() == 0) {
            if (isInside(mouseX, mouseY,
                    this.leftPos + CLOSE_X, this.topPos + BUTTON_TOP, BUTTON_SIZE, BUTTON_SIZE)) {
                DebugLogger.info(MODULE, "点击关闭按钮，关闭酿造台: %s", this.title.getString());
                this.onClose();
                return true;
            }
            if (isInside(mouseX, mouseY,
                    this.leftPos + GUIDE_TOGGLE_X, this.topPos + BUTTON_TOP, BUTTON_SIZE, BUTTON_SIZE)) {
                toggleGuide();
                return true;
            }
            if (this.guideOpen && handleGuideClick(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(event, isActuallyClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.guideOpen && isInside(mouseX, mouseY,
                guideLeft(), this.topPos, GUIDE_WIDTH, this.imageHeight) && scrollY != 0.0D) {
            changePage(scrollY > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean handleGuideClick(int mouseX, int mouseY) {
        int x = guideLeft();
        int pageY = this.topPos + PAGE_Y;
        if (isInside(mouseX, mouseY, x + 5, pageY, PAGE_BUTTON_W, PAGE_BUTTON_H)) {
            changePage(-1);
            return true;
        }
        if (isInside(mouseX, mouseY,
                x + GUIDE_WIDTH - 5 - PAGE_BUTTON_W, pageY, PAGE_BUTTON_W, PAGE_BUTTON_H)) {
            changePage(1);
            return true;
        }

        int row = (mouseY - (this.topPos + LIST_Y)) / ROW_H;
        if (row >= 0 && row < ROWS_PER_PAGE
                && isInside(mouseX, mouseY, x + LIST_X,
                        this.topPos + LIST_Y + row * ROW_H, ROW_W, ROW_H - 1)) {
            int recipeIndex = this.guidePage * ROWS_PER_PAGE + row;
            if (recipeIndex < this.filteredRecipes.size()) {
                BrewingGuideRecipe recipe = this.filteredRecipes.get(recipeIndex);
                DebugLogger.debug(MODULE, "查看酿造配方: %s + %s -> %s",
                        recipe.inputName().getString(),
                        recipe.ingredientStack().getHoverName().getString(),
                        recipe.outputName().getString());
            }
            this.guideSearchBox.setFocused(false);
            return true;
        }

        if (isInside(mouseX, mouseY, x, this.topPos, GUIDE_WIDTH, this.imageHeight)
                && !isInside(mouseX, mouseY,
                        x + SEARCH_X, this.topPos + SEARCH_Y, SEARCH_W, SEARCH_H)) {
            this.guideSearchBox.setFocused(false);
            return true;
        }
        return false;
    }

    private void toggleGuide() {
        if (!this.guideOpen && !canFitGuide()) {
            DebugLogger.warn(MODULE, "当前界面宽度不足，无法展开酿造指南: width=%d required=%d",
                    this.width, MAIN_WIDTH + GUIDE_WIDTH + GUIDE_GAP + 2);
            return;
        }

        boolean oldValue = this.guideOpen;
        this.guideOpen = !this.guideOpen;
        positionMainPanel();
        if (this.guideSearchBox != null) {
            this.guideSearchBox.setX(guideLeft() + SEARCH_X);
            this.guideSearchBox.setY(this.topPos + SEARCH_Y);
            this.guideSearchBox.setVisible(this.guideOpen);
            this.guideSearchBox.active = this.guideOpen;
            if (!this.guideOpen) {
                this.guideSearchBox.setFocused(false);
            }
        }
        DebugLogger.stateChange(MODULE, "brewing_guide", "open", oldValue, this.guideOpen);
    }

    // ===== 工具方法 =====

    private int guideLeft() {
        return this.leftPos - GUIDE_GAP - GUIDE_WIDTH;
    }

    private String ellipsize(Component text, int maxWidth) {
        String value = text.getString();
        if (maxWidth <= 0 || this.font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        return this.font.plainSubstrByWidth(value, Math.max(0, maxWidth - this.font.width(suffix))) + suffix;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void fillRounded(GuiGraphicsExtractor graphics,
            int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        radius = Math.min(radius, Math.min(width, height) / 2);
        if (radius <= 3) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x, y + radius, x + width, y + height - radius, color);
        for (int offsetY = 0; offsetY < radius; offsetY++) {
            int offsetX = 0;
            while (offsetX < radius && offsetX * offsetX + offsetY * offsetY < radius * radius) {
                offsetX++;
            }
            int left = x + radius - offsetX;
            int right = x + width - radius + offsetX;
            graphics.fill(left, y + radius - offsetY - 1, right, y + radius - offsetY, color);
            graphics.fill(left, y + height - radius + offsetY,
                    right, y + height - radius + offsetY + 1, color);
        }
    }
}
