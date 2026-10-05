package com.slayerteleportswap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerTeleportSwapConfig.GROUP)
public interface SlayerTeleportSwapConfig extends Config
{
	String GROUP = "slayerteleportswap";

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
		keyName = "taskOverrides",
		name = "Task overrides",
		description = "One per line, as 'Task = Destination'. Destination is matched against the teleport option text; use 'none' to never swap for that task. Example: Abyssal demons = Stronghold Slayer Cave",
		position = 2
	)
	default String taskOverrides()
	{
		return "";
	}
}
