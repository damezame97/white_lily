"""Draw the native 16x16 'Simple Iron Pickaxe' item texture.

The design starts from a copy of vanilla Minecraft 1.20.1's iron pickaxe
texture, then changes only a few pixels on a real 16x16 RGBA canvas:

  * iron tones are shifted slightly darker (still clearly iron, not stone)
  * the left pick point is squared off by one pixel (a deliberately dull edge)
  * two cord pixels are added where the head meets the wooden handle

No large image is drawn and downscaled. The final PNG is the literal 16x16
pixel buffer.
"""

from pathlib import Path
import struct

from PIL import Image

HERE = Path(__file__).resolve().parent
VANILLA = HERE / "reference" / "vanilla_iron_pickaxe.png"
DELIVERY = HERE.parent.parent / "简易铁镐_物品贴图"
PREVIEW_DIR = DELIVERY / "预览"
FORMAL = DELIVERY / "简易铁镐_16x16.png"
PREVIEW = PREVIEW_DIR / "简易铁镐_16x16_放大16倍预览_勿导入.png"

TRANSPARENT = (0, 0, 0, 0)
IRON_DEEP = (24, 24, 24, 255)      # vanilla deep outline
IRON_EDGE = (64, 64, 64, 255)      # vanilla top edge
IRON_MID = (176, 176, 176, 255)    # was 193: slightly darker forged face
IRON_LIGHT = (201, 201, 201, 255)  # was 216: softened highlight
IRON_HILITE = (226, 226, 226, 255) # was 255: no pure-white glint
W_DEEP = (40, 30, 11, 255)         # vanilla handle colors, unchanged
W_DARK = (73, 54, 21, 255)
W_MID = (104, 78, 30, 255)
W_LIGHT = (137, 103, 39, 255)
CORD_LIGHT = (196, 188, 167, 255)  # two-pixel hemp cord at the joint
CORD_DARK = (119, 108, 87, 255)

VANILLA_MAP = {
    (255, 255, 255, 255): IRON_HILITE,
    (216, 216, 216, 255): IRON_LIGHT,
    (193, 193, 193, 255): IRON_MID,
    (68, 68, 68, 255): IRON_EDGE,
    (24, 24, 24, 255): IRON_DEEP,
    (137, 103, 39, 255): W_LIGHT,
    (104, 78, 30, 255): W_MID,
    (73, 54, 21, 255): W_DARK,
    (40, 30, 11, 255): W_DEEP,
}

source = Image.open(VANILLA)
assert source.size == (16, 16), f"reference must be 16x16, got {source.size}"
source = source.convert("RGBA")

# Start a genuinely new native 16x16 RGBA canvas and copy each pixel over.
image = Image.new("RGBA", (16, 16), TRANSPARENT)
for y in range(16):
    for x in range(16):
        pixel = source.getpixel((x, y))
        if pixel[3] == 0:
            continue
        if pixel not in VANILLA_MAP:
            raise ValueError(f"unexpected vanilla color at {(x, y)}: {pixel}")
        image.putpixel((x, y), VANILLA_MAP[pixel])

# Blunt the left pick point by one pixel: the outline closes over a flat face.
image.putpixel((5, 2), IRON_EDGE)
image.putpixel((5, 3), IRON_MID)

# A two-pixel cord/knot at the head-to-handle joint.
image.putpixel((9, 5), CORD_LIGHT)
image.putpixel((10, 5), CORD_DARK)

# No anti-aliasing: every pixel is either fully transparent or fully opaque.
assert image.mode == "RGBA"
assert image.size == (16, 16)
alpha = image.getchannel("A").tobytes()
assert set(alpha) <= {0, 255}

DELIVERY.mkdir(exist_ok=True)
PREVIEW_DIR.mkdir(exist_ok=True)
image.save(FORMAL, format="PNG")

# Only for inspection: exact nearest-neighbour 16x enlargement, no filtering.
image.resize((256, 256), Image.Resampling.NEAREST).save(PREVIEW, format="PNG")

# Verify the PNG header itself says 16x16 RGBA (color type 6).
raw = FORMAL.read_bytes()
assert raw[:8] == b"\x89PNG\r\n\x1a\n"
length = struct.unpack(">I", raw[8:12])[0]
assert raw[12:16] == b"IHDR"
width, height, bit_depth, color_type = struct.unpack(">IIBB", raw[16:26])
print(f"formal: {FORMAL}")
print(f"size: {width}x{height}, bit_depth={bit_depth}, color_type={color_type} (6=RGBA)")
print(f"transparent pixels: {alpha.count(0)}")
print(f"preview: {PREVIEW} ({Image.open(PREVIEW).size[0]}x{Image.open(PREVIEW).size[1]})")
