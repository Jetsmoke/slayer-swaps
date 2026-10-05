"""
Writes SLAYER_TELEPORTS.md, a readable listing of every slayer task and boss task in
src/main/resources/com/slayerteleportswap/slayer_locations.json with its locations and teleports.

    python3 tools/report.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'src/main/resources/com/slayerteleportswap/slayer_locations.json')
OUT = os.path.join(ROOT, 'SLAYER_TELEPORTS.md')

LABEL = {
    'ring': 'Slayer ring',
    'fairy': 'Fairy ring',
    'portal': 'Construction/max cape portal',
    'maxcape': 'Max cape',
    'karamja_gloves': 'Karamja gloves 4',
    'burning_amulet': 'Burning amulet',
    'ring_of_dueling': 'Ring of dueling',
    'amulet_of_glory': 'Amulet of glory',
    'boat': 'Teleport to Boat (boat moored at)',
}
# Which route types have had their in-game menu text confirmed from the dev client's menu log
VERIFIED = {'ring', 'fairy'}


def route_text(r):
    mark = '' if r['type'] in VERIFIED else ' ⁽ᵘ⁾'
    return f"{LABEL[r['type']]}: **{r['value']}**{mark}"


def location_line(data, loc, default):
    info = data['locations'].get(loc, {'routes': [], 'wilderness': False})
    star = '★ ' if default else ''
    wild = ' ☠️' if info['wilderness'] else ''
    routes = ' · '.join(route_text(r) for r in info['routes']) or '_no supported teleport_'
    return f"- {star}{loc}{wild} — {routes}"


def task_block(data, t):
    lines = [f"### {t['name']}"]
    if t['masters']:
        lines.append(f"Masters: {', '.join(dict.fromkeys(t['masters']))}")
    locs = t['locations']
    if not locs:
        lines.append('- _no locations listed on the wiki_')
    first_with_route = next((l for l in locs if data['locations'].get(l, {}).get('routes')
                             and not data['locations'][l]['wilderness']), None)
    for loc in locs:
        lines.append(location_line(data, loc, loc == first_with_route))
    return '\n'.join(lines)


def main():
    data = json.load(open(DATA))
    tasks = sorted((t for t in data['tasks'] if not t['boss']), key=lambda t: t['name'].lower())
    bosses = sorted((t for t in data['tasks'] if t['boss']), key=lambda t: t['name'].lower())

    def has_route(t):
        return any(data['locations'].get(l, {}).get('routes') for l in t['locations'])

    covered = sum(has_route(t) for t in data['tasks'])
    out = [
        '# Slayer Teleport Swap — every slayer task and its teleports',
        '',
        f"{len(tasks)} slayer tasks and {len(bosses)} boss tasks. {covered} of {len(data['tasks'])} have at least "
        'one teleport the plugin can swap or highlight.',
        '',
        '**How to read this**',
        '- Each task lists every location the OSRS wiki gives for it.',
        '- ★ marks the default location: the plugin uses it unless you pick another from the "Slayer location" '
        'right-click option on a teleport item or fairy ring.',
        '- ☠️ marks Wilderness locations. They are only used with the "Wilderness locations" setting on, or '
        'automatically for Krystilia tasks.',
        '- ⁽ᵘ⁾ marks teleports whose in-game menu text hasn\'t been checked in the game yet. Slayer ring and '
        'fairy ring entries are checked.',
        '- Fairy rings: left-click becomes the code if the ring offers it as a favourite or last destination, '
        'otherwise "Configure", with the code outlined in the fairy ring log.',
        '- Construction/max cape portal: house portal towns, on either cape.',
        '- Teleport to Boat only helps if your boat with a teleport focus is moored at that island.',
        '',
        '## Slayer masters (used when you have no task)',
        '',
    ]
    for m in data['masters']:
        routes = ' · '.join(route_text(r) for r in m['routes']) or '_no supported teleport_'
        out.append(f"- **{m['name']}** ({m['location']}) — {routes}")
        if m.get('note'):
            out.append(f"  - {m['note']}")
    out += ['', '## Slayer tasks', '']
    out += [task_block(data, t) + '\n' for t in tasks]
    out += ['## Boss tasks', '']
    out += [task_block(data, t) + '\n' for t in bosses]
    out += ['## Tasks with no supported teleport', '']
    out += [f"- {t['name']} ({', '.join(t['locations']) or 'no locations listed'})"
            for t in sorted(data['tasks'], key=lambda t: t['name'].lower()) if not has_route(t)]
    out += ['', '---', 'Locations and teleports are from the Old School RuneScape Wiki.', '']

    open(OUT, 'w').write('\n'.join(out))
    print('wrote', OUT)


if __name__ == '__main__':
    main()
