"""在隔离的 Fabric 26.3 服务端验证切池、重启恢复和故障回滚。"""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

from verify_java_types import classpath_from_ide, with_runtime_mods
from verify_logging import java_bin

ROOT = Path(__file__).resolve().parents[1]
AUDIT = 'top.csituka.youzaiworldcore.dimensionalinventories.PoolRuntimeAudit'


def compile_sources(java, temporary, libraries):
    classes = temporary / 'classes'
    shutil.copytree(ROOT / 'src/main/resources', classes)
    shutil.copytree(ROOT / 'src/client/resources', classes, dirs_exist_ok=True)
    metadata = classes / 'fabric.mod.json'
    source = metadata.read_text(encoding='utf-8')
    properties = dict(re.findall(r'^([^#\s=]+)=(.*)$', (ROOT / 'gradle.properties').read_text(), re.M))
    for key, value in properties.items():
        source = source.replace('${' + ('version' if key == 'mod_version' else key) + '}', value)
    mod = json.loads(source)
    mod['entrypoints'].setdefault('server', []).append(AUDIT)
    metadata.write_text(json.dumps(mod), encoding='utf-8')
    arguments = [
        '--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, libraries)),
        '-d', classes, *sorted((ROOT / 'src/main/java').rglob('*.java')), ROOT / 'tools/PoolRuntimeAudit.java',
    ]
    argfile = temporary / 'javac.args'
    argfile.write_text('\n'.join(json.dumps(str(arg), ensure_ascii=False) for arg in arguments), encoding='utf-8')
    subprocess.run([java / 'javac', '-J-XX:-UsePerfData', '@' + str(argfile)], check=True)
    return classes


def server_libraries(libraries):
    # IDE 类路径可能同时包含 Mojang 和模组要求的两个 ASM 版本。
    asm = {}
    others = []
    for library in map(Path, libraries):
        if 'minecraft-clientOnly' in library.name:
            continue
        if 'org.ow2.asm' not in library.parts:
            others.append(library)
            continue
        artifact, version = library.parts[-4:-2]
        current = asm.get(artifact)
        if current is None or tuple(map(int, version.split('.'))) > current[0]:
            asm[artifact] = (tuple(map(int, version.split('.'))), library)
    return others + [library for _, library in asm.values()]


def run_phase(launch, game, log, phase, timeout):
    command = [launch[0], '-Dyzwc.pool.audit=' + phase, *launch[1:]]
    with log.open('w') as output:
        process = subprocess.Popen(command, cwd=game, stdin=subprocess.PIPE, stdout=output,
                                   stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + timeout
            while process.poll() is None and time.monotonic() < deadline:
                text = log.read_text(errors='replace')
                if 'POOL AUDIT PASSED' in text or 'POOL AUDIT FAILED' in text:
                    process.communicate('stop\n', timeout=40)
                    break
                time.sleep(0.5)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    text = log.read_text(errors='replace')
    if process.returncode != 0 or 'POOL AUDIT PASSED' not in text or 'Encountered an unexpected exception' in text:
        print('\n'.join(text.splitlines()[-120:]))
        raise SystemExit(f'维度池服务端验证失败：{phase}')
    print(f'维度池服务端验证通过：{phase}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--classpath-file', type=Path)
    parser.add_argument('--timeout', type=float, default=100)
    args = parser.parse_args()
    java = java_bin(args.java_home)
    libraries = args.classpath_file.read_text().splitlines() if args.classpath_file else classpath_from_ide()
    with tempfile.TemporaryDirectory(prefix='yzwc-pool-runtime-') as directory:
        temporary = Path(directory)
        classes = compile_sources(java, temporary, with_runtime_mods(libraries, ROOT / 'run/mods', temporary))
        runtime = server_libraries(libraries)
        minecraft = next(path for path in runtime if path.name.startswith('minecraft-common-'))
        launch = [str(java / 'java'), '-XX:-UsePerfData', '-Xmx2300m', '-Dfabric.development=true',
                  '-Dfabric.gameJarPath=' + str(minecraft), '-Dfabric.defaultModDistributionNamespace=official',
                  '-Dfabric.defaultMixinRemapType=static', '-cp', os.pathsep.join(map(str, [classes, *runtime])),
                  'net.fabricmc.loader.impl.launch.knot.KnotServer', 'nogui']
        game = temporary / 'game'
        config = game / 'yzwc/server/config/global_settings.json'
        config.parent.mkdir(parents=True)
        config.write_text(json.dumps({
            'api_module': {'enabled': False},
            'update_module': {'enabled': False, 'check_on_startup_server': False},
            'dimensional_inventories_module': {'pools': [
                {'id': 'survival_world_pool', 'displayName': 'Survival test', 'gameMode': 'survival',
                 'dimensions': ['minecraft:overworld', 'minecraft:the_nether']},
                {'id': 'test_pool', 'displayName': 'End test', 'gameMode': 'creative',
                 'dimensions': ['minecraft:the_end']},
            ]},
        }), encoding='utf-8')
        (game / 'eula.txt').write_text('eula=true\n')
        (game / 'server.properties').write_text(
            'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n')
        for phase in ('switch', 'restart', 'bad-scoreboard'):
            if phase == 'bad-scoreboard':
                (game / 'world/data/yzwc/data/dimensional_inventories_module/test_pool/scoreboard.dat').write_bytes(
                    b'invalid scoreboard')
            run_phase(launch, game, temporary / (phase + '.log'), phase, args.timeout)


if __name__ == '__main__':
    main()
