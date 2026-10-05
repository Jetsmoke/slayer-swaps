package com.slayerteleportswap;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps slayer task names to the teleport destination used for them.
 * Default locations are from the OSRS wiki pages for each slayer ring destination.
 */
final class TaskDestinations
{
	static final String NONE = "none";

	private static final String STRONGHOLD = "Stronghold Slayer Cave";
	private static final String SLAYER_TOWER = "Slayer Tower";
	private static final String FREMENNIK = "Fremennik Slayer Dungeon";
	private static final String TARNS_LAIR = "Tarn's Lair";
	private static final String DARK_BEASTS = "Dark Beasts";
	private static final String WYRMSCRAIG = "Wyrmscraig Cavern";

	private static final Map<String, String> DEFAULTS = ImmutableMap.<String, String>builder()
		.put(normalize("Ankou"), STRONGHOLD)
		.put(normalize("Fire giants"), STRONGHOLD)
		.put(normalize("Hellhounds"), STRONGHOLD)
		.put(normalize("Aberrant spectres"), STRONGHOLD)
		.put(normalize("Crawling hands"), SLAYER_TOWER)
		.put(normalize("Banshees"), SLAYER_TOWER)
		.put(normalize("Infernal mages"), SLAYER_TOWER)
		.put(normalize("Bloodveld"), SLAYER_TOWER)
		.put(normalize("Gargoyles"), SLAYER_TOWER)
		.put(normalize("Nechryael"), SLAYER_TOWER)
		.put(normalize("Abyssal demons"), SLAYER_TOWER)
		.put(normalize("Cave crawlers"), FREMENNIK)
		.put(normalize("Rockslugs"), FREMENNIK)
		.put(normalize("Cockatrice"), FREMENNIK)
		.put(normalize("Pyrefiends"), FREMENNIK)
		.put(normalize("Basilisks"), FREMENNIK)
		.put(normalize("Jellies"), FREMENNIK)
		.put(normalize("Turoth"), FREMENNIK)
		.put(normalize("Kurask"), FREMENNIK)
		.put(normalize("Terror dogs"), TARNS_LAIR)
		.put(normalize("Dark beasts"), DARK_BEASTS)
		.put(normalize("Wyrms"), WYRMSCRAIG)
		.build();

	private final Map<String, String> overrides = new HashMap<>();

	void setOverrides(String text)
	{
		overrides.clear();
		if (text == null)
		{
			return;
		}

		for (String line : text.split("\n"))
		{
			int eq = line.indexOf('=');
			if (eq <= 0)
			{
				continue;
			}

			String task = normalize(line.substring(0, eq));
			String destination = line.substring(eq + 1).trim();
			if (!task.isEmpty() && !destination.isEmpty())
			{
				overrides.put(task, destination);
			}
		}
	}

	/**
	 * @return the destination for a task, or null if the task shouldn't cause a swap
	 */
	String get(String taskName)
	{
		String key = normalize(taskName);
		String destination = overrides.containsKey(key) ? overrides.get(key) : DEFAULTS.get(key);
		return destination == null || destination.equalsIgnoreCase(NONE) ? null : destination;
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
