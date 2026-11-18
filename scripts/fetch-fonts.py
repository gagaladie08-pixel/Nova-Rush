#!/usr/bin/env python3
"""Bundle the requested open font families from google/fonts, with upstream licences.
Requires the authenticated gh CLI. Never contacts an artist or music provider.
Not run at app startup; font files are packaged in the APK.
"""
import base64
import hashlib
import json
import pathlib
import subprocess

FAMILIES = 'orbitron rajdhani audiowide poppins bebasneue inter playfairdisplay montserrat baloo2 fredoka abrilfatface anton comfortaa quicksand dmserifdisplay syne unicaone righteous nunito bungee bowlbyonesc fraunces ubuntu cinzel cormorant'.split()
ROOT = pathlib.Path(__file__).resolve().parent.parent

def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', path]))

def main():
    version = api('repos/google/fonts/commits/main')['sha']
    entries = []
    for family in FAMILIES:
        directory = ('ufl/' if family == 'ubuntu' else 'ofl/') + family
        listing = api(f'repos/google/fonts/contents/{directory}?ref={version}')
        candidates = [f for f in listing if f['name'].endswith('.ttf') and 'italic' not in f['name'].lower()]
        chosen = next((f for f in candidates if f['name'].endswith('-Regular.ttf')), candidates[0])
        data = base64.b64decode(api(chosen['url'])['content'])
        target = ROOT / 'app/src/main/res/font' / (family + '.ttf')
        target.write_bytes(data)
        licence = next(f for f in listing if f['name'] == ('UFL.txt' if family == 'ubuntu' else 'OFL.txt'))
        lic_data = base64.b64decode(api(licence['url'])['content'])
        lic_target = ROOT / 'app/src/main/assets/font-licenses' / (family + '.txt')
        lic_target.parent.mkdir(parents=True, exist_ok=True)
        lic_target.write_bytes(lic_data)
        entries.append({'family': family, 'source': f'https://github.com/google/fonts/blob/{version}/{directory}/{chosen["name"]}', 'sha256': hashlib.sha256(data).hexdigest(), 'licence': lic_target.relative_to(ROOT).as_posix()})
        print(f'{family}: {len(data):,} bytes', flush=True)
    (ROOT / 'app/src/main/assets/font-licenses/sources.json').write_text(json.dumps(entries, indent=2) + '\n')

if __name__ == '__main__':
    main()
