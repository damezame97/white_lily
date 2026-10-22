"""Native pixel tiles for the approved wood / old-paper archive style. No reference pixels copied."""
from pathlib import Path
from PIL import Image, ImageDraw
import random, json

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/white_lily/textures/gui/research_archive.png'
im=Image.new('RGBA',(256,256)); d=ImageDraw.Draw(im); rng=random.Random(20261023)
wood=['#211a15','#2d2119','#3c2a1c','#503725','#6f4c30']
paper=['#e9d6ab','#ecdbb7','#e6d1a5','#e4cea0','#f0dfba']
for k in range(6):
    x=k*16; base=paper[0] if k==1 else wood[1]
    d.rectangle((x,0,x+15,15),fill=base)
    for y in range(1,15):
        for px in range(1,15):
            c=rng.choice(paper) if k==1 else wood[rng.choices(range(1,4),[6,3,1])[0]]
            if k==1 and rng.random()>.22:c=paper[0]
            d.point((x+px,y),fill=c)
    edge='#987c49' if k in (1,2) else '#23160f'
    d.rectangle((x,0,x+15,15),outline=edge)
    d.line((x+1,1,x+14,1),fill='#f4e5c3' if k==1 else '#8d6239')
    d.line((x+1,1,x+1,14),fill='#d3ba83' if k==1 else '#6b472b')
    d.line((x+2,14,x+14,14),fill='#c3a46c' if k==1 else '#100e0c')
    if k==2:d.rectangle((x+1,1,x+14,14),outline='#bf9a45')
    if k==4:d.rectangle((x+1,1,x+14,14),outline='#762d2b')
for state in range(4):
    x=state*32;y=24
    d.rectangle((x,y,x+31,y+17),fill=['#35251b','#553c26','#251a14','#39332c'][state],outline='#17110c')
    rim=['#98764b','#c7a65a','#8b663b','#6e6250'][state]
    d.rectangle((x+1,y+1,x+30,y+16),outline=rim)
    d.line((x+3,y+3,x+28,y+3),fill='#725034' if state!=3 else '#4c4337')
    d.line((x+3,y+14,x+28,y+14),fill='#20170f')
    for xx in (x+2,x+29):
        for yy in (y+2,y+15):d.point((xx,yy),fill='#d8b662' if state!=3 else '#76674a')
for state in range(5):
    x=state*32;y=48
    d.rectangle((x,y,x+27,y+27),fill='#cdb78c',outline='#4a3721')
    d.rectangle((x+1,y+1,x+26,y+26),outline=['#9b8154','#908778','#c4a64c','#547543','#792f2d'][state])
    d.rectangle((x+3,y+3,x+24,y+24),fill='#ebd8b0' if state!=1 else '#bdb7a3',outline='#bc9e69')
    for xx in (x+2,x+23):
        for yy in (y+2,y+23):
            d.rectangle((xx,yy,xx+2,yy+2),fill='#9a7737');d.point((xx,yy),fill='#e9ca73')
    if state==4:d.rectangle((x,y,x+27,y+27),outline='#702521',width=2)
# Brass corner, lock, check, research cross, expansion chevron, close.
d.polygon([(0,80),(9,80),(9,83),(4,83),(4,89),(0,89)],fill='#8d642b')
d.line((1,81,8,81),fill='#dfbd67');d.line((1,81,1,88),fill='#b18b43');d.point((2,82),fill='#f3db91')
for xx in (16,24,32,40,48):d.rectangle((xx,80,xx+7,87),fill=(0,0,0,0))
d.rectangle((17,83,22,87),fill='#615e50');d.line((18,83,18,81,21,81,21,83),fill='#615e50',width=1);d.point((20,85),fill='#e9d6ab')
d.line((24,84,27,87,31,81),fill='#3f6b36',width=2)
d.line((33,84,38,84),fill='#a07821',width=2);d.line((35,81,35,87),fill='#a07821',width=2)
d.polygon([(41,82),(46,82),(43,85)],fill='#c6ad71')
d.line((49,81,54,86),fill='#d5b771',width=2);d.line((54,81,49,86),fill='#d5b771',width=2)
# Five original tiny category glyphs; existing herb/tea item icons remain untouched.
d.line((4,109,11,98),fill='#6f893a',width=2);d.polygon([(4,105),(2,100),(6,99),(8,96),(12,96),(11,102),(7,106)],fill='#668735',outline='#334c22')
d.polygon([(18,103),(30,103),(28,110),(21,110)],fill='#777b74',outline='#383e39');d.line((25,102,29,97),fill='#a7aaa0',width=3)
d.polygon([(34,106),(37,98),(41,104),(44,96),(47,107),(41,111)],fill='#785480',outline='#422d4f')
d.rectangle((51,98,61,110),fill='#c5a56c',outline='#67472c');d.line((54,101,58,101),fill='#6b4e2f');d.line((54,104,59,104),fill='#6b4e2f');d.line((53,108,58,108),fill='#6b4e2f')
d.ellipse((66,97,77,110),outline='#a27e3b',width=2);d.rectangle((70,96,73,99),fill='#b5ad8c');d.point((71,96),fill='#d5d7be')
OUT.parent.mkdir(parents=True,exist_ok=True); im.save(OUT)
preview=im.resize((768,768),Image.Resampling.NEAREST)
preview.save(ROOT/'docs/previews/research_archive_tiles.png')
(Path(__file__).parent/'archive_palette.json').write_text(json.dumps({'wood':wood,'paper':paper,'atlas':[256,256],'reference':'docs/previews/handbook_archive_style_reference_v1.png','reference_is_not_packaged':True},indent=2)+'\n')
print('Wrote native 256x256 archive tiles and nearest-neighbor tile preview; handbook item unchanged.')
