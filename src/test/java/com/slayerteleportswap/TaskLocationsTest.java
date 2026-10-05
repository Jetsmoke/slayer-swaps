package com.slayerteleportswap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class TaskLocationsTest
{
	@Test
	public void matchesTaskNameVariants()
	{
		assertEquals(Location.SLAYER_TOWER, TaskLocations.get("Gargoyles").get(0));
		assertEquals(ImmutableList.of(Location.SLAYER_TOWER, Location.ABYSSAL_SIRE), TaskLocations.get("Abyssal Demons"));
		assertEquals(Location.FREMENNIK, TaskLocations.get("Jelly").get(0));
		assertEquals(Location.FREMENNIK, TaskLocations.get("Cockatrices").get(0));
		assertTrue(TaskLocations.get("Hydras").isEmpty());
	}
}
