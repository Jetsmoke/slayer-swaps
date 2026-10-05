package com.slayerteleportswap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerTeleportSwapConfig.GROUP)
public interface SlayerTeleportSwapConfig extends Config
{
	String GROUP = "slayerteleportswap";
	// Per-task chosen location is stored under this prefix + the normalized task name
	String LOCATION_KEY_PREFIX = "location_";

	@ConfigItem(
		keyName = "slayerMaster",
		name = "Slayer master",
		description = "Where teleports take you when you have no task",
		position = 0
	)
	default SlayerMaster slayerMaster()
	{
		return SlayerMaster.MORTIMER;
	}

	@ConfigItem(
		keyName = "swapSlayerRing",
		name = "Slayer ring",
		description = "Swap the slayer ring's left-click to the teleport for your task or master",
		position = 1
	)
	default boolean swapSlayerRing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "swapFairyRing",
		name = "Fairy rings",
		description = "When your task's location uses a fairy ring, swap the fairy ring's left-click to the last destination if it matches, otherwise to Configure",
		position = 2
	)
	default boolean swapFairyRing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "locationPicker",
		name = "Location picker",
		description = "Add a 'Slayer location' option to the slayer ring's right-click menu when your task can be done in more than one place",
		position = 3
	)
	default boolean locationPicker()
	{
		return true;
	}
}
