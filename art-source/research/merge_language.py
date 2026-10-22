"""Merge this feature's translated strings without reformatting existing language files."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMMON = {
    "zh_cn": {
        "item.white_lily.investigation_handbook": "调查手册",
        "item.white_lily.investigation_handbook.description.1": "把见过的记下来，",
        "item.white_lily.investigation_handbook.description.2": "把未知的",
        "item.white_lily.investigation_handbook.description.3": "留在下一页。",
        "item.white_lily.investigation_handbook.usage.1": "右键打开调查手册。",
        "item.white_lily.investigation_handbook.usage.2": "记录发现、研究异常，并解锁新的制作方法。",
        "research.white_lily.category.herbs_medical": "草药与医疗",
        "research.white_lily.category.materials_processing": "材料与加工",
        "research.white_lily.category.pollution_anomalies": "污染与异常",
        "research.white_lily.category.prescriptions": "处方研究",
        "research.white_lily.category.curiosities": "诡物档案",
        "research.white_lily.chapter.basic_herbs": "基础草药",
        "research.white_lily.unknown_prerequisite": "尚未发现的前置研究",
        "research.white_lily.node.calming_flower.name": "发现安神花",
        "research.white_lily.node.calming_flower.intro": "把花瓣的气味与短暂的安定一并记下。",
        "research.white_lily.node.calming_flower.hint": "获得一株安神花，记录它的性质。",
        "research.white_lily.node.calming_flower.archive.1": "使用前SAN≥5000时恢复10点；SAN＜5000时恢复5点。按使用前的值判断，不因跨过阈值追加恢复。",
        "research.white_lily.node.calming_flower.archive.2": "副作用：反胃Ⅰ，持续20秒。",
        "research.white_lily.node.calming_flower.archive.3": "不能用骨粉复制。当前植物自然生成已暂停，可人工种植已有药草。",
        "research.white_lily.node.calming_flower_tea.name": "研究安神花茶",
        "research.white_lily.node.calming_flower_tea.intro": "用一份花瓣与标签整理记录，研究如何将安神花制成花茶。研究提交材料与制作材料分别消耗。",
        "research.white_lily.node.calming_flower_tea.hint": "先完成安神花的发现记录。",
        "research.white_lily.node.calming_flower_tea.archive.1": "饮用前SAN≥5000时恢复30点；SAN＜5000时恢复15点。恢复遵守当前SAN上限。",
        "research.white_lily.node.calming_flower_tea.archive.2": "副作用：反胃Ⅰ，持续10秒。饮用后返还玻璃瓶。",
        "research.white_lily.node.calming_flower_tea.archive.3": "仅使用普通水瓶；其他药水、喷溅药水与滞留药水不适用。获得他人的花茶可以饮用，但不会替代个人研究。"
    },
    "en_us": {
        "item.white_lily.investigation_handbook": "Investigation Handbook",
        "item.white_lily.investigation_handbook.description.1": "Record what you have seen,",
        "item.white_lily.investigation_handbook.description.2": "and leave the unknown",
        "item.white_lily.investigation_handbook.description.3": "for the next page.",
        "item.white_lily.investigation_handbook.usage.1": "Right-click to open the handbook.",
        "item.white_lily.investigation_handbook.usage.2": "Record discoveries, investigate anomalies, and unlock crafting methods.",
        "research.white_lily.category.herbs_medical": "Herbs and Medicine",
        "research.white_lily.category.materials_processing": "Materials and Processing",
        "research.white_lily.category.pollution_anomalies": "Pollution and Anomalies",
        "research.white_lily.category.prescriptions": "Prescription Research",
        "research.white_lily.category.curiosities": "Curiosity Archives",
        "research.white_lily.chapter.basic_herbs": "Basic Herbs",
        "research.white_lily.unknown_prerequisite": "An undiscovered prerequisite",
        "research.white_lily.node.calming_flower.name": "Discover Calming Flower",
        "research.white_lily.node.calming_flower.intro": "Record the scent of the petals and the brief calm they bring.",
        "research.white_lily.node.calming_flower.hint": "Obtain a Calming Flower and record its properties.",
        "research.white_lily.node.calming_flower.archive.1": "Restores 10 SAN when SAN is at least 5000 before use, or 5 below 5000. Crossing the threshold does not grant additional restoration.",
        "research.white_lily.node.calming_flower.archive.2": "Side effect: Nausea I for 20 seconds.",
        "research.white_lily.node.calming_flower.archive.3": "Cannot be duplicated with bone meal. Natural plant generation is currently paused; existing herbs can be replanted.",
        "research.white_lily.node.calming_flower_tea.name": "Research Calming Flower Tea",
        "research.white_lily.node.calming_flower_tea.intro": "Use a flower and a label to organize your notes and study a tea preparation. Research materials and crafting ingredients are separate costs.",
        "research.white_lily.node.calming_flower_tea.hint": "Complete the Calming Flower discovery first.",
        "research.white_lily.node.calming_flower_tea.archive.1": "Restores 30 SAN when SAN is at least 5000 before drinking, or 15 below 5000. Restoration respects the current SAN limit.",
        "research.white_lily.node.calming_flower_tea.archive.2": "Side effect: Nausea I for 10 seconds. Returns a glass bottle after drinking.",
        "research.white_lily.node.calming_flower_tea.archive.3": "Requires a normal water bottle, not another potion, splash potion, or lingering potion. Gifted tea can be consumed but does not complete personal research."
    }
}

for locale, additions in COMMON.items():
    fragment = json.loads((Path(__file__).parent / f"lang_{locale}.json").read_text(encoding="utf-8"))
    additions = fragment | additions
    target = ROOT / f"src/main/resources/assets/white_lily/lang/{locale}.json"
    original = target.read_bytes().decode("utf-8")
    current = json.loads(original)
    pending = {key: value for key, value in additions.items() if key not in current}
    conflicts = [key for key, value in additions.items() if key in current and current[key] != value]
    if conflicts:
        raise ValueError(f"Existing translations differ; review before updating: {conflicts}")
    if pending:
        newline = "\r\n" if "\r\n" in original else "\n"
        extra = json.dumps(pending, ensure_ascii=False, indent=2)[2:-2].replace("\n", newline)
        content = original.rstrip().removesuffix("}").rstrip() + "," + newline + extra + newline + "}" + newline
        target.write_bytes(content.encode("utf-8"))
    print(f"{locale}: {len(pending)} translated entries added")
