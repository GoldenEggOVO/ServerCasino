"""Generate a rounded, low-entity-count Plinko ball in the existing CE pack."""
from pathlib import Path
import json, math, shutil
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
pack = root / 'craftengine/resources/casino'
assets = pack / 'resourcepack/assets/casino'
for folder in ['models/item', 'items', 'textures/item']:
    (assets / folder).mkdir(parents=True, exist_ok=True)

# Eight layers and eight depth strips approximate a sphere without extra entities.
elements = []
for yi in range(8):
    for zi in range(8):
        y, z = -7 + 2 * yi, -7 + 2 * zi
        remaining = 64 - y*y - z*z
        if remaining <= 0:
            continue
        half = math.sqrt(remaining)
        elements.append({'from': [round(8-half, 4), 2*yi, 2*zi],
                         'to': [round(8+half, 4), 2*yi+2, 2*zi+2],
                         'faces': {face: {'texture': '#ball', 'uv': [0, 0, 16, 16]}
                                   for face in ['up', 'down', 'north', 'south', 'east', 'west']}})
model = {'ambientocclusion': False, 'textures': {'ball': 'casino:item/plinko_ball',
         'particle': 'casino:item/plinko_ball'}, 'elements': elements,
         'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}
(assets/'models/item/plinko_ball.json').write_text(json.dumps(model, indent=2), encoding='utf-8')
(assets/'items/plinko_ball.json').write_text(json.dumps({'model': {'type': 'minecraft:model',
    'model': 'casino:item/plinko_ball'}}), encoding='utf-8')
texture = Image.new('RGBA', (32, 32))
for y in range(32):
    for x in range(32):
        shine = max(0, 1-math.hypot(x-9, y-8)/22)
        texture.putpixel((x,y), (int(115+140*shine), int(190+65*shine), 255, 255))
texture.save(assets/'textures/item/plinko_ball.png')
(pack/'configuration').mkdir(exist_ok=True)
(pack/'configuration/machine-ball.yml').write_text('''items:
  casino:plinko_ball:
    material: snowball
    data:
      item_name: <!i><aqua>弹珠
    model:
      type: minecraft:model
      path: casino:item/plinko_ball
''', encoding='utf-8')
assert len(elements) <= 64
print(json.dumps({'elements_per_model':len(elements), 'entities_per_ball':1, 'texture_size':[32,32]}))

def item(name, model):
    (assets/f'models/item/{name}.json').write_text(json.dumps(model,indent=2),encoding='utf-8')
    (assets/f'items/{name}.json').write_text(json.dumps({'model':{'type':'minecraft:model',
        'model':f'casino:item/{name}'}}),encoding='utf-8')

# Item models require textures stitched into the item atlas, not font-only images.
(assets/'textures/item/cards').mkdir(exist_ok=True)
for source in (assets/'textures/advanced').glob('card_*.png'):
    shutil.copy2(source,assets/'textures/item/cards'/source.name)
for card in range(53):
    item(f'card_{card}', {'ambientocclusion':False,
        'textures': {str(i):f'casino:item/cards/card_{card}_{i}' for i in range(4)},
        'elements':[{'from':[0,8+32/3-(i+1)*16/3,8], 'to':[16,8+32/3-i*16/3,8.02],
            'faces':{'south':{'texture':f'#{i}','uv':[0,0,16,16]},
                     'north':{'texture':f'#{i}','uv':[0,0,16,16]}}} for i in range(4)]})
for name,texture in [('mine_gem','casino:ui/icon_gem'),('mine_bomb','casino:ui/icon_bomb'),
                     ('mine_hidden','minecraft:block/polished_deepslate')]:
    item(name,{'textures':{'face':texture},'elements':[{'from':[0,0,8],'to':[16,16,8.02],
        'faces':{'south':{'texture':'#face','uv':[0,0,16,16]},'north':{'texture':'#face','uv':[0,0,16,16]}}}]})

spots={1:[(16,16)],2:[(8,8),(24,24)],3:[(8,8),(16,16),(24,24)],
       4:[(8,8),(24,8),(8,24),(24,24)],5:[(8,8),(24,8),(16,16),(8,24),(24,24)],
       6:[(8,8),(8,16),(8,24),(24,8),(24,16),(24,24)]}
for n,points in spots.items():
    face=Image.new('RGBA',(32,32),'#f4f0df');draw=ImageDraw.Draw(face)
    draw.rectangle((0,0,31,31),outline='#b4b2ab',width=2)
    for x,y in points:draw.ellipse((x-3,y-3,x+3,y+3),fill='#203748')
    face.save(assets/f'textures/item/die_{n}.png')
item('demo_die',{'textures':{str(n):f'casino:item/die_{n}' for n in spots},
    'elements':[{'from':[0,0,0],'to':[16,16,16],
        'faces':{face:{'texture':f'#{i+1}','uv':[0,0,16,16]}
            for i,face in enumerate(['south','east','up','down','west','north'])}}]})
print('Generated 53 card models, three Mines panels and one six-sided die.')
