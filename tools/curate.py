"""
Builds slayer_locations.json for the plugin from the wiki data gathered in tasks.json / bosses.json,
plus the hand-curated location -> teleport table below. Every fairy code, portal and item teleport here
comes from the wiki pages fetched into wiki/ (location "getting there" sections, fairy ring code list,
construction/max cape teleport lists, slayer master pages).
"""
import json, re, collections
from autoroutes import routes_for
import catalog

# ---- Teleport route constructors -------------------------------------------------------------
# type: ring | fairy | portal (construction cape / max cape house portal) | maxcape | item | boat
def ring(o): return {'type': 'ring', 'value': o}
def fairy(c): return {'type': 'fairy', 'value': c}
def portal(p): return {'type': 'portal', 'value': p}
def maxcape(d): return {'type': 'maxcape', 'value': d}
def burning(d): return {'type': 'burning_amulet', 'value': d}
def dueling(d): return {'type': 'ring_of_dueling', 'value': d}
def gloves(d): return {'type': 'karamja_gloves', 'value': d}
def boat(i): return {'type': 'boat', 'value': i}
def glory(d): return {'type': 'amulet_of_glory', 'value': d}
def item(k, v): return {'type': 'item', 'item': k, 'value': v}
def spell(v): return {'type': 'spell', 'value': v}
def network(k, v): return {'type': 'network', 'item': k, 'value': v}

W = 'wilderness'

