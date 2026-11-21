"""使用真实 Minecraft 26.3 组件初始化器验证唱片注册表缺失及切换；不构建模组或启动游戏。"""

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "src/main/java/top/csituka/youzaiworldcore/item/ModItems.java"

CHECKS = r"""
import java.util.stream.Stream;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.*;

public final class NativeMusicDiscChecks {
    private static final ResourceKey<JukeboxSong> CLOUD_GENSHIN_SONG = __SONG_KEY__;
    private static final ResourceKey<Item> DISC_KEY = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("youzaiworldcore", "music_disc_cloud_genshin"));
    private static int checks;

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static DataComponentInitializers.Initializer<Item> initializer(Item.Properties properties) throws Exception {
        var field = Item.Properties.class.getDeclaredField("componentInitializer");
        field.setAccessible(true);
        return (DataComponentInitializers.Initializer<Item>) field.get(properties);
    }

    private static HolderLookup.Provider registry(ResourceKey<JukeboxSong> key, JukeboxSong song) {
        var registry = new MappedRegistry<JukeboxSong>(Registries.JUKEBOX_SONG, Lifecycle.stable());
        if (key != null) registry.register(key, song, RegistrationInfo.BUILT_IN);
        return HolderLookup.Provider.create(Stream.of(registry.freeze()));
    }

    private static DataComponentMap components(DataComponentInitializers.Initializer<Item> initializer,
            HolderLookup.Provider context) {
        var builder = DataComponentMap.builder();
        initializer.run(builder, context, DISC_KEY);
        return builder.build();
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var missingRegistry = HolderLookup.Provider.create(Stream.empty());
        var missingSong = registry(null, null);
        var song = new JukeboxSong(Holder.direct(SoundEvent.createVariableRangeEvent(
                CLOUD_GENSHIN_SONG.identifier())), Component.literal("云·原神"), 48, 7);
        var present = registry(CLOUD_GENSHIN_SONG, song);

        // 先复现原版强制取值的同一异常，确保缺失场景确实覆盖此次连接故障。
        try {
            components(initializer(new Item.Properties().jukeboxPlayable(CLOUD_GENSHIN_SONG)), missingSong);
            throw new AssertionError("原版初始化器应复现缺少曲目的异常");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("Missing element"), "必须复现相同异常");
        }

        // 直接使用从 ModItems 提取的注册表达式，避免另一份实现掩盖回归。
        var initializer = initializer(__PROPERTIES__);
        check(!components(initializer, missingRegistry).has(DataComponents.JUKEBOX_PLAYABLE), "整个曲目注册表缺失时应省略播放组件");
        check(!components(initializer, missingSong).has(DataComponents.JUKEBOX_PLAYABLE), "空曲目注册表不得阻断连接");
        var unrelated = ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.withDefaultNamespace("test"));
        check(!components(initializer, registry(unrelated, song)).has(DataComponents.JUKEBOX_PLAYABLE), "不得使用其他曲目代替缺失曲目");

        var playable = components(initializer, present);
        check(playable.get(DataComponents.JUKEBOX_PLAYABLE).song() == present.getOrThrow(CLOUD_GENSHIN_SONG), "播放组件必须引用当前注册表的曲目 Holder");
        check(playable.get(DataComponents.JUKEBOX_PLAYABLE).song().value() == song, "正常曲目必须保留原始内容");
        check(playable.get(DataComponents.MAX_STACK_SIZE) == 1, "唱片仍不可堆叠");
        check(playable.get(DataComponents.RARITY) == Rarity.EPIC, "唱片稀有度不得改变");

        var reloadedSong = new JukeboxSong(song.soundEvent(), Component.literal("重新加载的曲目"), 50, 8);
        var reloaded = registry(CLOUD_GENSHIN_SONG, reloadedSong);
        check(components(initializer, reloaded).get(DataComponents.JUKEBOX_PLAYABLE).song()
                == reloaded.getOrThrow(CLOUD_GENSHIN_SONG), "切换服务器后必须使用新的曲目 Holder");
        check(!components(initializer, missingSong).has(DataComponents.JUKEBOX_PLAYABLE), "离开含曲目的服务器后不得保留旧组件");
        check(components(initializer, present).has(DataComponents.JUKEBOX_PLAYABLE), "重新连接含曲目的服务器后应恢复播放组件");

        var reused = DataComponentMap.builder();
        initializer.run(reused, present, DISC_KEY);
        initializer.run(reused, missingSong, DISC_KEY);
        check(!reused.build().has(DataComponents.JUKEBOX_PLAYABLE), "缺失时应移除构建器中的旧播放组件");
        System.out.println("唱片注册表检查通过：" + checks + " 项");
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home")
    args = parser.parse_args()
    java_home = args.java_home or os.environ.get("JAVA_HOME")
    if java_home:
        binary = Path(java_home) / "bin"
    elif shutil.which("javac"):
        binary = Path(shutil.which("javac")).parent
    else:
        candidates = list((Path.home() / ".gradle/jdks").glob("*/bin/javac"))
        assert candidates, "需要本地 JDK 25，或通过 --java-home 指定路径"
        binary = candidates[0].parent

    source = ITEMS.read_text(encoding="utf-8")
    song_key = re.search(r"private static final ResourceKey<JukeboxSong> CLOUD_GENSHIN_SONG = (.*?);", source, re.S)
    properties = re.search(r'public static final Item MUSIC_DISC_CLOUD_GENSHIN = registerMusicDiscCloudGenshin\(\s*"music_disc_cloud_genshin",\s*(.*?)\n\s*\);', source, re.S)
    assert song_key and properties, "无法提取实际唱片注册表达式，请同步更新检查脚本"
    mod_source = (ROOT / "src/main/java/top/csituka/youzaiworldcore/YouzaiworldCore.java").read_text(encoding="utf-8")
    mod_id = re.search(r'public static final String MOD_ID\s*=\s*("[^"]+")', mod_source)
    assert mod_id, "无法提取模组命名空间"
    checks = CHECKS.replace("__SONG_KEY__", song_key[1].replace("YouzaiworldCore.MOD_ID", mod_id[1]))
    checks = checks.replace("__PROPERTIES__", properties[1])

    minecraft = ROOT / "minecraft_jar/26.3/26.3.jar"
    assert minecraft.is_file(), "需要 Minecraft 26.3 原版 jar"
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    libraries = sorted(p for p in cache.glob("*/*/*/*/*.jar")
                       if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
    classpath = os.pathsep.join(map(str, [minecraft, *libraries]))
    with tempfile.TemporaryDirectory(prefix="yzwc-native-music-disc-") as directory:
        temporary = Path(directory)
        harness = temporary / "NativeMusicDiscChecks.java"
        harness.write_text(checks, encoding="utf-8")
        subprocess.run([str(binary / "javac"), "-J-XX:-UsePerfData", "--release", "25", "-proc:none",
                        "-encoding", "UTF-8", "-cp", classpath, "-d", directory, str(harness)], check=True)
        subprocess.run([str(binary / "java"), "-XX:-UsePerfData", "-cp", directory + os.pathsep + classpath,
                        "NativeMusicDiscChecks"], cwd=directory, check=True)


if __name__ == "__main__":
    main()
