"""
Adds where each teleport lands ("landing": {x, y, plane}) to slayer_locations.json, from the OSRS Wiki: the
{{TeleportLocationLine}}s on item and spell pages, the map in each row of teleport tables (fairy ring codes, slayer
ring, obelisks), or else the destination's own page. Each landing is then moved onto RuneLite's own world map icon
for that teleport when one is close (World Map plugin's TeleportLocationData and FairyRingLocation, BSD-2-Clause,
Copyright (c) 2018, Morgan Lewis and RuneLite contributors), so the plugin's ring sits around the icon.
Runs on the finished data file, so it needs only the wiki and RuneLite's source.

    cd <scratch dir with wiki/> && python3 landings.py path/to/slayer_locations.json
"""
import json, os, re, sys, unicodedata, urllib.request
from fetch import raw, UA
import spots

ITEM_PAGES = {'ring': 'Slayer ring', 'amulet_of_glory': 'Amulet of glory', 'burning_amulet': 'Burning amulet',
              'ring_of_dueling': 'Ring of dueling', 'maxcape': 'Max cape', 'portal': 'Construction cape',
              'obelisk': 'Wilderness Obelisk', 'fairy': 'Fairy ring'}
NUM = r'(\d{3,5})'


def norm(text):
    """Lower case words: "Rada's blessing" is "radas blessing", "Le-sabrè" is "le sabre"."""
    text = re.sub(r'\[\[(?:[^\]|]*\|)?([^\]]*)\]\]', r'\1', text)
    text = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode().replace("'", '')
    return re.sub(r'[^a-z0-9]+', ' ', text.lower()).strip()


def _point(text):
    m = (re.search(r'\bx\s*=\s*' + NUM + r'\s*\|\s*y\s*=\s*' + NUM, text)
         or re.search(r'x:\s*' + NUM + r'\s*,\s*y:\s*' + NUM, text)
         or re.search(r'\|\s*' + NUM + r'\s*,\s*' + NUM + r'\s*(?=[|}])', text))
    if not m:
        return None
    plane = re.search(r'\bplane\s*=\s*(\d)', text)
    return {'x': int(m.group(1)), 'y': int(m.group(2)), 'plane': int(plane.group(1)) if plane else 0}


def candidates(page):
    """(text around it, point) for each teleport line, and each table row with a map, on a page."""
    txt = raw(page)
    out = []
    for block in spots._templates(txt, 'TeleportLocationLine'):
        p = _point(block)
        if p:
            out.append((norm(block), p))
    # Table rows; the text before the first row separator is the page itself, kept last for items with one teleport
    rows = re.split(r'\n\|-', txt)
    for row in rows[1:] + rows[:1]:
        if '{{Map' in row or '{{map' in row:
            p = _point(row[row.lower().index('{{map'):])
            if p:
                out.append((norm(row) if row is not rows[0] else '', p))
    return out


def match(cands, value):
    v = norm(value)
    if not v:
        return None
    words = set(v.split())
    # "Level 44 Wilderness": the number has to be there
    numbers = {w for w in words if w.isdigit()}
    cands = [(text, p) for text, p in cands if numbers <= set(text.split())]
    for text, p in cands:
        if re.search(r'\b' + re.escape(v) + r'\b', text):
            return p
    # "Warrior's Guild" and "Warriors' Guild", "Dark Beasts" and "dark beast": most of the words
    best, score, length = None, 0, 0
    for text, p in cands:
        s = len(words & set(text.split()))
        # On a tie, the shorter text: a table row rather than the page's infobox
        if s > score or s == score and s > 0 and len(text) < length:
            best, score, length = p, s, len(text)
    return best if score >= max(1, len(words) - 1) and len(words) > 0 and score / len(words) >= 0.6 else None


