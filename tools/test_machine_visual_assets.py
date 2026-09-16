from pathlib import Path
import json,unittest,collections,importlib.util,math
from PIL import Image
spec=importlib.util.spec_from_file_location("showcase",Path(__file__).with_name("build-showcase-machines.py"))
showcase=importlib.util.module_from_spec(spec);spec.loader.exec_module(showcase)
A=Path(__file__).resolve().parents[1]/'craftengine/resources/casino/resourcepack/assets/casino'
class VisualAssetsTest(unittest.TestCase):
 def test_showcase_dynamic_piece_contracts(self):
  panel=json.loads((A/'models/item/showcase_hilo_panel.json').read_text(encoding='utf-8'))
  self.assertEqual([2.32,5.4,7.86],panel['elements'][0]['from'])
  self.assertEqual([13.68,10.6,8.14],panel['elements'][0]['to'])
  for name in ['tile','tile_selected']:
   model=json.loads((A/f'models/item/showcase_{name}.json').read_text(encoding='utf-8'))
   self.assertEqual([7.52,7.52,7.86],[min(e['from'][i] for e in model['elements']) for i in range(3)])
   self.assertEqual([8.48,8.48,8.14],[max(e['to'][i] for e in model['elements']) for i in range(3)])
  for name in ['spin','reset','under','over',*[f'duck_{n}' for n in range(1,5)],*[f'money_{n}' for n in range(4)]]:
   self.assertTrue((A/f'items/showcase_button_{name}.json').exists(),name)
 def test_penguin_route_and_wheel_silhouette_are_clear(self):
  for name in ['penguin_cross','wheel_of_fortune','money_wheel']:
   model=json.loads((A/f'models/item/showcase_{name}.json').read_text(encoding='utf-8'))
   for e in model['elements']:
    low=[v/4-2 for v in e['from']];high=[v/4-2 for v in e['to']]
    if name=='penguin_cross':
     if low[0]<1.1 and high[0]>-1.1 and low[2]<.15 and high[2]>-.15:
      self.assertLessEqual(high[1],.90001)
    elif low[1]<2.8 and high[1]>1.2:
     self.assertLessEqual(high[0]-low[0],.45)
 def test_disc_art_is_only_on_front_and_back(self):
  for name in ['wheel_fortune','wheel_money']:
   data=json.loads((A/f'models/item/showcase_{name}.json').read_text(encoding='utf-8'))
   for element in data['elements']:
    for face,properties in element['faces'].items():
     if face in ['up','down','east','west']:
      self.assertNotEqual([0,0,16,16],properties.get('uv'),(name,face))
 def test_showcase_buttons_keep_front_lettering_and_all_sides(self):
  for model in A.glob('models/item/showcase_button_*.json'):
   elements=json.loads(model.read_text(encoding='utf-8'))['elements']
   faces={face for element in elements for face in element['faces']}
   self.assertEqual({'up','down','north','south','east','west'},faces,model.name)
   if model.stem!='showcase_button_round_spin':
    self.assertTrue(any(e.get('faces',{}).get('south',{}).get('uv')==[0,8,16,16] for e in elements),model.name)
 def test_retired_models_are_removed(self):
  for name in ['poker','baccarat','street_craps','dice','duck','button_draw','button_next','button_previous','tile_safe','tile_trap','roulette','roulette_panel','roulette_disc',*[f'bet_{n}' for n in range(49)]]:
   for folder,ext in [('models/item','json'),('items','json'),('textures/item','png')]:
    self.assertFalse((A/f'{folder}/showcase_{name}.{ext}').exists())
 def test_roulette_registration_is_removed(self):
  config=(showcase.old.PACK/'configuration/showcase.yml').read_text(encoding='utf-8')
  self.assertNotIn('roulette',config)
  self.assertNotIn('showcase_bet_',config)
 def test_keno_tiles_have_rounded_solid_corners(self):
  for name in ['tile','tile_selected']:
   elements=json.loads((A/f'models/item/showcase_{name}.json').read_text(encoding='utf-8'))['elements']
   # A square corner has no geometry at any height, including the side walls.
   for e in elements:
    low=[v/4-2 for v in e['from']];high=[v/4-2 for v in e['to']]
    self.assertFalse(low[0]<=.115<=high[0] and low[1]<=.115<=high[1])
   top=[e for e in elements if 'south' in e['faces']]
   self.assertGreater(len(top),4)
   self.assertTrue(any(e['from'][0]<7.53 and e['to'][0]>8.47 for e in top))
   self.assertAlmostEqual(.035,max(e['to'][2]/4-2 for e in elements))
 def test_blackjack_screen_and_mines_symbols(self):
  model=json.loads((A/'models/item/cabinet_blackjack_screen.json').read_text(encoding='utf-8'))
  low=[min(e['from'][i] for e in model['elements'])/4-2 for i in range(3)]
  high=[max(e['to'][i] for e in model['elements'])/4-2 for i in range(3)]
  for actual,expected in zip(low,[-.675,1.05,-.855]):self.assertAlmostEqual(expected,actual)
  for actual,expected in zip(high,[.675,1.65,-.785]):self.assertAlmostEqual(expected,actual)
  self.assertTrue(any('south' in e['faces'] and abs(e['to'][2]/4-2+.785)<1e-6 for e in model['elements']))
  for name in ['minus','plus']:
   elements=json.loads((A/f'models/item/cabinet_button_{name}.json').read_text(encoding='utf-8'))['elements']
   self.assertTrue(any(e['faces'].get('south',{}).get('uv')==[0,8,16,16] for e in elements))
   self.assertTrue((A/f'items/cabinet_button_{name}.json').exists())
  config=(showcase.old.PACK/'configuration/cabinets.yml').read_text(encoding='utf-8')
  for name in ['blackjack_screen','button_minus','button_plus']:self.assertIn('casino:cabinet_'+name+':',config)
 def test_dragon_background_tracks_cylindrical_tiles(self):
  elements=json.loads((A/'models/item/showcase_dragon_tower.json').read_text(encoding='utf-8'))['elements']
  for angle in [-18,-6,6,18]:
   x=1.65*math.sin(math.radians(angle));z=.40+1.65*(math.cos(math.radians(angle))-1)
   panels=[e for e in elements if 'south' in e['faces'] and e['from'][0]/4-2<=x<=e['to'][0]/4-2 and e['from'][1]/4-2<=2<=e['to'][1]/4-2]
   self.assertTrue(panels)
   front=max(e['to'][2]/4-2 for e in panels)
   self.assertGreater(z-front,.035)
   self.assertLess(z-front,.09)
 def test_hilo_support_stays_behind_tilted_panel(self):
  elements=json.loads((A/'models/item/showcase_hilo.json').read_text(encoding='utf-8'))['elements']
  supports=[]
  for e in elements:
   low=[v/4-2 for v in e['from']];high=[v/4-2 for v in e['to']]
   if high[1]>.53:
    supports.append(e)
    # At the highest-z corner, the front-panel normal distance must remain negative.
    normal=(high[1]-1.1)*math.sin(math.radians(35))+high[2]*math.cos(math.radians(35))
    self.assertLess(normal,-.035)
  self.assertTrue(supports)
 def test_wheel_colors_match_game_segment_order(self):
  for name,values,palette in [('wheel_money',showcase.MONEY_SEGMENTS,showcase.MONEY_COLORS),('wheel_fortune',showcase.FORTUNE_SEGMENTS,showcase.FORTUNE_COLORS)]:
   self.assertEqual(20,len(values))
   im=Image.open(A/f'textures/item/showcase_{name}.png')
   for n,value in enumerate(values):
    angle=math.radians((n+.5)*18-90)
    x=round(256+150*math.cos(angle));y=round(256+150*math.sin(angle))
    self.assertEqual(im.getpixel((x,y))[:3],tuple(bytes.fromhex(palette[value][1:])))
  self.assertEqual({0:10,1:6,2:3,3:1},dict(collections.Counter(showcase.MONEY_SEGMENTS)))
 def test_geometry_and_dynamic_panel_contracts(self):
  report=showcase.ROOT/'tools/showcase-model-baseline.json'
  report=json.loads(report.read_text())
  self.assertEqual([[-1.5,0.0,-4.85],[1.5,1.6,1.1]],report['showcase_duck_race']['bounds'])
  self.assertEqual(0,report['showcase_keno']['bounds'][0][1])
  self.assertAlmostEqual(.807,report['showcase_keno']['bounds'][1][1])
  self.assertLess(report['showcase_pointer']['bounds'][0][1],0)
  for n in range(1,5):
   self.assertTrue((A/f'items/showcase_duck_{n}.json').exists())
   self.assertAlmostEqual(-.16,report[f'showcase_duck_{n}']['bounds'][0][2])
  im=Image.open(A/'textures/item/showcase_hilo_panel.png')
  self.assertEqual((39,45,59),im.getpixel((200,128))[:3])
 def test_cards_use_stitched_item_textures(self):
  for i in range(53):
   data=json.loads((A/f'models/item/card_{i}.json').read_text(encoding='utf-8'))
   for value in data['textures'].values():
    self.assertTrue(value.startswith('casino:item/'),value)
    self.assertTrue((A/'textures'/f'{value.split(":")[1]}.png').exists())
 def test_no_overlapping_coplanar_cabinet_faces(self):
  axes={'up':(1,1),'down':(1,0),'east':(0,1),'west':(0,0),'south':(2,1),'north':(2,0)}
  for model in [*A.glob('models/item/cabinet_*.json'),*A.glob('models/item/showcase_*.json')]:
   planes=collections.defaultdict(list)
   for e in json.loads(model.read_text(encoding='utf-8'))['elements']:
    for face in e['faces']:
     axis,side=axes[face];a,b=[v for v in range(3) if v!=axis]
     planes[(face,round(e['to' if side else 'from'][axis],5))].append((e['from'][a],e['from'][b],e['to'][a],e['to'][b]))
   for key,rects in planes.items():
    for i,r in enumerate(rects):
     for q in rects[i+1:]:
      self.assertFalse(min(r[2],q[2])-max(r[0],q[0])>1e-6 and min(r[3],q[3])-max(r[1],q[1])>1e-6,(model.name,key,r,q))
if __name__=='__main__':unittest.main()
