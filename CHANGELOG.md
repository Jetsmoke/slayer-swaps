# Changelog

All notable changes to Slayer Swaps. Each Plugin Hub update lists what changed since the previous one.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions use small patch steps.

## [Unreleased]

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

[Unreleased]: https://github.com/Jetsmoke/slayer-swaps/compare/666ffcc...HEAD
[1.0.1]: https://github.com/Jetsmoke/slayer-swaps/compare/c6e1cc3...666ffcc
[1.0.0]: https://github.com/Jetsmoke/slayer-swaps/tree/c6e1cc3