def landing(route, items, masters):
    t, value = route['type'], route['value']
    if t == 'karamja_gloves':
        return masters.get('Duradel / Kuradal')
    if t == 'fairy':
        for text, p in candidates('Fairy ring'):
            if re.search(r'\b' + value.lower() + r'\b', text):
                return p
        return None
    if t == 'boat' and mooring(value):
        return mooring(value)
    if t == 'boat':
        # The island's mooring point, where the boat is: the docking point icon on the world map
        port = MOORINGS.get(value, value)
        for row in re.split(r'\n\|-', raw('Mooring point')):
            if re.search(r'\{\{ilinkt\|' + re.escape(port) + r'\s*[|}]', row):
                return _point(row[row.lower().index('{{map'):])
        return None
    if t == 'spell':
        cands = candidates(value)
        return cands[0][1] if cands else None
    if t == 'item':
        item = items.get(route.get('item'), {})
        pages = [item.get('label', '')] + [n[0].upper() + n[1:] for n in item.get('names', [])]
        for page in pages:
            page = re.sub(r'\s*/.*$', '', re.sub(r' \d/\d$', '', page)).strip()
            if not page:
                continue
            cands = candidates(page)
            p = match(cands, value) or (cands[0][1] if len(item.get('options', [])) <= 1 and cands else None)
            if p:
                return p
            # A one-click item with no map of its own: the place its page says it teleports to, like the hallowed
            # crystal shard's "teleport to the [[Hallowed Sepulchre]]"
            to = re.search(r'teleports?\b[^.]{0,60}?\bto (?:the )?\[\[([^\]|#]+)', raw(page))
            if len(item.get('options', [])) <= 1 and to:
                place = [p for text, p in candidates(to.group(1)) if not text]
                if place:
                    return place[0]
    elif t in ITEM_PAGES:
        p = match(candidates(ITEM_PAGES[t]), value)
        if p:
            return p
    # The place itself: the map in its infobox, not a pin for the way in
    place = [p for text, p in candidates(re.sub(r'\s*\(.*\)$', '', value)) if not text]
    return place[0] if place else None


# RuneLite's world map teleport icons, from the client version the plugin builds against
RUNELITE_TAG = 'runelite-parent-1.13.3'
RUNELITE_ICON_FILES = ['TeleportLocationData.java', 'FairyRingLocation.java']
# What names a route's item in RuneLite's icon text, by route type, and by catalog item where the label doesn't
TYPE_ITEMS = {'ring': 'slayer ring', 'amulet_of_glory': 'glory', 'burning_amulet': 'burning amulet',
              'ring_of_dueling': 'ring of dueling', 'portal': 'construction cape', 'obelisk': 'obelisk',
              'karamja_gloves': 'karamja gloves'}
ITEM_ICONS = {'diary_cape': 'achievement cape', 'quest_point_cape': 'quest cape', 'mortton_teleport': 'mortton',
              'revenant_cave_teleport': 'revenant cave', 'karamja_gloves_3': 'karamja gloves'}
# Options named differently from RuneLite's icon: the hilt's "Trollheim" is the God Wars Dungeon, the Karamja
# gloves' slayer master is Duradel
OPTION_ICONS = {('ghommals_hilt', 'trollheim'): 'god wars dungeon', ('karamja_gloves', 'slayer master'): 'duradel'}
# The max cape's teleports are the skill capes' teleports, with their icons
MAXCAPE_ICONS = {'feldip hills': 'hunter cape carnivorous', 'black chinchompas': 'hunter cape black',
                 'ottos grotto': 'fishing cape ottos grotto', 'warriors guild': 'strength cape',
                 'hunter guild': 'hunter cape hunter guild', 'crafting guild': 'crafting cape',
                 'farming guild': 'farming cape', 'fishing guild': 'fishing cape'}
# Islands whose mooring point is named for its port: The Great Conch's docking point is at The Summer Shore
MOORINGS = {'The Great Conch': 'The Summer Shore'}
# Teleports landing underground, out of the surface map's view, and the wiki page whose map pin is their way in from
# the surface: AJQ's dungeon is reached from Dorgesh-Kaan, under Lumbridge Castle's cellar; DLS is in the Myreque
# hideout; Ghorrock Dungeon is "accessed through the Salt Mine under Weiss"; Cam Torum's entrance is its quetzal stop
UNDERGROUND = {('fairy', None, 'AJQ'): 'Trapdoor (Lumbridge Castle)', ('fairy', None, 'DLS'): 'Myreque Hideout',
               ('item', 'ring_of_shadows', 'Ghorrock Dungeon'): 'Salt Mine',
               ('item', 'ring_of_shadows', 'Ancient Vault'): 'Ancient Vault'}
