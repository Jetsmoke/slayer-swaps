"""
Writes SLAYER_TELEPORTS.html, a filterable grid of every slayer task and boss task against each teleport type,
from src/main/resources/com/slayerteleportswap/slayer_locations.json.

    python3 tools/grid.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'src/main/resources/com/slayerteleportswap/slayer_locations.json')
OUT = os.path.join(ROOT, 'SLAYER_TELEPORTS.html')
TEMPLATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'grid_template.html')

data = json.load(open(DATA))
payload = json.dumps(data, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/')
html = open(TEMPLATE).read().replace('/*DATA*/null', payload)
open(OUT, 'w').write(html)
print('wrote', OUT)
