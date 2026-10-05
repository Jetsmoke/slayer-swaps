package com.slayerteleportswap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class TaskDestinationsTest
{
	@Test
	public void matchesTaskNameVariants()
	{
		TaskDestinations d = new TaskDestinations();
		assertEquals("Slayer Tower", d.get("Gargoyles"));
		assertEquals("Slayer Tower", d.get("ABYSSAL DEMONS"));
		assertEquals("Fremennik Slayer Dungeon", d.get("Jelly"));
		assertEquals("Fremennik Slayer Dungeon", d.get("Cockatrices"));
		assertNull(d.get("Hydras"));
	}

	@Test
	public void overridesWin()
	{
		TaskDestinations d = new TaskDestinations();
		d.setOverrides("Abyssal demons = Stronghold Slayer Cave\ngargoyle=none\nnonsense line\nHydras = Karuulm");
		assertEquals("Stronghold Slayer Cave", d.get("Abyssal demons"));
		assertNull(d.get("Gargoyles"));
		assertEquals("Karuulm", d.get("Hydras"));
		assertEquals("Fremennik Slayer Dungeon", d.get("Kurask"));
	}
}