# From the Quetzal Transport System's table of landing sites: "Cam Torum entrance"
CAM_TORUM_ENTRANCE = {'x': 1448, 'y': 3108, 'plane': 0}
UNDERGROUND_Y = 4200
# Cities on a map area of their own, which the surface map doesn't show: their ways in are every map link into them
SEPARATE_AREAS = [('Prifddinas', (3136, 5952, 3391, 6207)), ('Zanaris', (2304, 4288, 2559, 4543))]
# The world map's links between map areas, from the game cache by MapLinks.java: [(start, end)]
MAP_LINKS_FILE = 'map_links.tsv'
# How far from a landing underground the end of a map link can be to count as its way in, in tiles; and how many
# links deep, for a dungeon reached through another one
LINK_TILES = 80
LINK_HOPS = 3
# How far a wiki pin for a way in can be from the map link it's for, in tiles
SNAP_TILES = 4
_links = None


def map_links():
    global _links
    if _links is None:
        _links = []
        for line in open(MAP_LINKS_FILE):
            v = [int(n) for n in line.split()]
            _links.append(({'x': v[0], 'y': v[1], 'plane': v[2]}, {'x': v[3], 'y': v[4], 'plane': v[5]}))
    return _links


def on_surface(point):
    return point['y'] < UNDERGROUND_Y and not any(x1 <= point['x'] <= x2 and y1 <= point['y'] <= y2
                                                     for _, (x1, y1, x2, y2) in SEPARATE_AREAS)


def ways_in(point):
    """The map links from the surface into the separate map area a point is in, like Prifddinas's four gates; [] for
    none."""
    for name, (x1, y1, x2, y2) in SEPARATE_AREAS:
        if point and x1 <= point['x'] <= x2 and y1 <= point['y'] <= y2:
            return [dict(s) for s, e in map_links() if on_surface(s) and x1 <= e['x'] <= x2 and y1 <= e['y'] <= y2]
    return []


def onto_link(point):
    """A wiki pin moved onto the map link on the surface right by it, like the Lumbridge Castle trapdoor's, where the
    world map draws the way in; the pin as it is when there's none within SNAP_TILES."""
    near = [s for s, e in map_links() if on_surface(s) and _distance(s, point) <= SNAP_TILES]
    return dict(min(near, key=lambda s: _distance(s, point))) if near else point


def link_in(point):
    """For a point underground, the map link on the surface that leads towards it: the link ending nearest it, and if
    that starts underground too, the one leading there, and so on; None when there's none near."""
    seen = []
    for _ in range(LINK_HOPS):
        near = [(_distance(e, point), s) for s, e in map_links()]
        near = [(d, s) for d, s in near if d <= LINK_TILES and s not in seen]
        if not near:
            return None
        point = min(near, key=lambda n: n[0])[1]
        if on_surface(point):
            return dict(point)
        seen.append(point)
    return None


# Options that say nothing about where they go
GENERIC = {'teleport', 'crush', 'commune', 'activate', 'invoke', 'break'}
# How far RuneLite's icon for the item can be from the wiki's landing when the names don't match, in tiles
NEAR_TILES = 15


def runelite_icons():
    """[(kind, text, point)] for every teleport icon RuneLite's World Map plugin draws: kind is its TeleportType (or
    "fairy"), text its name and tooltip, such as "slayer tower slayer ring slayer tower"."""
    icons = []
    for name in RUNELITE_ICON_FILES:
        fn = 'wiki/runelite_' + name
        if not os.path.exists(fn):
            url = ('https://raw.githubusercontent.com/runelite/runelite/%s/runelite-client/src/main/java/net/runelite/'
                   'client/plugins/worldmap/%s' % (RUNELITE_TAG, name))
            open(fn, 'w').write(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read().decode())
        for m in re.finditer(r'^\s*([A-Z0-9_]+)\((.*?)new WorldPoint\(([\d +*-]+),([\d +*-]+),\s*(\d)\)', open(fn).read(), re.M):
            # Some are written as sums, like "2581 + 64 * 3": only digits and arithmetic, so safe to work out
            x, y = (eval(m.group(i), {'__builtins__': {}}) for i in (3, 4))
            kind = re.search(r'TeleportType\.(\w+)', m.group(2))
            text = norm(m.group(1).replace('_', ' ') + ' ' + ' '.join(re.findall(r'"([^"]*)"', m.group(2))))
            icons.append((kind.group(1) if kind else 'fairy', text, {'x': x, 'y': y, 'plane': int(m.group(5))}))
    return icons