# location -> (routes, flags). Keys are wiki page names; aliases map variant spellings onto them.
LOCATIONS = {
    # Slayer ring destinations (option text verified in game)
    'Stronghold Slayer Cave': [ring('Stronghold')],
    'Slayer Tower': [ring('Slayer Tower'), fairy('CKS')],
    'Fremennik Slayer Dungeon': [ring('Fremennik Dungeon'), fairy('AJR'), portal('Rellekka')],
    "Tarn's Lair": [ring("Tarn's Lair"), fairy('BIP')],
    'Mourner Tunnels': [ring('Dark Beasts')],
    'Wyrmscraig Cavern': [ring('Wyrmscraig Cavern'), boat('Wyrmscraig')],
    # Fairy ring destinations (codes from each location's wiki page / the fairy ring code list)
    'Abyssal Nexus': [fairy('DIP')],
    'Abyssal Area': [fairy('ALR')],
    'Catacombs of Kourend': [fairy('CIS')],
    'Chasm of Fire': [fairy('DJR')],
    'Karuulm Slayer Dungeon': [fairy('CIR'), maxcape('Farming Guild')],
    'Kraken Cove': [fairy('AKQ')],
    'Smoke Devil Dungeon': [fairy('BKP')],
    'Smoke Dungeon': [portal('Pollnivneach'), fairy('DLQ')],
    'Kalphite Lair': [fairy('BIQ')],
    'Kalphite Cave': [fairy('BIQ'), glory('Al Kharid')],
    'Mor Ul Rek': [fairy('BLP')],
    'Karamja Volcano': [glory('Karamja'), fairy('BLP')],
    'Lighthouse': [fairy('ALP')],
    'Asgarnian Ice Dungeon': [fairy('AIQ'), portal('Rimmington')],
    'Mudskipper Point': [fairy('AIQ'), portal('Rimmington')],
    'Port Sarim': [fairy('AIQ'), portal('Rimmington')],
    'Brimhaven Dungeon': [portal('Brimhaven'), fairy('CKR')],
    'Brimhaven': [portal('Brimhaven')],
    'Taverley Dungeon': [portal('Taverley')],
    'Iorwerth Dungeon': [portal('Prifddinas')],
    'Prifddinas': [portal('Prifddinas')],
    'Tower of Voices': [portal('Prifddinas')],
    'Zanaris': [fairy('BKS')],
    'Isle of Souls Dungeon': [fairy('BJP')],
    'Isle of Souls': [fairy('BJP')],
    'Brine Rat Cavern': [fairy('DKS')],
    'Dorgesh-Kaan South Dungeon': [fairy('AJQ')],
    'Lizardman Settlement': [fairy('BLS')],
    'Meiyerditch Laboratories': [fairy('DLS')],
    'Witchaven Dungeon': [fairy('BLR')],
    'Witchaven Shrine Dungeon': [fairy('BLR')],
    "Legends' Guild": [fairy('BLR')],
    'Morytania Spider Cave': [fairy('ALQ')],
    'Haunted Woods': [fairy('ALQ')],
    'Stalker Den': [fairy('AIS')],
    'Grimstone Dungeon': [fairy('DLP')],
    'Miscellania and Etceteria Dungeon': [fairy('CIP')],
    'Feldip Hills': [fairy('AKS'), maxcape('Feldip Hunter area')],
    'Canifis': [fairy('CKS')],
    'Paterdomus': [fairy('CKS')],
    'Ardougne Zoo': [fairy('BIS')],
    'East Ardougne': [fairy('DJP')],
    'Ardougne': [fairy('DJP')],
    'Yanille': [portal('Yanille'), fairy('CLS')],
    'Tree Gnome Village (location)': [fairy('CIQ')],
    'Gnome Maze': [fairy('CIQ')],
    'Mort Myre Swamp': [fairy('BKR')],
    "Mort'ton": [fairy('BIP')],
    'Barrows': [fairy('BKR')],
    'Morytania': [fairy('BKR')],
    'Edgeville Dungeon': [glory('Edgeville'), fairy('DKR')],
    'Draynor Village': [glory('Draynor Village'), fairy('DIS')],
    'Draynor Sewers': [glory('Draynor Village')],
    'Draynor Manor': [glory('Draynor Village')],
    'Al Kharid': [glory('Al Kharid')],
    'Al Kharid Mine': [glory('Al Kharid')],
    'Al Kharid mine': [glory('Al Kharid')],
    "Wizards' Tower": [fairy('DIS')],
    'Ape Atoll': [fairy('CLR')],
    'Kebos Lowlands': [fairy('CIR')],
    'Battlefront': [fairy('CIR')],
    'Great Kourend': [fairy('CIS')],
    'Arceuus': [fairy('CIS')],
    'Hosidius': [portal('Hosidius'), fairy('AKR')],
    'Woodcutting Guild': [fairy('AKR')],
    'Shayzien': [fairy('DJR')],
    'Lovakengj': [fairy('DJR')],
    'Sinclair Mansion': [fairy('CJR')],
    "McGrubor's Wood": [fairy('ALS')],
    'Kharidian Desert': [fairy('DLQ')],
    'Tai Bwo Wannai': [fairy('CKR')],
    'Poison Waste': [fairy('BJS')],
    'The Stranglewood': [fairy('BLS')],
    'Forthos Dungeon': [portal('Hosidius')],
    'Ancient Cavern': [maxcape("Otto's Grotto")],
    'Crafting Guild': [maxcape('Crafting Guild')],
    'Fishing Guild': [maxcape('Fishing Guild')],
    'Rellekka': [portal('Rellekka')],
    'Ungael': [portal('Rellekka')],
    'Rimmington': [portal('Rimmington')],
    'Taverley': [portal('Taverley')],
    "Cerberus' Lair": [portal('Taverley')],
    'Pollnivneach': [portal('Pollnivneach')],
    'Mistrock': [portal('Aldarin')],
    'Aldarin': [portal('Aldarin')],
    # Sailing: islands reached with Teleport to Boat when your boat with a teleport focus is moored there
    'Kurask Lair': [boat('Laguna Aurorae'), network('spirit_tree', 'Laguna Aurorae')],
    'Laguna Aurorae': [boat('Laguna Aurorae')],
    'Ynysdail Cavern': [boat('Ynysdail')],
    'Charred Dungeon': [boat('Charred Island')],
    'Charred Island Dungeon': [boat('Charred Island')],
    'Lunar Isle': [boat('Lunar Isle')],
    'Corsair Cove Dungeon': [boat('Corsair Cove')],
    'Corsair Cove': [boat('Corsair Cove')],
    # Wilderness (only used when the wilderness toggle is on, or the task is from Krystilia)
    'Wilderness Slayer Cave': [dueling('Ferox Enclave')],
    'Lava Maze': [burning('Lava Maze')],
    "King Black Dragon's Lair": [burning('Lava Maze')],
    'Revenant Caves': [burning('Lava Maze')],
    'Bandit Camp (Wilderness)': [burning('Bandit Camp')],
    'Chaos Temple (Wilderness)': [burning('Chaos Temple')],
    'Wilderness God Wars Dungeon': [burning('Bandit Camp'), dueling('Ferox Enclave')],
    'The Forgotten Cemetery': [burning('Lava Maze')],
    'Graveyard of Shadows': [dueling('Ferox Enclave')],
    "Dark Warriors' Fortress": [burning('Bandit Camp'), dueling('Ferox Enclave')],
    'Ferox Enclave': [dueling('Ferox Enclave')],
    'Bone Yard': [dueling('Ferox Enclave')],
    'Wilderness': [dueling('Ferox Enclave'), maxcape('Wilderness Hunter area')],
    'Bone Yard Hunter area': [maxcape('Wilderness Hunter area')],
    'Edgeville': [glory('Edgeville'), fairy('DKR')],
    'Gryphons (dungeon)': [fairy('CJQ'), boat('The Great Conch')],
    'Shellbane Gryphon Cave': [fairy('CJQ'), boat('The Great Conch')],
    'Sophanem Dungeon': [fairy('AKP'), portal('Pollnivneach')],
    'Ghorrock Prison': [boat('Weiss')],
    # Gaps filled from each place's wiki page (entrances, levers, nearby towns)
    'Stronghold of Security': [item('skull_sceptre', 'Invoke'), glory('Edgeville'), network('canoe', 'Barbarian Village')],
    'Sourhog Cave': [spell('Draynor Manor Teleport'), glory('Draynor Village')],
    'Killerwatt plane': [spell('Draynor Manor Teleport'), glory('Draynor Village')],
    'Waterbirth Island Dungeon': [spell('Waterbirth Teleport'), item('enchanted_lyre', 'Waterbirth Island')],
    'Mole Lair': [spell('Falador Teleport'), item('ring_of_wealth', 'Falador')],
    'River Elid': [item('desert_amulet', 'Nardah'), fairy('DLQ')],
    'Ruins of Unkah': [item('desert_amulet', 'Nardah'), fairy('DLQ')],
    'Ruins of Ullek': [item('desert_amulet', 'Nardah'), fairy('DLQ')],
    'Lumbridge Swamp Caves': [spell('Lumbridge Teleport')],
    'Scorpion Pit': [glory('Edgeville'), spell('Ardougne Teleport')],
    'Mage Arena': [glory('Edgeville'), spell('Ardougne Teleport')],
    'Magic Axe Hut': [glory('Edgeville'), spell('Ardougne Teleport')],
    "Pirates' Hideout": [glory('Edgeville'), spell('Ardougne Teleport')],
}

