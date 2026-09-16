from asset_fonts import load_font
"""Original low-entity cabinets for the free ServerCasino test machines."""
from pathlib import Path
import importlib.util
import json
import math
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('cabinet_builder', ROOT/'tools/build-casino-cabinets.py')
old = importlib.util.module_from_spec(spec)
spec.loader.exec_module(old)
ASSETS = old.ASSETS
FONT = 'bold'
REPORT = {}
MONEY_SEGMENTS = [0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,1,0,2,0,1]
MONEY_COLORS = ['#2a8066','#315b9b','#97485a','#ba9139']
FORTUNE_SEGMENTS = [-1,3,.1,.5,.25,5,.1,.25,2,.1,-1,3,.5,.1,.25,0,.5,.1,2,.25]
FORTUNE_COLORS = {-1:'#7450a0',0:'#394452',.1:'#368f91',.25:'#3979b1',.5:'#648f48',2:'#bd7440',3:'#b34e73',5:'#c6a644'}
DUCK_COLORS = ['#f1c83b','#e56b70','#62b1e5','#92ca71']
LABELS = {'slots':'LUCKY SLOTS','duck_race':'DUCK RACE',
          'wheel_of_fortune':'WHEEL OF FORTUNE',
          'money_wheel':'MONEY WHEEL','penguin_cross':'PENGUIN CROSS','keno':'KENO',
          'hilo':'HI LO','dragon_tower':'DRAGON TOWER'}
COLORS = {'slots':'#982738','duck_race':'#226f95',
          'wheel_of_fortune':'#6d3e99','money_wheel':'#28614c','penguin_cross':'#366e94',
          'keno':'#283557','hilo':'#774467','dragon_tower':'#8b422e'}

def centered(draw, box, text, size, fill):
    while size > 8:
        font=load_font(FONT,size)
        b=draw.textbbox((0,0),text,font=font)
        if b[2]-b[0] <= box[2]-box[0]-12: break
        size-=1
    x=(box[0]+box[2]-b[2]+b[0])/2-b[0]
    y=(box[1]+box[3]-b[3]+b[1])/2-b[1]
    draw.text((x,y),text,font=font,fill=fill)

def texture(name, label='', color='#31616b', button=False):
    palette=[color,'#b59a6e','#efce77','#111e28','#d8f5ef',color,'#638286','#355463']
    im=Image.new('RGBA',(512,512)); d=ImageDraw.Draw(im)
    for n,c in enumerate(palette):
        x=n%4*128;y=n//4*128
        d.rectangle((x,y,x+127,y+127),fill=c)
        d.line((x+2,y+2,x+125,y+2),fill='#829899',width=2)
    d.rounded_rectangle((2,386,509,509),radius=18,fill='#12232a',outline='#efce77',width=5)
    centered(d,(4,390,508,508),label,54,'#f9e2a3')
    if button:
        d.rectangle((0,256,511,511),fill=color)
        rgb=tuple(bytes.fromhex(color[1:]))
        ink='#f6edcf' if sum(rgb)/3<140 else '#152c29'
        centered(d,(0,261,512,511),label,82,'#11212a')
        centered(d,(0,256,512,506),label,82,ink)
    im.save(ASSETS/f'textures/item/showcase_{name}.png')

def emit(name, elements, model_texture=None, clipped=False):
    item='showcase_'+name
    model_texture=model_texture or item
    model={'ambientocclusion':False,'textures':{'atlas':f'casino:item/{model_texture}','particle':f'casino:item/{model_texture}'},
           'elements':elements if clipped else old.exposed_faces(elements)}
    (ASSETS/f'models/item/{item}.json').write_text(json.dumps(model,separators=(',',':')),encoding='utf-8')
    (ASSETS/f'items/{item}.json').write_text(json.dumps({'model':{'type':'minecraft:model','model':f'casino:item/{item}'}}),encoding='utf-8')
    bounds=[[min(e['from'][i] for e in elements)/4-2 for i in range(3)],
            [max(e['to'][i] for e in elements)/4-2 for i in range(3)]]
    REPORT[item]={'elements':len(model['elements']),'bounds':bounds,'entities':1}

def base(name):
    b=old.Body(name)
    b.feet(2.7,1.75,.85)
    b.box(-1.5,.84,-1.0,1.5,1.00,1.1,1)
    b.box(-1.42,1.0,-.92,1.42,1.05,1.03,0)
    b.box(-1.3,.39,1.03,1.3,.60,1.09,3,sign=True)
    return b