# RuneLite's World Map plugin's other places, whose (invisible) hover spots it puts right over the game's own icons
# for them: dungeon entrances, minigames, boats' mooring points and other transport, like the quetzal stops
RUNELITE_PLACE_FILES = ['DungeonLocation.java', 'MinigameLocation.java', 'MooringLocation.java',
                        'TransportationPointLocation.java']
_places = None


def runelite_places():
    """[(file, name, point)] for each of those places."""
    global _places
    if _places is None:
        _places = []
        for name in RUNELITE_PLACE_FILES:
            fn = 'wiki/runelite_' + name
            if not os.path.exists(fn):
                url = ('https://raw.githubusercontent.com/runelite/runelite/%s/runelite-client/src/main/java/net/'
                       'runelite/client/plugins/worldmap/%s' % (RUNELITE_TAG, name))
                open(fn, 'w').write(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read().decode())
            for m in re.finditer(r'^\s*([A-Z0-9_]+)\((.*?)new WorldPoint\(([\d +*-]+),\s*([\d +*-]+),\s*(\d)\)',
                                 open(fn).read(), re.M):
                x, y = (eval(m.group(i), {'__builtins__': {}}) for i in (3, 4))
                names = re.findall(r'"([^"]*)"', m.group(2))
                _places.append((name, norm(names[0] if names else m.group(1)), {'x': x, 'y': y, 'plane': 0}))
    return _places


def mooring(value):
    """RuneLite's mooring point for a boat's island, by name; None if it has none."""
    port = norm(MOORINGS.get(value, value))
    found = [p for f, n, p in runelite_places() if f == 'MooringLocation.java' and n == port]
    return dict(found[0]) if found else None


def onto_icon(point):
    """A point moved onto RuneLite's place right by it, like the Karamja volcano's dungeon entrance, where the game's
    icon is; the point as it is when there's none within SNAP_TILES."""
    if not point:
        return point
    near = [p for f, n, p in runelite_places() if _distance(p, point) <= SNAP_TILES]
    return dict(min(near, key=lambda p: _distance(p, point))) if near else point


def _distance(a, b):
    return max(abs(a['x'] - b['x']), abs(a['y'] - b['y']))


def _overlap(words, text):
    return len(words & set(text.split())) / len(words) if words else 0


def runelite_icon(route, items, icons, near):
    """RuneLite's icon (kind, text, point) for a route: by its item and destination, or else the item's icon nearest
    the wiki's landing; None when RuneLite doesn't draw one."""
    t, value = route['type'], norm(route['value'])
    value = OPTION_ICONS.get((route.get('item') or t, value), value)
    words = set(value.split()) - GENERIC
    if t == 'fairy':
        mine = [i for i in icons if i[0] == 'fairy' and i[1].split()[0] == value]
        words = set()
    elif t == 'spell':
        mine = [i for i in icons if 'MAGIC' in i[0]]
    elif t == 'maxcape':
        mine = [i for i in icons if MAXCAPE_ICONS.get(value, '#') in i[1]]
        words = set()
    elif t == 'obelisk':
        # "Level 44 Wilderness" is RuneLite's "Obelisk 44"
        mine = [i for i in icons if 'obelisk' in i[1] and set(i[1].split()) & {w for w in words if w.isdigit()}]
        words = set()
    else:
        item = TYPE_ITEMS.get(t)
        if t == 'item':
            label = items.get(route.get('item'), {}).get('label', '')
            item = ITEM_ICONS.get(route.get('item')) or norm(re.sub(r'\s*/.*$', '', re.sub(r' \d(/\d)?$', '', label)))
        mine = [i for i in icons if i[0] != 'fairy' and item and item in i[1]]
    named = [i for i in mine if _overlap(words, i[1]) >= 0.6]
    if named:
        mine = named
    elif words or len(mine) > 1:
        # Names don't say which: only an icon right by the wiki's landing
        mine = [i for i in mine if near and _distance(i[2], near) <= NEAR_TILES]
    if not mine:
        return None
    return min(mine, key=lambda i: _distance(i[2], near)) if near else mine[0]


