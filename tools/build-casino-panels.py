from asset_fonts import load_font
from pathlib import Path
import json
from PIL import Image,ImageDraw,ImageFont,ImageOps
root=Path(__file__).resolve().parents[1]
pack=root/'craftengine/resources/casino/resourcepack/assets/casino'
tex=pack/'textures/advanced';tex.mkdir(parents=True,exist_ok=True)
providers=[{'type':'space','advances':{'\uefff':-1}}]
def sliced(img,name,code):
 for i in range(img.height//36):
  file=f'{name}_{i}.png';img.crop((0,i*36,img.width,(i+1)*36)).save(tex/file)
  providers.append({'type':'bitmap','file':'casino:advanced/'+file,'height':9,'ascent':7,'chars':[chr(code+i)]})
atlas=Image.open(root/'artwork/casino-panels-source.png').convert('RGBA')
for i,name in enumerate(['dice','blackjack','plinko','limbo','crash','mines']):
 cell=atlas.crop((i%3*512,i//3*512,(i%3+1)*512,(i//3+1)*512))
 # Technical crop/resampling of generated art; logical panel is 144 x 54.
 panel=Image.new('RGBA',(576,216),'#0e202c');art=ImageOps.fit(cell,(216,216),method=Image.Resampling.LANCZOS)
 panel.alpha_composite(art,(180,0));sliced(panel,name,0xE300+i*6)
 icon=cell.resize((64,64),Image.Resampling.LANCZOS);icon.save(tex/f'{name}_button.png')
 providers.append({'type':'bitmap','file':f'casino:advanced/{name}_button.png','height':12,'ascent':9,'chars':[chr(0xE380+i)]})
font=load_font('bold',32)
symbolfont=load_font('symbols',43)
for card in range(53):
 img=Image.new('RGBA',(108,144),'#0e202c');d=ImageDraw.Draw(img)
 d.rounded_rectangle((3,3,104,140),radius=10,fill='#f4f0df' if card<52 else '#174870',outline='#71b5c0',width=3)
 if card<52:
  color='#c7354e' if card//13 in (1,3) else '#162b3c'
  rank=['A','2','3','4','5','6','7','8','9','10','J','Q','K'][card%13]
  d.text((11,7),rank,font=font,fill=color)
  d.text((30,54),['♠','♥','♣','♦'][card//13],font=symbolfont,fill=color)
 else:
  d.polygon([(54,24),(88,72),(54,120),(20,72)],outline='#52ded0',width=4)
 sliced(img,f'card_{card}',0xE400+card*4)
for i,char in enumerate('0123456789.x'):
 img=Image.new('RGBA',(72,108),'#0e202c');d=ImageDraw.Draw(img)
 d.text((4,5),char,font=load_font('bold',90),fill='#55e2c6',stroke_width=1)
 sliced(img,f'number_{i}',0xE600+i*3)
for i in range(6):
 img=Image.new('RGBA',(36,36),'#0e202c');d=ImageDraw.Draw(img)
 if i in (1,2):d.ellipse((12,12,24,24) if i==1 else (5,5,31,31),fill='#809aaa' if i==1 else '#4df2bc')
 if i in (3,4,5):d.rounded_rectangle((0,12,35,25),radius=3,fill=['#367fa0','#b84764','#55e2c6'][i-3])
 sliced(img,f'cell_{i}',0xE700+i)
(pack/'font/advanced.json').write_text(json.dumps({'providers':providers},ensure_ascii=False,indent=2),encoding='utf-8')
print('Generated panel, card, number and control glyph assets:',len(providers))
