"""
Konar quo Maten's task areas, from her wiki page: task -> {area name -> wiki location pages}. Konar names an area
with every task; bosses she accepts at an area (like the Abyssal Sire for "Abyss") add their lair to it.
"""
import re

# Konar's table names a few tasks differently from the task list
TASK_NAMES = {'Jelly': 'Jellies', 'Mutated Zygomite': 'Zygomites', 'Slayer task/Warped creature': 'Warped creatures'}


def _links(block):
    return re.findall(r'^\*\s*\[\[([^\]|]+)(?:\|([^\]]+))?\]\]', block, re.M)


def konar_areas(path='wiki/Konar_quo_Maten.txt'):
    txt = open(path).read()
    table = txt[txt.index('!data-sort-value=""|Possible locations'):]
    table = table[:table.index('\n|}')]
    out = {}
    for row in table.split('\n|-\n')[1:]:
        cells = row.split('\n|')
        m = re.match(r'\|?\[\[([^\]|]+)(?:\|[^\]]*)?\]\]', cells[0].strip())
        if not m or m.group(1).startswith('Boss'):
            continue
        task = TASK_NAMES.get(m.group(1), m.group(1))
        areas = {}
        for link, text in _links(cells[1]):
            areas.setdefault((text or link).strip(), []).append(link.strip())
        # Alternatives column: "[[Abyssal Nexus|Abyss]]: [[Abyssal Sire]]" adds the Nexus to the Abyss area
        for link, text in _links(cells[5] if len(cells) > 5 else ''):
            area = (text or link).strip()
            if area in areas and link.strip() not in areas[area]:
                areas[area].append(link.strip())
        out[task] = areas
    return out
