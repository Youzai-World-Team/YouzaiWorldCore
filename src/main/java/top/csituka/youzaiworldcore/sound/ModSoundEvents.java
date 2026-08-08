package top.csituka.youzaiworldcore.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 模组自定义 SoundEvent 注册器。
 * <p>
 * 26.2 起，原版唱片彻底迁移至数据驱动模型：原本的 {@code MusicDiscItem} 子类被移除，
 * 改用 {@code Item.Properties.jukeboxPlayable(ResourceKey&lt;JukeboxSong&gt;)} 注入一个
 * {@code JukeboxPlayable} 数据组件。{@code JukeboxSong} 自身在 {@code Registries.JUKEBOX_SONG}
 * 这个 datapack 类注册表中；它内部需要持有一个 {@code Holder&lt;SoundEvent&gt;}，因此对应的
 * {@code SoundEvent} 必须事先在 {@link BuiltInRegistries#SOUND_EVENT} 中存在。
 * </p>
 * <p>
 * 这里集中放置模组的所有自定义 SoundEvent，方便在调用时给出充足的 DebugLogger 上下文，
 * 也便于将来扩展更多音乐唱片或氛围音效。
 * </p>
 *
 * @author Youzai World Team
 */
@SuppressWarnings("null")
public class ModSoundEvents {

    /**
     * {@code youzaiworldcore:cloud_genshin} —— 《云·原神》宣传曲，47.45 秒，
     * 需在 {@link #initialize()} 通过 {@link SoundEvent#createVariableRangeEvent(Identifier)}
     * 注册到 {@code BuiltInRegistries.SOUND_EVENT}，供
     * {@code data/youzaiworldcore/jukebox_song/cloud_genshin.json} 通过 SoundEvent Holder 引用。
     */
    public static final SoundEvent MUSIC_DISC_CLOUD_GENSHIN = register(
            "cloud_genshin",
            SoundEvent.createVariableRangeEvent(
                    Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "cloud_genshin"))
    );

    private static SoundEvent register(String name, SoundEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name);
        SoundEvent registered = Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
        DebugLogger.info("ModSoundEvents", "注册 SoundEvent: %s".formatted(id));
        return registered;
    }

    /**
     * 在 {@link YouzaiworldCore#onInitialize()} 中显式调用。
     * 实际注册由类加载阶段完成（{@code public static final} 字段触发）；
     * 此方法保留用于未来需要按需加载的额外 SoundEvent，并输出汇总日志。
     */
    public static void initialize() {
        DebugLogger.info("ModSoundEvents", "SoundEvent 注册完毕：music_disc_cloud_genshin=%s".formatted(
                MUSIC_DISC_CLOUD_GENSHIN.location()));
    }
}
