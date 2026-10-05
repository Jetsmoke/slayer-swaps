package com.slayerteleportswap;

import static com.slayerteleportswap.Location.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

/**
 * The locations each slayer task can be done at, default first.
 * Monster lists are from the OSRS wiki pages for each location.
 */
final class TaskLocations
{
	private static final Map<String, List<Location>> LOCATIONS = ImmutableMap.<String, List<Location>>builder()
		.put(normalize("Ankou"), ImmutableList.of(STRONGHOLD))
		.put(normalize("Fire giants"), ImmutableList.of(STRONGHOLD))
		.put(normalize("Hellhounds"), ImmutableList.of(STRONGHOLD))
		.put(normalize("Aberrant spectres"), ImmutableList.of(STRONGHOLD, SLAYER_TOWER))
		.put(normalize("Crawling hands"), ImmutableList.of(SLAYER_TOWER))
		.put(normalize("Banshees"), ImmutableList.of(SLAYER_TOWER))
		.put(normalize("Infernal mages"), ImmutableList.of(SLAYER_TOWER))
		.put(normalize("Bloodveld"), ImmutableList.of(SLAYER_TOWER, STRONGHOLD))
		.put(normalize("Gargoyles"), ImmutableList.of(SLAYER_TOWER))
		.put(normalize("Nechryael"), ImmutableList.of(SLAYER_TOWER))
		.put(normalize("Abyssal demons"), ImmutableList.of(SLAYER_TOWER, ABYSSAL_SIRE))
		.put(normalize("Abyssal Sire"), ImmutableList.of(ABYSSAL_SIRE))
		.put(normalize("Cave crawlers"), ImmutableList.of(FREMENNIK))
		.put(normalize("Rockslugs"), ImmutableList.of(FREMENNIK))
		.put(normalize("Cockatrice"), ImmutableList.of(FREMENNIK))
		.put(normalize("Pyrefiends"), ImmutableList.of(FREMENNIK))
		.put(normalize("Basilisks"), ImmutableList.of(FREMENNIK))
		.put(normalize("Jellies"), ImmutableList.of(FREMENNIK))
		.put(normalize("Turoth"), ImmutableList.of(FREMENNIK))
		.put(normalize("Kurask"), ImmutableList.of(FREMENNIK))
		.put(normalize("Terror dogs"), ImmutableList.of(TARNS_LAIR))
		.put(normalize("Dark beasts"), ImmutableList.of(DARK_BEASTS))
		.put(normalize("Wyrms"), ImmutableList.of(WYRMSCRAIG))
		.put(normalize("Smoke devils"), ImmutableList.of(SMOKE_DEVIL_DUNGEON))
		.put(normalize("Thermonuclear smoke devil"), ImmutableList.of(SMOKE_DEVIL_DUNGEON))
		.build();

	private TaskLocations()
	{
	}

	/**
	 * @return the locations for a task, default first; empty if the task isn't known
	 */
	static List<Location> get(String taskName)
	{
		return LOCATIONS.getOrDefault(normalize(taskName), ImmutableList.of());
	}

	/**
	 * Reduces a task name to a comparable key, so "Abyssal demons", "abyssal demon" and
	 * "ABYSSAL DEMONS" all match, and "Jellies" matches "Jelly".
	 */
	static String normalize(String name)
	{
		String s = name.toLowerCase().replaceAll("[^a-z]", "");
		if (s.endsWith("ies"))
		{
			return s.substring(0, s.length() - 3) + "y";
		}
		if (s.endsWith("s") && !s.endsWith("ss"))
		{
			return s.substring(0, s.length() - 1);
		}
		return s;
	}
}
