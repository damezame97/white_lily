"""Write this round's additive bilingual resources and ordinary crafting data.

Does not write registrations, machine recipes, gameplay classes or old assets.
Only the two explicitly migrated medical crafting recipes are removed.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
DATA = RES / "data/white_lily"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")


zh, en = {}, {}
texts = {
    "plant_fiber": ("植物纤维", "Plant Fiber",
        ["从茎秆中分离出的细丝，颜色淡黄，", "末端散开。看起来柔弱，", "捻在一起却很结实。"],
        ["Pale strands separated from plant stalks,", "their ends splayed loose. They look fragile,", "but become sturdy when twisted together."]),
    "wooden_tray": ("木制托盘", "Wooden Tray",
        ["边缘略高的浅木托盘，", "能将零碎材料拢在一处。", "木纹间留着淡淡的草药气味。"],
        ["A pale wooden tray with a raised rim", "that gathers loose materials in one place.", "A faint herbal scent lingers in its grain."]),
    "stone_mortar": ("石制研钵", "Stone Mortar",
        ["粗石凿成的小钵，配着一根短杵。", "钵底渐渐磨得光滑，", "缝隙里仍嵌着旧药渣。"],
        ["A rough stone bowl with a short pestle.", "Its base has worn smooth with use;", "old herbal residue remains in the cracks."]),
    "clay_bowl": ("陶土碗", "Clay Bowl",
        ["一只暗红色的粗陶碗，碗沿略有缺损。", "盛放药膏绰绰有余，", "拿取时留意边缘。"],
        ["A dark red earthenware bowl with a chipped rim.", "It has ample room for herbal paste;", "take care around the edge."]),
    "glass_tube": ("玻璃细管", "Glass Tubes",
        ["两根细长的浅色玻璃管，", "管壁薄而通透。适合引导少量液体，", "也经不起粗手粗脚。"],
        ["Two slender, pale glass tubes", "with thin, clear walls. Useful for guiding", "small amounts of liquid, but easily broken."]),
    "filter_paper": ("纸质滤片", "Filter Paper",
        ["压得薄而均匀的纸片，", "表面布着细小孔隙。", "药液穿过后，总会留下些什么。"],
        ["An evenly pressed, thin sheet of paper", "covered in tiny pores. When a preparation", "passes through, something always remains."]),
    "desiccant_packet": ("干燥剂包", "Desiccant Packet",
        ["系紧的小纸袋，角落印着一枚蓝色标记。", "常被放进材料包装里，", "帮助隔开潮气。"],
        ["A tied paper packet with a blue corner mark.", "Often tucked inside material wrappings", "to help keep dampness away."]),
    "herbal_residue": ("药草残渣", "Herbal Residue",
        ["研磨后留下的碎叶与短根，", "混着一点苦涩气味。有用的部分取走了，", "剩下的仍染着草绿。"],
        ["Leaf fragments and short roots left by grinding,", "with a slightly bitter scent. The useful part", "is gone, yet a herbal green still remains."]),
    "herbal_pouch": ("药草布包", "Herbal Pouch",
        ["一只用棕绳扎紧的米白布包，", "袋口露出两片绿叶。轻巧朴素，", "装着一路上攒下的零碎。"],
        ["An off-white cloth pouch tied with brown cord,", "two green leaves peeking from its mouth.", "Light and plain, carrying a journey's odds and ends."]),
    "simple_processing_table": ("简易加工台", "Simple Processing Table",
        ["一张安置着研钵与托盘的旧木桌。", "工具不多，足够把采来的草叶", "变成能用的东西。"],
        ["An old wooden table fitted with a mortar and tray.", "Its few tools are enough to turn", "gathered herbs into something useful."]),
}
for item_id, (name_zh, name_en, lines_zh, lines_en) in texts.items():
    key = ("block" if item_id == "simple_processing_table" else "item")+".white_lily."+item_id
    zh[key], en[key] = name_zh, name_en
    for i, (zz, ee) in enumerate(zip(lines_zh, lines_en), 1):
        zh[key+".description."+str(i)] = zz
        en[key+".description."+str(i)] = ee

usage = {
    "item.white_lily.stone_mortar": [
        ("放入简易加工台的工具槽。", "Place in the Simple Processing Table's tool slot."),
        ("每完成一批加工消耗1点耐久，最大耐久128。", "Each completed batch uses 1 durability; maximum 128.")],
    "item.white_lily.herbal_pouch": [
        ("普通材料，暂不提供储物功能。", "An ordinary material; currently provides no storage.")],
    "block.white_lily.simple_processing_table": [
        ("放入工具和所需材料后自动加工。", "Processes automatically with the required tool and materials."),
        ("输出空间不足时暂停加工。", "Pauses when output space is insufficient."),
        ("更换工具或取走必要材料会清空加工进度。", "Changing tools or removing required materials resets progress.")]
}
for base, lines in usage.items():
    for i, (zz, ee) in enumerate(lines, 1):
        zh[base+".usage."+str(i)], en[base+".usage."+str(i)] = zz, ee

zh["itemGroup.white_lily.misguided_livelihood"] = "歧途“生计”"
en["itemGroup.white_lily.misguided_livelihood"] = "Misguided Livelihood"
labels = {
    "tool": ("工具", "Tool"), "ingredients": ("原料", "Ingredients"),
    "container": ("容器", "Container"), "result": ("成品", "Result"),
    "returned": ("返还", "Return"), "byproduct": ("副产物", "Residue"),
    "status.idle": ("空闲", "Idle"), "status.working": ("加工中", "Processing"),
    "status.blocked": ("输出空间不足", "Output blocked"),
    "durability": ("耐久：%s / %s", "Durability: %s / %s"),
    "progress": ("%s / %s 刻", "%s / %s ticks")
}
for key, (zz, ee) in labels.items():
    zh["screen.white_lily.processing."+key], en["screen.white_lily.processing."+key] = zz, ee
for locale, additions in [("zh_cn", zh), ("en_us", en)]:
    path = RES / "assets/white_lily/lang" / (locale+".json")
    current = json.loads(path.read_text(encoding="utf-8-sig"))
    # Fail visibly if a future edit disagrees, rather than overwrite another
    # author's newly introduced text without review.
    for key, value in additions.items():
        if key in current and current[key] != value:
            raise ValueError("Conflicting existing translation: "+locale+":"+key)
        current[key] = value
    write(path, current)

wood_slabs = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "bamboo_mosaic", "crimson", "warped"]
write(DATA / "tags/items/wooden_tray_slabs.json", {"replace": False, "values": ["minecraft:"+x+"_slab" for x in wood_slabs]})
write(DATA / "tags/items/mortar_stones.json", {"replace": False, "values": ["minecraft:stone", "minecraft:cobblestone"]})


def item(name):
    return {"item": name if ":" in name else "white_lily:"+name}


def tag(name):
    return {"tag": name}


def shaped(name, pattern, key, count, unlock):
    recipe(name, {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
                  "result": {"item": "white_lily:"+name, "count": count}}, unlock)


def shapeless(name, ingredients, count, unlock, output=None):
    recipe(name, {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
                  "result": {"item": "white_lily:"+(output or name), "count": count}}, unlock)


def recipe(name, data, unlock):
    write(DATA / "recipes" / (name+".json"), data)
    condition = {"tag": unlock[1:]} if unlock.startswith("#") else {"items": [unlock]}
    write(DATA / "advancements/recipes/processing" / (name+".json"), {
        "parent": "minecraft:recipes/root", "criteria": {"has_material": {
            "trigger": "minecraft:inventory_changed", "conditions": {"items": [condition]}}},
        "requirements": [["has_material"]], "rewards": {"recipes": ["white_lily:"+name]}})


shaped("simple_processing_table", ["PTP", "P P", "S S"], {
    "P": tag("minecraft:planks"), "T": item("wooden_tray"), "S": item("minecraft:stick")}, 1, "white_lily:wooden_tray")
shaped("wooden_tray", ["###"], {"#": tag("white_lily:wooden_tray_slabs")}, 1, "#white_lily:wooden_tray_slabs")
shaped("stone_mortar", ["SIS", " S "], {"S": tag("white_lily:mortar_stones"), "I": item("minecraft:stick")}, 1, "#white_lily:mortar_stones")
# 经用户授权调整：原 V 形与原版花盆冲突，保持3红砖和4产量，改为水平一行。
shaped("clay_bowl", ["BBB"], {"B": item("minecraft:brick")}, 4, "minecraft:brick")
shaped("glass_tube", ["###"], {"#": item("minecraft:glass")}, 6, "minecraft:glass")
shapeless("filter_paper", [item("minecraft:paper"), item("plant_fiber")], 4, "white_lily:plant_fiber")
shapeless("desiccant_packet", [item("minecraft:paper"), item("minecraft:sand"), item("charcoal_powder")], 2, "white_lily:charcoal_powder")
shapeless("coarse_cloth_from_fiber", [item("plant_fiber"), item("plant_fiber")], 1, "white_lily:plant_fiber", "coarse_cloth")

for name in ("bitterleaf_paste", "yarrow_dressing"):
    for path in (DATA / "recipes" / (name+".json"), DATA / "advancements/recipes/medical" / (name+".json")):
        if path.exists():
            path.unlink()

write(DATA / "loot_tables/blocks/simple_processing_table.json", {
    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "white_lily:simple_processing_table"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
axe_path = RES / "data/minecraft/tags/blocks/mineable/axe.json"
axe = json.loads(axe_path.read_text(encoding="utf-8-sig")) if axe_path.exists() else {"replace": False, "values": []}
if "white_lily:simple_processing_table" not in axe["values"]:
    axe["values"].append("white_lily:simple_processing_table")
write(axe_path, axe)
print("Added 8 crafting recipes and unlocks, tags, table loot; migrated 2 old medical crafting recipes; added bilingual text.")
