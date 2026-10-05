# Slayer Swaps

Automatically swaps menu entries, and highlights teleport items and spells to quickly teleport to your slayer task.
Supports choosing your preferred slayer master and preferred task location.

**To start**, right-click your slayer helmet: the first time, it offers *Choose slayer master* (with no task) or
*Choose location* (for a task that can be done in more than one place). After that, change things in the
Slayer Swaps side panel or the plugin settings.

- **Left-click swaps** on teleport items (slayer ring, construction and max cape, Karamja gloves, glory, burning amulet,
  ring of dueling and more) and fairy rings, to the teleport for your task's location, or your slayer master when you
  have no task.
- **Highlights** the best teleport item you're carrying, the spell in your spellbook, and the destination to pick in
  teleport menus (fairy ring log, cape teleports, spirit trees, gliders, quetzals, boat teleports and more).
- **Slayer Swaps panel**: for every task, switch it on or off and choose the location and teleport.
- **Wilderness** locations are behind their own setting, and are used automatically for Krystilia tasks.
- **Only with slayer helmet**: optionally only act while you're wearing a slayer helmet.

Every slayer master, slayer task and boss task, with its locations and teleports, is listed in
[Slayer_Swaps.pdf](Slayer_Swaps.pdf). The data lives in
`src/main/resources/com/slayerswaps/slayer_locations.json` and is built from the
[OSRS Wiki](https://oldschool.runescape.wiki) by `tools/curate.py` (keeping the best 3 locations per task and 3
teleports per location); `tools/pdf_report.py` regenerates the PDF.
