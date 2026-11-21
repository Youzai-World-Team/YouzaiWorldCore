package top.csituka.youzaiworldcore.startup;

import com.geckolib.loading.definition.animation.ActorAnimations;
import com.geckolib.loading.definition.geometry.Geometry;
import com.geckolib.loading.math.MathParser;
import com.google.gson.JsonParser;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.csituka.youzaiworldcore.util.TrinketHelper;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;

/** 使用实际 26.3、Fabric、Trinkets 和 GeckoLib 类执行隔离回归，不启动游戏。 */
public final class MinecraftRuntimeCompatChecks {
    private static int checks;

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    private static void dependencies(Path metadata, Path installed) throws Exception {
        var depends = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject().getAsJsonObject("depends");
        var versions = JsonParser.parseString(Files.readString(installed)).getAsJsonObject();
        versions.addProperty("java", Integer.toString(Runtime.version().feature()));
        for (var entry : depends.entrySet()) {
            check(versions.has(entry.getKey()), "缺少用于验证的依赖：" + entry.getKey());
            check(VersionPredicate.parse(entry.getValue().getAsString())
                    .test(Version.parse(versions.get(entry.getKey()).getAsString())), "依赖版本不满足声明：" + entry.getKey());
        }
        var minecraft = VersionPredicate.parse(depends.get("minecraft").getAsString());
        check(!minecraft.test(Version.parse("26.2")), "拒绝旧版 Minecraft");
        check(!minecraft.test(Version.parse("26.4")), "未经核验的新版 Minecraft 不应自动放行");
        var trinkets = VersionPredicate.parse(depends.get("trinkets_updated").getAsString());
        check(trinkets.test(Version.parse("4.2.2+26.3")), "允许同系列的 Trinkets 修订版");
        check(!trinkets.test(Version.parse("4.3.0")), "不自动放行下一系列的 Trinkets");
    }

