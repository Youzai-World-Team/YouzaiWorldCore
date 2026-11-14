"""由实际 YZUI 配色生成六套半透明 Jade 主题；仅使用 Python 标准库。"""

from pathlib import Path
import json
import math
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/youzaiworldcore"
PALETTE = ROOT / "src/client/java/top/csituka/youzaiworldcore/client/render/YzuiPalette.java"
PANEL_ALPHA = {"standard": 0.70, "frosted": 0.66, "minimal": 0.72}


def channels(color):
    return tuple((color >> shift) & 255 for shift in (16, 8, 0))


def luminance(color):
    linear = [c / 255 / 12.92 if c / 255 <= 0.04045 else ((c / 255 + 0.055) / 1.055) ** 2.4
              for c in channels(color)]
    return sum(c * weight for c, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(first, second):
    a, b = sorted((luminance(first), luminance(second)))
    return (b + 0.05) / (a + 0.05)


def composite(color, background, opacity):
    return sum(round(a * opacity + b * (1 - opacity)) << shift
               for a, b, shift in zip(channels(color), channels(background), (16, 8, 0)))


def readable(color, background, dark):
    for _ in range(64):
        if contrast(color, background) >= 4.5:
            return f"#{color & 0xFFFFFF:06X}"
        adjusted = [min(255, c + max(1, (255 - c) // 12)) if dark else max(0, c - max(1, c // 12))
                    for c in channels(color)]
        color = sum(c << shift for c, shift in zip(adjusted, (16, 8, 0)))
    raise AssertionError("Jade 文字无法达到预期对比度")


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def inside(x, y, inset, radius):
    low, high = inset, 32 - inset
    if not low <= x <= high or not low <= y <= high:
        return False
    dx = max(low + radius - x, 0, x - (high - radius))
    dy = max(low + radius - y, 0, y - (high - radius))
    return dx * dx + dy * dy <= radius * radius


def sprite(fill, opacity, outline, border_opacity):
    """4× 采样圆角；边框和内部互不重叠，避免半透明颜色在边缘重复叠加。"""
    pixels = bytearray()
    for y in range(32):
        pixels.append(0)
        for x in range(32):
            outer = inner = 0
            for sy in range(4):
                for sx in range(4):
                    px, py = x + (sx + 0.5) / 4, y + (sy + 0.5) / 4
                    outer += inside(px, py, 0, 8)
                    inner += inside(px, py, 1, 7)
            body = inner / 16 * opacity
            border = (outer - inner) / 16 * border_opacity
            alpha = body + border
            rgb = [round((a * body + b * border) / alpha) if alpha else 0
                   for a, b in zip(channels(fill), channels(outline))]
            pixels.extend((*rgb, math.floor(alpha * 255 + 0.5)))
    header = struct.pack(">IIBBBBB", 32, 32, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(pixels, 9)) + chunk(b"IEND", b"")


def main():
    assert Path.cwd().resolve() == ROOT.resolve(), "请在 YouzaiWorldCore 仓库根目录运行"
    source = PALETTE.read_text(encoding="utf-8")
    fields = re.findall(r"int (\w+)", re.search(r"record YzuiPalette\((.*?)\) \{", source, re.S).group(1))
    outputs = {}
    for dark in (False, True):
        mode = "DARK" if dark else "LIGHT"
        declaration = re.search(r"\b" + mode + r" = new YzuiPalette\((.*?)\);", source, re.S).group(1)
        values = [int(token, 16) for token in re.findall(r"0x[\dA-Fa-f]+", declaration)]
        assert len(fields) == len(values)
        colors = dict(zip(fields, values))
        for style, opacity in PANEL_ALPHA.items():
            name = "yzui" + ("_dark" if dark else "") + ("_" + style if style != "standard" else "")
            theme_path = ASSETS / "jade_themes" / f"{name}.json"
            theme = json.loads(theme_path.read_text(encoding="utf-8"))
            # 使用 PNG 量化后的真实 Alpha 校验，确保白色雪地/黑暗洞穴上仍能读清文字。
            effective_alpha = math.floor(opacity * 255 + 0.5) / 255
            worst_background = composite(colors["surface"], 0xFFFFFF if dark else 0, effective_alpha)
            semantic = {"normal": "text", "title": "text", "info": "info", "success": "success",
                        "warning": "warning", "danger": "error", "failure": "error"}
            theme["text"]["colors"] = {key: readable(colors[value], worst_background, dark)
                                         for key, value in semantic.items()}
            theme["text"]["modNameStyle"]["color"] = readable(colors["textMuted"], worst_background, dark)
            theme["text"]["itemAmountColor"] = readable(colors["text"], worst_background, dark)
            outputs[theme_path] = (json.dumps(theme, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
            for suffix, fill, alpha in (
                ("", colors["surface"], opacity),
                ("_nested", colors["surfaceLow"], 0.10 if style == "frosted" else 0.14),
                ("_group", colors["surfaceLow"], 0.06 if style == "frosted" else 0.08),
                ("_slot", colors["surfaceHigh"], 0.10 if style == "frosted" else 0.14),
            ):
                path = ASSETS / "textures/gui/sprites" / f"{name}{suffix}.png"
                outputs[path] = sprite(fill, alpha, colors["outlineVariant"], 0.38 if not suffix else 0.22)
    # 所有资源先生成和校验，之后才写回限定的 assets 目录。
    for path, data in outputs.items():
        assert path.resolve().is_relative_to(ASSETS.resolve())
        assert path.is_file(), f"目标主题资源缺失：{path}"
    for path, data in outputs.items():
        path.write_bytes(data)
    print("已更新 6 套 Jade 主题文字与 24 张半透明圆角 PNG。")


if __name__ == "__main__":
    main()
