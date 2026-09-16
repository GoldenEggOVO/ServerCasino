"""Refresh legacy JSON and CE registration aliases after intentional asset edits."""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'craftengine/resources/casino'
ASSETS = PACK / 'resourcepack/assets'


def synchronize():
    for source in (ASSETS / 'casino').rglob('*.json'):
        destination = ASSETS / 'rimuri_mines' / source.relative_to(ASSETS / 'casino')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
    for source in (PACK / 'configuration').glob('*.yml'):
        if source.name.startswith('legacy-'):
            continue
        lines = source.read_text(encoding='utf-8').splitlines()
        text = '\n'.join(line.replace('casino:', 'rimuri_mines:', 1)
                         if line.startswith('  casino:') else line for line in lines)
        source.with_name('legacy-' + source.name).write_text(text + '\n', encoding='utf-8')


if __name__ == '__main__':
    synchronize()