ALIASES = {
    'Slayer tower': 'Slayer Tower', 'Morytania Slayer Tower': 'Slayer Tower',
    'Stronghold Slayer Dungeon': 'Stronghold Slayer Cave',
    'Fremennik Slayer Cave': 'Fremennik Slayer Dungeon',
    "Legends'_Guild": "Legends' Guild",
    'Lizardmen Settlement': 'Lizardman Settlement',
    'Asgarnian Ice Caves': 'Asgarnian Ice Dungeon', 'Asgarnia Ice Dungeon': 'Asgarnian Ice Dungeon',
    'Mount Karuulm Dungeon': 'Karuulm Slayer Dungeon',
    'Forgotten Cemetery': 'The Forgotten Cemetery',
    'Magic axe hut': 'Magic Axe Hut',
    'Meyerditch': 'Meiyerditch',
    'Chaos Temple': 'Chaos Temple (Wilderness)',
}

WILDERNESS = {
    'Wilderness Slayer Cave', 'Lava Maze', 'Lava Maze Dungeon', "King Black Dragon's Lair", 'Revenant Caves',
    'Bandit Camp (Wilderness)', 'Chaos Temple (Wilderness)', 'Wilderness God Wars Dungeon', 'The Forgotten Cemetery',
    'Graveyard of Shadows', 'Demonic Ruins', 'Lava Dragon Isle', "Dark Warriors' Fortress", "Rogues' Castle",
    'Frozen Waste Plateau', 'Bone Yard', 'Bone Yard Hunter area', 'Magic Axe Hut', "Pirates' Hideout",
    "Callisto's Den", 'Scorpion Pit', 'Silk Chasm', "Vet'ion's Rest", 'Deep Wilderness Dungeon', 'Wilderness',
    'Wilderness Agility Course', 'Mage Arena', 'Wilderness Pond', 'Ferox Enclave', 'South-west Wilderness mine',
    'Wilderness Agility Course Dungeon', 'Giant Pit',
}

