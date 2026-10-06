# Changelog

All notable changes to Slayer Swaps. Each Plugin Hub update lists what changed since the previous one.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions use small patch steps.

## [Unreleased]

## [1.0.2] - 2026-10-05

### Added
- "Show next teleport" box (on by default): shows the task (in the highlight colour), where it's done and the teleport to use next,
  centred, in a short form like "House : Brimhaven", with any step after arriving on its own line. The box widens to
  fit, so nothing runs outside it.
- Once you kill a monster for your task, the guidance (next teleport box, swaps and outlines) turns off. It comes
  back 3 minutes after your last task kill (like a bank trip), or when you take your slayer helmet off and put it
  back on, or when you Check your task (slayer helmet, enchanted gem or slayer ring), and switches to your slayer
  master when the task is done. The slayer helmet options stay available.
- Tasks with more than one location now wait for a choice: nothing is swapped until a location is picked on the
  slayer helmet, and the next teleport box says so. Tasks with one location work straight away.
- Konar's assigned area is used automatically for her tasks, whatever location is chosen for the task otherwise.
  All of her areas (from the OSRS Wiki) are mapped, matched to the names the game uses, and listed in the PDF
  report. Teleports were added for her areas
  no other task uses: Ogre Enclave, Lizardman Canyon, Kebos Swamp, Death Plateau, Neypotzli (calcified moth),
  Jormungand's Prison and the Evil Chicken's Lair.
- Teleport option on the slayer helmet, next to Location (or Master): pick which teleport to use for the task's
  location, or for getting back to your slayer master (for example the max cape's Otto's Grotto instead of a games
  necklace). Choosing a new location goes back to its best teleport.
- "Konar task" in the Testing section: pretend to have a specific Konar task in a specific one of her areas.
- "Random Konar task" in the Testing section: while ticked, pretend to have a random task from Konar's list, in a
  random one of her areas. The Testing settings always match what's being simulated; with all of them off, your real
  task is used.
- Clicking the slayer helmet's Location (or Master) option shows the choices in the chatbox, like the game's own
  "Select an option" dialogs, to pick by mouse or number key; hovering still shows them as a submenu.
