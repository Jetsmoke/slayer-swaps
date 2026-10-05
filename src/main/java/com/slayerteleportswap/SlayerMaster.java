package com.slayerteleportswap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SlayerMaster
{
	MORTIMER("Mortimer", "Wyrmscraig Cavern"),
	NIEVE("Nieve / Steve", "Stronghold Slayer Cave"),
	NONE("None", null);

	private final String name;
	// Slayer ring destination closest to this master, or null if the ring can't reach them
	private final String ringDestination;

	@Override
	public String toString()
	{
		return name;
	}
}
