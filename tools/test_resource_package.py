from pathlib import Path
import hashlib
import importlib.util
import json
import re
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'craftengine/resources/casino'
ASSETS = PACK / 'resourcepack/assets'


class ResourcePackageTest(unittest.TestCase):
    def test_approved_baseline_is_unchanged_except_namespace(self):
        manifest = json.loads((ROOT / 'tools/resource-baseline.json').read_text())
        for name, digest in manifest.items():
            with self.subTest(file=name):
                data = (ROOT / name).read_bytes()
                if name.endswith(('.json', '.yml')):
                    data = data.decode('utf-8').replace('\r\n', '\n').encode('utf-8')
                self.assertEqual(digest, hashlib.sha256(data).hexdigest())

    def assert_reference(self, value, folder, extension):
        if ':' not in value or value.startswith('#'):
            return
        namespace, path = value.split(':', 1)
        if namespace == 'minecraft':
            return
        self.assertIn(namespace, ('casino', 'rimuri_mines'))
        self.assertTrue((ASSETS / namespace / folder / (path + extension)).is_file(), value)

    def test_all_model_font_item_references_resolve(self):
        for path in ASSETS.rglob('*.json'):
            data = json.loads(path.read_text(encoding='utf-8'))
            for value in data.get('textures', {}).values():
                self.assert_reference(value, 'textures', '.png')
            if 'parent' in data:
                self.assert_reference(data['parent'], 'models', '.json')
            model = data.get('model', {})
            if isinstance(model, dict) and model.get('type') == 'minecraft:model':
                self.assert_reference(model['model'], 'models', '.json')
            for provider in data.get('providers', []):
                if provider['type'] == 'bitmap':
                    self.assert_reference(provider['file'], 'textures', '')
        for path in (PACK / 'configuration').glob('*.yml'):
            for reference in re.findall(r'^\s+path: (\S+)', path.read_text(encoding='utf-8'), re.M):
                self.assert_reference(reference, 'models', '.json')

    def test_all_legacy_json_ids_are_compatible(self):
        for path in (ASSETS / 'casino').rglob('*.json'):
            legacy = ASSETS / 'rimuri_mines' / path.relative_to(ASSETS / 'casino')
            self.assertEqual(json.loads(path.read_text(encoding='utf-8')),
                             json.loads(legacy.read_text(encoding='utf-8')))

    def test_packaging_is_reproducible(self):
        spec = importlib.util.spec_from_file_location('packager', ROOT / 'tools/package-resources.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temporary:
            first = module.package(Path(temporary) / 'first.zip').read_bytes()
            second = module.package(Path(temporary) / 'second.zip').read_bytes()
            self.assertEqual(first, second)

    def test_source_package_excludes_build_outputs_and_local_records(self):
        spec = importlib.util.spec_from_file_location('source_packager', ROOT / 'tools/package-source.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temporary:
            output = module.package(Path(temporary) / 'source.zip')
            with zipfile.ZipFile(output) as archive:
                names = archive.namelist()
            self.assertIn('server-casino/LICENSE', names)
            self.assertIn('server-casino/tools/showcase-model-baseline.json', names)
            for name in names:
                self.assertFalse(any(part in name.split('/') for part in
                                     ('target', 'reports', 'artwork', '__pycache__', 'fonts', '.git')))
                self.assertFalse(name.endswith(('.jar', '.ttf', '.ttc', '.otf', '.pyc', 'fonts.local.json')))


if __name__ == '__main__':
    unittest.main()
