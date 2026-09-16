"""Package committed assets deterministically; no font or Pillow dependency."""
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def package(destination=None):
    destination = Path(destination) if destination else ROOT / 'target/casino-craftengine.zip'
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_STORED) as archive:
        for file in sorted((ROOT / 'craftengine').rglob('*')):
            if file.is_file():
                info = zipfile.ZipInfo(file.relative_to(ROOT / 'craftengine').as_posix(), (2026, 1, 1, 0, 0, 0))
                info.external_attr = 0o100644 << 16
                archive.writestr(info, file.read_bytes())
    return destination


if __name__ == '__main__':
    print(package())
