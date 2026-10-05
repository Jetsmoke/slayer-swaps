"""
Writes Slayer_Swaps.pdf: every slayer master, slayer task and boss task with the teleports the plugin supports.
Needs reportlab (pip install reportlab).

    python3 tools/pdf_report.py
"""
import json
import os
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import letter, landscape
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle, KeepTogether

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'src/main/resources/com/slayerswaps/slayer_locations.json')
OUT = os.path.join(ROOT, 'Slayer_Swaps.pdf')

data = json.load(open(DATA))
LABEL = {
    'ring': 'Slayer ring', 'fairy': 'Fairy ring', 'portal': 'House portal', 'maxcape': 'Max cape',
    'karamja_gloves': 'Karamja gloves 4', 'burning_amulet': 'Burning amulet', 'ring_of_dueling': 'Ring of dueling',
    'amulet_of_glory': 'Amulet of glory', 'boat': 'Boat moored at',
}

ACCENT = colors.HexColor('#7a2620')
HEAD_BG = colors.HexColor('#2b2523')
ZEBRA = colors.HexColor('#f4f1ef')
NONE_BG = colors.HexColor('#fbe9e7')
GRID = colors.HexColor('#d9d3cf')

styles = getSampleStyleSheet()
title = ParagraphStyle('t', parent=styles['Title'], fontName='Helvetica-Bold', fontSize=20, textColor=HEAD_BG,
                       alignment=TA_LEFT, spaceAfter=4)
h2 = ParagraphStyle('h2', parent=styles['Heading2'], fontName='Helvetica-Bold', fontSize=13, textColor=ACCENT,
                    spaceBefore=10, spaceAfter=6)
body = ParagraphStyle('b', parent=styles['Normal'], fontName='Helvetica', fontSize=8.5, leading=10.5)
small = ParagraphStyle('s', parent=body, fontSize=8, leading=10, textColor=colors.HexColor('#555049'))
cell = ParagraphStyle('c', parent=body, fontSize=8, leading=10)
cell_bold = ParagraphStyle('cb', parent=cell, fontName='Helvetica-Bold')
head = ParagraphStyle('h', parent=cell, fontName='Helvetica-Bold', textColor=colors.white)


def describe(r):
    text = describe_teleport(r)
    return text if not r.get('note') else f"{text}, then {r['note']}"


def describe_teleport(r):
    t, v = r['type'], r.get('value')
    if t == 'fairy':
        return f'Fairy ring {v}'
    if t == 'spell':
        return f'{v} (spell/tablet)'
    if t == 'item':
        item = data['items'].get(r.get('item'), {})
        label = item.get('label', r.get('item'))
        return label if not v else f'{label}: {v}'
    if t == 'obelisk':
        return f'Obelisk: {v}'
    return f'{LABEL.get(t, t)}: {v}'


def loc_info(l):
    return data['locations'].get(l, {'routes': [], 'wilderness': False})


def task_row(t):
    locs = t['locations']
    normal = [l for l in locs if loc_info(l)['routes'] and not loc_info(l)['wilderness']]
    # Tasks with one place to go use it straight away; the rest wait for a choice
    usable = normal or [l for l in locs if loc_info(l)['routes']]
    default = usable[0] if len(usable) == 1 else ('Choose (%d places)' % len(usable) if usable else None)
    seen = set()
    lines = []
    for l in locs:
        info = loc_info(l)
        if not info['routes']:
            continue
        names = []
        for r in info['routes']:
            d = describe(r)
            seen.add(d)
            names.append(escape(d))
        tag = ' <font color="#6b3fa0">(Wilderness)</font>' if info['wilderness'] else ''
        lines.append(f'<b>{escape(l)}</b>{tag}: ' + ', '.join(names))
    n = len(seen)
    avail = f'<b>Yes</b> ({n})' if n else '<font color="#9c2b23"><b>None</b></font>'
    if not lines:
        where = ', '.join(locs) or 'no locations listed on the wiki'
        lines = [f'<i>No supported teleport. Found at: {escape(where)}</i>']
    return [Paragraph(escape(t['name']), cell_bold), Paragraph(avail, cell),
            Paragraph(escape(default) if default else '-', cell), Paragraph('<br/>'.join(lines), cell)], n


def table(rows, widths, none_rows):
    t = Table(rows, colWidths=widths, repeatRows=1)
    style = [
        ('BACKGROUND', (0, 0), (-1, 0), HEAD_BG),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('GRID', (0, 0), (-1, -1), 0.4, GRID),
        ('TOPPADDING', (0, 0), (-1, -1), 3), ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
        ('LEFTPADDING', (0, 0), (-1, -1), 4), ('RIGHTPADDING', (0, 0), (-1, -1), 4),
    ]
    for i in range(1, len(rows)):
        if i in none_rows:
            style.append(('BACKGROUND', (0, i), (-1, i), NONE_BG))
        elif i % 2 == 0:
            style.append(('BACKGROUND', (0, i), (-1, i), ZEBRA))
    t.setStyle(TableStyle(style))
    return t


