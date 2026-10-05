package com.slayerteleportswap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SlayerMaster
{
	MORTIMER("Mortimer", Location.WYRMSCRAIG),
	NIEVE("Nieve / Steve", Location.STRONGHOLD),
	NONE("None", null);

	private final String name;
	// Where to teleport to reach this master, or null to not swap when there's no task
	private final Location location;

	@Override
	public String toString()
	{
		return name;
	}
}
