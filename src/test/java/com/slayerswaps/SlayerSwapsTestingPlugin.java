package com.slayerswaps;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Development only: pretends to have any task, to try Slayer Swaps without it. Lives in the test sources, so it's
 * only in the dev client, never in Plugin Hub releases.
 */
@PluginDescriptor(
	name = "Slayer Swaps Testing",
	description = "Pretend to have a slayer task, to test Slayer Swaps",
	tags = {"slayer", "test"}
)
public class SlayerSwapsTestingPlugin extends Plugin
{
	@Inject
	private SlayerSwapsTestingConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	private SlayerData data;
	// Picked when Random task or Random Konar task is ticked, kept until it's unticked
	private String[] randomPick;
	private String[] randomKonarPick;

	@Provides
	SlayerSwapsTestingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerSwapsTestingConfig.class);
	}

	@Override
	protected void startUp()
	{
		data = SlayerData.load(gson);
		apply(false);
	}

	@Override
	protected void shutDown()
	{
		TaskOverride.task = null;
		TaskOverride.done = false;
		refresh();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(SlayerSwapsTestingConfig.GROUP))
		{
			return;
		}
		if (event.getKey().equals("randomTask"))
		{
			randomPick = null;
		}
		if (event.getKey().equals("randomKonarTask"))
		{
			randomKonarPick = null;
		}
		apply(true);
	}

	private void apply(boolean announce)
	{
		String[] task = pick();
		TaskOverride.task = task;
		TaskOverride.done = config.taskDone();
		refresh();
		if (announce)
		{
			String what = task == null ? "off, using your real task" : task[0] + (task[1] != null ? " in " + task[1] + " (Konar)" : "");
			String message = "Slayer Swaps test mode: " + what + (config.taskDone() ? ", done" : "") + ".";
			clientThread.invokeLater(() -> chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build()));
		}
	}

	private static void refresh()
	{
		Runnable refresh = TaskOverride.refresh;
		if (refresh != null)
		{
			refresh.run();
		}
	}

	/**
	 * @return the task (and Konar area, or null) to pretend to have, or null for the real task: Random Konar task
	 * when ticked, then Random task, then Konar task, then Test task
	 */
	private String[] pick()
	{
		if (config.randomKonarTask())
		{
			if (randomKonarPick == null)
			{
				List<String[]> picks = new ArrayList<>();
				for (SlayerData.TaskData task : data.getTasks())
				{
					if (task.getKonar() != null)
					{
						for (String area : task.getKonar().keySet())
						{
							picks.add(new String[]{task.getName(), area});
						}
					}
				}
				randomKonarPick = picks.get(ThreadLocalRandom.current().nextInt(picks.size()));
			}
			return randomKonarPick;
		}
		if (config.randomTask())
		{
			if (randomPick == null)
			{
				List<SlayerData.TaskData> tasks = new ArrayList<>();
				for (SlayerData.TaskData task : data.getTasks())
				{
					if (!task.isBoss())
					{
						tasks.add(task);
					}
				}
				randomPick = new String[]{tasks.get(ThreadLocalRandom.current().nextInt(tasks.size())).getName(), null};
			}
			return randomPick;
		}
		KonarTestTask konar = config.konarTestTask();
		if (konar != KonarTestTask.OFF)
		{
			return new String[]{konar.getTask(), konar.getArea()};
		}
		SimulatedTask simulated = config.simulatedTask();
		return simulated != SimulatedTask.OFF ? new String[]{simulated.getTask(), null} : null;
	}
}