def task_table(tasks):
    rows = [[Paragraph(h, head) for h in ['Task', 'Teleports', 'Location', 'All options (location: teleports)']]]
    none_rows = set()
    for t in sorted(tasks, key=lambda t: t['name'].lower()):
        row, n = task_row(t)
        if not n:
            none_rows.add(len(rows))
        rows.append(row)
    return table(rows, [1.45 * inch, 0.75 * inch, 1.55 * inch, 6.25 * inch], none_rows)


def konar_table():
    rows = [[Paragraph(h, head) for h in ['Task', "Konar's area", 'Teleports', 'Locations and teleports']]]
    none_rows = set()
    for t in sorted((t for t in data['tasks'] if t.get('konar')), key=lambda t: t['name'].lower()):
        first = True
        for area, locs in t['konar'].items():
            lines = [f'<b>{escape(l)}</b>: ' + ', '.join(escape(describe(r)) for r in loc_info(l)['routes'])
                     for l in locs if loc_info(l)['routes']]
            n = sum(len(loc_info(l)['routes']) for l in locs)
            if not lines:
                none_rows.add(len(rows))
                lines = ['<i>No supported teleport</i>']
            rows.append([Paragraph(escape(t['name']) if first else '', cell_bold), Paragraph(escape(area), cell),
                         Paragraph(f'<b>{n}</b>' if n else '<font color="#9c2b23"><b>None</b></font>', cell),
                         Paragraph('<br/>'.join(lines), cell)])
            first = False
    return table(rows, [1.45 * inch, 1.75 * inch, 0.75 * inch, 6.05 * inch], none_rows)


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 7.5)
    canvas.setFillColor(colors.HexColor('#7d7570'))
    canvas.drawString(0.5 * inch, 0.35 * inch, 'Slayer Swaps - teleports per slayer task. Source: Old School RuneScape Wiki.')
    canvas.drawRightString(10.5 * inch, 0.35 * inch, f'Page {doc.page}')
    canvas.restoreState()


def main():
    tasks = [t for t in data['tasks'] if not t['boss']]
    bosses = [t for t in data['tasks'] if t['boss']]
    covered = sum(1 for t in data['tasks'] if any(loc_info(l)['routes'] for l in t['locations']))
    total_routes = sum(len(v['routes']) for v in data['locations'].values())

    story = [
        Paragraph('Slayer Swaps', title),
        Paragraph(f'{len(tasks)} slayer tasks and {len(bosses)} boss tasks: {covered} have at least one supported '
                  f'teleport, {len(data["tasks"]) - covered} have none. {len(data["masters"])} slayer masters. '
                  f'{total_routes} teleports mapped across {len(data["locations"])} locations.', body),
        Spacer(1, 4),
        Paragraph('Teleports covers slayer ring, fairy rings, house portals (construction/max cape and house tablets), '
                  'max cape, teleport items (glory, hilt, necklaces, talismans and more), spells and their tablets, '
                  'Wilderness obelisks and Teleport to Boat. A task with one location uses it straight away; for the rest, '
                  'choose by right-clicking your slayer helmet. Konar\'s tasks use the area she '
                  'assigns. Wilderness locations are only used with the Wilderness setting on, or for Krystilia '
                  'tasks. Rows in red have no supported teleport.', small),
        Spacer(1, 6),
        Paragraph('Slayer masters (getting back after a task)', h2),
    ]
    mrows = [[Paragraph(h, head) for h in ['Master', 'Location', 'Teleports', 'Ways back']]]
    none_rows = set()
    for m in data['masters']:
        ways = [escape(describe(r)) for r in m['routes']]
        if m.get('note'):
            ways.append(f'<i>{escape(m["note"])}</i>')
        if not m['routes']:
            none_rows.add(len(mrows))
        mrows.append([Paragraph(escape(m['name']), cell_bold), Paragraph(escape(m['location']), cell),
                      Paragraph(f'<b>{len(m["routes"])}</b>', cell), Paragraph('<br/>'.join(ways), cell)])
    story.append(table(mrows, [1.45 * inch, 1.55 * inch, 0.75 * inch, 6.25 * inch], none_rows))
    story += [Paragraph('Slayer tasks', h2), task_table(tasks), Paragraph('Boss tasks', h2), task_table(bosses),
              Paragraph("Konar's areas", h2),
              Paragraph('Konar names an area with every task; the plugin goes there whatever is chosen for the task. '
                        'Areas in red have no supported teleport yet.', small), Spacer(1, 4), konar_table()]

    doc = SimpleDocTemplate(OUT, pagesize=landscape(letter), leftMargin=0.5 * inch, rightMargin=0.5 * inch,
                            topMargin=0.5 * inch, bottomMargin=0.55 * inch, title='Slayer Swaps',
                            author='Slayer Swaps')
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
