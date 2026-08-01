package top.csituka.youzaiworldcore.client.laowumeme;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 注册三首内置曲目的 {@link SoundEvent}：laowu2（那个那个）/ qiliang（老吴凄凉）/ zhanhou（战吼）。
 * <p>
 * 对应资源在 {@code assets/youzaiworldcore/sounds/*.ogg}，由
 * {@code assets/youzaiworldcore/sounds.json} 定义，开箱即用、零操作。
 * </p>
 */
public final class LaowuModSounds {

    public static final String MODULE = "LaowuModSounds";

    /** 内置曲目索引常量（与服务端 {@code LaowuMemeHandler.BUILTIN_SOUND_COUNT} 及 trigger 包 soundId 对齐） */
    public static final int SOUND_LAOWU2 = 0;
    public static final int SOUND_QILIANG = 1;
    public static final int SOUND_ZHANHOU = 2;

    public static SoundEvent LAOWU2;
    public static SoundEvent QILIANG;
    public static SoundEvent ZHANHOU;

    private LaowuModSounds() {
    }

    /** 注册三个 SoundEvent（客户端初始化时调用一次） */
    public static void init() {
        LAOWU2 = register("laowu2");
        QILIANG = register("qiliang");
        ZHANHOU = register("zhanhou");
        DebugLogger.info(MODULE, "已注册内置曲目 SoundEvent: laowu2 / qiliang / zhanhou");
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
