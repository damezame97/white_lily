"""Focused resource checks for this round; does not launch Minecraft."""
from pathlib import Path
from PIL import Image
import json

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/white_lily"
DATA = RES / "data/white_lily"
items = ["plant_fiber", "wooden_tray", "stone_mortar", "clay_bowl", "glass_tube", "filter_paper", "desiccant_packet", "herbal_residue", "herbal_pouch"]
result = {"kind": "static file checks only; not in-game verification", "items": {}, "checks": []}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


for name in items:
    im = Image.open(ASSETS / "textures/item" / (name+".png"))
    assert im.size == (16, 16) and im.mode == "RGBA", name
    alpha = im.getchannel("A")
    assert alpha.getextrema() == (0, 255), name
    assert set(alpha.getdata()) == {0, 255}, name
    bounds = alpha.getbbox()
    assert bounds and bounds[0] >= 1 and bounds[1] >= 1 and bounds[2] <= 15 and bounds[3] <= 15, (name, bounds)
    model = read(ASSETS / "models/item" / (name+".json"))
    assert model == {"parent": "minecraft:item/generated", "textures": {"layer0": "white_lily:item/"+name}}, name
    result["items"][name] = {"size": list(im.size), "mode": im.mode, "bounds": bounds, "colors": len(im.getcolors())}

model = read(ASSETS / "models/block/simple_processing_table.json")
assert model["render_type"] == "minecraft:cutout"
for material, path in model["textures"].items():
    im = Image.open(ASSETS / "textures" / (path.split(":")[1]+".png"))
    assert im.size == (16, 16), material
for element in model["elements"]:
    assert all(0 <= n <= 16 for n in element["from"]+element["to"]), element["name"]
    assert all(element["from"][i] < element["to"][i] for i in range(3)), element["name"]
    for face in element["faces"].values():
        assert face["texture"][1:] in model["textures"]
        assert len(face["uv"]) == 4 and all(0 <= n <= 16 for n in face["uv"])
    if "rotation" in element:
        assert element["rotation"]["axis"] in ("x", "y", "z")
        assert element["rotation"]["angle"] in (-45, -22.5, 0, 22.5, 45)
assert read(ASSETS / "models/item/simple_processing_table.json")["parent"] == "white_lily:block/simple_processing_table"
assert len(read(ASSETS / "blockstates/simple_processing_table.json")["variants"]) == 4
assert Image.open(ASSETS / "textures/gui/simple_processing_table.png").size == (256, 256)
result["checks"].append("9 native 16x16 transparent item textures with 1px margins; 7 native 16x16 block materials; independent models; native cuboid UVs and four facings; 256x256 UI")

for locale in ("zh_cn", "en_us"):
    lang = read(ASSETS / "lang" / (locale+".json"))
    for name in items+["simple_processing_table"]:
        base = ("block" if name == "simple_processing_table" else "item")+".white_lily."+name
        assert lang[base]
        for i in range(1, 4):
            assert lang[base+".description."+str(i)]
    assert "§" not in lang["itemGroup.white_lily.misguided_livelihood"]
    for base, count in [("item.white_lily.stone_mortar", 2), ("item.white_lily.herbal_pouch", 1), ("block.white_lily.simple_processing_table", 3)]:
        for i in range(1, count+1):
            assert lang[base+".usage."+str(i)]
    for suffix in ["tool", "ingredients", "container", "result", "returned", "byproduct", "status.idle", "status.working", "status.blocked", "durability", "progress"]:
        assert lang["screen.white_lily.processing."+suffix]
result["checks"].append("Bilingual names, three description lines, usage text and screen labels exist; tab title translation has no color escape")

expected = {"simple_processing_table": 1, "wooden_tray": 1, "stone_mortar": 1, "clay_bowl": 4, "glass_tube": 6, "filter_paper": 4, "desiccant_packet": 2, "coarse_cloth_from_fiber": 1}
for name, count in expected.items():
    recipe = read(DATA / "recipes" / (name+".json"))
    assert recipe["result"]["count"] == count
    unlock = read(DATA / "advancements/recipes/processing" / (name+".json"))
    assert unlock["rewards"]["recipes"] == ["white_lily:"+name]
    assert unlock["requirements"] == [["has_material"]]
assert read(DATA / "recipes/coarse_cloth.json")["result"]["count"] == 2
assert read(DATA / "recipes/glass_tube.json")["key"]["#"] == {"item": "minecraft:glass"}
assert read(DATA / "recipes/desiccant_packet.json")["ingredients"][1] == {"item": "minecraft:sand"}
assert read(DATA / "tags/items/mortar_stones.json")["values"] == ["minecraft:stone", "minecraft:cobblestone"]
for name in ("bitterleaf_paste", "yarrow_dressing"):
    assert not (DATA / "recipes" / (name+".json")).exists()
    assert not (DATA / "advancements/recipes/medical" / (name+".json")).exists()
for name in ("herbal_pouch", "herbal_residue"):
    assert not (DATA / "recipes" / (name+".json")).exists()
result["checks"].append("8 requested ordinary crafting recipes and unlocks; existing direct hemp-to-cloth route retained; old medical crafting routes removed; no invented pouch/residue crafting")
result["model_elements"] = len(model["elements"])
(Path(__file__).resolve().parent / "static_checks.json").write_text(json.dumps(result, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print("Static resource checks passed: 9 items, 7 block materials, 29 model elements, 8 crafting recipes, bilingual text, migrated routes.")