MASTERS = [
    # id is the SLAYER_MASTER varbit value where known (core Slayer plugin: Krystilia 7, Mortimer 10)
    {'key': 'TURAEL', 'name': 'Turael / Spria', 'location': 'Burthorpe',
     'routes': [item('games_necklace', 'Burthorpe'), network('minigame', 'Burthorpe Games Room'), portal('Taverley')],
     'note': 'Burthorpe; Taverley house portal then run north. Spria is in Draynor Village (amulet of glory: Draynor Village, or fairy ring DIS).'},
    {'key': 'MAZCHNA', 'name': 'Mazchna', 'location': 'Canifis', 'routes': [fairy('CKS'), spell('Kharyrll Teleport')]},
    {'key': 'VANNAKA', 'name': 'Vannaka', 'location': 'Edgeville Dungeon',
     'routes': [glory('Edgeville'), fairy('DKR'), spell('Paddewwa Teleport')]},
    {'key': 'CHAELDAR', 'name': 'Chaeldar', 'location': 'Zanaris', 'routes': [fairy('BKS')]},
    {'key': 'KONAR', 'name': 'Konar quo Maten', 'location': 'Mount Karuulm',
     'routes': [item('radas_blessing', 'Mount Karuulm'), fairy('CIR'), item('skills_necklace', 'Farming Guild')]},
    {'key': 'NIEVE', 'name': 'Nieve / Steve', 'location': 'Tree Gnome Stronghold',
     'routes': [ring('Stronghold'), item('royal_seed_pod', 'Commune'), item('necklace_of_passage', 'The Outpost')]},
    {'key': 'DURADEL', 'name': 'Duradel / Kuradal', 'location': 'Shilo Village',
     'routes': [gloves('Slayer Master'), item('karamja_gloves_3', 'Gem Mine'), fairy('CKR')]},
    {'key': 'KRYSTILIA', 'name': 'Krystilia', 'location': 'Edgeville', 'routes': [glory('Edgeville'), fairy('DKR')]},
    {'key': 'MORTIMER', 'name': 'Mortimer', 'location': 'Wyrmscraig Cavern',
     'routes': [ring('Wyrmscraig Cavern'), item('necklace_of_passage', 'Wyrmscraig'), boat('Wyrmscraig')]},
]

# Which location is the default for a task when it's the standard spot. Applied before automatic ordering.
PREFERRED_FIRST = {
    'Abyssal demons': 'Slayer Tower',
    'Aberrant spectres': 'Stronghold Slayer Cave',
    'Kurasks': 'Fremennik Slayer Dungeon',
    'Dark beasts': 'Mourner Tunnels',
    'Wyrms': 'Karuulm Slayer Dungeon',
    'Fire giants': 'Stronghold Slayer Cave',
    'Hellhounds': 'Stronghold Slayer Cave',
    'Bloodvelds': 'Slayer Tower',
    'Nechryael': 'Slayer Tower',
    'Dust devils': 'Smoke Dungeon',
    'Greater demons': 'Chasm of Fire',
    'Black demons': 'Chasm of Fire',
    'Waterfiends': 'Kraken Cove',
    'Dagannoth': 'Lighthouse',
    'Blue dragons': 'Taverley Dungeon',
    'Black dragons': 'Taverley Dungeon',
    'Metal dragons': 'Brimhaven Dungeon',
    'Red dragons': 'Brimhaven Dungeon',
}

