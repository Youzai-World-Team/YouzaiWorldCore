"""修复本地 Iceberg 1.4.3.1 与 Forge Config API Port 的 Night Config 包名冲突。

只调整本地运行依赖，不构建项目、不改配置文件。原始 jar 备份到 mods 目录之外。
运行：python3 tools/fix_iceberg_night_config.py [Iceberg.jar]
"""

from pathlib import Path
import argparse
import copy
import hashlib
import io
import json
import os
import shutil
import struct
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OLD = b"com/electronwill/nightconfig"
NEW = b"com/anthonyhilyard/iceberg/shadow/nightconfig"
MARKER = "META-INF/youzaiworldcore-iceberg-night-config-fix.txt"


def relocate_class(data):
    """重写常量池 UTF-8 类型名及反射字符串，保持字节码指令、常量索引和栈帧不变。"""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("不是有效的 Java class 文件")
    count = struct.unpack_from(">H", data, 8)[0]
    output = bytearray(data[:10])
    offset, index = 10, 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
             11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]
        output.append(tag)
        offset += 1
        if tag == 1:
            size = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            value = data[offset:offset + size]
            value = value.replace(OLD, NEW).replace(OLD.replace(b"/", b"."), NEW.replace(b"/", b"."))
            output.extend(struct.pack(">H", len(value)))
            output.extend(value)
        else:
            if tag not in sizes:
                raise ValueError(f"未知常量池类型：{tag}")
            size = sizes[tag]
            output.extend(data[offset:offset + size])
            if tag in (5, 6):
                index += 1
        offset += size
        index += 1
    output.extend(data[offset:])
    return bytes(output)


def fix(path):
    """校验目标、生成隔离包名的 jar 并原子替换；重复运行直接返回。"""
    path = path.resolve()
    original = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(original)) as source:
        metadata = json.loads(source.read("fabric.mod.json"))
        if metadata.get("id") != "iceberg" or metadata.get("version") != "1.4.3.1":
            raise ValueError("此工具只针对已核验的 Iceberg 1.4.3.1")
        if MARKER in source.namelist():
            print(f"已修复，无需重复处理：{path}")
            return
        writing_mode = OLD.decode() + "/core/io/WritingMode.class"
        if writing_mode not in source.namelist() or b"REPLACE_ATOMIC" in source.read(writing_mode):
            raise ValueError("目标不包含导致本次崩溃的旧版 Night Config，未修改")
        if any(name.upper().endswith((".SF", ".RSA", ".DSA", ".EC"))
               for name in source.namelist() if name.startswith("META-INF/")):
            raise ValueError("目标含签名文件，未修改")

        with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".tmp", delete=False) as temp:
            temporary = Path(temp.name)
        try:
            with zipfile.ZipFile(temporary, "w") as target:
                target.comment = source.comment
                for entry in source.infolist():
                    value = source.read(entry)
                    renamed = copy.copy(entry)
                    renamed.filename = entry.filename.replace(OLD.decode(), NEW.decode())
                    if entry.filename.endswith(".class"):
                        value = relocate_class(value)
                    elif entry.filename.startswith("META-INF/services/"):
                        renamed.filename = renamed.filename.replace(OLD.decode().replace("/", "."), NEW.decode().replace("/", "."))
                        value = value.replace(OLD.replace(b"/", b"."), NEW.replace(b"/", b"."))
                    target.writestr(renamed, value)
                target.writestr(MARKER, "Local compatibility fix: isolate Iceberg's embedded Night Config.\n")

            with zipfile.ZipFile(temporary) as target:
                if target.testzip() is not None:
                    raise ValueError("生成的 jar 校验失败")
                if writing_mode in target.namelist() or NEW.decode() + "/core/io/WritingMode.class" not in target.namelist():
                    raise ValueError("Night Config 包名隔离失败")

            digest = hashlib.sha256(original).hexdigest()[:12]
            backup_dir = path.parent.parent / "mod-backups" / "iceberg-night-config"
            backup_dir.mkdir(parents=True, exist_ok=True)
            backup = backup_dir / f"{path.name}.{digest}.original"
            if backup.exists():
                if backup.read_bytes() != original:
                    raise ValueError("备份内容不一致，未替换原文件")
            else:
                shutil.copy2(path, backup)
            shutil.copymode(path, temporary)
            os.replace(temporary, path)
            print(f"已修复：{path}\n原始文件备份：{backup}")
        finally:
            temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", nargs="?", type=Path,
                        default=ROOT / "run/mods/Iceberg-26.3-fabric-1.4.3.1.jar")
    args = parser.parse_args()
    fix(args.jar)