- House teleports: when the main way to a task is in your house and you're not there, going home comes first (the
  max cape's Home is swapped to left-click), then the house furniture:
  - Portal nexus: for teleport spells it holds. Its left-click is used when it goes there, otherwise its Teleport
    Menu, where the destination is outlined. The nexus shows what to pick above it, with its menu hotkey
    (for example "Teleport Menu, 6: Kourend Castle").
  - House fairy ring or spiritual fairy tree.
  - Jewellery box (basic, fancy or ornate), for jewellery teleports you aren't carrying: its last destination when
    that's the one, otherwise its Teleport Menu, with the destination and hotkey shown above it.
  The plugin remembers your house's nexus, fairy ring and jewellery box from seeing them in the house. A fairy ring
  you're standing near is used instead of going home to the house's. The next teleport box shows only the step to
  take now ("House : Home", then the house step once you're there). Every way home you have is outlined: max cape,
  construction cape, Teleport to House tablets, and the Teleport to House spell in the spellbook.
- Max cape and fishing cape teleports to Otto's Grotto for the Waterfall Dungeon.
- Mythical cape teleport for the Corsair Cove Dungeon, including the Myths' Guild part Konar assigns.
- Outlines on fairy rings, Wilderness obelisks and the Wilderness levers in the world when they lead to your task
  ("Highlight objects", on by default).
- More Wilderness teleports, checked against the OSRS Wiki:
  - Wilderness obelisks, house or Wilderness ("Wilderness obelisks" setting, off by default since choosing a
    destination needs the hard Wilderness diary). The level to pick is outlined in the obelisk's destination menu.
  - The Edgeville and Ardougne levers to the Deserted Keep, for the Mage Arena, Scorpion Pit (Scorpia), Magic Axe
    Hut, Pirates' Hideout and Deep Wilderness Dungeon.
  - Wilderness sword 3 and 4 (Fountain of Rune) and the Wilderness crabs teleport (next to the Silk Chasm).
  - Callisto, Venenatis and Vet'ion had no teleports; they now have three each.
  - Chaos Elemental (Rogues' Castle), Chaos Fanatic (west of the Lava Maze) and Crazy archaeologist (western ruins)
    now go to the boss instead of the Wilderness in general.
- Test mode (Testing section, off by default): pick any task in its dropdown to see that task's swaps and
  highlights without having it.
- Teleports that need a step after arriving say so, such as "Amulet of glory: Edgeville, then pull the lever to the
  Deserted Keep".

### Changed
- The slayer helmet's right-click option is now always there while the helmet is worn or in the inventory, not
  just the first time. It's shorter: "Location <where>" with a task, "Master <who>" without one, and its submenu
  changes the choice at any time.
- New Wilderness settings section. Burning amulet and Ring of dueling only go to the Wilderness, so they moved
  there and are only used while Wilderness locations is on (or for Krystilia tasks).
- Teleport items are outlined following the item's shape instead of a box around the slot.
- Teleports that get used up (charged jewellery like a necklace of passage, tablets and scrolls) are only used when
  nothing reusable you carry gets there, such as an eternal slayer ring or a max cape.
- Outlines (items, menus and objects) follow only the teleport in use: the one chosen on the slayer helmet, or the
  best one available. Before, every teleport to the location was outlined at once.
- Swaps now run after other plugins' swaps (such as Menu Entry Swapper custom swaps), so Slayer Swaps wins while you
  have a task to go to.
- The chatbox chooser closes when you click anywhere else, like the game's own dialogs.
- The slayer helmet's Location and Teleport options always show, even for a task with one location or one teleport,
  and use the highlight colour.

### Removed
- The "Ask on new task" setting and its chat reminder; the slayer helmet always has the Location option.
- The Slayer Swaps side panel. Each task's location is chosen on the slayer helmet only; locations chosen in the
  panel in 1.0.x carry over.
- Switching individual tasks on or off; tasks switched off in 1.0.x go back to "Not chosen".
- Travel networks (spirit trees, gnome gliders, quetzals, minecarts, canoes, charter ships, minigame teleport) and
  the "Travel networks" setting. Locations only reached that way (Poison Waste Dungeon, White Wolf Mountain,
  Neypotzli, Colossal Wyrm Remains) are no longer offered; Warped creatures no longer has a teleport.

### Fixed
- Locations listed only in the OSRS Wiki's Location Comparison tables were missing: Catacombs of Kourend for Jellies
  (warped jellies) and Black dragons, King Black Dragon's Lair for Black dragons (Wilderness), Uzer Mastaba for
  Scabarites, Stalker Den for Zygomites, Taverley Dungeon for Dwarves and Keldagrim for Trolls.
- Catacombs of Kourend went by fairy ring CIS first, which is far from the entrance. It now uses Xeric's talisman
  (Xeric's Heart) or Kourend Castle Teleport first, as the wiki recommends, then fairy ring DJR.
- Teleport spells could be outlined in the bank, on a tablet of the same name, even when it was scrolled out of
  sight. Spells are now only outlined in the spellbook, and nothing scrolled out of view is outlined.
- The max cape's left-click now goes to Teleports > Boat for tasks reached by Teleport to Boat; it was outlined but
  not swapped.

## [1.0.1] - 2026-10-05

### Fixed
- The plugin failed to start, so it didn't appear in the plugin list and had no panel. A settings helper RuneLite's
  config system doesn't support returned nothing during startup; it now lives in the plugin itself.
- Shutting the plugin down after a failed start no longer throws an error.

## [1.0.0] - 2026-10-05

### Added
- Left-click swaps on teleport items (slayer ring, construction and max cape, Karamja gloves, amulet of glory, burning
  amulet, ring of dueling and more) and fairy rings, to the teleport for the current slayer task's location, or the
  chosen slayer master when there's no task.
- Outlines on the best teleport item being carried, the teleport spell in the spellbook, and the destination in
  teleport menus (fairy ring log, cape teleports, spirit trees, gliders, quetzals, boat teleports).
- First-time choice of task location and slayer master from the slayer helmet's right-click menu.
- Slayer Swaps side panel: switch each task on or off and choose its location and teleport.
- Settings for each teleport type, Wilderness locations (always used for Krystilia tasks) and "Only with slayer
  helmet" (on by default).
- Locations and teleports for every slayer task and boss task, from the OSRS Wiki.

[Unreleased]: https://github.com/Jetsmoke/slayer-swaps/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/Jetsmoke/slayer-swaps/compare/666ffcc...v1.0.2
[1.0.1]: https://github.com/Jetsmoke/slayer-swaps/compare/c6e1cc3...666ffcc
[1.0.0]: https://github.com/Jetsmoke/slayer-swaps/tree/c6e1cc3
