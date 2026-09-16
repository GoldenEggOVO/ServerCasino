"""Allowlisted public source archive: no build output, local fonts or reports."""
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIRECTORIES = ('src', 'craftengine', 'tools', 'docs')
TOP_LEVEL = ('pom.xml', 'README.md', 'LICENSE', 'THIRD_PARTY.md', '.gitignore', '.gitattributes')
EXTENSIONS = {'.java', '.xml', '.yml', '.yaml', '.json', '.png', '.py', '.md'}


def source_files():
    candidates = [ROOT / name for name in TOP_LEVEL]
    for directory in DIRECTORIES:
        candidates.extend((ROOT / directory).rglob('*'))
    for path in sorted(candidates):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        if path.name == 'fonts.local.json' or 'fonts' in relative.parts or '__pycache__' in relative.parts:
            continue
        if len(relative.parts) == 1 or path.suffix in EXTENSIONS:
            yield path


def package(destination=None):
    destination = Path(destination) if destination else ROOT / 'target/server-casino-source.zip'
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_STORED) as archive:
        for path in source_files():
            info = zipfile.ZipInfo('server-casino/' + path.relative_to(ROOT).as_posix(), (2026, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())
    return destination


if __name__ == '__main__':
    print(package())
