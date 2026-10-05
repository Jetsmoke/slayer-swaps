"""
Reads each location's wiki "getting there" text and turns every catalogued teleport it mentions into a route.
Lines are wiki bullets / sentences; within a line, an item's destination option is chosen from words in that line,
then from the location's own name.
"""
import re, sys
sys.path.insert(0, '.')
from fetch import raw
from catalog import ITEMS, ITEM_ALIASES, SPELLS, PORTAL_TOWNS

HEADINGS = ['==Transportation==', '==Getting there==', '==Transport==', '==Getting to', '==Travel==', '==Access==',
            '=== Teleports', '==Teleports', '==Location==']


def clean(s):
    s = re.sub(r'\{\{Fairycode\|(\w+)\}\}', r'fairy ring \1', s, flags=re.I)
    s = re.sub(r'\{\{plinkp?\|([^}|]+)[^}]*\}\}', r'\1', s)
    s = re.sub(r'\[\[(?:[^|\]]*\|)?([^\]]*)\]\]', r'\1', s)
    s = re.sub(r'\{\{[^}]*\}\}', '', s)
    s = re.sub(r'<[^>]+>', ' ', s)
    s = re.sub(r"'{2,}", '', s)
    return s.strip()


def lines(txt):
    out = []
    m = re.search(r'\|\s*teleport\s*=\s*([^\n]*)', txt)
    if m:
        out += [clean(x) for x in re.split(r',|<br\s*/?>', m.group(1))]
    secs = []
    for h in HEADINGS:
        for mm in re.finditer(re.escape(h), txt):
            i = mm.start()
            j = txt.find('\n==', i + len(h))
            while 0 <= j < len(txt) - 3 and txt[j + 3] == '=':
                j = txt.find('\n==', j + 3)
            secs.append(txt[i:j if j > 0 else None])
    body = '\n'.join(secs) if secs else '\n'.join(l for l in txt[:9000].split('\n') if re.search('teleport|fairy', l, re.I))
    for l in body.split('\n'):
        l = l.lstrip('*: ')
        if l and not l.startswith(('|', '{|', '!', '==', '[[File')):
            out += [clean(x) for x in re.split(r'(?<=[.;])\s+', l)]
    return [x for x in out if x]


def pick(syn, options, text, location):
    t = text.lower()
    for word, opt in sorted(syn.items(), key=lambda kv: -len(kv[0])):
        if word in t:
            return opt
    for opt in options:
        if opt.lower() in t:
            return opt
    loc = location.lower()
    for word, opt in sorted(syn.items(), key=lambda kv: -len(kv[0])):
        if word in loc:
            return opt
    return None


def routes_for(location):
    found = []

    def add(r):
        if r not in found:
            found.append(r)

    for line in lines(raw(location)):
        low = line.lower()
        for key, aliases in ITEM_ALIASES.items():
            if any(a in low for a in aliases):
                label, names, options, syn = ITEMS[key]
                if len(options) == 1:
                    add({'type': 'item', 'item': key, 'value': options[0]})
                elif options:
                    opt = pick(syn, options, line, location)
                    add({'type': 'item', 'item': key, 'value': opt})
                else:
                    add({'type': 'item', 'item': key, 'value': None})
        for spell in SPELLS:
            if spell.lower() in low:
                add({'type': 'spell', 'value': spell})
        if re.search(r'teleport to house|house portal|construction cape|teleport to (?:the )?player', low):
            for town in PORTAL_TOWNS:
                if town.lower() in low:
                    add({'type': 'portal', 'value': town})
    return found


if __name__ == '__main__':
    for loc in sys.argv[1:]:
        print('==', loc)
        for r in routes_for(loc):
            print('   ', r)