# Hand-checked routes that replace the wiki-derived ones for a location, best first. Used where the automatic
# extraction picked detours (e.g. a house portal on the wrong side of an island) or missed the direct teleport.
OVERRIDE_ROUTES = {
    'Ancient Cavern': [maxcape("Otto's Grotto"), item('fishing_cape', "Otto's Grotto"), item('games_necklace', 'Barbarian Outpost')],
    'Asgarnian Ice Dungeon': [fairy('AIQ'), portal('Rimmington')],
    'Brimhaven Dungeon': [portal('Brimhaven'), fairy('CKR')],
    'Draynor Village': [glory('Draynor Village'), fairy('DIS')],
    'Falador': [spell('Falador Teleport'), item('ring_of_wealth', 'Falador')],
    'Fossil Island': [item('digsite_pendant', 'Fossil Island')],
    'Iorwerth Dungeon': [portal('Prifddinas'), item('teleport_crystal', 'Prifddinas'), network('spirit_tree', 'Prifddinas')],
    'Prifddinas': [portal('Prifddinas'), item('teleport_crystal', 'Prifddinas'), network('spirit_tree', 'Prifddinas')],
    'Tower of Voices': [portal('Prifddinas'), item('teleport_crystal', 'Prifddinas'), network('spirit_tree', 'Prifddinas')],
    'Karamja': [glory('Karamja'), network('gnome_glider', 'Gandius')],
    'Karuulm Slayer Dungeon': [item('radas_blessing', 'Mount Karuulm'), fairy('CIR'), item('skills_necklace', 'Farming Guild')],
    'Lava Dragon Isle': [item('revenant_cave_teleport', 'Teleport'), spell('Annakarl Teleport')],
    'Lighthouse': [fairy('ALP'), item('games_necklace', 'Barbarian Outpost'), portal('Rellekka')],
    'Mor Ul Rek': [fairy('BLP'), item('ghommals_hilt', 'Mor Ul Rek'), glory('Karamja')],
    'Taverley Dungeon': [portal('Taverley'), spell('Falador Teleport')],
    'Varrock': [spell('Varrock Teleport'), item('ring_of_wealth', 'Grand Exchange')],
    'White Wolf Mountain': [network('gnome_glider', 'Sindarpos')],
    'Wilderness': [dueling('Ferox Enclave'), maxcape('Wilderness Hunter area')],
    "Wizards' Tower": [item('necklace_of_passage', "Wizards' Tower"), fairy('DIS'), glory('Draynor Village')],
}

# Locations always kept for a task, beyond the best few (the wiki table misses them, or they're a common choice)
EXTRA_LOCATIONS = {
    'Abyssal demons': ['Abyssal Nexus'],  # Abyssal Sire counts for an abyssal demon task
    'Smoke devils': [],
    'Gryphons': ['Gryphons (dungeon)'],
    'Scabarites': ['Sophanem Dungeon'],
}

BOSS_LOCATION_FIX = {'Waterbirth island': 'Waterbirth Island Dungeon', 'Morytania': 'Barrows'}

# How many locations per task, and teleports per location, to keep (best first). Raise to show more.
MAX_LOCATIONS = 3
MAX_ROUTES = 3

RANK = {'item': 2, 'spell': 2, 'network': 3, 'amulet_of_glory': 1, 'ring': 0, 'portal': 1, 'fairy': 1, 'karamja_gloves': 1, 'burning_amulet': 1, 'ring_of_dueling': 1,
        'maxcape': 2, 'boat': 3}


def canon(loc):
    return ALIASES.get(loc, loc)


_auto = {}


def routes(loc):
    loc = canon(loc)
    if loc in OVERRIDE_ROUTES:
        return OVERRIDE_ROUTES[loc][:MAX_ROUTES]
    if loc not in _auto:
        merged = list(LOCATIONS.get(loc, []))
        for r in routes_for(loc) if loc else []:
            if r not in merged:
                merged.append(r)
        # A teleport item with no known destination can't be swapped or highlighted in a menu
        merged = [r for r in merged if not (r['type'] == 'item' and not r.get('value'))]
        # Best teleport types first; the hand-curated routes stay ahead of wiki-derived ones of the same rank
        merged.sort(key=route_rank)
        _auto[loc] = merged[:MAX_ROUTES]
    return _auto[loc]