def surface(key, point):
    """For a landing underground, the way in from the surface, where the world map shows it."""
    if not point or point['y'] < UNDERGROUND_Y or ways_in(point):
        return point
    if key[1] == 'calcified_moth':
        return CAM_TORUM_ENTRANCE
    link = link_in(point)
    if link:
        return link
    page = UNDERGROUND.get(key)
    entrance = spots.entrance(page) if page else None
    return onto_link(entrance) if entrance else point


def main(path):
    data = json.load(open(path))
    masters = {m['name']: m.get('spot') for m in data['masters']}
    icons = runelite_icons()
    cache, missing, snapped, wiki_pins = {}, set(), 0, set()
    routes = [r for l in data['locations'].values() for r in l['routes']] + [r for m in data['masters'] for r in m['routes']]
    for r in routes:
        key = (r['type'], r.get('item'), r['value'])
        if key not in cache:
            point = landing(r, data['items'], masters)
            icon = runelite_icon(r, data['items'], icons, point)
            cache[key] = icon[2] if icon else onto_icon(surface(key, point))
            snapped += bool(icon)
            # A wiki pin on the surface map, not on RuneLite's icon or a map link: by one of the game's own icons
            if (not icon and cache[key] and on_surface(cache[key]) and all(s != cache[key] for s, e in map_links())
                    and all(p != cache[key] for f, n, p in runelite_places())):
                wiki_pins.add(key)
            if '-v' in sys.argv:
                far = icon and point and max(abs(icon[2]['x'] - point['x']), abs(icon[2]['y'] - point['y']))
                print('  %-50s %s' % (key, ('RuneLite: %s (%s tiles from the wiki)' % (icon[1], far)) if icon
                                       else 'wiki %s' % point))
        if cache[key]:
            r['landing'] = cache[key]
            if key in wiki_pins:
                r['nearIcon'] = True
            else:
                r.pop('nearIcon', None)
            entrances = ways_in(cache[key])
            if not entrances and cache[key]['y'] >= UNDERGROUND_Y:
                link = link_in(cache[key])
                entrances = [link] if link else []
            if not entrances and cache[key]['y'] >= UNDERGROUND_Y:
                # RuneLite's surface icon for the same teleport, like the slayer ring's Fremennik Slayer Dungeon one
                # by the cave mouth: the way in, where the surface map shows it
                twin = runelite_icon(r, data['items'], [i for i in icons if i[2]['y'] < UNDERGROUND_Y], None)
                if twin:
                    entrances = [twin[2]]
            if entrances:
                r['entrances'] = [onto_icon(e) for e in entrances]
            else:
                r.pop('entrances', None)
        else:
            r.pop('landing', None)
            missing.add(key)
    # Task locations' ways in, from the wiki's pins, onto the map links there
    for loc in data['locations'].values():
        if loc.get('entrance'):
            loc['entrance'] = onto_icon(onto_link(loc['entrance']))
    # A task location inside one of those areas, like Zanaris, is walked into from its way in on the surface
    for t in data['tasks']:
        for l, spot in (t.get('spots') or {}).items():
            loc = data['locations'].get(l)
            if loc is not None and 'entrance' not in loc:
                ways = ways_in(spot)
                if ways:
                    loc['entrance'] = min(ways, key=lambda w: _distance(w, spot))
    # Which of them are on a map link, which the world map draws on the tile's corner
    starts = {(st['x'], st['y'], st['plane']) for st, e in map_links()}
    # RuneLite's place there means the game draws its own icon for it, in the middle of the tile like any other
    starts -= {(p['x'], p['y'], p['plane']) for f, n, p in runelite_places()}
    spots_ = [r.get('landing') for r in routes] + [e for r in routes for e in r.get('entrances') or []] \
        + [l.get('entrance') for l in data['locations'].values()]
    for spot in spots_:
        if spot:
            if (spot['x'], spot['y'], spot['plane']) in starts:
                spot['link'] = True
            else:
                spot.pop('link', None)
    json.dump(data, open(path, 'w'), indent=1, ensure_ascii=False)
    print(len(cache) - len(missing), 'of', len(cache), 'teleports have a landing,', snapped, 'on RuneLite\'s icon; missing:')
    for k in sorted(missing, key=str):
        print('  ', k)


if __name__ == '__main__':
    main(sys.argv[1])
