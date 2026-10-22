"""Rebuild this round's hand-drawn pixel assets, native model and offline previews.

Requires Pillow. Run from any directory; no concept art is modified or resampled.
All item/block pixels are deliberately painted at 16 x 16, without antialiasing.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageChops
import json
import math

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/white_lily"
SOURCE = Path(__file__).resolve().parent
PREVIEW = ROOT / "docs/previews"
PREVIEW.mkdir(parents=True, exist_ok=True)


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def canvas():
    im = Image.new("RGBA", (16, 16))
    return im, ImageDraw.Draw(im)


def texture(name, im, kind="item"):
    assert im.size == (16, 16) and im.mode == "RGBA"
    assert set(im.getchannel("A").getdata()).issubset({0, 255})
    path = ASSETS / "textures" / kind / (name + ".png")
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    return im


ITEMS = {}

# Plant fibre: separate pointed ends and a gathered, loosely twisted centre.
im, d = canvas()
d.line([(2, 3), (5, 5), (7, 8), (11, 12), (13, 13)], fill="#705a32", width=2)
d.line([(4, 2), (5, 5), (8, 8), (10, 12), (12, 14)], fill="#d0b873")
d.line([(2, 5), (5, 6), (7, 9), (10, 11), (13, 11)], fill="#aa8a4e")
d.line([(6, 2), (6, 5), (9, 8), (11, 10), (14, 11)], fill="#e6d39a")
d.line([(3, 3), (6, 6), (8, 9), (10, 11), (11, 14)], fill="#ead9a2")
d.line([(2, 7), (5, 7), (8, 10), (11, 12), (14, 12)], fill="#bfa566")
d.point([(3, 2), (7, 3), (13, 14)], fill="#e6d39a")
ITEMS["plant_fiber"] = texture("plant_fiber", im)

# Shallow pale wooden tray, with a dark inner recess and raised four-sided rim.
im, d = canvas()
d.polygon([(1, 5), (10, 2), (14, 5), (14, 10), (5, 14), (1, 11)], fill="#49331f")
d.polygon([(2, 5), (10, 3), (13, 5), (5, 8)], fill="#c8a26a")
d.polygon([(2, 6), (5, 9), (13, 6), (13, 10), (5, 13), (2, 10)], fill="#9b733f")
d.polygon([(4, 6), (10, 4), (12, 5), (5, 8)], fill="#654927")
d.polygon([(4, 7), (10, 5), (12, 6), (5, 9)], fill="#b78a4f")
d.line([(3, 10), (5, 12), (12, 9)], fill="#d4b176")
d.line([(5, 10), (11, 8)], fill="#8a6338")
d.point([(3, 6), (7, 5), (9, 11)], fill="#e0bf83")
ITEMS["wooden_tray"] = texture("wooden_tray", im)

# Rough stone mortar and short pestle. The cavity remains clearly distinct.
im, d = canvas()
d.polygon([(2, 7), (4, 5), (11, 5), (14, 7), (13, 11), (10, 14), (5, 14), (2, 11)], fill="#393b3a")
d.polygon([(3, 8), (5, 7), (11, 7), (13, 8), (11, 12), (9, 13), (5, 12)], fill="#777a73")
d.polygon([(3, 6), (5, 5), (11, 5), (13, 7), (11, 9), (5, 9), (2, 7)], fill="#a0a49a")
d.polygon([(5, 6), (10, 6), (11, 7), (9, 8), (5, 8), (4, 7)], fill="#363d35")
d.polygon([(7, 7), (10, 2), (12, 1), (14, 3), (10, 8)], fill="#424640")
d.polygon([(8, 6), (11, 2), (12, 2), (13, 3), (9, 7)], fill="#a5aaa0")
d.line([(4, 9), (5, 11), (7, 12)], fill="#b4b7ae")
d.line([(10, 10), (9, 12)], fill="#5a6158")
d.point([(5, 10), (11, 9)], fill="#8e9488")
ITEMS["stone_mortar"] = texture("stone_mortar", im)

# Coarse red clay bowl, a one-pixel chip breaking the upper right lip.
im, d = canvas()
d.polygon([(2, 5), (5, 3), (11, 3), (14, 5), (13, 10), (10, 13), (5, 13), (2, 10)], fill="#4a2e28")
d.polygon([(3, 6), (13, 6), (12, 10), (9, 12), (5, 11)], fill="#995540")
d.polygon([(3, 5), (5, 4), (11, 4), (13, 5), (11, 7), (5, 7)], fill="#c5815c")
d.polygon([(5, 5), (10, 5), (11, 6), (6, 6)], fill="#5b382e")
d.rectangle((11, 4, 11, 5), fill="#4a2e28")
d.line([(4, 8), (5, 10), (7, 11)], fill="#b97650")
d.point([(10, 10), (5, 8)], fill="#78402f")
ITEMS["clay_bowl"] = texture("clay_bowl", im)

# Two separate translucent-looking tubes, using opaque outline/highlight pixels
# and actual transparent air in the bore rather than semi-transparent blur.
im, d = canvas()
for a, b in [((3, 12), (9, 2)), ((7, 13), (13, 4))]:
    d.line([a, b], fill="#42636b", width=3)
    d.line([(a[0], a[1]-1), (b[0], b[1]-1)], fill="#d8f0e8")
    d.line([(a[0]+1, a[1]), (b[0]+1, b[1])], fill="#82b8bf")
    d.point([a, b], fill=(0, 0, 0, 0))
ITEMS["glass_tube"] = texture("glass_tube", im)

# A circular filter paper, visible as a stepped pixel circle with sparse pores.
im, d = canvas()
d.polygon([(5, 2), (10, 2), (13, 5), (13, 10), (10, 13), (5, 13), (2, 10), (2, 5)], fill="#77776a")
d.polygon([(5, 3), (10, 3), (12, 5), (12, 10), (10, 12), (5, 12), (3, 10), (3, 5)], fill="#d4d3bb")
d.polygon([(5, 3), (9, 3), (11, 5), (11, 9), (9, 11), (5, 11), (3, 9), (3, 5)], fill="#ece9d7")
d.point([(6, 5), (9, 6), (4, 8), (7, 9), (10, 10)], fill="#b6b5a1")
ITEMS["filter_paper"] = texture("filter_paper", im)

# A tied paper sachet, blue mark on its lower right corner.
im, d = canvas()
d.polygon([(5, 2), (7, 3), (10, 2), (11, 4), (10, 6), (13, 11), (12, 14), (3, 14), (2, 11), (5, 6), (4, 4)], fill="#655845")
d.polygon([(5, 3), (7, 4), (10, 3), (9, 6), (12, 11), (11, 13), (4, 13), (3, 11), (6, 6)], fill="#e3d6ae")
d.line([(6, 7), (5, 10), (5, 12)], fill="#f2e7c8")
d.line([(9, 7), (10, 11), (9, 12)], fill="#b9a57e")
d.line([(4, 6), (10, 6)], fill="#835d36")
d.rectangle((10, 11, 11, 12), fill="#497eaa")
d.point((10, 11), fill="#7ea9bc")
ITEMS["desiccant_packet"] = texture("desiccant_packet", im)

# Ground herbal residue: irregular brown-green leaf fragments and short roots.
im, d = canvas()
d.polygon([(2, 11), (4, 7), (6, 8), (8, 4), (10, 6), (12, 7), (14, 11), (12, 13), (4, 13)], fill="#44442c")
d.polygon([(3, 10), (5, 7), (7, 9), (8, 6), (10, 6), (11, 9), (13, 10), (11, 12), (5, 12)], fill="#6b7741")
d.line([(3, 12), (6, 11), (9, 12), (12, 11)], fill="#9b8247")
d.line([(6, 9), (8, 7), (9, 9)], fill="#9ca158")
d.point([(5, 10), (10, 10), (12, 8), (8, 4)], fill="#a2ad65")
d.point([(2, 13), (13, 13), (10, 14)], fill="#715630")
ITEMS["herbal_residue"] = texture("herbal_residue", im)

# Herbal pouch: a broad off-white cloth body, brown cord, exactly two leaves.
im, d = canvas()
d.polygon([(5, 6), (4, 4), (3, 1), (6, 2), (7, 5), (8, 6)], fill="#344c29")
d.polygon([(4, 2), (5, 3), (6, 6), (5, 5)], fill="#7d9c49")
d.polygon([(8, 6), (9, 3), (12, 2), (12, 4), (10, 6)], fill="#38582c")
d.line([(9, 5), (11, 3)], fill="#8aaa55")
d.polygon([(3, 5), (6, 6), (10, 6), (12, 5), (11, 8), (14, 11), (13, 13), (10, 14), (4, 14), (1, 12), (2, 10), (4, 8)], fill="#5c4633")
d.polygon([(4, 6), (7, 7), (10, 7), (11, 6), (10, 8), (13, 11), (12, 12), (10, 13), (4, 13), (2, 12), (3, 10), (5, 8)], fill="#d9c6a3")
d.polygon([(5, 9), (9, 9), (11, 11), (10, 13), (5, 13), (3, 12)], fill="#f2e4c3")
d.line([(4, 8), (10, 8), (12, 10)], fill="#8b5d34")
d.point([(5, 7), (9, 10), (11, 12)], fill="#fff0ce")
d.line([(10, 8), (10, 10)], fill="#68432b")
ITEMS["herbal_pouch"] = texture("herbal_pouch", im)

# Every new ordinary item uses an independent generated model; the table uses
# its actual block model, never a flattened image masquerading as a block.
for name in ITEMS:
    save_json(ASSETS / "models/item" / (name + ".json"), {
        "parent": "minecraft:item/generated", "textures": {"layer0": "white_lily:item/" + name}})


def wood(name, colors):
    im, d = canvas()
    d.rectangle((0, 0, 15, 15), fill=colors[1])
    for y in (0, 5, 10, 15):
        d.line([(0, y), (15, y)], fill=colors[0])
    for x, y, n in [(1, 2, 5), (8, 3, 6), (3, 7, 8), (1, 12, 8), (10, 13, 4), (11, 7, 3)]:
        d.line([(x, y), (x+n, y)], fill=colors[2])
    for x, y in [(6, 4), (11, 9), (3, 14)]:
        d.line([(x, y), (x+2, y)], fill=colors[0])
    return texture(name, im, "block")


BLOCK = {
    "wood": wood("simple_processing_wood", ["#6d492c", "#99703f", "#b58b51"]),
    "edge": wood("simple_processing_edge", ["#3e2c20", "#59402a", "#775231"]),
    "tray": wood("simple_processing_tray", ["#876036", "#b28b52", "#cfac6b"]),
}
im, d = canvas()
d.rectangle((0, 0, 15, 15), fill="#777b72")
for y in range(0, 16, 4):
    for x in range(0, 16, 4):
        tone = ["#5c625b", "#8b9087", "#6b7068", "#9ca095"][(x//4 + y//4*3) % 4]
        d.rectangle((x, y, x+2, y+2), fill=tone)
d.line([(1, 5), (5, 5), (6, 9)], fill="#4f554f")
BLOCK["stone"] = texture("simple_processing_stone", im, "block")
im, d = canvas()
d.rectangle((0, 0, 15, 15), fill="#5b713c")
for x, y in [(1, 1), (4, 8), (8, 3), (12, 12), (3, 13), (10, 9)]:
    d.rectangle((x, y, x+2, y+1), fill="#88a24f")
    d.point((x+1, y+2), fill="#394d2b")
BLOCK["herb"] = texture("simple_processing_herb", im, "block")
im, d = canvas()
d.rectangle((0, 0, 15, 15), fill="#e7d8b5")
for y in (3, 8, 13):
    d.line([(0, y), (15, y)], fill="#c8b891")
d.line([(12, 0), (12, 15)], fill="#eee2c5")
BLOCK["cloth"] = texture("simple_processing_cloth", im, "block")
im, d = canvas()
d.rectangle((0, 0, 15, 15), fill="#d9c696")
d.rectangle((0, 0, 15, 0), fill="#ad986d")
d.line([(7, 2), (7, 12)], fill="#5b713c")
d.rectangle((3, 5, 6, 7), fill="#7c914a")
d.rectangle((8, 8, 11, 10), fill="#62763a")
BLOCK["tag"] = texture("simple_processing_tag", im, "block")

elements = []


def cube(name, lo, hi, tex, rotation=None):
    sx, sy, sz = [hi[i]-lo[i] for i in range(3)]
    sizes = {"north": (sx, sy), "south": (sx, sy), "west": (sz, sy), "east": (sz, sy),
             "up": (sx, sz), "down": (sx, sz)}
    e = {"name": name, "from": lo, "to": hi,
         "faces": {face: {"uv": [0, 0, w, h], "texture": "#"+tex} for face, (w, h) in sizes.items()}}
    if rotation:
        e["rotation"] = rotation
    elements.append(e)


cube("table_top", [1, 11, 1], [15, 13, 15], "wood")
cube("front_dark_edge", [0, 11, 0], [16, 13, 1], "edge")
cube("back_dark_edge", [0, 11, 15], [16, 13, 16], "edge")
cube("left_dark_edge", [0, 11, 1], [1, 13, 15], "edge")
cube("right_dark_edge", [15, 11, 1], [16, 13, 15], "edge")
for x in (1, 13):
    for z in (1, 13):
        cube("leg_%s_%s" % (x, z), [x, 0, z], [x+2, 11, z+2], "edge")
cube("lower_shelf", [3, 2, 2], [13, 3, 14], "wood")
cube("lower_front_rail", [3, 2, 1], [13, 3, 2], "edge")
cube("lower_back_rail", [3, 2, 14], [13, 3, 15], "edge")
cube("left_mortar_base", [2, 13, 3], [7, 13.5, 8], "stone")
cube("mortar_front", [2, 13.5, 3], [7, 15, 4], "stone")
cube("mortar_back", [2, 13.5, 7], [7, 15, 8], "stone")
cube("mortar_left", [2, 13.5, 4], [3, 15, 7], "stone")
cube("mortar_right", [6, 13.5, 4], [7, 15, 7], "stone")
cube("mortar_herbs", [3, 13.5, 4], [6, 13.75, 7], "herb")
cube("short_pestle", [4.5, 14, 5], [5.5, 15.75, 6], "stone",
     {"origin": [5, 14, 5.5], "axis": "z", "angle": -22.5})
cube("tray_floor", [8, 13, 2], [14, 13.5, 10], "tray")
cube("tray_front", [8, 13.5, 2], [14, 14.25, 2.5], "tray")
cube("tray_back", [8, 13.5, 9.5], [14, 14.25, 10], "tray")
cube("tray_left", [8, 13.5, 2.5], [8.5, 14.25, 9.5], "tray")
cube("tray_right", [13.5, 13.5, 2.5], [14, 14.25, 9.5], "tray")
cube("tray_cloth", [11.5, 13.5, 6.5], [13.25, 13.75, 9], "cloth")
cube("tray_leaf_a", [9, 13.5, 3.5], [10, 13.75, 6.5], "herb")
cube("tray_leaf_b", [10.25, 13.5, 4.5], [11, 13.75, 7], "herb")
cube("front_cord", [11.5, 10, 0.25], [11.75, 12, 0.5], "edge")
cube("front_small_tag", [10.5, 8.5, 0], [12.75, 10.75, 0.25], "tag")

model = {
    "credit": "White Lily: hand-built single-block cuboids; edit art-source/processing/draw_processing_assets.py",
    "render_type": "minecraft:cutout",
    "ambientocclusion": True,
    "textures": {**{k: "white_lily:block/simple_processing_"+k for k in BLOCK},
                 "particle": "white_lily:block/simple_processing_wood"},
    "elements": elements,
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.7, .7, .7]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [.25, .25, .25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.5, .5, .5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375, .375, .375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.4, .4, .4]}
    }
}
save_json(ASSETS / "models/block/simple_processing_table.json", model)
save_json(SOURCE / "simple_processing_table.editable.json", model)
save_json(ASSETS / "models/item/simple_processing_table.json", {"parent": "white_lily:block/simple_processing_table"})
save_json(ASSETS / "blockstates/simple_processing_table.json", {"variants": {
    "facing=north": {"model": "white_lily:block/simple_processing_table"},
    "facing=east": {"model": "white_lily:block/simple_processing_table", "y": 90},
    "facing=south": {"model": "white_lily:block/simple_processing_table", "y": 180},
    "facing=west": {"model": "white_lily:block/simple_processing_table", "y": 270}
}})

# Screen background. Coordinates here are menu item coordinates (16px icons),
# with the 18px inset slot border one pixel outside each icon.
ui = Image.new("RGBA", (256, 256))
d = ImageDraw.Draw(ui)
d.rectangle((0, 0, 229, 211), fill="#101010")
d.rectangle((1, 1, 228, 210), fill="#555555")
d.rectangle((2, 2, 227, 209), fill="#c6c6c6")
d.line([(2, 208), (2, 2), (226, 2)], fill="#ffffff", width=2)
d.line([(3, 209), (227, 209), (227, 3)], fill="#555555", width=2)
d.line([(7, 16), (222, 16)], fill="#8b8b8b")
d.line([(7, 17), (222, 17)], fill="#eeeeee")
d.line([(7, 112), (222, 112)], fill="#8b8b8b")
d.line([(7, 113), (222, 113)], fill="#eeeeee")


def slot(draw, x, y):
    draw.rectangle((x-1, y-1, x+16, y+16), fill="#373737")
    draw.line([(x-1, y+16), (x+16, y+16), (x+16, y-1)], fill="#ffffff")
    draw.rectangle((x, y, x+15, y+15), fill="#8b8b8b")


positions = [(12, 40), (60, 30), (78, 30), (96, 30), (78, 64), (172, 30), (158, 64), (194, 64)]
for xy in positions:
    slot(d, *xy)
for row in range(3):
    for col in range(9):
        slot(d, 34+18*col, 130+18*row)
for col in range(9):
    slot(d, 34+18*col, 188)

# Empty and filled arrows use identical silhouettes so the Screen clips the
# active strip horizontally from x=0,y=220. The latter is outside the panel.
arrow = [(0, 5), (16, 5), (16, 1), (23, 8), (16, 15), (16, 11), (0, 11)]
d.polygon([(124+x, 33+y) for x, y in arrow], fill="#777777")
d.line([(124, 37), (140, 37), (140, 33)], fill="#555555")
d.polygon([(x, 220+y) for x, y in arrow], fill="#e1dfb0")
d.line([(0, 224), (16, 224), (16, 221)], fill="#fff0c8")
(ASSETS / "textures/gui").mkdir(parents=True, exist_ok=True)
ui.save(ASSETS / "textures/gui/simple_processing_table.png")

# Offline preview only: checkerboard makes transparent pixels obvious.
font_path = Path("C:/Windows/Fonts/msyh.ttc")
font = ImageFont.truetype(str(font_path), 20) if font_path.exists() else ImageFont.load_default()
small = ImageFont.truetype(str(font_path), 12) if font_path.exists() else ImageFont.load_default()
names = ["植物纤维", "木制托盘", "石制研钵", "陶土碗", "玻璃细管", "纸质滤片", "干燥剂包", "药草残渣", "药草布包"]
sheet = Image.new("RGB", (720, 810), "#282b2e")
sd = ImageDraw.Draw(sheet)
for n, ((key, im), label) in enumerate(zip(ITEMS.items(), names)):
    x, y = (n % 3)*240+24, (n//3)*270+20
    for yy in range(0, 192, 12):
        for xx in range(0, 192, 12):
            sd.rectangle((x+xx, y+yy, x+xx+11, y+yy+11), fill="#606266" if (xx//12+yy//12)%2 else "#818386")
    sheet.paste(im.resize((192, 192), Image.Resampling.NEAREST), (x, y), im.resize((192, 192), Image.Resampling.NEAREST))
    sd.text((x, y+201), label+" · 16×16", font=font, fill="#e4e1d8")
    sd.text((x, y+231), key, font=small, fill="#b8b7b3")
sheet.save(PREVIEW / "processing_items_16x.png")

ui_demo = ui.crop((0, 0, 230, 212))
ui_demo.paste(ITEMS["stone_mortar"], (12, 40), ITEMS["stone_mortar"])
ui_demo.paste(ITEMS["plant_fiber"], (172, 30), ITEMS["plant_fiber"])
ui_demo.paste(ITEMS["herbal_residue"], (194, 64), ITEMS["herbal_residue"])
ui_demo.paste(ui.crop((0, 220, 12, 237)), (124, 33), ui.crop((0, 220, 12, 237)))
# Preview annotations are deliberately added ONLY here, never to the game PNG.
dd = ImageDraw.Draw(ui_demo)
ui_font = ImageFont.truetype(str(font_path), 9) if font_path.exists() else ImageFont.load_default()
for x, y, label in [(8, 7, "简易加工台"), (9, 28, "工具"), (61, 18, "原料"), (77, 53, "容器"),
                    (172, 18, "成品"), (157, 53, "返还"), (192, 53, "副产物"), (8, 118, "背包"),
                    (9, 66, "耐久："), (9, 75, "127 / 128"), (124, 100, "20 / 40 刻"), (9, 90, "加工中")]:
    dd.text((x, y), label, font=ui_font, fill="#404040")
ui_demo.resize((920, 848), Image.Resampling.NEAREST).save(PREVIEW / "processing_ui.png")


def transformed_vertex(vertex, rotation):
    if not rotation:
        return vertex
    x, y, z = vertex
    ox, oy, oz = rotation["origin"]
    a = math.radians(rotation["angle"])
    assert rotation["axis"] == "z"
    return [ox+(x-ox)*math.cos(a)-(y-oy)*math.sin(a),
            oy+(x-ox)*math.sin(a)+(y-oy)*math.cos(a), z]


def project(v):
    x, y, z = v
    return (32+(x+z)*6, 176+(x-z)*3-y*6)


faces = []
for e in elements:
    x0, y0, z0 = e["from"]
    x1, y1, z1 = e["to"]
    verts = {
        "north": [[x1, y1, z0], [x0, y1, z0], [x0, y0, z0], [x1, y0, z0]],
        "east": [[x1, y1, z1], [x1, y1, z0], [x1, y0, z0], [x1, y0, z1]],
        "up": [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]],
    }
    for face, vv in verts.items():
        vv = [transformed_vertex(v, e.get("rotation")) for v in vv]
        faces.append(([v[0]-v[2]+v[1] for v in vv], [project(v) for v in vv], e["faces"][face], face))

render = Image.new("RGBA", (256, 256))
depth_buffer = [-float("inf")] * (256*256)
for depths, points, face, side in faces:
    # Rasterise the real native quads with a depth buffer. Centroid sorting
    # would incorrectly paint a broad tabletop over small cloth/leaf cuboids.
    u0, v0, u1, v1 = face["uv"]
    uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    tex = BLOCK[face["texture"][1:]]
    shade = {"up": 1.0, "north": .82, "east": .68}[side]
    for ia, ib, ic in [(0, 1, 2), (0, 2, 3)]:
        a, b, c = points[ia], points[ib], points[ic]
        den = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den) < 1e-6:
            continue
        for yy in range(max(0, math.floor(min(a[1], b[1], c[1]))), min(256, math.ceil(max(a[1], b[1], c[1])))):
            for xx in range(max(0, math.floor(min(a[0], b[0], c[0]))), min(256, math.ceil(max(a[0], b[0], c[0])))):
                px, py = xx+.5, yy+.5
                wa = ((b[1]-c[1])*(px-c[0])+(c[0]-b[0])*(py-c[1]))/den
                wb = ((c[1]-a[1])*(px-c[0])+(a[0]-c[0])*(py-c[1]))/den
                wc = 1-wa-wb
                if min(wa, wb, wc) < -1e-6:
                    continue
                depth = wa*depths[ia]+wb*depths[ib]+wc*depths[ic]
                ix = yy*256+xx
                if depth < depth_buffer[ix]:
                    continue
                u = wa*uvs[ia][0]+wb*uvs[ib][0]+wc*uvs[ic][0]
                v = wa*uvs[ia][1]+wb*uvs[ib][1]+wc*uvs[ic][1]
                color = tex.getpixel((min(15, max(0, int(u))), min(15, max(0, int(v)))))
                if color[3]:
                    depth_buffer[ix] = depth
                    render.putpixel((xx, yy), tuple(round(ch*shade) for ch in color[:3])+(255,))
model_bg = Image.new("RGBA", render.size, "#45494b")
model_bg.alpha_composite(render)
model_bg.resize((768, 768), Image.Resampling.NEAREST).convert("RGB").save(PREVIEW / "processing_model.png")
overview = Image.new("RGB", (1440, 930), "#282b2e")
overview.paste(sheet.resize((720, 810), Image.Resampling.NEAREST), (0, 45))
overview.paste(model_bg.resize((512, 512), Image.Resampling.NEAREST).convert("RGB"), (790, 35))
overview.paste(ui_demo.resize((690, 636), Image.Resampling.NEAREST).convert("RGB").resize((460, 424), Image.Resampling.NEAREST), (840, 500))
od = ImageDraw.Draw(overview)
od.text((24, 12), "新增物品：原生16×16透明像素", font=font, fill="#eee8d4")
od.text((790, 12), "原生方块模型离线投影预览", font=font, fill="#eee8d4")
overview.save(PREVIEW / "processing_overview.png")

save_json(SOURCE / "palette_and_layout.json", {
    "items": list(ITEMS), "item_dimensions": [16, 16], "alpha_values": [0, 255],
    "block_materials": list(BLOCK), "model_elements": len(elements),
    "ui_canvas": [256, 256], "ui_logical_size": [230, 212], "menu_icon_positions": positions,
    "player_inventory_origin": [34, 130], "hotbar_y": 188,
    "empty_arrow": [124, 33, 24, 17], "progress_arrow_uv": [0, 220, 24, 17],
    "reference_files_viewed": ["D:/bb/png/livelihood_tab_concept.png", "D:/bb/png/herbal_workbench_concepts/ui.png", "D:/bb/png/herbal_workbench_concepts/model.png"],
    "preview_note": "Offline Pillow projection and annotations, not an in-game screenshot. Game UI texture contains no text."
})
print(f"Created 9 native 16x16 item textures, 7 native block textures, {len(elements)}-cuboid model, 256x256 GUI and previews.")
