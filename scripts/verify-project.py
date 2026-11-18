#!/usr/bin/env python3
"""Fast, dependency-free integrity checks, also executed before the Android build."""
import hashlib
import json
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parent.parent
catalog = (root / 'app/src/main/java/com/novalisten/app/ui/theme/ThemeCatalog.kt').read_text()
spec = (root / 'docs/conception/21-systeme-de-themes-visuels.md').read_text()
palettes = re.findall(r'\*\*Palette :\*\* ([^\n]+)', spec)
expected = [c for row in palettes for c in re.findall(r'#[0-9A-F]{6}', row)]
actual = re.findall(r'#[0-9A-F]{6}', catalog)
assert len(expected) == 120 and actual == expected, 'Theme palettes differ from the specification'
fonts = json.loads((root / 'app/src/main/assets/font-licenses/sources.json').read_text())
assert len(fonts) == 25
for font in fonts:
    path = root / 'app/src/main/res/font' / (font['family'] + '.ttf')
    assert hashlib.sha256(path.read_bytes()).hexdigest() == font['sha256']
    assert (root / font['licence']).is_file()
for path in (root / 'app/src').rglob('*.xml'):
    ET.parse(path)
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
assert 'android.permission.INTERNET' not in manifest
assert 'android:allowBackup="false"' in manifest
assert (root / 'gradle/wrapper/gradle-wrapper.jar').stat().st_size > 40000
assert not list((root / 'app/src/main').rglob('*.mp3'))
for path in (root / 'app/src').rglob('*.kt'):
    text = path.read_text()
    assert '\r' not in text
    assert not any(line.rstrip() != line for line in text.splitlines()), path
print('OK — 15 palettes / 120 exact tokens; 25 licensed font families; XML; offline manifest; source hygiene.')