def cabinets():
    for kind,label in LABELS.items():
        texture(kind,label,COLORS[kind]);b=base(kind)
        if kind=='slots':
            b=old.Body(kind)
            b.box(-1.05,0,-.62,1.05,1.1,.55,0)
            b.box(-1.1,1.1,-.55,1.1,3.14,.34,0)
            b.box(-.91,1.16,.34,.91,2.62,.39,3)
            for x in [-1.1,.96]:b.box(x,1.10,.34,x+.14,3.14,.48,2)
            for x in [-.26,.24]:b.box(x,1.15,.39,x+.02,2.65,.43,1)
            for y in [1.15,1.64,2.12,2.60]:b.box(-.96,y,.39,.96,y+.025,.43,1)
            b.box(-.95,2.76,.35,.95,3.12,.45,1,sign=True)
            b.box(-.72,.88,.55,.72,1.04,.68,3)
        elif kind in ('wheel_of_fortune','money_wheel'):
            b=old.Body(kind)
            b.box(-.75,0,-.50,.75,.18,.60,3)
            b.box(-.22,.18,-.15,.22,2.02,.08,1)
            b.box(-1.16,3.04,-.10,1.16,3.30,.14,1,sign=True)
        elif kind=='duck_race':
            b=old.Body(kind)
            for z in [.45,-3.95]:
                for x in [-1.3,1.3]:b.box(x-.09,0,z-.09,x+.09,.84,z+.09,3)
            b.box(-1.5,.84,-4.85,1.5,1.0,1.1,1)
            b.box(-1.42,1.0,-4.77,1.42,1.05,1.03,0)
            b.box(-1.20,1.053,-4.30,1.20,1.057,.70,6)
            for x in [-1.2,-.6,0,.6,1.2]:b.box(x-.012,1.057,-4.30,x+.012,1.073,.70,2)
            for z in [-4.30,.68]:b.box(-1.2,1.057,z,1.2,1.074,z+.02,4)
            for x in [-.9,-.3,.3,.9]:
                b.box(x-.22,1.05,-4.72,x+.22,1.40,-4.36,1)
                b.box(x-.14,1.05,-4.355,x+.14,1.29,-4.345,3)
                for step in range(5):
                    half=.27-step*.045
                    b.box(x-half,1.40+step*.04,-4.76,x+half,1.44+step*.04,-4.32,2)
        elif kind=='penguin_cross':
            b=old.Body(kind);b.feet(2.7,1.75,.73)
            b.box(-1.5,.73,-1,1.5,.87,1.1,0)
            b.box(-1.38,.87,-.88,1.38,.9,.88,6)
            for x in [-1.45,1.28]:b.box(x,.87,-.94,x+.17,1.0,.98,4)
            for z in [-.93,.88]:b.box(-1.28,.87,z,1.28,.99,z+.08,4)
            for x,z in [(-1.0,-.7),(-.35,.65),(.5,-.65),(1.0,.67)]:
                b.box(x-.13,.9,z-.1,x+.13,1.02,z+.1,4)
                b.box(x-.08,1.02,z-.065,x+.06,1.10,z+.06,4)
        elif kind=='keno':
            b.box(-1.30,1.051,-.88,1.30,1.057,.84,3)
        elif kind=='hilo':
            # Panel centre (0,1.1,0), pitch -35: front edge is y=.568,z=.373.
            b=old.Body(kind);b.feet(2.7,1.0,.46)
            b.box(-1.5,.46,-.55,1.5,.52,.55,0)
            for x in [-1.38,1.26]:
                for step in range(24):
                    z0=-.36+step*.03;z1=z0+.03
                    top=1.1-z1/math.tan(math.radians(35))-.062
                    b.box(x,.52,z0,x+.12,max(.525,top),z1,1)
        elif kind=='dragon_tower':
            # Narrow strips trace the same cylindrical surface as the four tile columns.
            for strip in range(28):
                x0=-.70+strip*.05;x1=x0+.05;x=(x0+x1)/2
                front=.40+math.sqrt(1.65**2-x*x)-1.65-.065
                b.box(x0,1.06,front-.10,x1,3.04,front,3)
            for x in [-.83,.70]:b.box(x,1.04,.06,x+.13,3.40,.27,0)
            b.box(-.70,3.10,.18,.70,3.40,.23,0,sign=True)
        if kind=='keno':
            for element in b.elements:
                for key in ['from','to']:
                    element[key][1]=round(max(8,element[key][1]-1),5)
        emit(kind,b.elements)