    /** 使用实际 SQLite 原生驱动验证地图所需的 WAL、事务及二进制地形保存。 */
    private static void sqliteStorage() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:runtime-compat.sqlite")) {
            try (var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("PRAGMA journal_mode=WAL")) {
                    check(result.next() && result.getString(1).equalsIgnoreCase("wal"), "SQLite 支持 WAL 模式");
                }
                statement.execute("CREATE TABLE tiles (x INTEGER, z INTEGER, data BLOB, PRIMARY KEY (x,z))");
            }
            byte[] terrain = {0, 1, 2, (byte) 255};
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO tiles VALUES (?, ?, ?)")) {
                insert.setInt(1, -12);
                insert.setInt(2, 34);
                insert.setBytes(3, terrain);
                check(insert.executeUpdate() == 1, "SQLite 能写入地形记录");
            }
            connection.commit();
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM tiles");
            }
            connection.rollback();
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT x, z, data FROM tiles")) {
                check(result.next() && result.getInt(1) == -12 && result.getInt(2) == 34
                        && java.util.Arrays.equals(result.getBytes(3), terrain), "SQLite 回滚保留原始地形数据");
            }
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:runtime-compat.sqlite");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM tiles")) {
            check(result.next() && result.getInt(1) == 1, "SQLite 重新打开后仍能读取已提交数据");
        }
    }

    private static void geckoResources(Path resources) throws Exception {
        try (var paths = Files.walk(resources)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                if (path.toString().endsWith(".animation.json")) {
                    var animations = ActorAnimations.GSON.fromJson(Files.readString(path), ActorAnimations.class);
                    for (var entry : animations.animations().entrySet()) {
                        // 不使用 ActorAnimations.bake：该入口捕获异常后仍会返回残缺结果。
                        check(entry.getValue().bake(entry.getKey(), MathParser.create()) != null,
                                "动画无法烘焙：" + entry.getKey());
                    }
                } else if (path.toString().endsWith(".geo.json")) {
                    var id = Identifier.fromNamespaceAndPath("youzaiworldcore", path.getFileName().toString());
                    check(Geometry.GSON.fromJson(Files.readString(path), Geometry.class).bake(id) != null,
                            "模型无法烘焙：" + path);
                }
            }
        }
    }

    /** 最小库存实现；保留实际 Trinkets 接口、默认方法和 SlotAccess 的读取写入逻辑。 */
    private static void trinketEffects() {
        // 26.3 的物品组件在数据包加载阶段才绑定；隔离检查只初始化本用例的测试物品。
        Items.TOTEM_OF_UNDYING.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        ItemStack[] items = {ItemStack.EMPTY};
        ItemStack[] cosmetics = {new ItemStack(Items.TOTEM_OF_UNDYING)};
        boolean[] active = {true};
        int[] changes = {0};
        var inventory = (TrinketInventory) Proxy.newProxyInstance(TrinketInventory.class.getClassLoader(),
                new Class<?>[]{TrinketInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isValidSlot" -> (int) args[0] == 0;
                    case "getItem" -> items[(int) args[0]];
                    case "getCosmeticItem" -> cosmetics[(int) args[0]];
                    case "hasCosmeticItems" -> true;
                    case "setItem" -> { items[(int) args[0]] = (ItemStack) args[1]; yield null; }
                    case "setChanged" -> { changes[0]++; yield null; }
                    case "getAttachment" -> TrinketsApi.ATTACHMENT;
                    case "getContainerSize" -> 1;
                    case "isVisible" -> false;
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        var normal = new TrinketSlotAccess(inventory, 0);
        var cosmetic = new TrinketSlotAccess(inventory, 0, true);
        TrinketsApi.ATTACHMENT = (TrinketAttachment) Proxy.newProxyInstance(TrinketAttachment.class.getClassLoader(),
                new Class<?>[]{TrinketAttachment.class}, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("canApplyEffects")) return active[0];
                    if (List.of("isEquipped", "equipped", "findFirst").contains(method.getName())) {
                        @SuppressWarnings("unchecked")
                        var predicate = (Predicate<ItemStack>) args[0];
                        boolean requireActive = (boolean) args[1];
                        var matches = new ArrayList<TrinketSlotAccess>();
                        for (var access : List.of(normal, cosmetic)) {
                            if ((!requireActive || access.canApplyEffects()) && predicate.test(access.get())) matches.add(access);
                        }
                        return switch (method.getName()) {
                            case "isEquipped" -> !matches.isEmpty();
                            case "equipped" -> matches;
                            default -> matches.stream().findFirst();
                        };
                    }
                    throw new UnsupportedOperationException(method.toString());
                });

        var item = Items.TOTEM_OF_UNDYING;
        check(TrinketsApi.ATTACHMENT.isEquipped(item), "实际 API 默认查询应复现包含外观槽的旧行为");
        check(!TrinketHelper.isItemEquipped(null, item), "外观槽不提供玩法效果");
        check(TrinketHelper.countItem(null, item) == 0, "外观槽不计入有效数量");
        check(!TrinketHelper.consumeOne(null, item), "不能消耗外观槽物品");
        check(cosmetics[0].getCount() == 1 && changes[0] == 0, "外观槽保持原样");
        items[0] = new ItemStack(item, 2);
        active[0] = false;
        check(!TrinketHelper.isItemEquipped(null, item), "禁用效果的槽位不提供玩法效果");
        check(TrinketHelper.countItem(null, item) == 0 && !TrinketHelper.consumeOne(null, item), "统计和消耗使用相同有效性条件");
        active[0] = true;
        check(TrinketHelper.isItemEquipped(null, item), "隐藏的有效槽仍提供玩法效果");
        check(TrinketHelper.countItem(null, item) == 2, "只统计有效槽的物品数量");
        check(TrinketHelper.consumeOne(null, item) && items[0].getCount() == 1, "仅消耗一个有效物品");
        check(changes[0] == 1, "消耗后标记库存同步");
        check(TrinketHelper.consumeOne(null, item) && items[0].isEmpty(), "最后一个物品消耗后槽位为空");
        check(changes[0] == 2 && cosmetics[0].getCount() == 1, "外观物品不受有效物品消耗影响");
        TrinketsApi.ATTACHMENT = null;
        check(!TrinketHelper.isItemEquipped(null, item) && TrinketHelper.countItem(null, item) == 0
                && !TrinketHelper.consumeOne(null, item), "缺少附件时安全返回");
    }

    /** 模拟 Knot 在语言适配器阶段拒绝普通库，再验证系统加载器回退。 */
    private static void startupLibrary() throws Throwable {
        check(StartupReflectAccess.getInstrumentation() != null, "普通启动路径可获取 Instrumentation");
        var blockedLoader = new ClassLoader(MinecraftRuntimeCompatChecks.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.lenni0451.reflect.")) throw new ClassNotFoundException("模拟 Knot 尚未开放依赖");
                if (!name.equals(StartupReflectAccess.class.getName())) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    var type = findLoadedClass(name);
                    if (type == null) {
                        try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            var bytes = stream.readAllBytes();
                            type = defineClass(name, bytes, 0, bytes.length);
                        } catch (java.io.IOException exception) {
                            throw new ClassNotFoundException(name, exception);
                        }
                    }
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        var bridge = Class.forName(StartupReflectAccess.class.getName(), true, blockedLoader);
        var get = bridge.getDeclaredMethod("getInstrumentation");
        get.setAccessible(true);
        check(get.invoke(null) instanceof Instrumentation, "Knot 未开放普通库时仍可获取 Instrumentation");
        var define = bridge.getDeclaredMethod("defineClass", ClassLoader.class, String.class, byte[].class);
        define.setAccessible(true);
        var target = new ClassLoader(null) { };
        String generated = "top.csituka.youzaiworldcore.startup.CompatibilityGeneratedClass";
        try (var stream = MinecraftRuntimeCompatChecks.class.getClassLoader().getResourceAsStream(generated.replace('.', '/') + ".class")) {
            var defined = (Class<?>) define.invoke(null, target, generated, stream.readAllBytes());
            check(defined.getClassLoader() == target, "共享类定义在指定加载器");
        }
        try {
            define.invoke(null, target, "Invalid", new byte[]{0});
            throw new AssertionError("无效字节码应抛异常");
        } catch (java.lang.reflect.InvocationTargetException exception) {
            check(exception.getCause() instanceof ClassFormatError, "保留原始异常而非反射包装");
        }
    }

    /** 对实际 Fabric Loader 字节码执行转换，并检查注入回调与 JVM 栈帧。 */
    private static void startupTransformer() throws Exception {
        for (String name : List.of(StartupEntrypointTransformer.TARGET_CLASS, StartupEntrypointTransformer.ERROR_GUI_CLASS)) {
            try (var stream = MinecraftRuntimeCompatChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
                var transformed = StartupEntrypointTransformer.transform(name, stream.readAllBytes());
                check(transformed != null, "实际 Loader 类能接入启动追踪：" + name);
                var node = new ClassNode();
                new ClassReader(transformed).accept(node, 0);
                var hooks = new ArrayList<String>();
                for (var method : node.methods) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode invoke && invoke.owner.equals(StartupEntrypointTransformer.BRIDGE_CLASS)) {
                            hooks.add(invoke.name);
                        }
                    }
                }
                check(name.equals(StartupEntrypointTransformer.TARGET_CLASS)
                        ? hooks.containsAll(List.of("beforeEntrypointType", "beforeSingleEntrypoint", "afterEntrypointType"))
                        : hooks.contains("markFailed"), "启动进度回调完整：" + name);
                var errors = new java.io.StringWriter();
                CheckClassAdapter.verify(new ClassReader(transformed), false, new java.io.PrintWriter(errors));
                check(errors.toString().isBlank(), "转换后的 Loader 字节码验证失败：" + errors);
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        dependencies(Path.of(args[0]), Path.of(args[1]));
        sqliteStorage();
        geckoResources(Path.of(args[2]));
        trinketEffects();
        startupLibrary();
        startupTransformer();
        System.out.println("Minecraft 26.3 实际依赖回归通过：" + checks + " 项");
    }
}

/** 只用于验证把 class 字节定义到另一加载器。 */
final class CompatibilityGeneratedClass { }
