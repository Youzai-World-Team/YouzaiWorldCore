"""离线验证全模组日志归档、两端隔离和真实文件输出；不执行 Gradle 构建。"""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import argparse
import os
import re
import shutil
import subprocess
import tempfile

from logging_audit import ROOT, METHOD_LEVELS, inventory, masked, summary


def check_sources():
    errors = []
    for source_set in ('main', 'client'):
        for path in (ROOT / 'src' / source_set / 'java').rglob('*.java'):
            if path.name in ('DebugLogger.java', 'LogOutput.java'):
                continue
            code = masked(path.read_text())
            if re.search(r'\b(?:LoggerFactory|LogUtils|LogManager)\s*\.', code):
                errors.append(f'{path.relative_to(ROOT)}: 仍有裸日志器')
            if re.search(r'\bDebugLogger\.(?:' + '|'.join(METHOD_LEVELS) + r'|isEnabled|setLogLevel|setDevModeEnabled)\s*\(', code):
                errors.append(f'{path.relative_to(ROOT)}: 仍有共享静态日志调用')
            if source_set == 'client' and re.search(r'\bDebugLogger\.SERVER\.(?:configure|' + '|'.join(METHOD_LEVELS) + r')\s*\(', code):
                errors.append(f'{path.relative_to(ROOT)}: 客户端修改或输出服务端日志')
            if re.search(r'YouzaiworldCore\.(?:logToFile|devModeEnabled)', code):
                errors.append(f'{path.relative_to(ROOT)}: 仍在依赖跨端镜像开关')
            if re.search(r'\btop\.csituka\.youzaiworldcore\.(?:[\w$]*MOD_LOG|[\w$]*LOGGER)\.', code):
                errors.append(f'{path.relative_to(ROOT)}: 日志字段被错误地当作包内类型引用')
    server = (ROOT / 'src/main/java/top/csituka/youzaiworldcore/config/ServerExternalSettings.java').read_text()
    assert 'getIntExact("log_level"' in server and 'DebugLogger.SERVER.configure(devModeEnabled, logLevel)' in server
    assert 'getBoolean("log_to_file"' not in server
    client = (ROOT / 'src/client/java/top/csituka/youzaiworldcore/client/config/ClientExternalSettings.java').read_text()
    assert 'DebugLogger.CLIENT.configure(devModeEnabled, logLevel)' in client
    if errors:
        raise AssertionError('\n'.join(errors))
    calls = inventory()
    for call in calls:
        if call.method not in ('trace', 'debug', 'info', 'notice', 'warn', 'error'):
            continue
        if re.search(r'%(?:0\d+|\.\d+)?[sdbfX]', call.message) or '.formatted(' in call.message or 'String.format(' in call.message:
            raise AssertionError(f'{call.file}:{call.line}: 日志模板未统一为 {{}}')
    return calls


def java_bin(requested):
    if requested:
        return requested / 'bin'
    if os.environ.get('JAVA_HOME'):
        return Path(os.environ['JAVA_HOME']) / 'bin'
    if shutil.which('javac'):
        return Path(shutil.which('javac')).parent
    candidates = list((Path.home() / '.gradle/jdks').glob('*/bin/javac'))
    if not candidates:
        raise RuntimeError('没有可用 JDK；使用 --java-home 指定 JDK 25。')
    return sorted(candidates)[-1].parent


