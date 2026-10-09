#!/usr/bin/env python3
"""fetch-mods.py - Fabric Loader (and its libraries) for 26.3 from Fabric's meta service into game/fabric, and the mods
(newest Fabric build for 26.3 on Modrinth, primary file, sha1-checked) into game/mods. Writes game/fabric/profile.json."""
import hashlib, json, os, urllib.request

MC, LOADER = '26.3', '0.19.5'
MODS = ['fabric-api', 'sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'entityculling', 'c2me-fabric', 'krypton']
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'game')

def get(url, path=None, sha1=None):
    if path and os.path.exists(path) and sha1 and hashlib.sha1(open(path, 'rb').read()).hexdigest() == sha1:
        return
    req = urllib.request.Request(url, headers={'User-Agent': 'etonedemid/mc-android-26'})
    data = urllib.request.urlopen(req, timeout=60).read()
    if sha1 and hashlib.sha1(data).hexdigest() != sha1: raise SystemExit('sha1 mismatch: ' + url)
    if not path: return data
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(data)

prof = json.loads(get(f'https://meta.fabricmc.net/v2/versions/loader/{MC}/{LOADER}/profile/json'))
os.makedirs(f'{OUT}/fabric', exist_ok=True)
json.dump(prof, open(f'{OUT}/fabric/profile.json', 'w'), indent=1)
for l in prof['libraries']:
    g, a, v = l['name'].split(':')[:3]
    rel = f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"
    get(l['url'].rstrip('/') + '/' + rel, f'{OUT}/fabric/{rel}', l.get('sha1'))
print(f"fabric loader {LOADER}: {len(prof['libraries'])} libraries, main {prof['mainClass']}")
for m in MODS:
    vs = json.loads(get(f'https://api.modrinth.com/v2/project/{m}/version?game_versions=%5B%22{MC}%22%5D&loaders=%5B%22fabric%22%5D'))
    f = next(x for x in vs[0]['files'] if x['primary'])
    get(f['url'], f"{OUT}/mods/{f['filename']}", f['hashes']['sha1'])
    print(f"{m}: {f['filename']}")
