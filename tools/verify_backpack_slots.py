"""用实际 Trinkets 加载器及同步编解码验证背包槽移除，不构建整包或启动游戏。"""
from pathlib import Path
from zipfile import ZipFile
import argparse
import json
import os
import shutil
import subprocess
import tempfile
from verify_java_types import classpath_from_ide, with_runtime_mods
from verify_logging import java_bin

ROOT = Path(__file__).resolve().parents[1]
MIXIN = 'top/csituka/youzaiworldcore/mixin/compat/TrinketEntitySlotLoaderMixin'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--classpath-file', type=Path)
    parser.add_argument('--runtime-mods', type=Path, default=ROOT / 'run/mods')
    args = parser.parse_args()
    binary = java_bin(args.java_home)
    libs = args.classpath_file.read_text().splitlines() if args.classpath_file else classpath_from_ide()
    configuration = json.loads((ROOT / 'src/main/resources/youzaiworldcore.mixins.json').read_text())
    assert 'compat.TrinketEntitySlotLoaderMixin' in configuration['mixins'], '槽位移除 Mixin 未登记'
    with tempfile.TemporaryDirectory(prefix='yzwc-backpack-slots-') as directory:
        temporary = Path(directory)
        libraries = with_runtime_mods([ROOT / 'minecraft_jar/26.3/26.3.jar', *libs],
                                      args.runtime_mods, temporary)
        packs = temporary / 'packs'
        for library in libraries:
            with ZipFile(library) as archive:
                if 'fabric.mod.json' not in archive.namelist():
                    continue
                mod = json.loads(archive.read('fabric.mod.json'))
                if mod['id'] not in ('trinkets_updated', 'sophisticatedbackpacks'):
                    continue
                destination = packs / mod['id']
                if destination.exists():
                    continue
                destination.mkdir(parents=True)
                for name in archive.namelist():
                    if name.startswith(('data/trinkets/slots/', 'data/trinkets/entities/')) and name.endswith('.json'):
                        file = destination / name.removeprefix('data/trinkets/')
                        file.parent.mkdir(parents=True, exist_ok=True)
                        file.write_bytes(archive.read(name))
        assert (packs / 'sophisticatedbackpacks/entities').is_dir(), '需要精妙背包的实际槽位分配资源'
        shutil.copytree(ROOT / 'src/main/resources/data/trinkets', packs / 'youzaiworldcore')
        # 仅替代模组安装状态查询，槽位加载、分组和同步编解码均使用实际依赖类。
        loader = temporary / 'src/net/fabricmc/loader/api/FabricLoader.java'
        loader.parent.mkdir(parents=True)
        loader.write_text('''package net.fabricmc.loader.api;
public interface FabricLoader {
    static FabricLoader getInstance() { return id -> State.backpacks; }
    boolean isModLoaded(String id);
    final class State { public static boolean backpacks = true; }
}''')
        classpath = os.pathsep.join(map(str, libraries))
        subprocess.run([str(binary / 'javac'), '-J-XX:-UsePerfData', '--release', '25', '-encoding', 'UTF-8',
                        '-proc:none', '-cp', classpath, '-d', directory, str(loader),
                        str(ROOT / f'src/main/java/{MIXIN}.java'), str(ROOT / 'tools/BackpackSlotChecks.java')],
                       check=True)
        subprocess.run([str(binary / 'java'), '-XX:-UsePerfData', '-cp', directory + os.pathsep + classpath,
                        'BackpackSlotChecks', str(packs)], cwd=temporary, check=True)


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(f'背包槽加载与同步检查失败，退出码 {error.returncode}；原因见上方输出。') from None
