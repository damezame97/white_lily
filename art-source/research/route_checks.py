"""Inspect actual first-batch data and the reobfuscated JAR; not a runtime/UI test."""
from pathlib import Path
import json, hashlib, zipfile
ROOT=Path(__file__).resolve().parents[2];R=ROOT/'src/main/resources';B=ROOT/'backups/research-machine-v1-20261023/src/main/resources'
def read(p):return json.loads(p.read_text('utf-8'))
catalog=read(R/'data/white_lily/research/handbook.json');nodes={n['id']:n for n in catalog['nodes']}
assert len(nodes)==9 and len(catalog['categories'][0]['chapters'])==2
assert {n['id'] for n in nodes.values() if n['root_visible']}=={'white_lily:'+n for n in ('bitterleaf','yarrow','calming_flower','coarse_cloth')}
fine=nodes['white_lily:clean_fine_bandage'];assert fine['reveal_after']==['white_lily:simple_bandage']
assert set(fine['prerequisites'])=={'white_lily:simple_bandage','white_lily:yarrow_dressing'}
bindings={}
for n in nodes.values():
 for recipe in n['recipes']:
  assert recipe not in bindings;bindings[recipe]=n['id']
  p=R/f'data/white_lily/recipes/{recipe.split(":",1)[1]}.json';d=read(p)
  if d['type']=='white_lily:simple_processing':assert d['research']==n['id']
  else:assert d['type']=='white_lily:research_crafting'
assert len(bindings)==6
for name in ('simple_bandage','clean_fine_bandage_beeswax','clean_fine_bandage_honey'):
 relative=f'data/white_lily/recipes/{name}.json';old=read(B/relative);new=read(R/relative)
 old.pop('type');new.pop('type');assert old==new,relative
for name in ('bitterleaf_paste','yarrow_dressing'):
 relative=f'data/white_lily/recipes/processing/{name}.json';new=read(R/relative);new.pop('research');assert new==read(B/relative),relative
for name in ('calming_flower','calming_flower_tea'):
 old=next(n for n in read(B/'data/white_lily/research/handbook.json')['nodes'] if n['id']=='white_lily:'+name).copy();new=nodes['white_lily:'+name].copy()
 for key in ('x','y'):old.pop(key);new.pop(key)
 assert old==new,'Existing route ID, materials and history must remain stable'
for name in ('hemp','bitterleaf','yarrow','calming_flower'):assert read(R/f'data/white_lily/forge/biome_modifier/add_{name}.json')=={'type':'forge:none'}
for locale in ('zh_cn','en_us'):
 lang=read(R/f'assets/white_lily/lang/{locale}.json')
 for n in nodes.values():
  assert all(lang.get(k) for k in (n['name'],n['intro'],n['hint'],*n['archive']))
jar=ROOT/'build/libs/white_lily-0.1.2.jar'
paths=[p for p in R.rglob('*') if p.is_file() and (p.suffix in ('.json','.png'))]
with zipfile.ZipFile(jar) as z:
 for p in paths:assert z.read(p.relative_to(R).as_posix())==p.read_bytes(),p
record={'scope':'Static source/resource/JAR check, not multiplayer or UI testing','nodes':list(nodes),'recipe_bindings':bindings,'all_json_png_packaged_identically':len(paths),'existing_crafting_and_processing_numbers':'unchanged; compared to pre-task backup','old_calming_flower_route':'stable IDs, requirements and archives unchanged','worldgen':'all four disabled','jar':str(jar),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}
(Path(__file__).parent/'route_checks.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(record,ensure_ascii=False,indent=2))
