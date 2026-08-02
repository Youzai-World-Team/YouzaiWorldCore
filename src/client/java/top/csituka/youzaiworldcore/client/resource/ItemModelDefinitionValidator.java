package top.csituka.youzaiworldcore.client.resource;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品模型定义（item model definition）自检器。
 * <p>
 * 自 1.21.4 起（26.2 沿用），物品在<b>物品栏 / 手持 / 掉落物</b>中的渲染不再直接读取
 * {@code assets/<命名空间>/models/item/<物品ID>.json}，而是先经由
 * {@code assets/<命名空间>/items/<物品ID>.json}（客户端物品模型定义）间接指向模型。
 * 该定义由 {@code net.minecraft.client.resources.model.ClientItemInfoLoader} 加载
 * （其 {@code LISTER = FileToIdConverter.json("items")}）。
 * </p>
 * <p>
 * 若某个已注册物品缺失这份 {@code items/*.json}，客户端会回退到
 * {@code MissingItemModel}，表现为<b>黑紫色丢失材质</b>；
 * 而实体形态（如展示框实体）由实体渲染器绘制，不受影响——这正是此类 bug
 * 常被描述为「放下正常、物品栏和手持丢失材质」的原因。
 * </p>
 * <p>
 * 本监听器在每次客户端资源重载后遍历本模组命名空间下的所有物品，
 * 校验其模型定义文件是否存在，并通过 {@link DebugLogger} 输出结果，便于日志排查。
 * </p>
 * <p>
 * 实现说明：Fabric 的 {@code ResourceManagerHelper / SimpleSynchronousResourceReloadListener}
 * 在 26.2 已弃用，改用原版 {@link ReloadableResourceManager#registerReloadListener} +
 * {@link SimplePreparableReloadListener}。注册时机必须是 {@code CLIENT_STARTED}
 * （Minecraft 构造完成后、{@code resourceManager} 已赋值）：Fabric 的 client entrypoint
 * 在 {@code Minecraft.<init>} 中途触发，此时 {@code getResourceManager()} 仍为 null
 * （26.2 jar javap 核实：字段为 {@code private final ReloadableResourceManager resourceManager}，
 * 构造器后期才赋值）。
 * </p>
 */
@SuppressWarnings("null")
public final class ItemModelDefinitionValidator extends SimplePreparableReloadListener<Void> {

    private static final String MODULE = "ItemModelDefinitionValidator";

    /** 客户端物品模型定义所在目录（对应 ClientItemInfoLoader.LISTER 的 "items"） */
    private static final String ITEM_DEFINITION_DIR = "items/";

    private ItemModelDefinitionValidator() {
    }

    /**
     * 注册资源重载监听器（应在客户端初始化阶段调用）。
     * <p>
     * 实际注册推迟到 {@code CLIENT_STARTED}：Minecraft 构造完成后 {@code resourceManager}
     * 才可用（onInitializeClient 阶段调用 {@code getResourceManager()} 会得到 null）。
     * 注册后立即手动执行一次校验，弥补已错过的首次资源重载。
     * </p>
     */
    public static void register() {
        DebugLogger.entering(MODULE, "register");
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ReloadableResourceManager rm = (ReloadableResourceManager) client.getResourceManager();
            ItemModelDefinitionValidator validator = new ItemModelDefinitionValidator();
            rm.registerReloadListener(validator);
            // CLIENT_STARTED 时首次资源重载已完成，手动补一次启动校验
            validator.validate(rm);
            DebugLogger.info(MODULE, "物品模型定义自检已注册（每次资源重载校验 assets/%s/%s*.json）",
                    YouzaiworldCore.MOD_ID, ITEM_DEFINITION_DIR);
        });
        DebugLogger.exiting(MODULE, "register");
    }

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        validate(resourceManager);
    }

    /** 核心校验逻辑：遍历本模组物品，检查 items/*.json 定义是否存在 */
    private void validate(ResourceManager resourceManager) {
        DebugLogger.entering(MODULE, "validate");

        List<String> missing = new ArrayList<>();
        int checked = 0;

        for (Identifier itemId : BuiltInRegistries.ITEM.keySet()) {
            if (!YouzaiworldCore.MOD_ID.equals(itemId.getNamespace())) {
                continue;
            }
            checked++;

            Identifier definitionId = Identifier.fromNamespaceAndPath(
                    itemId.getNamespace(), ITEM_DEFINITION_DIR + itemId.getPath() + ".json");
            boolean present = resourceManager.getResource(definitionId).isPresent();

            DebugLogger.trace(MODULE, "校验物品模型定义：%s -> %s (%s)",
                    itemId, definitionId, present ? "存在" : "缺失");

            if (!present) {
                missing.add(itemId.getPath());
            }
        }

        DebugLogger.branch(MODULE, "所有模组物品均具备模型定义", missing.isEmpty(),
                "checked=" + checked + ", missing=" + missing.size());

        if (missing.isEmpty()) {
            DebugLogger.info(MODULE, "物品模型定义自检通过：%d 个模组物品均已提供 %s*.json",
                    checked, ITEM_DEFINITION_DIR);
        } else {
            DebugLogger.warn(MODULE,
                    "物品模型定义缺失 %d/%d 项，这些物品在物品栏与手持时将显示为丢失材质（黑紫方块）：%s",
                    missing.size(), checked, String.join(", ", missing));
            DebugLogger.warn(MODULE,
                    "修复方式：为每个缺失项补建 assets/%s/%s<物品ID>.json，"
                            + "内容形如 {\"model\":{\"type\":\"minecraft:model\",\"model\":\"%s:item/<模型名>\"}}",
                    YouzaiworldCore.MOD_ID, ITEM_DEFINITION_DIR, YouzaiworldCore.MOD_ID);
        }

        DebugLogger.exiting(MODULE, "validate",
                "checked=" + checked + ", missing=" + missing.size());
    }
}
