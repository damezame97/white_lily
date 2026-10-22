"""Inspect packaged research resources; this does not replace runtime GameTests or UI QA."""
from pathlib import Path
from PIL import Image
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
book = Image.open(RES / "assets/white_lily/textures/item/investigation_handbook.png")
assert book.size == (16, 16) and book.mode == "RGBA"
assert book.getextrema()[3] == (0, 255)
assert len(book.getcolors(256)) <= 24
atlas = Image.open(RES / "assets/white_lily/textures/gui/research_archive.png")
assert atlas.size == (256, 256)
assert atlas.mode == "RGBA"
model = json.loads((RES / "assets/white_lily/models/item/investigation_handbook.json").read_text("utf-8"))
assert model["parent"] == "minecraft:item/generated"
assert model["textures"]["layer0"] == "white_lily:item/investigation_handbook"
recipe = json.loads((RES / "data/white_lily/recipes/investigation_handbook.json").read_text("utf-8"))
assert recipe["type"] == "minecraft:crafting_shapeless"
assert {entry["item"] for entry in recipe["ingredients"]} == {"minecraft:book", "white_lily:blank_label", "white_lily:charcoal_powder"}
tea = json.loads((RES / "data/white_lily/recipes/calming_flower_tea.json").read_text("utf-8"))
assert tea["type"] == "white_lily:research_crafting"
assert sum(1 for entry in tea["ingredients"] if entry.get("item") == "white_lily:calming_flower") == 2
water = next(entry for entry in tea["ingredients"] if entry.get("type") == "forge:nbt")
assert water["item"] == "minecraft:potion" and "minecraft:water" in water["nbt"]
for plant in ("hemp", "bitterleaf", "yarrow", "calming_flower"):
    assert json.loads((RES / f"data/white_lily/forge/biome_modifier/add_{plant}.json").read_text("utf-8")) == {"type": "forge:none"}
lang_keys = {}
for locale in ("zh_cn", "en_us"):
    lang = json.loads((RES / f"assets/white_lily/lang/{locale}.json").read_text("utf-8"))
    keys = [key for key in lang if key.startswith(("research.white_lily.", "screen.white_lily.research.", "item.white_lily.investigation_handbook"))]
    assert all(lang[key] and "§" not in lang[key] for key in keys)
    lang_keys[locale] = sorted(keys)
assert lang_keys["zh_cn"] == lang_keys["en_us"]
nodes = json.loads((RES / "data/white_lily/research/handbook.json").read_text("utf-8"))
for node in nodes["nodes"]:
    for locale in ("zh_cn", "en_us"):
        lang = json.loads((RES / f"assets/white_lily/lang/{locale}.json").read_text("utf-8"))
        assert all(key in lang for key in [node["name"], node["intro"], node["hint"], *node["archive"]])

jar = ROOT / "build/libs/white_lily-0.1.2.jar"
resources = [path for path in RES.rglob("*") if path.is_file() and (
    "investigation_handbook" in str(path) or path.name in ("research_atlas.png", "research_archive.png")
    or "white_lily/research/" in path.as_posix()
    or path.name == "calming_flower_tea.json"
    or path.name == "white_lily.mixins.json"
    or path.parent.name == "biome_modifier" and path.name.startswith("add_")
    or path.parent.name == "lang" and path.name in ("zh_cn.json", "en_us.json"))]
with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    for path in resources:
        name = path.relative_to(RES).as_posix()
        assert z.read(name) == path.read_bytes(), name
    classes = [name for name in names if name.endswith(".class") and ("/research/" in name or name.endswith(("/investigation_handbook.class", "/InvestigationScreen.class", "/research_client.class", "/research_crafting_recipe.class")))]
    assert any("InvestigationScreen.class" in name for name in classes)
    for client_class in ("research_archive_button", "research_preferences", "research_tree_view"):
        assert f"com/damezame97/white_lily/client/{client_class}.class" in names
    refmap = json.loads(z.read("white_lily.refmap.json"))
    assert "Research" in json.dumps(refmap)
    assert not any(name.startswith(("art-source/", "docs/")) for name in names)
    assert not any("style_reference" in name for name in names)
    mixins = json.loads(z.read("white_lily.mixins.json"))
    hooks = [name for name in mixins["mixins"] if name.startswith("research.")]

record = {
    "scope": "Static resource/JAR inspection, not a game or multiplayer test",
    "item_png": {"size": book.size, "mode": book.mode, "palette_colors_including_transparency": len(book.getcolors(256)), "alpha_range": book.getextrema()[3]},
    "gui_atlas_size": atlas.size,
    "translated_keys_per_locale": len(lang_keys["zh_cn"]),
    "visible_config_nodes": [node["id"] for node in nodes["nodes"]],
    "jar": str(jar), "bytes": jar.stat().st_size, "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
    "resources_verified_byte_for_byte": [path.relative_to(RES).as_posix() for path in resources],
    "research_classes_packaged": classes, "independent_research_mixins": hooks,
    "plant_generation": "all four biome modifiers remain forge:none"
}
(Path(__file__).parent / "package_checks.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({key: value for key, value in record.items() if key not in ("research_classes_packaged", "resources_verified_byte_for_byte")}, ensure_ascii=False, indent=2))
