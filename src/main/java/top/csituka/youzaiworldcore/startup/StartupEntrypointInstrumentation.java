package top.csituka.youzaiworldcore.startup;

import net.lenni0451.reflect.Agents;
import net.lenni0451.reflect.ClassLoaders;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * 在 Fabric 调用模组入口点前安装 JVM 类转换器。
 * <p>
 * 安装失败不会阻止游戏启动，启动窗口会自动退回本模组内部阶段指示。
 * </p>
 */
final class StartupEntrypointInstrumentation {

    private static final String TARGET_CLASS_NAME = StartupEntrypointTransformer.TARGET_CLASS.replace('/', '.');
    private static final String ERROR_GUI_CLASS_NAME = StartupEntrypointTransformer.ERROR_GUI_CLASS.replace('/', '.');
    private static final String BRIDGE_CLASS_NAME = StartupEntrypointTransformer.BRIDGE_CLASS.replace('/', '.');

    private static boolean installed;
    private static boolean installing;

    private StartupEntrypointInstrumentation() {
    }

    /** 获取 Instrumentation、定义共享桥接类并重新转换 Fabric Loader。 */
    static synchronized void install() throws Throwable {
        if (installed || installing) {
            return;
        }
        installing = true;

        Instrumentation instrumentation = null;
        ClassFileTransformer installedTransformer = null;
        try {
            instrumentation = Agents.getInstrumentation();
            Class<?> loaderClass = findLoadedClass(instrumentation, TARGET_CLASS_NAME);
            if (loaderClass == null) {
                throw new IllegalStateException("FabricLoaderImpl 尚未加载");
            }
            if (!instrumentation.isRetransformClassesSupported()
                    || !instrumentation.isModifiableClass(loaderClass)) {
                throw new IllegalStateException("当前 JVM 不支持重新转换 FabricLoaderImpl");
            }

            Class<?> bridgeClass = defineBridgeClass(loaderClass.getClassLoader());
            StartupLoadingStatus.attachBridge(bridgeClass);

            installedTransformer = new ClassFileTransformer() {
                @Override
                public byte[] transform(
                        ClassLoader loader,
                        String className,
                        Class<?> classBeingRedefined,
                        ProtectionDomain protectionDomain,
                        byte[] classfileBuffer) throws IllegalClassFormatException {
                    return StartupEntrypointTransformer.transform(className, classfileBuffer);
                }
            };
            instrumentation.addTransformer(installedTransformer, true);
            instrumentation.retransformClasses(loaderClass);

            Class<?> errorGuiClass = findLoadedClass(instrumentation, ERROR_GUI_CLASS_NAME);
            if (errorGuiClass != null) {
                try {
                    if (instrumentation.isModifiableClass(errorGuiClass)) {
                        instrumentation.retransformClasses(errorGuiClass);
                    } else {
                        DebugLogger.warn("StartupLoading", "Fabric 错误窗口类不支持重新转换");
                    }
                } catch (Throwable throwable) {
                    // 错误提示属于附加能力，不能因其安装失败撤销已经生效的模组进度追踪。
                    DebugLogger.exception("StartupLoading", "接入 Fabric 错误窗口", throwable);
                }
            }

            installed = true;
            DebugLogger.info("StartupLoading", "Fabric Loader 实时模组进度追踪已启用");
        } catch (Throwable throwable) {
            if (instrumentation != null && installedTransformer != null) {
                instrumentation.removeTransformer(installedTransformer);
            }
            throw throwable;
        } finally {
            installing = false;
        }
    }

    private static Class<?> findLoadedClass(Instrumentation instrumentation, String className) {
        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            if (className.equals(loadedClass.getName())) {
                return loadedClass;
            }
        }
        return null;
    }

    private static Class<?> defineBridgeClass(ClassLoader targetLoader) throws IOException {
        try {
            return Class.forName(BRIDGE_CLASS_NAME, false, targetLoader);
        } catch (ClassNotFoundException ignored) {
            // 目标类加载器中不存在时，使用当前模组内的 class 字节定义一份共享桥接类。
        }

        String resourcePath = StartupEntrypointTransformer.BRIDGE_CLASS + ".class";
        ClassLoader sourceLoader = StartupEntrypointInstrumentation.class.getClassLoader();
        try (InputStream input = sourceLoader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("找不到启动进度桥接类资源：" + resourcePath);
            }
            return ClassLoaders.defineClass(targetLoader, BRIDGE_CLASS_NAME, input.readAllBytes());
        }
    }
}
