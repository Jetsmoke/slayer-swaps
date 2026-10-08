"""
Where to point the player for each task: the middle of the task monsters' spawns at each location, from the wiki's
{{LocLine}} and maplink pins, and for places underground, the entrance from the surface, from the place's own page.
"""
import re
from statistics import median
from fetch import raw

# Underground and other separate map areas (Zanaris, instances) sit north of the surface world
UNDERGROUND_Y = 4200


def _plane(block):
    m = re.search(r'\|\s*plane\s*=\s*(\d)', block)
    return int(m.group(1)) if m else 0


def _points(block):
    num = r'(\d{3,5})(?:\.\d+)?'
    pts = [(int(x), int(y)) for x, y in re.findall(r'x:\s*' + num + r'\s*,\s*y:\s*' + num, block)]
    # "|2797,3616" pins, also "|3260,3664,title:Southern entrance"
    pts += [(int(x), int(y)) for x, y in re.findall(r'\|\s*' + num + r'\s*,\s*' + num + r'\s*(?=\||\}|\n|,)', block)]
    pts += [(int(x), int(y)) for x, y in re.findall(r'\|\s*x\s*=\s*' + num + r'\s*\|\s*y\s*=\s*' + num, block)]
    return pts


def _locations(block):
    """The place a location line is about: its first link, such as "North [[Lunar Isle]]", not one it's only
    beside, as in "[[Lizardman Caves]], underneath [[Lizardman Settlement]]"."""
    line = re.search(r'\|\s*location\s*=([^\n]*)', block)
    # Spawns only there during a quest or cutscene, often in a copy of the area elsewhere on the map
    if not line or re.search(r'\bduring\b|cutscene', line.group(1), re.I):
        return []
    m = re.match(r'[^\[|]*\[\[([^\]|#]+)', line.group(1))
    return [m.group(1).strip()] if m else []


def _templates(txt, name):
    """Each {{name ...}} template's text, allowing nested templates inside."""
    out = []
    for m in re.finditer(r'\{\{\s*' + name + r'\b', txt, re.I):
        depth, i = 0, m.start()
        while i < len(txt) - 1:
            if txt.startswith('{{', i):
                depth += 1
                i += 2
            elif txt.startswith('}}', i):
                depth -= 1
                i += 2
                if depth == 0:
                    break
            else:
                i += 1
        out.append(txt[m.start():i])
    return out


def spawns(page_text):
    """{location: [(x, y, plane)]} from a page's LocLine and maplink pins that name their location."""
    found = {}
    for block in _templates(page_text, 'LocLine') + _templates(page_text, 'Map'):
        plane = _plane(block)
        for loc in _locations(block):
            for x, y in _points(block):
                found.setdefault(loc, []).append((x, y, plane))
    return found


def monster_pages(task_text):
    """The monsters a task page lists in its first table column (variants, superiors and so on)."""
    names = []
    m = re.search(r'==\s*Monster [Vv]ariants\s*==(.*?)\n\|\}', task_text, re.S)
    if m:
        for row in m.group(1).split('\n|-')[1:]:
            first = re.match(r'\s*\|\s*\[\[([^\]|#]+)', row)
            if first:
                names.append(first.group(1).strip())
    return names


def target(points):
    """The middle of the spawns: the median tile on the most common plane."""
    if not points:
        return None
    planes = [p for _, _, p in points]
    plane = max(set(planes), key=planes.count)
    pts = [(x, y) for x, y, p in points if p == plane]
    return {'x': int(median(x for x, _ in pts)), 'y': int(median(y for _, y in pts)), 'plane': plane}


def entrance(location, surface_only=True):
    """The surface tile of a place's entrance: a map pin on its page, preferring one called an entrance. With
    surface_only off, a pin anywhere, such as the map of a place on its own part of the map."""
    txt = raw(location)
    best = None
    for block in _templates(txt, 'Map'):
        pts = [(x, y) for x, y in _points(block) if y < UNDERGROUND_Y or not surface_only]
        if not pts:
            continue
        # The sentence around the pin, to tell an entrance from other pins
        start = txt.find(block)
        context = txt[max(0, start - 200):start + len(block)].lower()
        title = re.search(r'x:\s*(\d+)\s*,\s*y:\s*(\d+)\s*,\s*title:\s*main entrance', block, re.I)
        if title:
            return {'x': int(title.group(1)), 'y': int(title.group(2)), 'plane': 0}
        if 'entrance' in context:
            return {'x': pts[0][0], 'y': pts[0][1], 'plane': 0}
        best = best or {'x': pts[0][0], 'y': pts[0][1], 'plane': 0 if surface_only else _plane(block)}
    return best


def on_surface(spot):
    return spot['y'] < UNDERGROUND_Y


def master_spot(name):
    """A slayer master's tile, from the map on their page."""
    for block in _templates(raw(name), 'Map'):
        pts = _points(block)
        if pts:
            return {'x': pts[0][0], 'y': pts[0][1], 'plane': _plane(block)}
    return None
