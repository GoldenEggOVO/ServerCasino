"""Package generated artwork into Minecraft font textures (4x source resolution)."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageOps
import json
root=Path(__file__).resolve().parents[1]
pack=root/'craftengine/resources/casino/resourcepack/assets/casino'
tex=pack/'textures/ui';tex.mkdir(parents=True,exist_ok=True)
source=Image.open(root/'artwork/casino-icons-source.png').convert('RGBA')
icons=[]
for i,name in enumerate(['gem','bomb','dice','multiplier','cards','plinko']):
 x0=round(i%3*source.width/3);x1=round((i%3+1)*source.width/3)
 y0=round(i//3*source.height/2);y1=round((i//3+1)*source.height/2)
 cell=source.crop((x0,y0,x1,y1));cell=cell.crop((0,55,cell.width,cell.height-38))
 icon=Image.new('RGBA',(256,256),'#0e202c');fit=ImageOps.contain(cell,(248,248),Image.Resampling.LANCZOS);icon.alpha_composite(fit,((256-fit.width)//2,(256-fit.height)//2))
 icon.save(tex/f'icon_{name}.png');icons.append(icon)
font={'providers':[{'type':'bitmap','file':f'casino:ui/icon_{name}.png','height':24,'ascent':20,'chars':[chr(0xE200+i)]} for i,name in enumerate(['gem','bomb','dice','multiplier','cards','plinko'])]}
(pack/'font/icons.json').write_text(json.dumps(font,ensure_ascii=False,indent=2),encoding='utf-8')
# Preserve old glyph definitions for compatibility; replace only board tile pixels.
for name,index in [('hidden',None),('gem',0),('mine',1)]:
 tile=Image.new('RGBA',(144,144),'#0e202c');d=ImageDraw.Draw(tile)
 d.rounded_rectangle((6,6,137,137),radius=20,fill='#1b3344')
 d.rounded_rectangle((7,7,136,130),radius=18,fill='#29485c' if index is None else '#123044',outline='#44667a',width=2)
 if index is None:
  d.polygon([(72,43),(94,72),(72,101),(50,72)],fill='#446b82',outline='#6b94ad',width=2)
 else:
  icon=icons[index].resize((120,120),Image.Resampling.LANCZOS);tile.alpha_composite(icon,(12,12))
 for row in range(4):tile.crop((0,row*36,144,(row+1)*36)).save(tex/f'{name}_{row}.png')
preview=Image.new('RGBA',(720,720),'#0e202c')
for i in range(25):
 name='gem' if i in (6,7,12) else 'hidden'
 for row in range(4):preview.alpha_composite(Image.open(tex/f'{name}_{row}.png'),(i%5*144,i//5*144+row*36))
preview.save(root/'reports/mines-hd-board-preview.png')
print('HD textures and six icons packaged')
