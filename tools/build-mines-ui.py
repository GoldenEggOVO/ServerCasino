from asset_fonts import load_font
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json
root=Path(__file__).resolve().parents[1]
pack=root/'craftengine/resources/casino'
tex=pack/'resourcepack/assets/casino/textures/ui';tex.mkdir(parents=True,exist_ok=True)
fontdir=pack/'resourcepack/assets/casino/font';fontdir.mkdir(parents=True,exist_ok=True)
font=load_font('cjk',12)
small=load_font('cjk',9)
providers=[{'type':'space','advances':{'\uefff':-1}}]
parts={}
def register(name,img,code,slices=True):
 parts[name]=img
 for y in range(4 if slices else 1):
  strip=img.crop((0,y*9,img.width,y*9+9)) if slices else img
  file=f'{name}_{y}.png';strip.save(tex/file)
  providers.append({'type':'bitmap','file':'casino:ui/'+file,'height':9,'ascent':7,'chars':[chr(code+y)]})
def tile(kind):
 im=Image.new('RGBA',(36,36),'#0e202c');d=ImageDraw.Draw(im)
 d.rounded_rectangle((2,2,33,33),radius=5,fill='#192f3d')
 d.rounded_rectangle((2,2,33,30),radius=5,fill='#2c4657' if kind=='hidden' else '#193b39')
 if kind=='gem':
  d.polygon([(11,10),(25,10),(30,16),(18,29),(6,16)],fill='#37e6a2')
  d.polygon([(11,10),(18,16),(6,16)],fill='#a3ffe1');d.polygon([(18,16),(25,10),(30,16)],fill='#1fbf8d')
  d.polygon([(6,16),(18,16),(18,29)],fill='#22b882');d.polygon([(18,16),(30,16),(18,29)],fill='#119366')
 elif kind=='mine':
  d.rounded_rectangle((2,2,33,30),radius=5,fill='#512f3c')
  d.ellipse((10,12,27,29),fill='#f05c74');d.ellipse((12,14,17,19),fill='#ffb0b4')
  d.line([(20,12),(23,7),(28,7)],fill='#ffd57b',width=2);d.line([(29,3),(29,10)],fill='#ffe499');d.line([(26,6),(32,6)],fill='#ffe499')
 else:
  d.line((13,16,23,16),fill='#49697b',width=2);d.line((13,20,23,20),fill='#49697b',width=2)
 return im
for name,code in [('hidden',0xe000),('gem',0xe010),('mine',0xe020)]:register(name,tile(name),code)
def side(text,caption,color):
 im=Image.new('RGBA',(192,36),'#0e202c');d=ImageDraw.Draw(im)
 d.rounded_rectangle((3,2,177,32),radius=5,fill=color)
 for line,y,ft,fill in [(text,6,font,'#ffffff'),(caption,22,small,'#b5c9d4')]:
  box=d.textbbox((0,0),line,font=ft);d.text(((180-box[2])/2,y),line,font=ft,fill=fill,anchor='lt')
 return im
for name,code,text,caption,color in [
 ('settings',0xe100,'投注与雷数','1–100 金币  /  1–24 颗雷','#284253'),
 ('start',0xe110,'开始新一局','确认后才扣除金币','#176bce'),
 ('cash',0xe120,'领取奖励','见好就收 · 包含本金','#147c62'),
 ('practice',0xe130,'练习模式','点击切换为金币模式','#284253'),
 ('real',0xe140,'金币模式','点击切换为练习模式','#856128'),
 ('rules',0xe150,'玩法与概率','公开赔率 · 理论返还率 98%','#284253'),
 ('disabled',0xe160,'暂不可用','请先完成当前步骤','#1e3341')]:register(name,side(text,caption,color),code)
register('blank_left',Image.new('RGBA',(192,9),'#0e202c'),0xe170,False)
register('blank_right',Image.new('RGBA',(180,9),'#0e202c'),0xe180,False)
(fontdir/'ui.json').write_text(json.dumps({'providers':providers},ensure_ascii=False,indent=2),encoding='utf-8')
(pack/'pack.yml').write_text('namespace: casino\n',encoding='utf-8')
(root/'reports').mkdir(exist_ok=True)
preview=Image.new('RGBA',(372,216),'#0e202c')
for y,name in enumerate(['settings','start','cash','practice','rules']):preview.paste(parts[name],(0,18+y*36))
for i in range(25):preview.paste(parts['gem' if i in [6,7,12] else 'hidden'],(192+i%5*36,18+i//5*36))
preview.resize((1116,648)).save(root/'reports/mines-ui-preview.png')
print('Generated UI: 372x216, 42 bitmap glyph slices + spacing')
