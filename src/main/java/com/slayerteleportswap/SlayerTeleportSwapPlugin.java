package com.slayerteleportswap;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
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
	// Routes that are used by clicking an item (as opposed to a fairy ring, travel network or spellbook)
	private static final Set<RouteType> ITEM_ROUTES = ImmutableSet.of(RouteType.RING, RouteType.PORTAL,
		RouteType.MAXCAPE, RouteType.KARAMJA_GLOVES, RouteType.BURNING_AMULET, RouteType.RING_OF_DUELING,
		RouteType.AMULET_OF_GLORY, RouteType.BOAT, RouteType.ITEM, RouteType.SPELL);

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
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

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
	private SlayerTeleportPanel panel;
	private NavigationButton navButton;

	// Current task name as the game names it, or null when there is no task
	private String taskName;
	private boolean menusDirty;
	// The route whose item to outline: the first one in order that the player is carrying
	private SlayerData.Route carriedRoute;

	@Override
	protected void startUp()
	{
		data = SlayerData.load(gson);
		teleportItems.setData(data);
		menuHighlighter.setData(data);
		overlayManager.add(menuHighlightOverlay);
		overlayManager.add(teleportItemOverlay);

		panel = new SlayerTeleportPanel(data, new PanelChoices());
		navButton = NavigationButton.builder()
			.tooltip("Slayer Teleports")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		clientThread.invokeLater(this::updateTask);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(menuHighlightOverlay);
		overlayManager.remove(teleportItemOverlay);
		clientToolbar.removeNavigation(navButton);
		menuHighlighter.clear();
		taskName = null;
		carriedRoute = null;
		panel = null;
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
			clientThread.invokeLater(this::updateCarriedRoute);
			String key = event.getKey();
			SlayerTeleportPanel p = panel;
			if (p != null && !key.startsWith(SlayerTeleportSwapConfig.LOCATION_KEY_PREFIX)
				&& !key.startsWith(SlayerTeleportSwapConfig.ROUTE_KEY_PREFIX)
				&& !key.startsWith(SlayerTeleportSwapConfig.DISABLED_KEY_PREFIX))
			{
				// Teleport and Wilderness settings change which locations and teleports the panel offers
				SwingUtilities.invokeLater(p::refresh);
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			updateCarriedRoute();
		}
	}

	private void updateTask()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		String name = remaining > 0 ? lookupTaskName(client.getVarpValue(VarPlayerID.SLAYER_TARGET)) : null;
		SlayerData.TaskData task = data.findTask(name);

		if (!Objects.equals(name, taskName))
		{
			taskName = name;
			menusDirty = true;
			updateCarriedRoute();
			if (name != null && task == null)
			{
				log.debug("Task {} isn't in the location data", name);
			}
			log.debug("Task is now {} ({} left), master {}, location {}, routes {}",
				taskName, remaining, client.getVarbitValue(VarbitID.SLAYER_MASTER), currentLocation(), currentRoutes());
			if (task != null)
			{
				askForLocation(task);
			}
		}

		SlayerTeleportPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.setCurrentTask(task, remaining));
		}
	}

	/**
	 * The first time a task with more than one location comes up, open the panel so a location can be chosen.
	 */
	private void askForLocation(SlayerData.TaskData task)
	{
		if (!config.askOnNewTask() || isDisabled(task) || taskLocations(task, true).size() < 2
			|| configManager.getConfiguration(SlayerTeleportSwapConfig.GROUP, locationKey(task.getName())) != null)
		{
			return;
		}

		String message = new ChatMessageBuilder()
			.append("Slayer Teleports: choose where to do " + task.getName() + " in the Slayer Teleports panel. Using "
				+ currentLocation() + " until you do.")
			.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
		SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
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
	 * @param current true for the player's current task, where Krystilia's tasks are limited to the Wilderness
	 * @return the locations a task can be done at with at least one enabled teleport, best first
	 */
	private List<String> taskLocations(SlayerData.TaskData task, boolean current)
	{
		boolean krystilia = current && client.getVarbitValue(VarbitID.SLAYER_MASTER) == KRYSTILIA;
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

	private String chosenLocation(SlayerData.TaskData task, List<String> locations)
	{
		if (locations.isEmpty())
		{
			return null;
		}
		String chosen = configManager.getConfiguration(SlayerTeleportSwapConfig.GROUP, locationKey(task.getName()));
		return locations.contains(chosen) ? chosen : locations.get(0);
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

		SlayerData.TaskData task = data.findTask(taskName);
		return task == null || isDisabled(task) ? null : chosenLocation(task, taskLocations(task, true));
	}

	/**
	 * @return the enabled teleports to the current location, with the player's chosen teleport first
	 */
	List<SlayerData.Route> currentRoutes()
	{
		if (taskName == null)
		{
			SlayerData.MasterData master = data.master(config.slayerMaster().name());
			return master != null ? filterEnabled(master.getRoutes()) : Collections.emptyList();
		}

		String location = currentLocation();
		if (location == null)
		{
			return Collections.emptyList();
		}

		List<SlayerData.Route> routes = new ArrayList<>(enabledRoutes(location));
		String chosen = configManager.getConfiguration(SlayerTeleportSwapConfig.GROUP, routeKey(taskName));
		for (int i = 0; i < routes.size(); i++)
		{
			if (Routes.key(routes.get(i)).equals(chosen))
			{
				routes.add(0, routes.remove(i));
				break;
			}
		}
		return routes;
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
	 * Finds the first item-based route to the current location that the player is carrying.
	 */
	private void updateCarriedRoute()
	{
		carriedRoute = null;
		List<Integer> carried = carriedItemIds();
		for (SlayerData.Route route : currentRoutes())
		{
			if (!ITEM_ROUTES.contains(route.routeType()))
			{
				continue;
			}
			for (int itemId : carried)
			{
				if (teleportItems.matches(itemId, route))
				{
					carriedRoute = route;
					return;
				}
			}
		}
	}

	private List<Integer> carriedItemIds()
	{
		List<Integer> ids = new ArrayList<>();
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container != null)
			{
				for (Item item : container.getItems())
				{
					if (item.getId() > 0)
					{
						ids.add(item.getId());
					}
				}
			}
		}
		return ids;
	}

	/**
	 * @return true if this item should be outlined: it's the teleport to use for the current location
	 */
	boolean shouldHighlight(int itemId)
	{
		SlayerData.Route route = carriedRoute;
		return route != null && teleportItems.matches(itemId, route);
	}

	private boolean isDisabled(SlayerData.TaskData task)
	{
		return Boolean.parseBoolean(configManager.getConfiguration(SlayerTeleportSwapConfig.GROUP,
			SlayerTeleportSwapConfig.DISABLED_KEY_PREFIX + SlayerData.normalize(task.getName())));
	}

	private static String locationKey(String task)
	{
		return SlayerTeleportSwapConfig.LOCATION_KEY_PREFIX + SlayerData.normalize(task);
	}

	private static String routeKey(String task)
	{
		return SlayerTeleportSwapConfig.ROUTE_KEY_PREFIX + SlayerData.normalize(task);
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
			int itemId = itemIdOf(entry);
			if (itemId <= 0)
			{
				continue;
			}

			for (SlayerData.Route route : routes)
			{
				if (route.getValue() == null || !teleportItems.matches(itemId, route))
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

	// Development aid: logs every right-click menu so option names can be read from the log
	// instead of guessed. Debug level, so it's silent in normal clients.
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}

		log.debug("Menu opened (task {}, location {}):", taskName, currentLocation());
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
		if (WidgetUtil.componentToInterface(w.getId()) == group)
		{
			if (w.getText() != null && !w.getText().isEmpty())
			{
				out.add(Text.removeTags(w.getText()));
			}
			else if (w.getName() != null && !w.getName().isEmpty())
			{
				out.add("[" + Text.removeTags(w.getName()) + "]");
			}
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

	private static BufferedImage icon()
	{
		// A simple map pin, so no image file is needed
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xC0392B));
		g.fillOval(3, 1, 10, 10);
		g.fillPolygon(new int[]{4, 12, 8}, new int[]{8, 8, 15}, 3);
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(1.5f));
		g.drawOval(6, 4, 4, 4);
		g.dispose();
		return image;
	}

	/**
	 * Lets the side panel read and change per-task choices. Panel calls come from the Swing thread.
	 */
	private class PanelChoices implements SlayerTeleportPanel.Choices
	{
		@Override
		public String location(SlayerData.TaskData task)
		{
			return chosenLocation(task, locations(task));
		}

		@Override
		public String routeKey(SlayerData.TaskData task)
		{
			return configManager.getConfiguration(SlayerTeleportSwapConfig.GROUP, SlayerTeleportSwapPlugin.routeKey(task.getName()));
		}

		@Override
		public List<String> locations(SlayerData.TaskData task)
		{
			List<String> locations = new ArrayList<>();
			for (String location : task.getLocations())
			{
				SlayerData.LocationData info = data.location(location);
				if (info != null && !enabledRoutes(location).isEmpty() && (!info.isWilderness() || config.wilderness()))
				{
					locations.add(location);
				}
			}
			return locations;
		}

		@Override
		public List<SlayerData.Route> routes(String location)
		{
			return enabledRoutes(location);
		}

		@Override
		public void choose(SlayerData.TaskData task, String location, String routeKey)
		{
			configManager.setConfiguration(SlayerTeleportSwapConfig.GROUP, locationKey(task.getName()), location);
			if (routeKey == null)
			{
				configManager.unsetConfiguration(SlayerTeleportSwapConfig.GROUP, SlayerTeleportSwapPlugin.routeKey(task.getName()));
			}
			else
			{
				configManager.setConfiguration(SlayerTeleportSwapConfig.GROUP, SlayerTeleportSwapPlugin.routeKey(task.getName()), routeKey);
			}
			log.debug("{}: location {}, teleport {}", task.getName(), location, routeKey == null ? "best carried" : routeKey);
		}

		@Override
		public boolean isEnabled(SlayerData.TaskData task)
		{
			return !isDisabled(task);
		}

		@Override
		public void setEnabled(SlayerData.TaskData task, boolean enabled)
		{
			String key = SlayerTeleportSwapConfig.DISABLED_KEY_PREFIX + SlayerData.normalize(task.getName());
			if (enabled)
			{
				configManager.unsetConfiguration(SlayerTeleportSwapConfig.GROUP, key);
			}
			else
			{
				configManager.setConfiguration(SlayerTeleportSwapConfig.GROUP, key, true);
			}
		}
	}

	@Provides
	SlayerTeleportSwapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerTeleportSwapConfig.class);
	}
}
