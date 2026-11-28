"""从原贴图生成首批方块和物品的 labPBR 材质贴图（需要 Pillow）。"""

from math import sqrt
from pathlib import Path
from typing import NamedTuple

from PIL import Image


TEXTURES = Path(__file__).resolve().parents[1] / "src/main/resources/assets/youzaiworldcore/textures"


class Material(NamedTuple):
    height: float
    smoothness: int
    reflectance: int
    emission: int = 255


STONE = Material(0.16, 31, 10)
CERAMIC = Material(0.36, 73, 12)
WOOD = Material(0.28, 38, 10)
METAL = Material(0.52, 147, 230)  # labPBR 铁
CRYSTAL = Material(0.63, 197, 25)

BLOCKS = (
    "tp_anchor", "tp_anchor_active",
    "fly_beacon_top", "fly_beacon_top_active", "fly_beacon_around",
    "magic_table_top", "magic_table_side1", "magic_table_side2",
    "yz_ore", "deepslate_yz_ore", "yz_block",
)
ITEMS = (
    "void_staff", "void_staff_active", "flame_staff", "sky_star_staff",
    "teleport_stone", "primogem", "heart_of_guardianship", "yz_ingot", "yz_sword",
)


def block_material(name, r, g, b):
    if name.startswith("tp_anchor"):
        if r == g == b == 255:
            return Material(0.22, 155, 19, 190)
        return Material(0.22, 106, 15) if r < 140 else CERAMIC
    if name.startswith("fly_beacon"):
        if (r, g, b) == (100, 235, 255):
            return Material(0.68, 184, 22, 185)
        if b - r > 35:
            return CRYSTAL
        return METAL if r > 110 else Material(0.26, 87, 230)
    if name.startswith("magic_table"):
        if b > 210:
            return Material(0.48, 134, 17, 125)
        return CERAMIC if b > 80 else STONE
    if name.endswith("yz_ore"):
        if g - r < 14:
            return STONE
        return Material(0.78, 214, 26, 54) if r > 150 else CRYSTAL
    return youzai_metal(r, g)


def youzai_metal(r, g):
    if r >= 160 and g >= 160:
        return Material(0.72, 205, 31)
    return Material(0.54, 176, 24) if g >= 140 else Material(0.41, 127, 20)


def item_material(name, x, y, r, g, b):
    if name.startswith("void_staff"):
        if x >= 11 and y <= 5 and b > g and r > 95:
            emission = 205 if name.endswith("_active") else 79
            return CRYSTAL._replace(emission=emission)
        return METAL if max(r, g, b) - min(r, g, b) < 40 else WOOD
    if name == "flame_staff":
        emission = 222 if g >= 200 else 148 if g > 130 else 80
        return Material(0.47 + g / 1024, 145, 17, emission)
    if name == "sky_star_staff":
        return CRYSTAL._replace(emission=208) if r > 200 and g > 175 else WOOD
    if name == "teleport_stone":
        return Material(0.22, 163, 18, 105) if r < 140 else CERAMIC
    if name == "primogem":
        return Material(0.42 + (r + g + b) / 3072, 210, 25, 153 if min(r, g, b) > 200 else 68)
    if name == "heart_of_guardianship":
        if r - b > 100:
            return CRYSTAL._replace(emission=218 if g > 160 else 89)
        return METAL if r > 100 else Material(0.33, 94, 230)
    return youzai_metal(r, g) if g > r + 10 else WOOD


def texture_regions(name, width, height):
    if not name.startswith("tp_anchor"):
        yield (0, 0, width, height)
        return
    # 锚点的侧面和顶底使用同一张展开图，每个面的法线只采样该面的像素。
    for y, face_width, face_height in ((0, 16, 10), (10, 20, 6), (16, 16, 40), (56, 32, 8)):
        for x in range(0, 4 * face_width, face_width):
            yield (x, y, x + face_width, y + face_height)
    yield (0, 64, 32, 96)


def write_material_maps(path):
    with Image.open(path) as source:
        base = source.convert("RGBA")
    width, height = base.size
    name = path.stem
    is_block = path.parent.name == "block"
    tileable = name in {"yz_ore", "deepslate_yz_ore", "yz_block"}
    normal = Image.new("RGBA", base.size, (128, 128, 255, 255))
    specular = Image.new("RGBA", base.size, (0, 0, 0, 255))

    for left, top, right, bottom in texture_regions(name, width, height):
        heights = {}
        for y in range(top, bottom):
            for x in range(left, right):
                r, g, b, alpha = base.getpixel((x, y))
                if alpha == 0:
                    continue
                material = block_material(name, r, g, b) if is_block else item_material(name, x, y, r, g, b)
                heights[x, y] = material.height
                # labPBR 的 Alpha 为 255 时关闭自发光，B=0 不添加孔隙或次表面散射。
                specular.putpixel((x, y), (material.smoothness, material.reflectance, 0, material.emission))

        def height_at(x, y):
            if tileable:
                return heights.get((x % width, y % height), 0.0)
            x = min(max(x, left), right - 1)
            y = min(max(y, top), bottom - 1)
            return heights.get((x, y), 0.0)

        for x, y in heights:
            nx = (height_at(x - 1, y) - height_at(x + 1, y)) * 0.52
            ny = (height_at(x, y + 1) - height_at(x, y - 1)) * 0.52
            length = sqrt(1 + nx * nx + ny * ny)
            # B=255 为无遮挡，A=255 避免光影对低分辨率像素纹理应用视差位移。
            normal.putpixel((x, y), (round(127.5 * (1 + nx / length)), round(127.5 * (1 + ny / length)), 255, 255))

    normal.save(path.with_stem(f"{name}_n"), optimize=True)
    specular.save(path.with_stem(f"{name}_s"), optimize=True)


if __name__ == "__main__":
    for folder, names in (("block", BLOCKS), ("item", ITEMS)):
        for texture in names:
            write_material_maps(TEXTURES / folder / f"{texture}.png")
