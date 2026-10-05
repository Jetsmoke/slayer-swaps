package com.slayerteleportswap;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slayer Teleport Swap",
	description = "Swaps the left-click of teleport items to the location of your slayer task, or to your slayer master when you have no task",
	tags = {"slayer", "teleport", "menu", "swap", "ring", "fairy", "mortimer"}
)
public class SlayerTeleportSwapPlugin extends Plugin
{
	// From [proc,helper_slayer_current_assignment], same as the core Slayer plugin
	private static final int BOSS_TASK_ID = 98;

	private static final Set<Integer> SLAYER_RINGS = ImmutableSet.of(
		ItemID.SLAYER_RING_1, ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_4,
		ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_6, ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_8,
		ItemID.SLAYER_RING_ETERNAL);

	private static final Set<MenuAction> OBJECT_MENU_TYPES = ImmutableSet.of(
		MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION,
		MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION);

	private static final String FAIRY_RING_CONFIGURE = "ring-configure";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SlayerTeleportSwapConfig config;

	// Current task name, or null when there is no task
	private String taskName;

	@Override
	protected void startUp()
	{
		clientThread.invokeLater(this::updateTask);
	}

	@Override
	protected void shutDown()
	{
		taskName = null;
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
			log.debug("Task is now {} ({} left), location {}",
				taskName, client.getVarpValue(VarPlayerID.SLAYER_COUNT), currentLocation());
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
	 * @return where teleports should take the player right now, or null to leave menus alone
	 */
	private Location currentLocation()
	{
		if (taskName == null)
		{
			return config.slayerMaster().getLocation();
		}

		List<Location> locations = TaskLocations.get(taskName);
		if (locations.isEmpty())
		{
			return null;
		}

		String chosen = configManager.getConfiguration(SlayerTeleportSwapConfig.GROUP, locationKey(taskName));
		for (Location location : locations)
		{
			if (location.name().equals(chosen))
			{
				return location;
			}
		}
		return locations.get(0);
	}

	private static String locationKey(String task)
	{
		return SlayerTeleportSwapConfig.LOCATION_KEY_PREFIX + TaskLocations.normalize(task);
	}

	// Runs after the Menu Entry Swapper (priority 0), so our swap wins over a custom swap there
	@Subscribe(priority = -1)
	public void onPostMenuSort(PostMenuSort event)
	{
		// The menu isn't rebuilt while it's open, so swapping now would swap repeatedly
		if (client.isMenuOpen() || client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		Location location = currentLocation();
		if (location == null)
		{
			return;
		}

		if (location.getRingOption() != null && config.swapSlayerRing())
		{
			swapSlayerRing(location.getRingOption());
		}
		if (location.getFairyCode() != null && config.swapFairyRing())
		{
			swapFairyRing(location.getFairyCode());
		}
	}

	private void swapSlayerRing(String ringOption)
	{
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			Menu sub = entry.getSubMenu();
			if (sub == null || !SLAYER_RINGS.contains(itemIdOf(entry)))
			{
				continue;
			}

			for (MenuEntry subEntry : sub.getMenuEntries())
			{
				if (Text.removeTags(subEntry.getOption()).equalsIgnoreCase(ringOption))
				{
					clone(subEntry);
					return;
				}
			}
		}
	}

	/**
	 * Puts the fairy ring option for a code on left-click: a favourite or last-destination option naming
	 * the code if there is one, otherwise Configure so the code can be picked from the interface.
	 */
	private void swapFairyRing(String code)
	{
		Pattern codePattern = Pattern.compile("\\b" + code + "\\b");
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		int configureIdx = -1;

		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			if (!isFairyRing(entry))
			{
				continue;
			}

			String option = Text.removeTags(entry.getOption());
			if (codePattern.matcher(option).find())
			{
				moveToTop(menu, entries, i);
				return;
			}

			Menu sub = entry.getSubMenu();
			if (sub != null)
			{
				for (MenuEntry subEntry : sub.getMenuEntries())
				{
					if (codePattern.matcher(Text.removeTags(subEntry.getOption())).find())
					{
						clone(subEntry);
						return;
					}
				}
			}

			if (configureIdx == -1 && option.equalsIgnoreCase(FAIRY_RING_CONFIGURE))
			{
				configureIdx = i;
			}
		}

		if (configureIdx != -1)
		{
			moveToTop(menu, entries, configureIdx);
		}
	}

	private static boolean isFairyRing(MenuEntry entry)
	{
		return OBJECT_MENU_TYPES.contains(entry.getType())
			&& Text.removeTags(entry.getTarget()).toLowerCase().contains("fairy");
	}

	private static void moveToTop(Menu menu, MenuEntry[] entries, int index)
	{
		int top = entries.length - 1;
		if (index == top)
		{
			return;
		}

		MenuEntry entry = entries[index];
		entries[index] = entries[top];
		entries[top] = entry;
		menu.setMenuEntries(entries);
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

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (config.locationPicker())
		{
			addLocationPicker(event.getMenuEntries());
		}

		if (log.isDebugEnabled())
		{
			logMenu(event.getMenuEntries());
		}
	}

	/**
	 * Adds a client-side "Slayer location" submenu under the slayer ring's options, for picking where the
	 * current task is done. Nothing is sent to the server; choosing just stores the choice in config.
	 */
	private void addLocationPicker(MenuEntry[] entries)
	{
		if (taskName == null)
		{
			return;
		}

		List<Location> locations = TaskLocations.get(taskName);
		if (locations.size() < 2)
		{
			return;
		}

		int ringIdx = -1;
		for (int i = 0; i < entries.length; i++)
		{
			if (SLAYER_RINGS.contains(itemIdOf(entries[i])))
			{
				ringIdx = i;
				break;
			}
		}
		if (ringIdx == -1)
		{
			return;
		}

		String task = taskName;
		Location current = currentLocation();
		MenuEntry picker = client.getMenu().createMenuEntry(ringIdx)
			.setOption("Slayer location")
			.setTarget(ColorUtil.wrapWithColorTag(task, Color.ORANGE))
			.setType(MenuAction.RUNELITE);
		Menu sub = picker.createSubMenu();
		for (Location location : locations)
		{
			String label = location == current
				? ColorUtil.wrapWithColorTag(location.getName(), Color.GREEN)
				: location.getName();
			sub.createMenuEntry(0)
				.setOption(label)
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					configManager.setConfiguration(SlayerTeleportSwapConfig.GROUP, locationKey(task), location.name());
					log.debug("Location for {} set to {}", task, location);
				});
		}
	}

	// Development aid: logs every right-click menu so option names can be read from the log
	// instead of guessed. Debug level, so it's silent in normal clients.
	private void logMenu(MenuEntry[] entries)
	{
		log.debug("Menu opened (task {}, location {}):", taskName, currentLocation());
		for (MenuEntry entry : entries)
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
