"""用 Minecraft 的重生点 Codec 和 NBT 验证维度池个人重生点。"""
import argparse
from os import pathsep
from pathlib import Path
import subprocess
import tempfile

from logging_audit import ROOT
from verify_java_types import classpath_from_ide
from verify_logging import java_bin


PACKAGE = 'top.csituka.youzaiworldcore.dimensionalinventories'
SOURCES = ROOT / 'src/main/java' / Path(*PACKAGE.split('.'))

SETTINGS = """
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class DimensionPoolSettings {
    static final String UNASSIGNED_POOL_ID = "_unassigned";
    static final Map<String, String> POOLS = new HashMap<>(Map.of(
        "minecraft:overworld", "survival",
        "minecraft:the_nether", "survival",
        "test:creative", "creative",
        "test:building", "building"
    ));

    static Optional<DimensionPool> getPoolByDimension(String dimension) {
        return Optional.ofNullable(POOLS.get(dimension)).map(DimensionPool::new);
    }
}

record DimensionPool(String id) {}
"""

CHECK = """
import java.nio.file.Path;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer.RespawnConfig;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

public final class PoolRespawnPointsCheck {
    private static final String KEY = "youzaiworldcore:pool_respawn_points";
    private static final ResourceKey<Level> CREATIVE =
        ResourceKey.create(Registries.DIMENSION, Identifier.parse("test:creative"));
    private static final ResourceKey<Level> BUILDING =
        ResourceKey.create(Registries.DIMENSION, Identifier.parse("test:building"));

    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(new ProblemReporter.Collector(), HolderLookup.Provider.create(Stream.empty()), tag);
    }

    private static CompoundTag save(PoolRespawnPoints points) {
        var problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithoutContext(problems);
        points.save(output);
        check(problems.isEmpty(), "保存没有编码错误");
        return output.buildResult();
    }

    private static RespawnConfig point(ResourceKey<Level> level, int x, boolean forced) {
        return new RespawnConfig(RespawnData.of(level, new BlockPos(x, 65, 7), 37.5f, 15.0f), forced);
    }

    private static void check(boolean valid, String label) {
        if (!valid) {
            throw new AssertionError(label);
        }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var bed = point(Level.OVERWORLD, 4, false);
        var anchor = point(Level.NETHER, 5, false);
        var creative = point(CREATIVE, 64, false);
        var forced = point(BUILDING, 14, true);

        var alice = new PoolRespawnPoints();
        alice.load(input(new CompoundTag()), bed);
        check(bed.equals(alice.get(Level.OVERWORLD)), "原版个人重生点迁移");
        check(bed.equals(alice.get(Level.NETHER)) && alice.get(CREATIVE) == null, "同池维度共享旧点");
        alice.set(CREATIVE, creative);
        check(bed.equals(alice.get(Level.OVERWORLD)) && creative.equals(alice.get(CREATIVE)), "不同池隔离");
        alice.set(Level.NETHER, anchor);
        check(anchor.equals(alice.get(Level.OVERWORLD)), "同池设置新点覆盖同池旧点");
        alice.set(Level.NETHER, forced);
        check(forced.equals(alice.get(BUILDING)) && anchor.equals(alice.get(Level.NETHER)), "指令按目标维度保存");

        var bob = new PoolRespawnPoints();
        bob.set(CREATIVE, point(CREATIVE, 95, false));
        check(creative.equals(alice.get(CREATIVE)) && bob.get(Level.OVERWORLD) == null, "不同玩家隔离");

        var playerFile = Path.of(args[0]);
        NbtIo.writeCompressed(save(alice), playerFile);
        var saved = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
        var rejoined = new PoolRespawnPoints();
        rejoined.load(input(saved), bed);
        check(anchor.equals(rejoined.get(Level.NETHER)) && creative.equals(rejoined.get(CREATIVE))
                && forced.equals(rejoined.get(BUILDING)), "重启后各池点、角度和强制属性完整");

        var replacement = new PoolRespawnPoints();
        replacement.copyFrom(rejoined);
        check(anchor.equals(replacement.get(Level.OVERWORLD)) && forced.equals(replacement.get(BUILDING)),
                "复活替换玩家时保留各池重生点");
        replacement.set(CREATIVE, null);
        check(replacement.get(CREATIVE) == null && anchor.equals(replacement.get(Level.OVERWORLD))
                && creative.equals(rejoined.get(CREATIVE)), "清除只影响当前池和玩家副本");

        DimensionPoolSettings.POOLS.put("test:building", "survival");
        check(rejoined.get(BUILDING).equals(anchor), "维度重新分池后使用新池个人重生点");
        DimensionPoolSettings.POOLS.put("test:building", "building");
        check(forced.equals(rejoined.get(BUILDING)), "旧池档案保持可用");

        saved.getCompoundOrEmpty(KEY).putString("creative", "invalid");
        var partial = new PoolRespawnPoints();
        partial.load(input(saved), bed);
        check(partial.get(CREATIVE) == null && anchor.equals(partial.get(Level.OVERWORLD))
                && forced.equals(partial.get(BUILDING)), "单个损坏的条目不影响其他池");

        replacement.clear();
        rejoined.load(input(save(replacement)), bed);
        check(rejoined.get(Level.NETHER) == null && rejoined.get(CREATIVE) == null
                && rejoined.get(BUILDING) == null, "空的新档案不会重新导入已清除的旧点");
        System.out.println("维度池个人重生点回归通过，包含 Minecraft NBT 实际写盘和重读。");
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--classpath-file', type=Path)
    args = parser.parse_args()
    java = java_bin(args.java_home)
    libs = args.classpath_file.read_text().splitlines() if args.classpath_file else classpath_from_ide()
    classpath = pathsep.join(libs)
    with tempfile.TemporaryDirectory(prefix='yzwc-pool-respawn-') as directory:
        output = Path(directory)
        settings = output / 'DimensionPoolSettings.java'
        tests = output / 'PoolRespawnPointsCheck.java'
        settings.write_text(f'package {PACKAGE};\n' + SETTINGS, encoding='utf-8')
        tests.write_text(f'package {PACKAGE};\n' + CHECK, encoding='utf-8')
        subprocess.run([str(java / 'javac'), '-J-XX:-UsePerfData', '--release', '25', '-proc:none',
                        '-encoding', 'UTF-8', '-cp', classpath, '-d', str(output),
                        str(SOURCES / 'PoolRespawnPoints.java'), str(settings), str(tests)], check=True)
        subprocess.run([str(java / 'java'), '-XX:-UsePerfData', '-cp', f'{output}{pathsep}{classpath}',
                        f'{PACKAGE}.PoolRespawnPointsCheck', str(output / 'player.dat')],
                       cwd=output, check=True)


if __name__ == '__main__':
    main()
