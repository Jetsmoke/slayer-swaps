package com.slayerteleportswap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A place a slayer task can be done, and the teleport options that reach it.
 * Option text is as shown in game (checked from the right-click menu), which doesn't always match the wiki.
 */
@Getter
@RequiredArgsConstructor
public enum Location
{
	STRONGHOLD("Stronghold Slayer Cave", "Stronghold", null),
	SLAYER_TOWER("Slayer Tower", "Slayer Tower", null),
	FREMENNIK("Fremennik Slayer Dungeon", "Fremennik Dungeon", null),
	TARNS_LAIR("Tarn's Lair", "Tarn's Lair", null),
	DARK_BEASTS("Dark Beasts", "Dark Beasts", null),
	WYRMSCRAIG("Wyrmscraig Cavern", "Wyrmscraig Cavern", null),
	ABYSSAL_SIRE("Abyssal Sire", null, "DIP"),
	SMOKE_DEVIL_DUNGEON("Smoke Devil Dungeon", null, "BKP");

	private final String name;
	// Slayer ring Teleport submenu option, or null if the ring doesn't go here
	private final String ringOption;
	// Fairy ring code, or null if no fairy ring is used
	private final String fairyCode;

	@Override
	public String toString()
	{
		return name;
	}
}
