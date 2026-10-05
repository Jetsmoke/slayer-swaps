package com.slayerswaps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.Test;

public class SlayerDataTest
{
	private final SlayerData data = SlayerData.load(new Gson());

	@Test
	public void findsTasksByGameAndWikiNames()
	{
		assertEquals("Abyssal demons", data.findTask("Abyssal Demons").getName());
		assertEquals("Kurasks", data.findTask("Kurask").getName());
		assertEquals("Werewolves", data.findTask("Werewolf").getName());
		assertEquals("Jellies", data.findTask("Jelly").getName());
		assertNotNull(data.findTask("Thermonuclear smoke devil"));
		assertNull(data.findTask("Not a monster"));
	}

	@Test
	public void everySettingIsDeclaredInTheConfig()
	{
		// RuneLite only stores defaults for the config interface's own methods; an inherited setting has no value,
		// and the settings screen fails to open
		for (java.lang.reflect.Method m : SlayerSwapsConfig.class.getMethods())
		{
			if (m.isAnnotationPresent(net.runelite.client.config.ConfigItem.class))
			{
				assertEquals(m.getName(), SlayerSwapsConfig.class, m.getDeclaringClass());
			}
		}
	}

	@Test
	public void simulatedTasksMatchTheData()
	{
		// SimulatedTask is generated from the data; regenerate it (tools/gen_simulated_tasks.py) when the data changes
		for (SimulatedTask sim : SimulatedTask.values())
		{
			if (sim.getTask() != null)
			{
				assertNotNull(sim.name(), data.findTask(sim.getTask()));
			}
		}
		assertEquals(data.getTasks().size() + 2, SimulatedTask.values().length);
		for (KonarTestTask konar : KonarTestTask.values())
		{
			if (konar.getTask() != null)
			{
				SlayerData.TaskData task = data.findTask(konar.getTask());
				assertTrue(konar.name(), task != null && task.getKonar().containsKey(konar.getArea()));
			}
		}
	}

	@Test
	public void konarAreasAreKnownLocations()
	{
		for (SlayerData.TaskData task : data.getTasks())
		{
			if (task.getKonar() != null)
			{
				for (java.util.List<String> locations : task.getKonar().values())
				{
					for (String location : locations)
					{
						assertNotNull("Konar " + task.getName() + " -> " + location, data.location(location));
					}
				}
			}
		}
	}

	@Test
	public void everyRouteTypeAndLocationIsKnown()
	{
		for (SlayerData.TaskData task : data.getTasks())
		{
			for (String location : task.getLocations())
			{
				assertNotNull(task.getName() + " -> " + location, data.location(location));
				for (SlayerData.Route route : data.routes(location))
				{
					assertNotNull(location + " " + route.getType(), route.routeType());
				}
			}
		}
		for (SlayerData.MasterData master : data.getMasters())
		{
			assertNotNull(master.getKey(), data.location(master.getLocation()));
		}
		for (SlayerMaster master : SlayerMaster.values())
		{
			assertTrue(master.name(), master == SlayerMaster.NONE || data.master(master.name()) != null);
		}
	}

	@Test
	public void defaultsUseTheBestTeleport()
	{
		assertEquals("Slayer Tower", data.findTask("Gargoyles").getLocations().get(0));
		assertEquals("Fremennik Slayer Dungeon", data.findTask("Kurask").getLocations().get(0));
		assertTrue(data.findTask("Abyssal demons").getLocations().contains("Abyssal Nexus"));
		assertTrue(data.location("Wilderness Slayer Cave").isWilderness());
		assertFalse(data.location("Slayer Tower").isWilderness());
	}
}
