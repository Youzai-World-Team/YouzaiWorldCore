"""使用 IDE 已解析的依赖做全源码 Java 类型分析，不执行 Gradle 构建。"""
from pathlib import Path
import argparse
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from logging_audit import ROOT
from verify_logging import java_bin


def classpath_from_ide():
    storage = Path.home() / '.config/Code/User/workspaceStorage'
    candidates = []
    for path in storage.glob('*/redhat.java/jdt_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/YouzaiWorldCore/.classpath'):
        entries = ET.parse(path).getroot().findall('classpathentry')
        libs = [e.attrib['path'] for e in entries if e.attrib.get('kind') == 'lib']
        if libs and all(Path(p).is_file() for p in libs):
            candidates.append((path.stat().st_mtime, libs))
    if not candidates:
        raise RuntimeError('未找到 IDE 已解析依赖，请使用 --classpath-file 指定每行一个依赖路径的文件。')
    return max(candidates, key=lambda item: item[0])[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--classpath-file', type=Path)
    args = parser.parse_args()
    binary = java_bin(args.java_home)
    libs = args.classpath_file.read_text().splitlines() if args.classpath_file else classpath_from_ide()
    with tempfile.TemporaryDirectory(prefix='yzwc-java-types-') as directory:
        output = Path(directory)
        classpath = output / 'classpath.txt'
        classpath.write_text('\n'.join(libs))
        subprocess.run([str(binary/'javac'), '-J-XX:-UsePerfData', '--release', '25', '-proc:none',
                        '-d', str(output), str(ROOT/'tools/JavaSourceAnalysis.java')], check=True)
        subprocess.run([str(binary/'java'), '-XX:-UsePerfData', '-Xmx2G', '-cp', str(output),
                        'JavaSourceAnalysis', str(ROOT), str(classpath)], check=True)


if __name__ == '__main__':
    main()
