from pathlib import Path
from collections import Counter
import hashlib
import struct

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
DELIVERY = ROOT / "简易铁镐_物品贴图"
FORMAL = DELIVERY / "简易铁镐_16x16.png"
PREVIEW = DELIVERY / "预览" / "简易铁镐_16x16_放大16倍预览_勿导入.png"

formal = Image.open(FORMAL).convert("RGBA")
preview = Image.open(PREVIEW).convert("RGBA")
with Image.open(FORMAL) as check_formal:
    check_formal.verify()
with Image.open(PREVIEW) as check_preview:
    check_preview.verify()
alpha_values = sorted(set(formal.getchannel("A").tobytes()))

unique_colors = Counter(formal.get_flattened_data())
print("formal path:", FORMAL)
print("formal size:", formal.size)
print("formal mode:", formal.mode)
print("alpha values:", alpha_values)
print("opaque pixels:", sum(count for color, count in unique_colors.items() if color[3] == 255))
print("transparent pixels:", unique_colors[(0, 0, 0, 0)])
print("unique RGBA colors:", len(unique_colors))
print("bbox of visible pixels:", formal.getchannel("A").getbbox())
print("non-transparent color list:")
for color, count in sorted(unique_colors.items(), key=lambda item: (-item[1], item[0])):
    if color[3]:
        print(f"  #{color[0]:02X}{color[1]:02X}{color[2]:02X}{color[3]:02X} x{count}")
print("preview size:", preview.size)
print("preview top-left pixel alpha:", preview.getpixel((0, 0))[3])
print("preview nearest-neighbour exact:", all(
    preview.getpixel((x * 16 + dx, y * 16 + dy)) == formal.getpixel((x, y))
    for y in range(16) for x in range(16) for dy in range(16) for dx in range(16)
))

raw = FORMAL.read_bytes()
assert raw[:8] == b"\x89PNG\r\n\x1a\n"
assert raw[12:16] == b"IHDR"
w, h, depth, color_type, *_ = struct.unpack(">IIBB", raw[16:26])
print("PNG IHDR:", w, "x", h, "bit_depth", depth, "color_type", color_type, "(6 = truecolour with alpha)")
print("sha256 formal:", hashlib.sha256(raw).hexdigest())
print("sha256 preview:", hashlib.sha256(PREVIEW.read_bytes()).hexdigest())
print("all edges transparent:", all(
    formal.getpixel((x, 0))[3] == 0 and formal.getpixel((x, 15))[3] == 0
    and formal.getpixel((0, y))[3] == 0 and formal.getpixel((15, y))[3] == 0
    for x in range(16) for y in range(16)
))
