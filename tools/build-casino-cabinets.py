from asset_fonts import load_font
"""Build original single-display arcade cabinets; physical coordinates use blocks.

FIXED ItemDisplay scale 4 cancels JSON's 1/16 units and this builder's factor 4.
The model origin is bottom centre, front is +Z; all details share one body.
"""
from pathlib import Path
import json
import random
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'craftengine/resources/casino'
ASSETS = PACK / 'resourcepack/assets/casino'
FACES = ('up', 'down', 'north', 'south', 'east', 'west')
PALETTES = {
    'plinko': ['#761d30', '#db3444', '#ffcc62', '#30192a', '#ffe6a4', '#f28655', '#b0c8d4', '#963749'],
    'mines': ['#124b4c', '#23a59b', '#f6cf79', '#112f37', '#9bffe0', '#437474', '#bad9df', '#18716b'],
    'blackjack': ['#36251e', '#724832', '#d9b65d', '#12382e', '#54ba84', '#255b43', '#e6e1c8', '#986347'],
    'crash': ['#24274b', '#5659ae', '#f3c971', '#111e37', '#83ebfa', '#b756cc', '#bbd9ea', '#353e78'],
    'button': ['#3c3043', '#bd3546', '#e8bc65', '#261f33', '#fff0c4', '#ef6971', '#c9cad1', '#682f48'],
    'rocket': ['#e5e7e1', '#e95856', '#f6cb62', '#20344a', '#89effb', '#ff9645', '#b6bfcc', '#8297ac'],
}
for color, base, light in [('green','#69a815','#91d02e'),('yellow','#df9b12','#ffc73c'),('red','#bf183c','#f13c54'),('purple','#7154ab','#a185d7')]:
    PALETTES['button_'+color]=['#15202b',base,'#324353','#101923','#ffffff',light,'#c9cad1',base]
for action,color in [('play','green'),('hit','red'),('stand','yellow'),('double','purple'),('cashout','yellow'),('minus','purple'),('plus','green')]:
    PALETTES['button_'+action]=PALETTES['button_'+color].copy()
PALETTES['control_panel']=PALETTES['blackjack'].copy()
PALETTES['blackjack_screen']=['#101114','#202126','#706650','#050607','#ffffff','#15171a','#202126','#101114']
LABELS = {'plinko': 'PLINKO', 'mines': 'MINE SWEEPER', 'blackjack': 'BLACKJACK', 'crash': 'CRASH', 'button': 'PLAY', 'rocket': ''}
LABELS.update({'button_play':'PLAY','button_hit':'HIT','button_stand':'STAND','button_double':'DOUBLE','button_cashout':'CASH OUT'})
LABELS.update({'button_minus':'-','button_plus':'+','blackjack_screen':'BLACKJACK SCREEN'})
for name in PALETTES:
    LABELS.setdefault(name,'')
report = {'coordinate_contract': {'source': '8 + physical * 4', 'display_context': 'FIXED', 'display_scale': 4, 'front': '+Z', 'origin': 'bottom centre'}, 'models': {}}
preview = {'description': 'Actual generated model geometry in physical block units; local preview, not an in-game screenshot', 'models': {}}


