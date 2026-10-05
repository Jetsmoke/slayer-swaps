# Slayer Swaps

Automatically swaps menu entries, and highlights teleport items and spells to quickly teleport to your slayer task.
Supports choosing your preferred slayer master and preferred task location.

**To start**, right-click your slayer helmet (worn or in your inventory): *Location* shows where your task is and
lets you choose (hover for a submenu, or click to pick in the chatbox by mouse or number key); with no task,
*Master* chooses your slayer master. A task with only one location needs no choice.

- **Left-click swaps** on teleport items (slayer ring, construction and max cape, Karamja gloves, glory, burning amulet,
  ring of dueling and more) and fairy rings, to the teleport for your task's location, or your slayer master when you
  have no task.
- **Highlights** the best teleport item you're carrying, the spell in your spellbook, and the destination to pick in
  teleport menus (fairy ring log, cape teleports, Wilderness obelisks, boat teleports and more).
- **Konar**: her assigned area is used automatically.
- **Show next teleport**: a box with the task, its location and the teleport to use next.
- **Outlines** on fairy rings, Wilderness obelisks and levers when they lead to your task.
- **Test mode** (Testing settings): pretend to have any task, or a random Konar task, to see its swaps and highlights.
- **Wilderness** locations are behind their own setting, and are used automatically for Krystilia tasks.
- **Only with slayer helmet** (on by default): only acts while you're wearing a slayer helmet. Turn it off in settings to use it any time.

Every slayer master, slayer task and boss task, with its locations and teleports, is listed in
[Slayer_Swaps.pdf](Slayer_Swaps.pdf). The data lives in
`src/main/resources/com/slayerswaps/slayer_locations.json` and is built from the
[OSRS Wiki](https://oldschool.runescape.wiki) by `tools/curate.py` (keeping the best 3 locations per task and 3
teleports per location); `tools/pdf_report.py` regenerates the PDF.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each update.