def route_rank(r):
    if r['type'] == 'item' and not r.get('value'):
        return 5
    return RANK[r['type']]


def location_rank(loc):
    r = routes(loc)
    return min((RANK[x['type']] for x in r), default=9)


def build():
    tasks = json.load(open('tasks.json'))
    bosses = json.load(open('bosses.json'))
    out_tasks = []

    for t in tasks:
        name = t['task']
        locs = [canon(l) for l in t['use_locations'] + EXTRA_LOCATIONS.get(name, [])]
        locs = list(dict.fromkeys(l for l in locs if l))
        pref = PREFERRED_FIRST.get(name)
        locs.sort(key=lambda l: (0 if l == pref else 1, l in WILDERNESS, location_rank(l)))
        routed = [l for l in locs if routes(l)]
        if routed:
            # Keep the best few normal and Wilderness locations; drop places no supported teleport reaches
            pinned = [canon(l) for l in EXTRA_LOCATIONS.get(name, []) if routes(l)]
            normal = [l for l in routed if l not in WILDERNESS and l not in pinned][:MAX_LOCATIONS]
            locs = normal + pinned + [l for l in routed if l in WILDERNESS][:MAX_LOCATIONS]
        aliases = list(dict.fromkeys([name, t['page'].split('/')[-1]] + [m for m in t['monsters'] if m not in LOCATIONS]))
        out_tasks.append({'name': name, 'aliases': aliases, 'masters': t['masters'], 'boss': False,
                          'locations': locs})

    for b in bosses:
        bn = re.sub(r'^\{\{ilinkt\|', '', b['boss']).split('|')[0].split('}')[0].strip()
        loc = canon(BOSS_LOCATION_FIX.get(b['location'], b['location']))
        aliases = [bn]
        if bn == 'Barrows chest':
            aliases = ['Barrows', 'Barrows Brothers', 'Barrows chest']
        if bn == 'Artio':
            aliases = ['Callisto', 'Artio']
        if bn == 'Spindel':
            aliases = ['Venenatis', 'Spindel']
        if bn == "Calvar'ion":
            aliases = ["Vet'ion", "Calvar'ion"]
        if bn == 'Deranged archaeologist':
            aliases = ['Crazy archaeologist', 'Deranged archaeologist']
        out_tasks.append({'name': aliases[0], 'aliases': aliases, 'masters': [], 'boss': True, 'locations': [loc]})

    used = collections.OrderedDict()
    for t in out_tasks:
        for l in t['locations']:
            used[l] = None
    for m in MASTERS:
        used[m['location']] = None

    locations = {}
    for l in used:
        locations[l] = {'routes': routes(l), 'wilderness': l in WILDERNESS}
    for m in MASTERS:
        # Master routes are hand-checked: the wiki pages list too many detours to rely on extraction
        locations.setdefault(m['location'], {'routes': m['routes'], 'wilderness': False})

    items = {k: {'label': v[0], 'names': v[1], 'options': v[2]} for k, v in catalog.ITEMS.items()}
    networks = {k: {'label': v[0], 'destinations': v[2]} for k, v in catalog.NETWORKS.items()}
    data = {'locations': locations, 'tasks': out_tasks, 'masters': MASTERS, 'items': items, 'networks': networks}
    return data


if __name__ == '__main__':
    data = build()
    json.dump(data, open('slayer_locations.json', 'w'), indent=1, ensure_ascii=False)
    n_loc = sum(1 for v in data['locations'].values() if v['routes'])
    covered = sum(1 for t in data['tasks'] if any(data['locations'][l]['routes'] for l in t['locations']))
    print(len(data['tasks']), 'tasks;', covered, 'with at least one teleport;', len(data['locations']), 'locations,', n_loc, 'with teleports')
    for t in data['tasks']:
        if not any(data['locations'][l]['routes'] for l in t['locations']):
            print('  no teleport:', t['name'], t['locations'][:6])
