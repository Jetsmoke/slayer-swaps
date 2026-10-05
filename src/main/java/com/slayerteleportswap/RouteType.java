package com.slayerteleportswap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Kinds of teleport a location can be reached by. The type names match the "type" field in slayer_locations.json.
 */
@Getter
@RequiredArgsConstructor
public enum RouteType
{
	RING("ring", "Slayer ring"),
	FAIRY("fairy", "Fairy ring"),
	PORTAL("portal", "House portal (construction / max cape)"),
	MAXCAPE("maxcape", "Max cape"),
	KARAMJA_GLOVES("karamja_gloves", "Karamja gloves 4"),
	BURNING_AMULET("burning_amulet", "Burning amulet"),
	RING_OF_DUELING("ring_of_dueling", "Ring of dueling"),
	BOAT("boat", "Teleport to Boat");

	private final String key;
	private final String displayName;

	static RouteType forKey(String key)
	{
		for (RouteType type : values())
		{
			if (type.key.equals(key))
			{
				return type;
			}
		}
		return null;
	}
}