def disc(name, sections, labels, colors, lettering=True):
    im=Image.new('RGBA',(512,512));d=ImageDraw.Draw(im)
    for n in range(sections):
        a=n*360/sections-90;b=(n+1)*360/sections-90
        d.pieslice((8,8,504,504),a,b,fill=colors[n%len(colors)],outline='#f6d283',width=2)
        theta=math.radians((a+b)/2);x=256+188*math.cos(theta);y=256+188*math.sin(theta)
        if lettering:centered(d,(x-35,y-25,x+35,y+25),str(labels[n]),25,'#ffffff')
    d.ellipse((223,223,289,289),fill='#dbb65a',outline='#fff6c7',width=4)
    im.save(ASSETS/f'textures/item/showcase_{name}.png')
    body=old.Body(name);radius=.9
    body.box(-radius,-radius,-.013,radius,radius,.013)
    body.elements[0]['faces']={face:{'texture':'#atlas','uv':[0,0,16,16]} for face in ['north','south']}
    emit(name,body.elements)

def accessories():
    # Runtime poses this continuous plane at (0,1.1,0), pitch -35 degrees.
    im=Image.new('RGBA',(512,256),'#272d3b');d=ImageDraw.Draw(im)
    d.rounded_rectangle((3,3,508,252),radius=18,outline='#8a7897',width=4)
    left=256-1.1/2.84*512;right=256+1.1/2.84*512
    for n in range(11):
        x=left+(right-left)*n/10
        d.line((x,142,x,151),fill='#c9bdce',width=2)
        centered(d,(x-20,158,x+20,179),str(n*10),15,'#ded3e3')
    im.save(ASSETS/'textures/item/showcase_hilo_panel.png')
    b=old.Body('hilo_panel');b.box(-1.42,-.65,-.035,1.42,.65,.035)
    for face in b.elements[0]['faces'].values():face['uv']=[.5,.5,.75,.75]
    b.elements[0]['faces']['south']['uv']=[0,0,16,16]
    emit('hilo_panel',b.elements)
    for action,label,color in [('play','PLAY','#72aa25'),('cash','CASH OUT','#dfac22'),
                               ('flip','HIGH / LOW','#769cca'),
                               ('step','STEP','#72aa25'),('select','SELECT','#67aab4'),
                               ('spin','SPIN','#df952d'),('reset','RESET','#605677'),
                               ('under','UNDER','#6656a6'),('over','OVER','#bf7443'),
                               *[(f'duck_{n}',str(n),DUCK_COLORS[n-1]) for n in range(1,5)],
                               *[(f'money_{n}',label,color) for n,(label,color) in enumerate(zip(['2x','3x','5x','10x'],['#2a8066','#315b9b','#97485a','#ba9139']))]]:
        name='button_'+action;texture(name,label,color,True)
        source=json.loads((ASSETS/'models/item/cabinet_button_play.json').read_text(encoding='utf-8'))
        emit(name,source['elements'],clipped=True)
    # Circular cap has a continuous front UV map across its stepped silhouette.
    texture('button_round_spin','SPIN','#df952d',True)
    b=old.Body('button_round_spin')
    for i in range(24):
        y0=i*.4/24;y1=(i+1)*.4/24
        half=math.sqrt(max(0,.2**2-((y0+y1)/2-.2)**2))
        b.box(-half,y0,0,half,y1,.16,1)
        b.elements[-1]['faces']['south']['uv']=[8-half*40,16-y1*20,8+half*40,16-y0*20]
    emit('button_round_spin',b.elements)
    texture('pointer','','#efce77')
    b=old.Body('pointer')
    for i in range(16):
        y0=-.16+i*.02;y1=y0+.02;half=.008+i*.007
        b.box(-half,y0,-.035,half,y1,.035,2)
    emit('pointer',b.elements)
    for name,color in [('tile','#423951'),('tile_selected','#d88831')]:
        texture(name,'',color)
        im=Image.open(ASSETS/f'textures/item/showcase_{name}.png');d=ImageDraw.Draw(im)
        d.rectangle((0,256,511,511),fill=(0,0,0,0))
        d.rounded_rectangle((4,260,507,507),radius=55,fill=color,outline='#a49ba8',width=8)
        im.save(ASSETS/f'textures/item/showcase_{name}.png')
        b=old.Body(name)
        # Round the full solid cap silhouette, including its side walls.
        for strip in range(24):
            y0=-.12+strip*.01;y1=y0+.01
            edge=max(0,abs((y0+y1)/2)-.08)
            half=.08+math.sqrt(max(0,.04**2-edge**2))
            b.box(-half,y0,-.035,half,y1,.035)
            b.elements[-1]['faces']['south']['uv']=[(.12-half)/.24*16,8+(.12-y1)/.24*8,
                                                        (.12+half)/.24*16,8+(.12-y0)/.24*8]
        emit(name,b.elements)
    im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im)
    d.ellipse((2,2,126,126),fill='#ef9e34',outline='#ffe0a0',width=7)
    im.save(ASSETS/'textures/item/showcase_slider.png')
    b=old.Body('slider');b.box(-.13,-.13,-.02,.13,.13,.02)
    b.elements[0]['faces']={face:{'texture':'#atlas','uv':[0,0,16,16]} for face in ['north','south']}
    emit('slider',b.elements)
    disc('wheel_fortune',20,FORTUNE_SEGMENTS,[FORTUNE_COLORS[n] for n in FORTUNE_SEGMENTS],False)
    disc('wheel_money',20,MONEY_SEGMENTS,[MONEY_COLORS[n] for n in MONEY_SEGMENTS],False)
    for name,color in [*[(f'duck_{n}',DUCK_COLORS[n-1]) for n in range(1,5)],('penguin','#172c3f')]:
        texture(name,'',color);b=old.Body(name)
        b.box(-.10,0,-.12,.10,.17,.12,0);b.box(-.085,.15,-.07,.085,.30,.09,0)
        b.box(-.09,.20,.085,.09,.24,.16,2)
        for x in [-.065,.042]:b.box(x,.25,.091,x+.023,.274,.097,3 if name.startswith('duck') else 4)
        if name=='penguin':b.box(-.066,.025,.121,.066,.15,.125,4)
        else:
            for e in b.elements:
                e['from'][2],e['to'][2]=16-e['to'][2],16-e['from'][2]
                e['faces']['north'],e['faces']['south']=e['faces']['south'],e['faces']['north']
        emit(name,b.elements)
    number_atlas=Image.new('RGBA',(512,512));d=ImageDraw.Draw(number_atlas)
    for n in range(1,41):
        x=(n-1)%8*64;y=(n-1)//8*64
        d.rounded_rectangle((x+1,y+1,x+62,y+62),radius=9,fill='#213747',outline='#c4ab65',width=2)
        centered(d,(x,y,x+64,y+64),str(n),28,'#f6edcf')
        b=old.Body('number');b.box(-.12,0,-.12,.12,.02,.12)
        for face in b.elements[0]['faces'].values():face['uv']=[x/32,y/32,(x+64)/32,(y+64)/32]
        emit('number_'+str(n),b.elements,'showcase_numbers')
    number_atlas.save(ASSETS/'textures/item/showcase_numbers.png')

if __name__=='__main__':
    for folder in ['models/item','items','textures/item']:(ASSETS/folder).mkdir(parents=True,exist_ok=True)
    for retired in ['poker','baccarat','street_craps','dice','duck','button_draw','button_next','button_previous','tile_safe','tile_trap',
                    'roulette','roulette_panel','roulette_disc',*[f'bet_{n}' for n in range(49)]]:
        for folder,suffix in [('models/item','json'),('items','json'),('textures/item','png')]:
            (ASSETS/f'{folder}/showcase_{retired}.{suffix}').unlink(missing_ok=True)
    cabinets();accessories()
    lines=['items:']
    for key in REPORT:
        lines += [f'  casino:{key}:','    material: paper','    data:',f'      item_name: <!i>{key.removeprefix("showcase_").replace("_"," ").upper()}',
                  '    model:','      type: minecraft:model',f'      path: casino:item/{key}']
    (old.PACK/'configuration/showcase.yml').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    (ROOT/'reports').mkdir(exist_ok=True)
    (ROOT/'reports/showcase-model-manifest.json').write_text(json.dumps(REPORT,indent=2),encoding='utf-8')
    print(f'Generated {len(REPORT)} original model/texture pairs')
