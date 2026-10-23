"""White Lily handbook: direct logical-pixel drawing; no generated high-res source.

Run with Pillow. The item is drawn at 16 x 16 from its first pixel.
The atlas is a reusable 256 x 256 sheet, not a baked screenshot.
Previews are explicitly offline layout previews and are not client screenshots.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/white_lily"
OUT = ROOT / "docs/previews"
OUT.mkdir(parents=True, exist_ok=True)

PALETTE = {
    "outline": "#30271f", "cover_dark": "#493025", "cover": "#63432c", "cover_light": "#785237",
    "spine_dark": "#805a3b", "spine": "#a78054", "spine_light": "#b99a68",
    "paper_dark": "#aa9b76", "paper": "#d3c9a4", "paper_light": "#eee5c6",
    "label_dark": "#ad9454", "label": "#d4bd75", "label_light": "#e2ce8b",
    "leaf_dark": "#314d32", "leaf": "#536c3c", "bookmark_dark": "#54252b", "bookmark": "#83313b",
}
icon = Image.new("RGBA", (16, 16))
d = ImageDraw.Draw(icon)
d.polygon([(4, 1), (12, 1), (14, 3), (14, 13), (12, 14), (3, 14), (2, 12), (2, 4)], fill=PALETTE["outline"])
d.rectangle((4, 2, 11, 12), fill=PALETTE["cover"])
d.rectangle((5, 3, 11, 11), fill=PALETTE["cover_dark"])
d.rectangle((5, 3, 10, 10), fill=PALETTE["cover"])
d.line((5, 2, 11, 2), fill=PALETTE["cover_light"])
d.rectangle((3, 4, 4, 12), fill=PALETTE["spine"])
d.line((3, 5, 3, 11), fill=PALETTE["spine_light"])
d.point((4, 3), fill=PALETTE["spine_light"])
d.point((4, 12), fill=PALETTE["spine_dark"])
d.rectangle((12, 4, 13, 12), fill=PALETTE["paper"])
d.line((12, 4, 12, 11), fill=PALETTE["paper_light"])
for y in (6, 9, 12): d.point((13, y), fill=PALETTE["paper_dark"])
d.line((5, 13, 11, 13), fill=PALETTE["cover_dark"])
d.rectangle((6, 4, 10, 8), fill=PALETTE["label_dark"])
d.rectangle((6, 4, 9, 7), fill=PALETTE["label"])
d.line((6, 4, 9, 4), fill=PALETTE["label_light"])
for xy in ((7, 6), (8, 5), (8, 6), (9, 5)): d.point(xy, fill=PALETTE["leaf_dark"])
d.point((8, 6), fill=PALETTE["leaf"])
d.point((7, 7), fill=PALETTE["leaf_dark"])
d.rectangle((7, 13, 8, 14), fill=PALETTE["bookmark_dark"])
d.point((7, 14), fill=PALETTE["bookmark"])
d.point((7, 15), fill=PALETTE["bookmark_dark"])
(ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
icon.save(ASSETS / "textures/item/investigation_handbook.png")
(ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
(ASSETS / "models/item/investigation_handbook.json").write_text(json.dumps({
    "parent": "minecraft:item/generated", "textures": {"layer0": "white_lily:item/investigation_handbook"}
}, indent=2) + "\n", encoding="utf-8")

atlas = Image.new("RGBA", (256, 256))
a = ImageDraw.Draw(atlas)
panel_colors = [
    ("#252824", "#b6b8aa", "#686e62", "#444940"),
    ("#20231f", "#686e62", "#353a33", "#30352f"),
    ("#302f20", "#c6b276", "#746c4a", "#4c4e3b"),
    ("#202621", "#697567", "#354035", "#303730"),
]
for i, (outer, light, shade, center) in enumerate(panel_colors):
    x = i * 16
    a.rectangle((x, 0, x + 15, 15), fill=outer)
    a.rectangle((x + 1, 1, x + 14, 14), fill=light)
    a.line((x + 1, 14, x + 14, 14), fill=shade)
    a.line((x + 14, 1, x + 14, 14), fill=shade)
    a.rectangle((x + 2, 2, x + 13, 13), fill=center)
    a.point((x, 0), fill=(0, 0, 0, 0))
    a.point((x + 15, 15), fill=(0, 0, 0, 0))
for i, (edge, light, center) in enumerate([
    ("#40453f", "#818479", "#393e38"),
    ("#9d8141", "#d3b966", "#474332"),
    ("#3e6542", "#8daf7d", "#334a35"),
]):
    x = i * 32
    a.rectangle((x + 2, 32, x + 27, 61), fill=edge)
    a.rectangle((x, 34, x + 29, 59), fill=edge)
    a.rectangle((x + 2, 34, x + 27, 59), fill=light)
    a.rectangle((x + 4, 36, x + 25, 57), fill=center)
    a.line((x + 3, 60, x + 27, 60), fill="#242b24")
# Explicit independent markers: lock, check, ready plus. Selection uses a separate outline.
a.rectangle((1, 72, 7, 76), fill="#b3b4a8")
a.line((2, 71, 2, 69, 6, 69, 6, 71), fill="#b3b4a8", width=1)
a.point((4, 74), fill="#41483e")
for x, y in [(11, 72), (12, 73), (13, 74), (14, 73), (15, 72), (16, 71), (17, 70)]:
    a.rectangle((x, y, x + 1, y + 1), fill="#9fc68c")
a.rectangle((23, 69, 24, 76), fill="#e1c66e")
a.rectangle((20, 72, 27, 73), fill="#e1c66e")
# Button states retained as actual resources for future custom widgets; vanilla widgets already provide all states.
for i, colors in enumerate((panel_colors[0], panel_colors[2], ("#222521", "#53584f", "#343930", "#30342d"))):
    x = i * 32
    a.rectangle((x, 88, x + 29, 105), fill=colors[0])
    a.rectangle((x + 1, 89, x + 28, 104), fill=colors[1])
    a.rectangle((x + 2, 90, x + 27, 103), fill=colors[3])
atlas.save(ASSETS / "textures/gui/research_atlas.png")
(ROOT / "art-source/research/palette.json").write_text(json.dumps(PALETTE, indent=2) + "\n", encoding="utf-8")

def font(size):
    for path in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"):
        if Path(path).is_file(): return ImageFont.truetype(path, size)
    return ImageFont.load_default()

def fit_text(dr, text, x, y, color, width, f):
    line = ""
    for char in text:
        if char == "\n":
            dr.text((x, y), line, fill=color, font=f)
            line, y = "", y + 17
            continue
        if dr.textlength(line + char, font=f) > width:
            dr.text((x, y), line, fill=color, font=f)
            line, y = char, y + 17
        else: line += char
    if line: dr.text((x, y), line, fill=color, font=f)
    return y + 19

# Offline preview deliberately draws separate dynamic pieces rather than a usable screenshot background.
preview = Image.new("RGB", (1140, 680), "#151a16")
p = ImageDraw.Draw(preview)
p.text((22, 12), "调查手册 · 离线资源与布局预览（非游戏截图）", font=font(22), fill="#d8d7c7")
preview.paste(icon.resize((160, 160), Image.Resampling.NEAREST), (22, 65), icon.resize((160, 160), Image.Resampling.NEAREST))
p.text((22, 240), "正式物品：16 × 16 RGBA", font=font(16), fill="#aeaFA1")
p.text((22, 268), "逐像素绘制 / 有限色板", font=font(16), fill="#aeaFA1")
for box in [(215, 60, 1116, 637), (225, 100, 393, 595), (403, 100, 823, 595), (833, 100, 1106, 595)]:
    p.rectangle(box, fill="#32392f", outline="#8c9584", width=3)
p.text((235, 72), "调查手册", font=font(20), fill="#e5e3d6")
p.text((958, 72), "恢复视图  ×", font=font(16), fill="#c9bc8d")
for i, (label, c) in enumerate([("草药与医疗", "#ece7ce"), ("  基础草药", "#dfbf67"), ("材料与加工", "#868b7f"), ("污染与异常", "#868b7f"), ("处方研究", "#868b7f"), ("诡物档案", "#868b7f")]):
    p.text((235, 120 + i * 64), label, font=font(17), fill=c)
    if i >= 2: p.text((235, 143 + i * 64), "尚未开放", font=font(12), fill="#747d6d")
for x in range(410, 820, 24): p.line((x, 104, x, 590), fill="#3a4437")
for y in range(110, 595, 24): p.line((406, y, 819, y), fill="#3a4437")
p.line((505, 297, 712, 297), fill="#839a70", width=3)
def item_image(item):
    for loc in (f"textures/item/{item}.png", f"textures/block/{item}.png"):
        path = ASSETS / loc
        if path.exists(): return Image.open(path).convert("RGBA")
    return icon
for x, name, item, state in [(490, "安神花", "calming_flower", 2), (695, "安神花茶", "calming_flower_tea", 1)]:
    piece = atlas.crop((state * 32, 32, state * 32 + 30, 62)).resize((60, 60), Image.Resampling.NEAREST)
    preview.paste(piece, (x - 15, 267), piece)
    im = item_image(item).resize((32, 32), Image.Resampling.NEAREST)
    preview.paste(im, (x, 281), im)
    p.text((x - 16, 338), name, font=font(17), fill="#e3dec8")
    p.text((x - 8, 365), "✓ 完成" if state == 2 else "+ 可研究", font=font(14), fill="#9dbb84" if state == 2 else "#dcc16e")
p.text((851, 120), "安神花茶", font=font(20), fill="#ece5cf")
p.text((851, 151), "可研究", font=font(15), fill="#dfc26b")
yy = fit_text(p, "将安神花的性质记录成稳定的饮用方法。", 851, 192, "#cdd0c2", 235, font(15))
yy = fit_text(p, "研究前置：已发现安神花", 851, yy + 16, "#9bbb83", 235, font(15))
yy = fit_text(p, "研究提交材料（并非制作材料）", 851, yy + 16, "#d6bd6d", 235, font(15))
yy = fit_text(p, "安神花  1 / 1\n空白标签  1 / 1", 851, yy + 12, "#d6d7c7", 235, font(15))
fit_text(p, "完成研究后，可查看制作方法。", 851, yy + 18, "#ddc174", 235, font(15))
p.rectangle((851, 547, 1088, 579), fill="#4c533f", outline="#bbb18a", width=2)
p.text((921, 553), "完成研究", font=font(17), fill="#eee5c6")
p.text((233, 608), "锁定  |  可研究  |  已完成      拖动空白区域 · 滚轮缩放 · 详情滚动", font=font(14), fill="#a3af97")
preview.save(OUT / "research_handbook_preview.png")
icon.resize((384, 384), Image.Resampling.NEAREST).save(OUT / "research_handbook_item_24x.png")
print("Wrote true 16x16 item, 256x256 reusable GUI atlas, and labeled offline previews.")
