import eu.pb4.trinkets.impl.data.EntitySlotLoader;
import eu.pb4.trinkets.impl.data.SlotLoader;
import eu.pb4.trinkets.impl.payload.SyncSlotsPayload;
import eu.pb4.trinkets.impl.slots.SlotGroupImpl;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.mixin.compat.TrinketEntitySlotLoaderMixin;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 使用实际加载器复现两个背包槽，并执行生产回调检查三个索引和客户端同步。
 * 回调的目标方法、返回注入点及 Shadow 字段会核验，但不启动 Fabric/Mixin 服务。
 */
@SuppressWarnings("unchecked")
public final class BackpackSlotChecks {
    private static int checks;

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static final class Loader extends EntitySlotLoader {
        Map<String, Map<String, Set<String>>> prepareResources(ResourceManager manager) {
            return super.prepare(manager, null);
        }

        void load(Map<String, Map<String, Set<String>>> assignments) {
            super.apply(assignments, null, null);
        }
    }

    private static Resource resource(String packId, byte[] data) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(),
                new Class<?>[]{PackResources.class}, (proxy, method, args) -> {
                    if (method.getName().equals("packId")) return packId;
                    throw new UnsupportedOperationException(method.getName());
                });
        return new Resource(pack, () -> new ByteArrayInputStream(data));
    }

    private static ResourceManager resources(Path packs) throws Exception {
        Map<Identifier, List<Resource>> files = new LinkedHashMap<>();
        for (String name : List.of("trinkets_updated", "sophisticatedbackpacks", "youzaiworldcore")) {
            Path root = packs.resolve(name);
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    Identifier id = Identifier.fromNamespaceAndPath("trinkets", relative);
                    // 故意使用不同的数据包 ID，防止回归到依赖 sourcePackId 的过滤。
                    files.computeIfAbsent(id, _ -> new ArrayList<>())
                            .add(resource("file/merged-" + name, Files.readAllBytes(file)));
                }
            }
        }
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("listResourceStacks")) {
                        Map<Identifier, List<Resource>> selected = new LinkedHashMap<>();
                        files.forEach((id, stack) -> {
                            if (id.getPath().startsWith(args[0] + "/")) selected.put(id, stack);
                        });
                        return selected;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Object field(EntitySlotLoader loader, String name) throws Exception {
        var field = EntitySlotLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(loader);
    }

    private static void remove(Loader loader, Map<String, Map<String, Set<String>>> assignments) throws Exception {
        var patch = new TrinketEntitySlotLoaderMixin() {};
        for (String name : List.of("groups", "entitySlots", "slots")) {
            var shadow = TrinketEntitySlotLoaderMixin.class.getDeclaredField(name);
            shadow.setAccessible(true);
            shadow.set(patch, field(loader, name));
        }
        var method = TrinketEntitySlotLoaderMixin.class.getDeclaredMethod("youzaiworldcore$removeDuplicateBackpackSlot",
                Map.class, ResourceManager.class, ProfilerFiller.class, CallbackInfo.class);
        var inject = method.getAnnotation(Inject.class);
        check(Arrays.equals(inject.method(), new String[]{"apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"})
                && inject.at().length == 1 && inject.at()[0].value().equals("RETURN"), "核验实际 apply 方法与返回注入点");
        EntitySlotLoader.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
        method.setAccessible(true);
        method.invoke(patch, assignments, null, null, new CallbackInfo("apply", false));
    }

    private static void removed(EntitySlotLoader loader) {
        check(!loader.getEntitySlotTypes(EntityTypes.PLAYER).containsKey("chest/back"), "玩家库存索引不含多余背包槽");
        check(loader.getEntityGroups(EntityTypes.PLAYER).values().stream()
                .flatMap(group -> group.getSlots().stream()).noneMatch(slot -> slot.getId().equals("chest/back")),
                "用于构建库存及同步的玩家分组不含多余背包槽");
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ResourceManager manager = resources(Path.of(args[0]));
        var prepareSlots = SlotLoader.class.getDeclaredMethod("prepare", ResourceManager.class, ProfilerFiller.class);
        var applySlots = SlotLoader.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
        prepareSlots.setAccessible(true);
        applySlots.setAccessible(true);
        applySlots.invoke(SlotLoader.INSTANCE, prepareSlots.invoke(SlotLoader.INSTANCE, manager, null), manager, null);

        Loader server = new Loader();
        var assignments = server.prepareResources(manager);
        server.load(assignments);
        Set<String> expected = new HashSet<>(server.getEntitySlotTypes(EntityTypes.PLAYER).keySet());
        check(expected.remove("chest/back") && expected.contains("chest/backpack"), "实际模组数据复现两个背包槽");
        var backpack = server.getEntitySlotTypes(EntityTypes.PLAYER).get("chest/backpack");
        var chest = server.getEntityGroups(EntityTypes.PLAYER).get("chest");
        remove(server, assignments);
        removed(server);
        check(server.getEntitySlotTypes(EntityTypes.PLAYER).keySet().equals(expected), "保留全部其他饰品槽");
        check(server.getEntitySlotTypes(EntityTypes.PLAYER).get("chest/backpack") == backpack, "Core 背包槽定义保持不变");
        var remainingChest = server.getEntityGroups(EntityTypes.PLAYER).get("chest");
        check(chest.order() == remainingChest.order() && chest.slotId() == remainingChest.slotId(), "保留胸部分组的位置和排序");
        check(!server.getSlotTypes().containsKey("chest/back"), "清除没有实体使用的全局槽位索引");

        var groups = (Map<EntityType<?>, Map<String, SlotGroupImpl>>) field(server, "groups");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            SyncSlotsPayload.CODEC.encode(buffer, new SyncSlotsPayload(Map.copyOf(groups)));
            var payload = SyncSlotsPayload.CODEC.decode(buffer);
            var client = new EntitySlotLoader();
            client.setGroupsLegacy(payload.map());
            removed(client);
            check(client.getEntitySlotTypes(EntityTypes.PLAYER).keySet().equals(expected), "真实同步编解码后客户端保留正确槽位");
        } finally {
            buffer.release();
        }

        server.load(assignments);
        remove(server, assignments);
        removed(server);
        check(server.getEntitySlotTypes(EntityTypes.PLAYER).keySet().equals(expected), "数据包重载后多余槽位不会回来");

        FabricLoader.State.backpacks = false;
        server.load(assignments);
        remove(server, assignments);
        check(server.getEntitySlotTypes(EntityTypes.PLAYER).containsKey("chest/back"), "未安装精妙背包时不禁用其他模组的背部槽");
        FabricLoader.State.backpacks = true;

        assignments.computeIfAbsent("minecraft:zombie", _ -> new HashMap<>())
                .put("chest", new HashSet<>(Set.of("back")));
        server.load(assignments);
        remove(server, assignments);
        removed(server);
        check(server.getEntitySlotTypes(EntityTypes.ZOMBIE).containsKey("chest/back")
                && server.getSlotTypes().containsKey("chest/back"), "保留非玩家实体及其全局槽位引用");

        var onlyBack = Map.of("minecraft:player", Map.of("chest", Set.of("back")));
        server.load(onlyBack);
        remove(server, onlyBack);
        removed(server);
        check(server.getEntityGroups(EntityTypes.PLAYER).isEmpty(), "移除最后一个胸部槽后不留下空分组");
        System.out.println("背包槽加载与同步通过 " + checks + " 项检查。");
    }
}
