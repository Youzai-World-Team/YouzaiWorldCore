package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混凝土粉末掉落物遇水固化事件处理器。
 * <p>
 * 当混凝土粉末以物品实体（掉落物）形式落入水中时，自动固化为对应的混凝土物品实体。
 * 检查频率为每 20 tick（即每秒）一次，避免性能开销。
 * </p>
 */
@SuppressWarnings("null")
public class ConcretePowderSolidifyHandler implements ServerTickEvents.StartTick {

    private static final ConcretePowderSolidifyHandler INSTANCE = new ConcretePowderSolidifyHandler();

    /** 检查间隔（tick 数），每 20 tick（1 秒）检查一次 */
    private static final int CHECK_INTERVAL = 20;

    /** 混凝土粉末物品 → 混凝土物品 的映射表 */
    private static final Map<Item, Item> POWDER_TO_CONCRETE = new HashMap<>();

    /** 是否已初始化映射表 */
    private static boolean initialized = false;

    /** Tick 计数器，用于间隔检查 */
    private int tickCounter = 0;

    private ConcretePowderSolidifyHandler() {
    }

    /**
     * 初始化混凝土粉末→混凝土映射表。
     * <p>
     * 遍历所有注册方块，对每个 ConcretePowderBlock 通过路径名称
     * (将 _powder 后缀替换为空) 查找对应的混凝土方块。
     * ConcretePowderBlock.concrete 字段为包级私有，无法直接访问。
     * </p>
     */
    public static void initialize() {
        DebugLogger.entering("ConcretePowderSolidifyHandler", "initialize");

        POWDER_TO_CONCRETE.clear();
        int count = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof ConcretePowderBlock) {
                Item powderItem = block.asItem();
                Identifier powderId = BuiltInRegistries.BLOCK.getKey(block);

                // 将 "white_concrete_powder" → "white_concrete"
                String powderPath = powderId.getPath();
                String concretePath = powderPath.replace("_powder", "");
                Identifier concreteId = Identifier.fromNamespaceAndPath(
                        powderId.getNamespace(), concretePath);
                // 26.2 的 Registry.get(Identifier) 返回 Optional<Holder.Reference<Block>>
                Block concreteBlock = BuiltInRegistries.BLOCK.get(concreteId)
                        .map(holder -> holder.value())
                        .orElse(null);

                if (concreteBlock == null || concreteBlock.asItem() == Items.AIR)
                    continue;

                Item concreteItem = concreteBlock.asItem();

                if (powderItem != Items.AIR && powderItem != concreteItem) {
                    POWDER_TO_CONCRETE.put(powderItem, concreteItem);
                    count++;
                    DebugLogger.debug("ConcretePowderSolidifyHandler",
                            "映射: %s(%s) -> %s(%s)",
                            powderItem, powderId, concreteItem, concreteId);
                }
            }
        }

        initialized = true;
        DebugLogger.info("ConcretePowderSolidifyHandler",
                "混凝土粉末→混凝土映射已初始化，共 %d 种", count);
        DebugLogger.exiting("ConcretePowderSolidifyHandler", "initialize");
    }

    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        if (!initialized)
            return;

        tickCounter++;
        if (tickCounter % CHECK_INTERVAL != 0)
            return;

        DebugLogger.trace("ConcretePowderSolidifyHandler", "开始检查掉落物固化...");

        for (ServerLevel level : server.getAllLevels()) {
            processLevel(level);
        }
    }

    private static void processLevel(ServerLevel level) {
        // 使用 EntityTypeTest.forClass 查找所有 ItemEntity，无需直接引用 EntityType.ITEM
        List<? extends ItemEntity> itemsInWater = level.getEntities(
                EntityTypeTest.forClass(ItemEntity.class),
                entity -> entity.isAlive() && entity.isInWater());

        if (itemsInWater.isEmpty())
            return;

        DebugLogger.trace("ConcretePowderSolidifyHandler",
                "维度 %s 中有 %d 个物品在水中待检查",
                level.dimension().identifier(), itemsInWater.size());

        int converted = 0;
        for (ItemEntity itemEntity : itemsInWater) {
            ItemStack stack = itemEntity.getItem();
            Item item = stack.getItem();
            Item concreteItem = POWDER_TO_CONCRETE.get(item);

            if (concreteItem != null) {
                ItemStack newStack = new ItemStack(concreteItem, stack.getCount());
                itemEntity.setItem(newStack);

                level.playSound(null, itemEntity.blockPosition(),
                        SoundEvents.STONE_PLACE, SoundSource.BLOCKS,
                        1.0F, 1.0F);

                DebugLogger.info("ConcretePowderSolidifyHandler",
                        "混凝土粉末固化: %s -> %s, 位置=%s, 数量=%d",
                        item, concreteItem,
                        itemEntity.blockPosition(), stack.getCount());
                converted++;
            }
        }

        if (converted > 0) {
            DebugLogger.debug("ConcretePowderSolidifyHandler",
                    "维度 %s 中 %d 个掉落物已固化",
                    level.dimension().identifier(), converted);
        }
    }

    public static void register() {
        DebugLogger.entering("ConcretePowderSolidifyHandler", "register");
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);
        DebugLogger.exiting("ConcretePowderSolidifyHandler", "register");
    }
}
