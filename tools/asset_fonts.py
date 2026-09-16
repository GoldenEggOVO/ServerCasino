"""Explicit, portable font inputs for optional texture redrawing."""
import json
import os
from pathlib import Path
from PIL import ImageFont

ROOT = Path(__file__).resolve().parents[1]


def load_font(role, size):
    config = Path(os.environ.get('CASINO_FONT_CONFIG', ROOT / 'tools/fonts.local.json'))
    if not config.is_absolute():
        config = ROOT / config
    if not config.is_file():
        raise RuntimeError('Texture redrawing requires CASINO_FONT_CONFIG; see docs/resources.md. '
                           'Packaging committed assets needs no fonts.')
    entry = json.loads(config.read_text(encoding='utf-8'))[role]
    path = Path(entry)
    if not path.is_absolute():
        path = config.parent / path
    return ImageFont.truetype(str(path), size)
