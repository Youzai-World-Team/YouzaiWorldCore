package top.csituka.youzaiworldcore.startup;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;

/**
 * 启动早期对 Reflect 的访问桥。
 * Fabric 开发环境的普通库尚未向 Knot 开放时，从启动器的系统类加载器读取；
 * 发布包中的内嵌库仍由模组类加载器读取。
 */
final class StartupReflectAccess {

    private static final String PACKAGE = "net.lenni0451.reflect.";

    private StartupReflectAccess() {
    }

    /** 在语言适配器初始化阶段获取 Instrumentation。 */
    static Instrumentation getInstrumentation() throws Throwable {
        return (Instrumentation) invoke("Agents", "getInstrumentation", new Class<?>[0]);
    }

    /** 在 Fabric Loader 所在类加载器定义共享启动桥接类。 */
    static Class<?> defineClass(ClassLoader loader, String name, byte[] bytes) throws Throwable {
        return (Class<?>) invoke("ClassLoaders", "defineClass",
                new Class<?>[]{ClassLoader.class, String.class, byte[].class}, loader, name, bytes);
    }

    private static Object invoke(String owner, String method, Class<?>[] parameterTypes, Object... arguments)
            throws Throwable {
        Class<?> type;
        try {
            type = Class.forName(PACKAGE + owner, false, StartupReflectAccess.class.getClassLoader());
        } catch (ClassNotFoundException unavailableInGame) {
            try {
                type = Class.forName(PACKAGE + owner, false, ClassLoader.getSystemClassLoader());
            } catch (ClassNotFoundException unavailableInLauncher) {
                unavailableInLauncher.addSuppressed(unavailableInGame);
                throw unavailableInLauncher;
            }
        }
        try {
            return type.getMethod(method, parameterTypes).invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
