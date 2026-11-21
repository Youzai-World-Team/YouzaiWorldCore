"""用本地 26.3 依赖验证动画、饰品效果、依赖声明和启动类加载，不构建整包。"""
from pathlib import Path
from zipfile import ZipFile
import argparse
import json
import os
import re
import subprocess
import tempfile
from verify_java_types import classpath_from_ide, with_runtime_mods
from verify_logging import java_bin

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'top/csituka/youzaiworldcore'
STUBS = {
    'eu/pb4/trinkets/api/TrinketsApi.java': '''package eu.pb4.trinkets.api;
public final class TrinketsApi {
    public static TrinketAttachment ATTACHMENT;
    public static TrinketAttachment getAttachment(net.minecraft.world.entity.LivingEntity entity) {return ATTACHMENT;}
}''',
    PACKAGE + '/util/DebugLogger.java': '''package top.csituka.youzaiworldcore.util;
public final class DebugLogger {
    public static final DebugLogger SERVER = new DebugLogger();
    public static DebugLogger bootstrap() {return SERVER;}
    public void debug(String module,String text,Object... args) {}
    public void exception(String module,String text,Throwable error) {throw new AssertionError(text,error);}
    public ModuleLogger module(String name) {return new ModuleLogger();}
    public static final class ModuleLogger {public void warn(String text,Object... args) {}}
}'''
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--classpath-file', type=Path, help='每行一个已解析依赖 jar 路径；未指定时使用 IDE 依赖')
    args = parser.parse_args()
    binary = java_bin(args.java_home)
    minecraft = ROOT / 'minecraft_jar/26.3/26.3.jar'
    assert minecraft.is_file(), f'需要本地 26.3 原版 jar：{minecraft}'
    libs = args.classpath_file.read_text().splitlines() if args.classpath_file else classpath_from_ide()
    # 已安装依赖优先于 IDE 旧缓存，用于核验当前 26.3 整合包的实际接口。
    libs = [minecraft, *map(Path, libs)]
    properties = dict(re.findall(r'^([^#\s=]+)=(.*)$', (ROOT / 'gradle.properties').read_text(), re.M))
    source = (ROOT / 'src/main/resources/fabric.mod.json').read_text()
    for key, value in properties.items():
        source = source.replace('${' + ('version' if key == 'mod_version' else key) + '}', value)
    assert '${' not in source, '元数据仍有未展开版本变量'
    with tempfile.TemporaryDirectory(prefix='yzwc-runtime-compat-') as directory:
        temporary = Path(directory)
        libs = with_runtime_mods(libs, ROOT / 'run/mods', temporary)
        versions = {'java': '25'}
        for library in libs:
            with ZipFile(library) as archive:
                if 'fabric.mod.json' in archive.namelist():
                    mod = json.loads(archive.read('fabric.mod.json'))
                    versions.setdefault(mod['id'], mod['version'])
                if library == minecraft:
                    versions['minecraft'] = json.loads(archive.read('version.json'))['id']
        files = []
        for relative, content in STUBS.items():
            path = temporary / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
            files.append(path)
        files.extend([ROOT / 'tools/MinecraftRuntimeCompatChecks.java',
                      ROOT / f'src/main/java/{PACKAGE}/startup/StartupReflectAccess.java',
                      ROOT / f'src/main/java/{PACKAGE}/startup/StartupEntrypointTransformer.java',
                      ROOT / f'src/main/java/{PACKAGE}/util/TrinketHelper.java'])
        metadata = temporary / 'fabric.mod.json'
        metadata.write_text(source)
        installed = temporary / 'installed.json'
        installed.write_text(json.dumps(versions))
        classpath = os.pathsep.join(map(str, libs))
        subprocess.run([str(binary / 'javac'), '-J-XX:-UsePerfData', '--release', '25', '-encoding', 'UTF-8',
                        '-proc:none', '-cp', classpath, '-d', directory, *map(str, files)], check=True)
        subprocess.run([str(binary / 'java'), '-XX:-UsePerfData', '-cp', directory + os.pathsep + classpath,
                        'top.csituka.youzaiworldcore.startup.MinecraftRuntimeCompatChecks',
                        str(metadata), str(installed), str(ROOT / 'src/main/resources/assets/youzaiworldcore/geckolib')],
                       cwd=temporary, check=True)


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(f'实际依赖兼容检查失败，退出码 {error.returncode}；原因见上方输出。') from None
