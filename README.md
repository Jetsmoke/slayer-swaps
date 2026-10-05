# Slayer Teleport Swap

Makes getting to your slayer task a matter of clicking what's highlighted:

- **Left-click swaps** on teleport items (slayer ring, construction cape, max cape, Karamja gloves, burning amulet,
  ring of dueling) and fairy rings, to the teleport for your task's location — or to your slayer master when you have
  no task.
- **Highlights** the teleport item to use in your inventory/equipment, and the destination to pick in teleport menus
  (fairy ring log, max cape and construction cape teleports, boat teleports, item dialogs).
- **Slayer Teleports panel** in the sidebar: for every task, switch it on or off and choose the location and teleport.
  It opens by itself the first time you get a task that can be done in more than one place.
- **Wilderness** locations and teleports are behind their own setting, and are used automatically for Krystilia tasks.

Every slayer master, slayer task and boss task, with its locations and teleports, is listed in
[Slayer_Teleports.pdf](Slayer_Teleports.pdf). The data lives in
`src/main/resources/com/slayerteleportswap/slayer_locations.json` and is built from the
[OSRS Wiki](https://oldschool.runescape.wiki) by `tools/curate.py` (keeping the best 3 locations per task and 3
teleports per location); `tools/pdf_report.py` regenerates the PDF.
