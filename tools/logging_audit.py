"""扫描真实 Java 日志调用，跳过注释与字符串，保留逐调用位置和档位。"""
from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
METHOD_LEVELS = {
    "notice": 0, "warn": 0, "error": 0, "exception": 0, "exceptionSummary": 0,
    "info": 1, "stateChange": 1,
    "debug": 2, "branch": 2, "debugStateChange": 2,
    "trace": 3, "entering": 3, "exiting": 3, "traceBranch": 3, "traceStateChange": 3,
}
LEXEMES = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
CALLS = re.compile(
    r'(?P<owner>\bDebugLogger(?:\.(?:SERVER|CLIENT)|\.bootstrap\(\)|\.forFile\([^()]*\))?(?:\.module\([^()]*\))?'
    r'|\b(?:[\w$]+\.)*(?:[\w$]*LOGGER|LOG|[\w$]*MOD_LOG|logger))'
    r'\.(?P<method>' + '|'.join(METHOD_LEVELS) + r')\s*\('
)


def masked(source: str) -> str:
    return LEXEMES.sub(lambda m: re.sub(r'[^\n]', ' ', m[0]), source)


def arguments(source: str, code: str, opening: int) -> tuple[list[str], int]:
    depth = 0
    start = opening + 1
    args: list[str] = []
    for i in range(opening + 1, len(code)):
        c = code[i]
        if c in '([{':
            depth += 1
        elif c in ')]}':
            if c == ')' and depth == 0:
                if source[start:i].strip():
                    args.append(source[start:i].strip())
                return args, i + 1
            depth -= 1
        elif c == ',' and depth == 0:
            args.append(source[start:i].strip())
            start = i + 1
    raise ValueError('日志调用括号未闭合')


@dataclass(frozen=True)
class Call:
    file: str
    line: int
    start: int
    head_end: int
    end: int
    owner: str
    method: str
    args: tuple[str, ...]

    @property
    def level(self) -> int:
        return METHOD_LEVELS[self.method]

    @property
    def message(self) -> str:
        offset = 1 if (self.owner.startswith('DebugLogger') and '.module(' not in self.owner) or self.owner == 'logger' else 0
        return self.args[offset] if len(self.args) > offset else ''


def calls_in(path: Path, source: str | None = None) -> list[Call]:
    if source is None:
        source = path.read_text(encoding='utf-8')
    code = masked(source)
    relative = str(path.relative_to(ROOT)) if path.is_absolute() else str(path)
    result: list[Call] = []
    for match in CALLS.finditer(code):
        args, end = arguments(source, code, match.end() - 1)
        result.append(Call(relative, source.count('\n', 0, match.start()) + 1,
                           match.start(), match.end() - 1, end, source[match.start():match.start('method') - 1],
                           match['method'], tuple(args)))
    return result


def inventory() -> list[Call]:
    result: list[Call] = []
    for source_set in ('main', 'client'):
        for path in sorted((ROOT / 'src' / source_set / 'java').rglob('*.java')):
            if path.name not in ('DebugLogger.java', 'LogOutput.java'):
                result.extend(calls_in(path))
    return result


def summary(calls: list[Call]) -> str:
    totals = Counter(call.level for call in calls)
    files: dict[str, Counter] = defaultdict(Counter)
    for call in calls:
        files[call.file][call.level] += 1
    lines = [
        '# 全模组日志归档清单', '',
        '由 `python3 tools/verify_logging.py --write-audit` 从真实 Java 调用生成。数字是静态调用点，不是运行次数。', '',
        '普通日志始终输出；1–3 档要求对应端开启开发者模式。各档累积包含低档诊断。', '',
        '| 类别 | 调用点 | 内容 |', '|---|---:|---|',
        f'| 普通日志 | {totals[0]} | 启停、更新、管理员操作及实际警告 / 故障 |',
        f'| 1 基本 | {totals[1]} | 配置 / 数据摘要、重要状态和结果 |',
        f'| 2 详细 | {totals[2]} | 注册、网络操作、计算与分支诊断 |',
        f'| 3 调试 | {totals[3]} | 方法入口 / 出口、每帧 / tick 与逐条追踪 |', '',
        '共享存储按文件归属路由；通用资源注册按物理进程路由，其余调用明确指定客户端或服务端。', '',
        '| 源集 / 文件 | 普通 | 1 | 2 | 3 |', '|---|---:|---:|---:|---:|',
    ]
    for file, counts in sorted(files.items()):
        short = file.replace('java/top/csituka/youzaiworldcore/', '')
        lines.append(f'| [{short}](../{file}) | {counts[0]} | {counts[1]} | {counts[2]} | {counts[3]} |')
    return '\n'.join(lines) + '\n'
