package com.slayerteleportswap;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slayer Teleport Swap",
	description = "Swaps the left-click of teleport items to the location of your slayer task, or to your slayer master when you have no task",
	tags = {"slayer", "teleport", "menu", "swap", "ring", "mortimer"}
)
public class SlayerTeleportSwapPlugin extends Plugin
{
	// From [proc,helper_slayer_current_assignment], same as the core Slayer plugin
	private static final int BOSS_TASK_ID = 98;

	private static final Set<Integer> SLAYER_RINGS = ImmutableSet.of(
		ItemID.SLAYER_RING_1, ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_4,
		ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_6, ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_8,
		ItemID.SLAYER_RING_ETERNAL);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SlayerTeleportSwapConfig config;

	private final TaskDestinations taskDestinations = new TaskDestinations();

	// Current task name, or null when there is no task
	private String taskName;

	@Override
	protected void startUp()
	{
		taskDestinations.setOverrides(config.taskOverrides());
		clientThread.invokeLater(this::updateTask);
	}

	@Override
	protected void shutDown()
	{
		taskName = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(SlayerTeleportSwapConfig.GROUP))
		{
			taskDestinations.setOverrides(config.taskOverrides());
			log.debug("Config changed, destination is now {}", ringDestination());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::updateTask);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varpId = event.getVarpId();
		if (varpId == VarPlayerID.SLAYER_COUNT
			|| varpId == VarPlayerID.SLAYER_TARGET
			|| event.getVarbitId() == VarbitID.SLAYER_TARGET_BOSSID)
		{
			clientThread.invokeLater(this::updateTask);
		}
	}

	private void updateTask()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		String name = null;
		if (client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0)
		{
			name = lookupTaskName(client.getVarpValue(VarPlayerID.SLAYER_TARGET));
		}

		if (!Objects.equals(name, taskName))
		{
			taskName = name;
			log.debug("Task is now {} ({} left), master {}, ring destination {}",
				taskName, client.getVarpValue(VarPlayerID.SLAYER_COUNT),
				client.getVarbitValue(VarbitID.SLAYER_MASTER), ringDestination());
		}
	}

	private String lookupTaskName(int taskId)
	{
		int taskRow;
		if (taskId == BOSS_TASK_ID)
		{
			List<Integer> bossRows = client.getDBRowsByValue(
				DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
				0,
				client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
			if (bossRows.isEmpty())
			{
				return null;
			}
			taskRow = (Integer) client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
		}
		else
		{
			List<Integer> taskRows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
			if (taskRows.isEmpty())
			{
				return null;
			}
			taskRow = taskRows.get(0);
		}

		return (String) client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
	}

	/**
	 * @return the slayer ring option to put on left-click, or null to leave the menu alone
	 */
	private String ringDestination()
	{
		if (taskName != null)
		{
			return taskDestinations.get(taskName);
		}
		return config.slayerMaster().getRingDestination();
	}

	// Runs after the Menu Entry Swapper (priority 0), so our swap wins over a custom swap there
	@Subscribe(priority = -1)
	public void onPostMenuSort(PostMenuSort event)
	{
		// The menu isn't rebuilt while it's open, so swapping now would swap repeatedly
		if (client.isMenuOpen() || client.isKeyPressed(KeyCode.KC_SHIFT) || !config.swapSlayerRing())
		{
			return;
		}

		String destination = ringDestination();
		if (destination == null)
		{
			return;
		}

		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			Menu sub = entry.getSubMenu();
			if (sub == null || !SLAYER_RINGS.contains(itemIdOf(entry)))
			{
				continue;
			}

			for (MenuEntry subEntry : sub.getMenuEntries())
			{
				if (Text.removeTags(subEntry.getOption()).toLowerCase().contains(destination.toLowerCase()))
				{
					clone(subEntry);
					return;
				}
			}
		}
	}

	/**
	 * @return the item id an inventory or worn item menu entry is for, or -1
	 */
	private static int itemIdOf(MenuEntry entry)
	{
		Widget w = entry.getWidget();
		if (w == null)
		{
			return -1;
		}

		int interfaceId = WidgetUtil.componentToInterface(w.getId());
		if (interfaceId == InterfaceID.INVENTORY)
		{
			return w.getItemId();
		}
		if (interfaceId == InterfaceID.WORNITEMS)
		{
			Widget child = w.getChild(1);
			return child != null ? child.getItemId() : -1;
		}
		return -1;
	}

	private void clone(MenuEntry menuEntry)
	{
		// A submenu entry can't be moved to the top level, so copy it there; same as the Menu Entry Swapper
		client.getMenu().createMenuEntry(-1)
			.setOption(menuEntry.getOption())
			.setTarget(menuEntry.getTarget())
			.setIdentifier(menuEntry.getIdentifier())
			.setType(menuEntry.getType() == MenuAction.CC_OP_LOW_PRIORITY ? MenuAction.CC_OP : menuEntry.getType())
			.setItemId(menuEntry.getItemId())
			.setParam0(menuEntry.getParam0())
			.setParam1(menuEntry.getParam1())
			.onClick(menuEntry.onClick());
	}

	// Development aid: logs every right-click menu so option names can be read from the log
	// instead of guessed. Debug level, so it's silent in normal clients.
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}

		log.debug("Menu opened (task {}, destination {}):", taskName, ringDestination());
		for (MenuEntry entry : event.getMenuEntries())
		{
			logEntry("  ", entry);
			Menu sub = entry.getSubMenu();
			if (sub != null)
			{
				for (MenuEntry subEntry : sub.getMenuEntries())
				{
					logEntry("    > ", subEntry);
				}
			}
		}
	}

	private static void logEntry(String prefix, MenuEntry e)
	{
		log.debug("{}option='{}' target='{}' type={} id={} itemId={} widgetItemId={} p0={} p1={}",
			prefix, Text.removeTags(e.getOption()), Text.removeTags(e.getTarget()), e.getType(),
			e.getIdentifier(), e.getItemId(), itemIdOf(e), e.getParam0(), e.getParam1());
	}

	@Provides
	SlayerTeleportSwapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerTeleportSwapConfig.class);
	}
}
