from pathlib import Path
import zipfile

from PIL import Image

JAR = Path(r"C:\Users\John\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar")
OUT = Path(__file__).resolve().parent / "reference"
OUT.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(JAR) as archive:
    for name in ("iron_pickaxe", "wooden_pickaxe", "stone_pickaxe"):
        member = f"assets/minecraft/textures/item/{name}.png"
        target = OUT / f"vanilla_{name}.png"
        target.write_bytes(archive.read(member))
        image = Image.open(target).convert("RGBA")
        print(f"{name}: {image.size}")
        image.resize((320, 320), Image.Resampling.NEAREST).save(
            OUT / f"vanilla_{name}_x20.png"
        )
