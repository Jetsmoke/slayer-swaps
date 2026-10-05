package com.slayerteleportswap;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slayer Teleport Swap",
	description = "Swaps and highlights the teleports to your slayer task's location, or to your slayer master when you have no task",
	tags = {"slayer", "teleport", "menu", "swap", "ring", "fairy", "cape", "mortimer", "duradel", "highlight"}
)
public class SlayerTeleportSwapPlugin extends Plugin
{
	// From [proc,helper_slayer_current_assignment], same as the core Slayer plugin
	private static final int BOSS_TASK_ID = 98;
	// SLAYER_MASTER varbit value for Krystilia, same as the core Slayer plugin
	private static final int KRYSTILIA = 7;

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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MenuHighlightOverlay menuHighlightOverlay;

	@Inject
	private TeleportItemOverlay teleportItemOverlay;

	@Inject
	private MenuHighlighter menuHighlighter;

	@Inject
	private TeleportItems teleportItems;

	@Inject
	private Gson gson;

	private SlayerData data;

	// Current task name as the game names it, or null when there is no task
	private String taskName;
	private boolean menusDirty;

	@Override
	protected void startUp()
	{
		data = SlayerData.load(gson);
		overlayManager.add(menuHighlightOverlay);
		overlayManager.add(teleportItemOverlay);
		clientThread.invokeLater(this::updateTask);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(menuHighlightOverlay);
		overlayManager.remove(teleportItemOverlay);
		menuHighlighter.clear();
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
			|| event.getVarbitId() == VarbitID.SLAYER_TARGET_BOSSID
			|| event.getVarbitId() == VarbitID.SLAYER_MASTER)
		{
			clientThread.invokeLater(this::updateTask);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(SlayerTeleportSwapConfig.GROUP))
		{
			menusDirty = true;
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
			menusDirty = true;
			if (name != null && data.findTask(name) == null)
			{
				log.debug("Task {} isn't in the location data", name);
			}
			log.debug("Task is now {} ({} left), master {}, location {}, routes {}",
				taskName, client.getVarpValue(VarPlayerID.SLAYER_COUNT), client.getVarbitValue(VarbitID.SLAYER_MASTER),
				currentLocation(), currentRoutes());
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
	 * @return the locations the current task can be done at with at least one enabled teleport, best first
	 */
	private List<String> taskLocations()
	{
		SlayerData.TaskData task = data.findTask(taskName);
		if (task == null)
		{
			return Collections.emptyList();
		}

		boolean krystilia = client.getVarbitValue(VarbitID.SLAYER_MASTER) == KRYSTILIA;
		List<String> locations = new ArrayList<>();
		for (String location : task.getLocations())
		{
			SlayerData.LocationData info = data.location(location);
			if (info == null || enabledRoutes(location).isEmpty())
			{
				continue;
			}
			// Krystilia's tasks only count in the Wilderness
			if (krystilia ? !info.isWilderness() : info.isWilderness() && !config.wilderness())
			{
				continue;
			}
			locations.add(location);
		}
		return locations;
	}

	/**
	 * @return where teleports should take the player right now, or null to leave menus alone
	 */
	String currentLocation()
	{
		if (taskName == null)
		{
			SlayerData.MasterData master = data.master(config.slayerMaster().name());
			return master != null ? master.getLocation() : null;
		}

		List<String> locations = taskLocations();
		if (locations.isEmpty())
		{
			return null;
		}

		String chosen = configManager.getConfiguration(SlayerTeleportSwapConfig.GROUP, locationKey(taskName));
		return locations.contains(chosen) ? chosen : locations.get(0);
	}

	/**
	 * @return the enabled teleports to the current location
	 */
	List<SlayerData.Route> currentRoutes()
	{
		if (taskName == null)
		{
			SlayerData.MasterData master = data.master(config.slayerMaster().name());
			return master != null ? filterEnabled(master.getRoutes()) : Collections.emptyList();
		}

		String location = currentLocation();
		return location != null ? enabledRoutes(location) : Collections.emptyList();
	}

	private List<SlayerData.Route> enabledRoutes(String location)
	{
		return filterEnabled(data.routes(location));
	}

	private List<SlayerData.Route> filterEnabled(List<SlayerData.Route> routes)
	{
		List<SlayerData.Route> enabled = new ArrayList<>();
		for (SlayerData.Route route : routes)
		{
			RouteType type = route.routeType();
			if (type != null && config.isEnabled(type))
			{
				enabled.add(route);
			}
		}
		return enabled;
	}

	/**
	 * @return true if an item that teleports by these route types reaches the current location
	 */
	boolean isUsefulItem(Set<RouteType> itemTypes)
	{
		if (itemTypes.isEmpty())
		{
			return false;
		}
		for (SlayerData.Route route : currentRoutes())
		{
			if (itemTypes.contains(route.routeType()))
			{
				return true;
			}
		}
		return false;
	}

	private static String locationKey(String task)
	{
		return SlayerTeleportSwapConfig.LOCATION_KEY_PREFIX + SlayerData.normalize(task);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		menusDirty = true;
		if (log.isDebugEnabled())
		{
			int group = event.getGroupId();
			clientThread.invokeLater(() -> logInterface(group));
		}
		clientThread.invokeLater(this::updateMenuHighlights);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		menusDirty = true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Teleport dialogs are often built by scripts after the interface loads, so rescan each tick
		menusDirty = true;
		updateMenuHighlights();
	}

	private void updateMenuHighlights()
	{
		if (!menusDirty)
		{
			return;
		}
		menusDirty = false;
		menuHighlighter.update(config.highlightMenus() ? currentRoutes() : Collections.emptyList());
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

		List<SlayerData.Route> routes = currentRoutes();
		if (routes.isEmpty())
		{
			return;
		}

		if (swapItem(routes))
		{
			return;
		}
		for (SlayerData.Route route : routes)
		{
			if (route.routeType() == RouteType.FAIRY && swapFairyRing(route.getValue()))
			{
				return;
			}
		}
	}

	/**
	 * Puts a teleport item's option for the current location on left-click.
	 */
	private boolean swapItem(List<SlayerData.Route> routes)
	{
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			Set<RouteType> itemTypes = teleportItems.routeTypes(itemIdOf(entry));
			if (itemTypes.isEmpty())
			{
				continue;
			}

			for (SlayerData.Route route : routes)
			{
				if (!itemTypes.contains(route.routeType()))
				{
					continue;
				}

				if (optionMatches(entry.getOption(), route.getValue()))
				{
					moveToTop(menu, entries, i);
					return true;
				}

				Menu sub = entry.getSubMenu();
				if (sub != null)
				{
					for (MenuEntry subEntry : sub.getMenuEntries())
					{
						if (optionMatches(subEntry.getOption(), route.getValue()))
						{
							clone(subEntry);
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean optionMatches(String option, String value)
	{
		return Text.removeTags(option).trim().equalsIgnoreCase(value);
	}

	/**
	 * Puts the fairy ring option for a code on left-click: a favourite or last-destination option naming
	 * the code if there is one, otherwise Configure so the code can be picked from the fairy ring log.
	 */
	private boolean swapFairyRing(String code)
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
				return true;
			}

			Menu sub = entry.getSubMenu();
			if (sub != null)
			{
				for (MenuEntry subEntry : sub.getMenuEntries())
				{
					if (codePattern.matcher(Text.removeTags(subEntry.getOption())).find())
					{
						clone(subEntry);
						return true;
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
			return true;
		}
		return false;
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
		// Item op4 and op5 are low priority, which makes them right-click only
		if (entry.getType() == MenuAction.CC_OP_LOW_PRIORITY)
		{
			entry.setType(MenuAction.CC_OP);
		}
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
	 * Adds a client-side "Slayer location" submenu under a teleport item's or fairy ring's options, for picking
	 * where the current task is done. Nothing is sent to the server; choosing just stores the choice in config.
	 */
	private void addLocationPicker(MenuEntry[] entries)
	{
		if (taskName == null)
		{
			return;
		}

		List<String> locations = taskLocations();
		if (locations.size() < 2)
		{
			return;
		}

		int anchor = -1;
		for (int i = 0; i < entries.length; i++)
		{
			if (!teleportItems.routeTypes(itemIdOf(entries[i])).isEmpty() || isFairyRing(entries[i]))
			{
				anchor = i;
				break;
			}
		}
		if (anchor == -1)
		{
			return;
		}

		String task = taskName;
		String current = currentLocation();
		MenuEntry picker = client.getMenu().createMenuEntry(anchor)
			.setOption("Slayer location")
			.setTarget(ColorUtil.wrapWithColorTag(task, Color.ORANGE))
			.setType(MenuAction.RUNELITE);
		Menu sub = picker.createSubMenu();
		for (int i = locations.size() - 1; i >= 0; i--)
		{
			String location = locations.get(i);
			sub.createMenuEntry(0)
				.setOption(location.equals(current) ? ColorUtil.wrapWithColorTag(location, Color.GREEN) : location)
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					configManager.setConfiguration(SlayerTeleportSwapConfig.GROUP, locationKey(task), location);
					log.debug("Location for {} set to {}, routes {}", task, location, currentRoutes());
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

	// Development aid: logs the text of newly opened interfaces, to find how teleport menus label destinations
	private void logInterface(int group)
	{
		List<String> texts = new ArrayList<>();
		Widget[] roots = client.getWidgetRoots();
		if (roots != null)
		{
			for (Widget root : roots)
			{
				collectTexts(root, group, texts);
			}
		}
		if (!texts.isEmpty())
		{
			log.debug("Interface {} opened: {}", group, texts.size() > 60 ? texts.subList(0, 60) : texts);
		}
	}

	private static void collectTexts(Widget w, int group, List<String> out)
	{
		if (w == null || w.isHidden())
		{
			return;
		}
		if (WidgetUtil.componentToInterface(w.getId()) == group && w.getText() != null && !w.getText().isEmpty())
		{
			out.add(Text.removeTags(w.getText()));
		}
		for (Widget[] children : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget child : children)
				{
					collectTexts(child, group, out);
				}
			}
		}
	}

	@Provides
	SlayerTeleportSwapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerTeleportSwapConfig.class);
	}
}