def logging_jars():
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    result = []
    for group, module in [('com.google.code.gson', 'gson'), ('org.slf4j', 'slf4j-api'), ('org.apache.logging.log4j', 'log4j-api'),
                          ('org.apache.logging.log4j', 'log4j-core'), ('org.apache.logging.log4j', 'log4j-slf4j2-impl')]:
        jars = [p for p in (cache / group / module).rglob('*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
        if not jars:
            raise RuntimeError(f'缺少缓存依赖：{group}:{module}')
        result.append(sorted(jars)[-1])
    return result


def check_runtime(binary):
    with tempfile.TemporaryDirectory(prefix='yzwc-logging-') as directory:
        temp = Path(directory)
        stub = temp / 'src/top/csituka/youzaiworldcore/config/ModPaths.java'
        stub.parent.mkdir(parents=True)
        stub.write_text('''package top.csituka.youzaiworldcore.config;
import java.nio.file.Path;
public final class ModPaths {
 public static Path clientRoot(){return Path.of(System.getProperty("yzwc.test.gamedir"),"yzwc","client");}
 public static boolean isClientEnvironment(){return true;}
 public static Path clientLogFile(){return Path.of(System.getProperty("yzwc.test.gamedir"),"logs","youzaiworldcore-client.log");}
 public static Path serverLogFile(){return Path.of(System.getProperty("yzwc.test.gamedir"),"logs","youzaiworldcore-server.log");}
 public static Path globalSettingsFile(){return Path.of(System.getProperty("yzwc.test.gamedir"),"yzwc","server","config","global_settings.json");}
 public static Path clientSettingsFile(){return clientRoot().resolve("global_settings.json");}
}''')
        additional_stubs = {
            'config/DefaultSettingsWriter.java': '''package top.csituka.youzaiworldcore.config;
public final class DefaultSettingsWriter {
 public static void writeAllDefaults(){GlobalSettings.runBatched(ServerExternalSettings::writeDefaults);}
}''',
            'client/config/ClientDefaultSettingsWriter.java': '''package top.csituka.youzaiworldcore.client.config;
public final class ClientDefaultSettingsWriter {
 public static void writeAllDefaults(){ClientGlobalSettings.runBatched(ClientExternalSettings::writeDefaults);}
}''',
            'client/animation/GuiAnimationController.java': '''package top.csituka.youzaiworldcore.client.animation;
import top.csituka.youzaiworldcore.client.config.GuiAnimationMode;
public final class GuiAnimationController {
 public static void onModeChanged(GuiAnimationMode mode){}
 public static GuiAnimationMode getMode(){return GuiAnimationMode.FULL;}
}''',
        }
        generated = [stub]
        for relative, text in additional_stubs.items():
            path = temp / 'src/top/csituka/youzaiworldcore' / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            generated.append(path)
        output = temp / 'classes'
        output.mkdir()
        classpath = os.pathsep.join(map(str, logging_jars()))
        source = ROOT / 'src/main/java/top/csituka/youzaiworldcore/util'
        configuration = ROOT / 'src/main/java/top/csituka/youzaiworldcore/config'
        client_configuration = ROOT / 'src/client/java/top/csituka/youzaiworldcore/client/config'
        sources = generated + [source / 'DebugLogger.java', source / 'LogOutput.java', source / 'LogValues.java', ROOT / 'tools/LoggingRuntimeChecks.java',
                               ROOT / 'tools/LoggingConfigChecks.java']
        sources += [configuration / (name + '.java') for name in
                    ['GlobalSettings', 'ServerExternalSettings', 'JsonFileStore', 'ConfigSection', 'ConfigCrash']]
        sources += [client_configuration / (name + '.java') for name in
                    ['ClientGlobalSettings', 'ClientExternalSettings', 'GuiAnimationMode', 'YzuiThemeMode', 'YzuiVisualStyle']]
        subprocess.run([str(binary / 'javac'), '-J-XX:-UsePerfData', '--release', '25', '-proc:none', '-encoding', 'UTF-8',
                        '-cp', classpath, '-d', str(output), *map(str, sources)], check=True)
        command = [str(binary / 'java'), '-XX:-UsePerfData', '-Dyzwc.test.gamedir=' + str(temp),
                   '-cp', str(output) + os.pathsep + classpath, 'top.csituka.youzaiworldcore.util.LoggingRuntimeChecks']
        subprocess.run(command, check=True)
        proof = Path('/tmp/yzwc-format-proof.json')
        if proof.is_file():
            subprocess.run(command + ['--formats', str(proof)], check=True)
        subprocess.run(command[:-1] + ['top.csituka.youzaiworldcore.util.LoggingConfigChecks'], check=True)
        subprocess.run(command + ['--parse', str(ROOT)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--write-audit', action='store_true')
    args = parser.parse_args()
    calls = check_sources()
    check_runtime(java_bin(args.java_home))
    counts = Counter(call.level for call in calls)
    print('日志调用点归档：' + '，'.join(f'{level}={counts[level]}' for level in range(4)))
    if args.write_audit:
        (ROOT / 'docs/LOGGING_AUDIT.md').write_text(summary(calls))


if __name__ == '__main__':
    main()