def atlas(name):
    rng = random.Random(1907)
    im = Image.new('RGB', (256, 256))
    draw = ImageDraw.Draw(im)
    for i, color in enumerate(PALETTES[name]):
        x, y = i % 4 * 64, i // 4 * 64
        draw.rectangle((x, y, x + 63, y + 63), fill=color)
        rgb = tuple(int(color[j:j + 2], 16) for j in (1, 3, 5))
        for _ in range(140):
            px, py = x + rng.randrange(64), y + rng.randrange(64)
            delta = rng.choice((-7, -4, 4, 7))
            draw.point((px, py), fill=tuple(max(0, min(255, c + delta)) for c in rgb))
        draw.line((x, y, x + 63, y), fill=tuple(min(255, c + 22) for c in rgb))
    # All lettering is authored here; no commercial game images or extracted assets.
    draw.rectangle((0, 192, 255, 255), fill=PALETTES[name][3])
    draw.rectangle((3, 195, 252, 252), outline=PALETTES[name][2], width=2)
    text = LABELS[name]
    font = ImageFont.load_default()
    layer = Image.new('RGBA', (256, 64))
    ld = ImageDraw.Draw(layer)
    bounds = ld.textbbox((0, 0), text, font=font)
    w, h = max(1, bounds[2]), max(1, bounds[3] - bounds[1])
    glyph = Image.new('RGBA', (w + 2, h + 2))
    ImageDraw.Draw(glyph).text((1, 1 - bounds[1]), text, font=font, fill=PALETTES[name][4])
    scale = min(3, 232 // (w + 2))
    glyph = glyph.resize((glyph.width * scale, glyph.height * scale), Image.Resampling.NEAREST)
    layer.alpha_composite(glyph, ((256 - glyph.width) // 2, (64 - glyph.height) // 2))
    im.paste(layer, (0, 192), layer)
    if name in ('button_play','button_hit','button_stand','button_double','button_cashout','button_minus','button_plus'):
        # Recessed lettering is baked into the cap, with a dark inset and fine lower highlight.
        draw.rectangle((0,128,255,255),fill=PALETTES[name][5])
        font=load_font('bold',40 if name=='button_cashout' else 48)
        bounds=draw.textbbox((0,0),text,font=font)
        x=(256-(bounds[2]-bounds[0]))/2-bounds[0];y=192-(bounds[3]-bounds[1])/2-bounds[1]
        draw.text((x,y+2),text,font=font,fill=PALETTES[name][4],stroke_width=0)
        draw.text((x,y),text,font=font,fill='#243125',stroke_width=0)
    im.save(ASSETS / f'textures/item/cabinet_{name}.png')


def exposed_faces(elements):
    """Clip hidden and coincident surface areas; later decorative boxes win ties."""
    result = []
    directions = {'east': (0, 1), 'west': (0, -1), 'up': (1, 1), 'down': (1, -1), 'south': (2, 1), 'north': (2, -1)}
    for index, element in enumerate(elements):
        for face, original in element['faces'].items():
            axis, sign = directions[face]
            a, b = [i for i in range(3) if i != axis]
            plane = element['to' if sign > 0 else 'from'][axis]
            rects = [(element['from'][a], element['from'][b], element['to'][a], element['to'][b])]
            for j, other in enumerate(elements):
                if j == index: continue
                lo, hi = other['from'][axis], other['to'][axis]
                # Cover a face if another solid continues outward, or a later surface occupies it.
                covered = lo <= plane + 1e-6 and hi >= plane - 1e-6 and (hi > plane + 1e-6 if sign > 0 else lo < plane - 1e-6)
                tied = j > index and abs((hi if sign > 0 else lo) - plane) < 1e-6
                if not (covered or tied): continue
                remaining = []
                for x0,y0,x1,y1 in rects:
                    u0,v0 = max(x0,other['from'][a]),max(y0,other['from'][b])
                    u1,v1 = min(x1,other['to'][a]),min(y1,other['to'][b])
                    if u1-u0 <= 1e-6 or v1-v0 <= 1e-6:
                        remaining.append((x0,y0,x1,y1));continue
                    remaining += [(x0,y0,u0,y1),(u1,y0,x1,y1),(u0,y0,u1,v0),(u0,v1,u1,y1)]
                rects = [r for r in remaining if r[2]-r[0]>1e-6 and r[3]-r[1]>1e-6]
            for x0,y0,x1,y1 in rects:
                lo,hi = element['from'].copy(),element['to'].copy()
                lo[a],lo[b],hi[a],hi[b] = x0,y0,x1,y1
                # Preserve explicit UVs; most clipped surfaces use a solid palette tile.
                result.append({'from':lo,'to':hi,'faces':{face:original}})
    return result


class Body:
    def __init__(self, name):
        self.name, self.elements, self.bounds = name, [], []

    def box(self, x0, y0, z0, x1, y1, z1, tile=0, sign=False):
        # Rear limits include the projecting front controls in the total depth.
        rear = {'plinko': -.43, 'crash': -.4}.get(self.name, -100)
        z0 = max(z0, rear)
        lo, hi = [x0, y0, z0], [x1, y1, z1]
        assert all(a < b for a, b in zip(lo, hi)), (lo, hi)
        self.bounds.append((lo, hi))
        uv = [tile % 4 * 4 + .1, tile // 4 * 4 + .1, tile % 4 * 4 + 3.9, tile // 4 * 4 + 3.9]
        faces = {f: {'texture': '#atlas', 'uv': uv} for f in FACES}
        if sign:
            faces['south'] = {'texture': '#atlas', 'uv': [0, 12, 16, 16]}
        self.elements.append({'from': [round(8 + c * 4, 5) for c in lo], 'to': [round(8 + c * 4, 5) for c in hi], 'faces': faces})

    def feet(self, width, depth, top=.3):
        for x in (-width / 2 + .23, width / 2 - .23):
            for z in (-depth / 2 + .15, depth / 2 - .15):
                self.box(x - .15, 0, z - .12, x + .15, top, z + .12, 3)

    def lamp(self, x, y, z, width=.09):
        self.box(x - width / 2, y - .04, z, x + width / 2, y + .04, z + .025, 4)

    def write(self):
        name = 'cabinet_' + self.name
        model = {'ambientocclusion': False, 'textures': {'atlas': f'casino:item/{name}', 'particle': f'casino:item/{name}'}, 'elements': exposed_faces(self.elements), 'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}
        path = ASSETS / f'models/item/{name}.json'
        path.write_text(json.dumps(model, separators=(',', ':')), encoding='utf-8')
        item = ASSETS / f'items/{name}.json'
        item.write_text(json.dumps({'model': {'type': 'minecraft:model', 'model': f'casino:item/{name}'}}), encoding='utf-8')
        lows = [min(v[0][i] for v in self.bounds) for i in range(3)]
        highs = [max(v[1][i] for v in self.bounds) for i in range(3)]
        assert all(-16 <= c <= 32 for e in self.elements for k in ('from', 'to') for c in e[k])
        assert highs[1] <= 5.5 and abs(lows[1]-(1.05 if self.name=='blackjack_screen' else 0))<1e-6
        assert json.loads(item.read_text())['model']['model'] == f'casino:item/{name}'
        expected = {'plinko': [4.9, 5.4, .9], 'mines': [3.3, 1.105, 3.5], 'blackjack': [3.6, .92, 2.1], 'control_panel': [2.7, .46, .07], 'crash': [2.8, 3.8, 1.15], 'button': [.6, .4, .18], 'rocket': [.22, .5, .138]}
        expected['blackjack_screen']=[1.35,.6,.07]
        assert all(abs(b - a - size) < 1e-6 for a, b, size in zip(lows, highs, expected['button' if self.name.startswith('button') else self.name]))
        if self.name == 'mines':
            for row in range(5):
                for col in range(5):
                    x,z=(col-2)*.53,-1.02+row*.51
                    assert not any(hi[1]>1.02 and lo[0]<x+.23 and hi[0]>x-.23 and lo[2]<z+.23 and hi[2]>z-.23 for lo,hi in self.bounds)
        if self.name == 'blackjack':
            assert not any(hi[1] > .92 for lo, hi in self.bounds)
        texture_path = ASSETS / f'textures/item/{name}.png'
        with Image.open(texture_path) as texture:
            assert texture.size == (256, 256)
            texture.verify()
        for e in self.elements:
            assert set(e['faces']) == set(FACES)
            assert all(f['texture'] == '#atlas' and all(0 <= v <= 16 for v in f['uv']) for f in e['faces'].values())
        report['models'][name] = {'elements': len(model['elements']), 'faces': sum(len(e['faces']) for e in model['elements']), 'physical_min': lows, 'physical_max': highs, 'physical_size': [round(b-a, 4) for a,b in zip(lows, highs)], 'asset_kb': round(sum(p.stat().st_size for p in (path, item, ASSETS / f'textures/item/{name}.png')) / 1024, 2), 'body_entities': 1}
        preview['models'][name] = {'texture': f'../craftengine/resources/casino/resourcepack/assets/casino/textures/item/{name}.png', 'boxes': [{'min': lo, 'max': hi, 'color': PALETTES[self.name][int(e['faces']['up']['uv'][1] // 4) * 4 + int(e['faces']['up']['uv'][0] // 4)], 'faces': e['faces']} for (lo, hi), e in zip(self.bounds, self.elements)]}


def plinko():
    b = Body('plinko')
    b.feet(4.9, .9)
    b.box(-2.45, .2, -.45, 2.45, .86, .45, 0)
    b.box(-2.43, .79, -.44, 2.43, 4.85, .26, 0)
    b.box(-2.31, 1.15, .26, 2.31, 4.72, .29, 3)
    for x in (-2.45, 2.29):
        b.box(x, .85, .23, x + .16, 4.94, .45, 1)
        b.box(x + .06, 1.25, .45, x + .1, 4.82, .46, 2)
    b.box(-2.45, 4.85, -.4, 2.45, 5.3, .45, 1)
    b.box(-2.25, 4.91, .45, 2.25, 5.26, .47, 2, True)
    b.box(-2.16, 5.3, -.3, 2.16, 5.4, .34, 2)
    for i in range(15):
        b.lamp(-2.1 + i * .3, 4.81, .36)
    for row in range(12):
        for col in range(row + 1):
            x, y = (col - row / 2) * .36, 4.5 - row * .27 - .13
            b.box(x - .052, y - .052, .29, x + .052, y + .052, .37, 7)
            b.box(x - .035, y - .035, .37, x + .035, y + .035, .455, 6)
    # Low tray lip and separators are below the falling ball route.
    b.box(-2.43, .86, .26, 2.43, .94, .45, 2)
    for i in range(14):
        x = (i - 6.5) * .36
        b.box(x - .022, .94, .29, x + .022, 1.14, .45, 2)
    b.box(-.65, .35, .451, .65, .67, .47, 3)
    b.write()


def mines():
    b=Body('mines')
    for x in (-1.32,1.32):
        for z in (-1.15,1.10):
            b.box(x-.13,0,z-.13,x+.13,.91,z+.13,0)
            b.box(x-.18,0,z-.18,x+.18,.10,z+.18,2)
    b.box(-1.65,.84,-1.55,1.65,1.0,1.42,0)
    b.box(-1.43,1.0,-1.32,1.43,1.02,1.30,3)
    for x in (-1.65,1.45):
        b.box(x,1.0,-1.55,x+.20,1.10,1.42,1)
        b.box(x+.07,1.10,-1.45,x+.12,1.105,1.30,2)
    b.box(-1.45,1.0,-1.55,1.45,1.10,-1.34,1)
    # A shallow control shelf in front of the horizontal grid.
    b.box(-1.5,.64,1.40,1.5,.71,1.95,0)
    b.box(-1.5,.71,1.40,1.5,.75,1.54,2)
    b.write()


def blackjack():
    b = Body('blackjack')
    for x in (-1.2, 1.2):
        b.box(x - .17, 0, -.48, x + .17, 0.77, .44, 0)
        b.box(x - .25, 0, -.57, x + .25, .1, .53, 2)
    # Five progressively narrower front steps evoke a semicircular casino table.
    strips = [(-1.05, -.6, 1.8), (-.6, .25, 1.8), (.25, .58, 1.68), (.58, .85, 1.43), (.85, 1.05, 1.05)]
    for z0, z1, half in strips:
        b.box(-half, 0.68, z0, half, 0.82, z1, 1)
        b.box(-half + .1, 0.82, z0, half - .1, 0.92, z1, 5)
        for x in (-half, half - .1):
            b.box(x, 0.82, z0, x + .1, 0.92, z1, 0)
    b.box(-1.8, 0.82, -1.05, 1.8, 0.92, -.91, 0)
    b.box(-1.05, 0.82, .96, 1.05, 0.92, 1.05, 0)
    b.box(-1, 0.71, 1.041, 1, 0.815, 1.05, 2, True)
    # Recessed chip channels and shoe stay clear of x +/-1.2, z -.32/+.35 cards.
    for i in range(5):
        x = -.48 + i * .18
        b.box(x, 0.825, -.85, x + .13, 0.915, -.67, (i % 3) + 2)
    b.box(1.25, 0.825, -.86, 1.6, 0.92, -.59, 3)
    b.box(1.28, 0.905, -.82, 1.54, 0.92, -.64, 6)
    b.write()


def crash():
    b = Body('crash')
    b.feet(2.8, 1.15)
    b.box(-1.3, .18, -.575, 1.3, .92, .575, 0)
    b.box(-1.22, .92, -.5, 1.22, 3.33, .36, 0)
    b.box(-1.04, 1.34, .36, 1.04, 3.2, .4, 3)
    for x in (-1.4, 1.23):
        b.box(x, .36, -.5, x + .17, 3.35, .55, 1)
        b.box(x + .06, 1.28, .55, x + .11, 3.26, .57, 4)
    b.box(-1.22, 1.19, .36, 1.22, 1.33, .58, 5)
    b.box(-1.22, 3.2, .36, 1.22, 3.33, .55, 5)
    b.box(-1.3, .91, .36, 1.3, .98, .75, 2)
    b.box(-.95, 3.33, -.25, -.75, 3.48, .15, 6)
    b.box(.75, 3.33, -.25, .95, 3.48, .15, 6)
    b.box(-1.4, 3.48, -.4, 1.4, 3.8, .45, 1)
    b.box(-1.22, 3.515, .45, 1.22, 3.765, .47, 2, True)
    # Tiny background stars are behind the independent animated rocket display.
    for x, y in [(-.86, 2.82), (-.15, 3.04), (.94, 1.62), (.35, 1.8), (.82, 2.28)]:
        b.lamp(x, y, .401, .025)
    b.box(-.72, .32, .575, .72, .61, .59, 3)
    for x in (-.5, -.3, -.1, .1, .3, .5):
        b.box(x, .37, .59, x + .035, .56, .602, 6)
    b.write()


def blackjack_screen():
    # Baked table coordinates; runtime item origin is (0,0,0), front is +Z.
    b=Body('blackjack_screen')
    b.box(-.675,1.05,-.855,.675,1.65,-.800,0)
    b.box(-.635,1.09,-.800,.635,1.61,-.785,3)
    b.write()


def accessories():
    b=Body("control_panel")
    b.box(-1.35,0,-.185,1.35,.46,-.115,3)
    b.write()
    for name in [name for name in PALETTES if name.startswith('button')]:
        button(name)
    rocket()


def button(name):
    b = Body(name)
    b.box(-.30,0,-.05,.30,.40,0,3)
    b.box(-.28,.02,0,.28,.38,.025,2)
    # Broad face with stepped bevels; one display per coloured key.
    b.box(-.24,.035,.025,.24,.365,.070,1)
    b.box(-.265,.065,.025,.265,.335,.070,1)
    b.box(-.225,.055,.070,.225,.345,.105,1)
    b.box(-.245,.080,.070,.245,.320,.105,1)
    b.box(-.21,.080,.105,.21,.320,.13,5)
    if name in ('button_play','button_hit','button_stand','button_double','button_cashout','button_minus','button_plus'):
        b.elements[-1]['faces']['south']['uv']=[0,8,16,16]
    b.write()


def rocket():
    b = Body('rocket')
    b.box(-.065, .1, -.06, .065, .37, .06, 0)
    b.box(-.052, .37, -.052, .052, .425, .052, 1)
    b.box(-.035, .425, -.035, .035, .47, .035, 1)
    b.box(-.017, .47, -.017, .017, .5, .017, 2)
    for x in (-.11, .065):
        b.box(x, .075, -.03, x + .045, .23, .035, 1)
    b.box(-.035, .245, .06, .035, .31, .075, 3)
    b.box(-.024, .256, .075, .024, .3, .078, 4)
    b.box(-.045, .04, -.035, .045, .1, .035, 5)
    b.box(-.02, 0, -.018, .02, .04, .018, 2)
    b.write()


if __name__ == '__main__':
    for folder in ('models/item', 'items', 'textures/item'):
        (ASSETS / folder).mkdir(parents=True, exist_ok=True)
    for name in PALETTES:
        atlas(name)
    plinko()
    mines()
    blackjack()
    blackjack_screen()
    crash()
    accessories()
    config = ['items:']
    for name in PALETTES:
        config += [f'  casino:cabinet_{name}:', '    material: paper', '    data:', f'      item_name: <!i>{LABELS[name] or "Rocket"}', '    model:', '      type: minecraft:model', f'      path: casino:item/cabinet_{name}']
    (PACK / 'configuration/cabinets.yml').write_text('\n'.join(config) + '\n', encoding='utf-8')
    report['alignment'] = {'plinko': {'peg_count': 78, 'rows': 12, 'peg_centres': '(col-row/2)*.36, 4.5-row*.27-.13, .43', 'slots': 13, 'slot_centres': '(i-6)*.36, 1.04, .43'}, 'mines': {'tile_centres': '(col-2)*.53, 1.29, -1.02+row*.51', 'clear_tile_size': [.46, .36], 'table_top': 1.02}, 'blackjack': {'table_y': .92, 'card_z': [-.32, .35], 'clear_card_x': [-1.2, 1.2]}, 'crash': {'screen_z': .4, 'rocket_x': -.55, 'rocket_bottom_y': [1.6, 2.65], 'rocket_z': .55}}
    report['validation'] = 'PASS: source bounds, item references, texture decoding and UVs, exact physical dimensions, bottom origins, height limits, 25 Mines tile clearances, Blackjack card plane; visual acceptance requires Minecraft'
    (ROOT / 'reports').mkdir(exist_ok=True)
    (ROOT / 'reports/four-machine-geometry.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (ROOT / 'reports/four-machine-geometry-preview.json').write_text(json.dumps(preview, separators=(',', ':')), encoding='utf-8')
    print(json.dumps(report, indent=2))
