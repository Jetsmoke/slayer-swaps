package com.slayerswaps;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Slayer masters to return to when there's no task. Keys match the masters in slayer_locations.json.
 */
@Getter
@RequiredArgsConstructor
public enum SlayerMaster
{
	// Until the player picks one on the slayer helmet
	NOT_CHOSEN("Not chosen"),
	MORTIMER("Mortimer"),
	DURADEL("Duradel / Kuradal"),
	NIEVE("Nieve / Steve"),
	KONAR("Konar quo Maten"),
	CHAELDAR("Chaeldar"),
	VANNAKA("Vannaka"),
	MAZCHNA("Mazchna"),
	TURAEL("Turael / Spria"),
	KRYSTILIA("Krystilia"),
	NONE("None");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
