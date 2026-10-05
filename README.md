# Slayer Teleport Swap

Makes getting to your slayer task a matter of clicking what's highlighted:

- **Left-click swaps** on teleport items (slayer ring, construction cape, max cape, Karamja gloves, burning amulet,
  ring of dueling) and fairy rings, to the teleport for your task's location — or to your slayer master when you have
  no task.
- **Highlights** the teleport item to use in your inventory/equipment, and the destination to pick in teleport menus
  (fairy ring log, max cape and construction cape teleports, boat teleports, item dialogs).
- **Location picker**: when a task can be done in several places, right-click a teleport item or fairy ring and choose
  *Slayer location*. The choice is remembered per task.
- **Wilderness** locations and teleports are behind their own setting, and are used automatically for Krystilia tasks.

Every slayer task and boss task, with its locations and teleports, is listed in
[SLAYER_TELEPORTS.md](SLAYER_TELEPORTS.md). The data lives in
`src/main/resources/com/slayerteleportswap/slayer_locations.json` and is built from the
[OSRS Wiki](https://oldschool.runescape.wiki) by `tools/curate.py`; `tools/report.py` regenerates the listing.
