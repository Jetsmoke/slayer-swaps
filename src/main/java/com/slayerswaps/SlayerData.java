package com.slayerswaps;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import net.runelite.api.coords.WorldPoint;

/**
 * Slayer tasks, the locations they're done at and the teleports that reach each location.
 * Loaded from slayer_locations.json, which is built from the OSRS wiki (see README).
 */
@Data
class SlayerData
{
	private Map<String, LocationData> locations;
	private List<TaskData> tasks;
	private List<MasterData> masters;
	private Map<String, ItemData> items;

	private transient Map<String, TaskData> taskIndex;

	@Data
	static class Route
	{
		private String type;
		private String value;
		// Catalog key for "item" and "network" routes
		private String item;
		// What to do after teleporting, such as pulling a lever
		private String note;
		// The quetzal landing site to fly to after teleporting, such as "The Teomat"
		private String quetzal;

		RouteType routeType()
		{
			return RouteType.forKey(type);
		}
	}

	@Data
	static class LocationData
	{
		private List<Route> routes;
		private boolean wilderness;
		// For places underground or otherwise apart from the surface map: the way in from the surface
		private Spot entrance;
	}

	/**
	 * A map tile, from the OSRS Wiki.
	 */
	@Data
	static class Spot
	{
		private int x;
		private int y;
		private int plane;

		WorldPoint toWorldPoint()
		{
			return new WorldPoint(x, y, plane);
		}
	}

	@Data
	static class TaskData
	{
		private String name;
		private List<String> aliases;
		private List<String> masters;
		private boolean boss;
		private List<String> locations;
		// Konar's areas for this task: area name -> locations in it
		private Map<String, List<String>> konar;
		// The middle of the task monsters' spawns at each location
		private Map<String, Spot> spots;
		// The monsters that count for the task, as the wiki names them
		private List<String> monsters;
	}

	@Data
	static class ItemData
	{
		private String label;
		// Lower-case item name prefixes, so every charge/variant of the item matches
		private List<String> names;
		private List<String> options;
	}

	@Data
	static class MasterData
	{
		private String key;
		private String name;
		private String location;
		private List<Route> routes;
		private String note;
		// Where the master stands
		private Spot spot;
	}

	static SlayerData load(Gson gson)
	{
		try (InputStream in = SlayerData.class.getResourceAsStream("slayer_locations.json");
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			SlayerData data = gson.fromJson(reader, SlayerData.class);
			data.buildIndex();
			return data;
		}
		catch (IOException | NullPointerException e)
		{
			throw new IllegalStateException("Unable to load slayer_locations.json", e);
		}
	}

	private void buildIndex()
	{
		taskIndex = new HashMap<>();
		// A task's own name wins over another task's alias (Mogres also goes by "Ogres")
		for (TaskData task : tasks)
		{
			taskIndex.put(normalize(task.getName()), task);
		}
		for (TaskData task : tasks)
		{
			for (String alias : task.getAliases())
			{
				taskIndex.putIfAbsent(normalize(alias), task);
			}
		}
	}

	TaskData findTask(String name)
	{
		return name == null ? null : taskIndex.get(normalize(name));
	}

	LocationData location(String name)
	{
		return locations.get(name);
	}

	List<Route> routes(String location)
	{
		LocationData data = locations.get(location);
		return data == null || data.getRoutes() == null ? Collections.emptyList() : data.getRoutes();
	}

	MasterData master(String key)
	{
		for (MasterData master : masters)
		{
			if (master.getKey().equals(key))
			{
				return master;
			}
		}
		return null;
	}

	/**
	 * Reduces a task name to a comparable key, so "Abyssal demons", "abyssal demon" and "ABYSSAL DEMONS" all
	 * match, as do "Jellies"/"Jelly" and "Werewolves"/"Werewolf".
	 */
	static String normalize(String name)
	{
		String s = name.toLowerCase().replaceAll("[^a-z]", "");
		if (s.endsWith("ves"))
		{
			return s.substring(0, s.length() - 3) + "f";
		}
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
