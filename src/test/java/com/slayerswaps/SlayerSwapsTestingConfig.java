package com.slayerswaps;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerSwapsTestingConfig.GROUP)
public interface SlayerSwapsTestingConfig extends Config
{
	String GROUP = "slayerswapstesting";

	@ConfigItem(keyName = "simulatedTask", name = "Test task", description = "Pretend to have this task, to see its swaps and highlights. Your real task is used when this and the settings below are all off.", position = 0)
	default SimulatedTask simulatedTask()
	{
		return SimulatedTask.OFF;
	}

	@ConfigItem(keyName = "konarTestTask", name = "Konar task", description = "Pretend to have this Konar task in this one of her areas. Used instead of Test task while it isn't Off.", position = 1)
	default KonarTestTask konarTestTask()
	{
		return KonarTestTask.OFF;
	}

	@ConfigItem(keyName = "randomTask", name = "Random task", description = "While ticked, pretend to have a random slayer task (said in chat). Untick and tick again for another.", position = 2)
	default boolean randomTask()
	{
		return false;
	}

	@ConfigItem(keyName = "randomKonarTask", name = "Random Konar task", description = "While ticked, pretend to have a random Konar task in a random one of her areas (said in chat). Untick and tick again for another.", position = 3)
	default boolean randomKonarTask()
	{
		return false;
	}

	@ConfigItem(keyName = "taskDone", name = "Task done", description = "While ticked, pretend the task above (or your real task) is done, to see the way back to your slayer master.", position = 4)
	default boolean taskDone()
	{
		return false;
	}
}
