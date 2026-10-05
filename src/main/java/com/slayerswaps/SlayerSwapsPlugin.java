package com.slayerswaps;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
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
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextMenuInput;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slayer Swaps",
	description = "Automatically swaps menu entries, and highlights teleport items and spells to quickly teleport to your slayer task. Supports choosing your preferred slayer master and preferred task location. To start right click your slayer helmet or check settings panel.",
	tags = {"slayer", "teleport", "menu", "swap", "ring", "fairy", "cape", "mortimer", "duradel", "highlight"}
)
public class SlayerSwapsPlugin extends Plugin
{
	// From [proc,helper_slayer_current_assignment], same as the core Slayer plugin
	private static final int BOSS_TASK_ID = 98;
	// SLAYER_MASTER varbit value for Krystilia, same as the core Slayer plugin
	private static final int KRYSTILIA = 7;

	private static final Set<MenuAction> OBJECT_MENU_TYPES = ImmutableSet.of(
		MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION,
		MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION);
	// Routes that are used by clicking an item (as opposed to a fairy ring, obelisk or spellbook)
	private static final Set<RouteType> ITEM_ROUTES = ImmutableSet.of(RouteType.RING, RouteType.PORTAL,
		RouteType.MAXCAPE, RouteType.KARAMJA_GLOVES, RouteType.BURNING_AMULET, RouteType.RING_OF_DUELING,
		RouteType.AMULET_OF_GLORY, RouteType.BOAT, RouteType.ITEM, RouteType.SPELL);

	private static final String FAIRY_RING_CONFIGURE = "ring-configure";
	// The max cape's Teleports option that teleports to your boat (option text from the in-game menu log)
	private static final String BOAT_OPTION = "Boat";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SlayerSwapsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private MenuHighlightOverlay menuHighlightOverlay;

	@Inject
	private TeleportItemOverlay teleportItemOverlay;

	@Inject
	private DestinationOverlay destinationOverlay;

	@Inject
	private TeleportObjectOverlay teleportObjectOverlay;

	@Inject
	private MenuHighlighter menuHighlighter;

	@Inject
	private TeleportItems teleportItems;

	@Inject
	private Gson gson;

	// Names of objects that teleport, lower-case; the ones to outline depend on the current routes
	private static final String FAIRY_RING = "fairy ring";
	private static final String SPIRITUAL_FAIRY_TREE = "spiritual fairy tree";
	private static final String OBELISK = "obelisk";
	private static final String LEVER = "lever";
	private static final String PORTAL_NEXUS = "portal nexus";
	private static final String NEXUS_TELEPORT_MENU = "Teleport Menu";
	// What the player's portal nexus can do, remembered from seeing it in the house: its left-click destination,
	// and its teleport menu's hotkeys ("kourend castle=6;varrock=1")
	private static final String NEXUS_LEFT_CLICK_KEY = "nexusLeftClick";
	private static final String NEXUS_KEYS_KEY = "nexusKeys";
	private static final String HOME = "Home";
	private static final SlayerData.Route HOUSE_SPELL = new SlayerData.Route();
	static
	{
		HOUSE_SPELL.setType(RouteType.SPELL.getKey());
		HOUSE_SPELL.setValue("Teleport to House");
	}
	// Slayer helmet, enchanted gem and slayer ring option that shows the task
	private static final String CHECK_TASK = "Check";
	// Charged jewellery names end in their charges, like "Necklace of passage(5)" or "Games necklace(8)"
	private static final Pattern CHARGES = Pattern.compile("\\(\\d+\\)$");
	// How long after the last task kill guidance comes back, for a trip away (like banking)
	private static final Duration GUIDANCE_RETURNS_AFTER = Duration.ofMinutes(3);
	// House furniture the player has, remembered from seeing it in the house
	private static final String HOUSE_FAIRY_RING_KEY = "houseFairyRing";
	private static final String JEWELLERY_BOX_KEY = "jewelleryBox";
	// Jewellery box tiers by name: each holds the jewellery of the ones below it too
	private static final Map<String, Integer> JEWELLERY_BOXES = ImmutableMap.of(
		"basic jewellery box", 1, "fancy jewellery box", 2, "ornate jewellery box", 3);
	// From the wiki: basic holds dueling rings and games necklaces, fancy adds skills necklaces and combat
	// bracelets, ornate adds amulets of glory and rings of wealth
	private static final Map<String, Integer> JEWELLERY_ITEM_TIERS = ImmutableMap.of(
		"games_necklace", 1, "skills_necklace", 2, "combat_bracelet", 2, "ring_of_wealth", 3);
	private static final String JEWELLERY_TELEPORT_MENU = "Teleport Menu";
	// The portal nexus's left-click names some destinations differently from the spell and its own lists (from the
	// in-game menu log: set to Kourend Castle it shows "Great Kourend"; "Seers' Village" appeared for Camelot)
	private static final Map<String, String> NEXUS_OPTION_NAMES = ImmutableMap.of(
		"kourend castle", "great kourend",
		"camelot", "seers' village");
	private static final Set<String> TELEPORT_OBJECT_NAMES = ImmutableSet.<String>builder()
		.add(FAIRY_RING, SPIRITUAL_FAIRY_TREE, OBELISK, LEVER, PORTAL_NEXUS)
		.addAll(JEWELLERY_BOXES.keySet())
		.build();

	private final Set<TileObject> teleportObjects = new HashSet<>();
	// When the player last killed a task monster: they're at the task and need no guidance until the task ends,
	// they put their slayer helmet back on, or a while passes without a kill (like a bank trip)
	private Instant lastTaskKill;
	private int taskRemaining;

	// Key from 1.0.x's side panel, which could switch tasks off; removed on start-up
	private static final String OLD_DISABLED_KEY_PREFIX = "disabled_";

	private SlayerData data;

	// Current task name as the game names it, or null when there is no task
	private String taskName;
	private boolean menusDirty;
	// The route whose item to outline: the first one in order that the player is carrying
	private SlayerData.Route carriedRoute;
	private boolean wearingSlayerHelmet;
	// Whether the current task is from Krystilia
	private boolean krystiliaTask;
	// The area Konar assigned the current task to, as the game names it, or null
	private String konarArea;
	// Test mode's random Konar task and area, picked when "Random Konar task" is ticked
	private String[] randomKonarPick;
	private boolean loggedKonarAreas;
	// The chatbox chooser opened from the slayer helmet, while it's open
	private ChatboxTextMenuInput chooser;

	@Override
	protected void startUp()
	{
		data = SlayerData.load(gson);
		teleportItems.setData(data);
		menuHighlighter.setData(data);
		overlayManager.add(menuHighlightOverlay);
		overlayManager.add(teleportItemOverlay);
		overlayManager.add(destinationOverlay);
		overlayManager.add(teleportObjectOverlay);
		migrateOldChoices();

		clientThread.invokeLater(this::updateTask);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(menuHighlightOverlay);
		overlayManager.remove(teleportItemOverlay);
		overlayManager.remove(destinationOverlay);
		overlayManager.remove(teleportObjectOverlay);
		teleportObjects.clear();
		lastTaskKill = null;
		chatboxPanelManager.close();
		menuHighlighter.clear();
		taskName = null;
		konarArea = null;
		carriedRoute = null;
	}

	/**
	 * 1.0.x's side panel could also switch tasks off; that's gone now.
	 */
	private void migrateOldChoices()
	{
		for (SlayerData.TaskData task : data.getTasks())
		{
			String norm = SlayerData.normalize(task.getName());
			configManager.unsetConfiguration(SlayerSwapsConfig.GROUP, OLD_DISABLED_KEY_PREFIX + norm);
		}
	}

	/**
	 * @return true after a task kill, until the task ends, the slayer helmet is put back on, or a while passes
	 */
	boolean atTask()
	{
		return taskName != null && lastTaskKill != null;
	}

	private void checkLeftTask()
	{
		if (lastTaskKill != null && Duration.between(lastTaskKill, Instant.now()).compareTo(GUIDANCE_RETURNS_AFTER) > 0)
		{
			resumeGuidance();
		}
	}

	private void resumeGuidance()
	{
		lastTaskKill = null;
		menusDirty = true;
		updateCarriedRoute();
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		trackObject(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		teleportObjects.remove(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		trackObject(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		teleportObjects.remove(event.getWallObject());
	}

	private void trackObject(TileObject object)
	{
		if (TELEPORT_OBJECT_NAMES.contains(objectName(object)))
		{
			teleportObjects.add(object);
			rememberHouseFurniture(objectName(object));
			ObjectComposition def = definition(object);
			String[] actions = def.getActions();
			if (PORTAL_NEXUS.equalsIgnoreCase(def.getName()) && actions != null && actions.length > 0 && actions[0] != null
				&& !actions[0].equals(configManager.getConfiguration(SlayerSwapsConfig.GROUP, NEXUS_LEFT_CLICK_KEY)))
			{
				configManager.setConfiguration(SlayerSwapsConfig.GROUP, NEXUS_LEFT_CLICK_KEY, actions[0]);
			}
		}
	}

	private void rememberHouseFurniture(String name)
	{
		if (!client.getTopLevelWorldView().isInstance())
		{
			return;
		}
		if (name.equals(SPIRITUAL_FAIRY_TREE) || name.equals(FAIRY_RING))
		{
			configManager.setConfiguration(SlayerSwapsConfig.GROUP, HOUSE_FAIRY_RING_KEY, true);
		}
		Integer tier = JEWELLERY_BOXES.get(name);
		if (tier != null)
		{
			configManager.setConfiguration(SlayerSwapsConfig.GROUP, JEWELLERY_BOX_KEY, tier);
		}
	}

	private boolean fairyRingInScene()
	{
		for (TileObject object : teleportObjects)
		{
			String name = objectName(object);
			if (name.equals(FAIRY_RING) || name.equals(SPIRITUAL_FAIRY_TREE))
			{
				return true;
			}
		}
		return false;
	}

	private boolean jewelleryBoxInScene()
	{
		for (TileObject object : teleportObjects)
		{
			if (JEWELLERY_BOXES.containsKey(objectName(object)))
			{
				return true;
			}
		}
		return false;
	}

	Set<TileObject> teleportObjects()
	{
		return teleportObjects;
	}

	/**
	 * @return the object's current name, lower-case
	 */
	String objectName(TileObject object)
	{
		ObjectComposition def = definition(object);
		return def == null || def.getName() == null ? "" : def.getName().toLowerCase();
	}

	private ObjectComposition definition(TileObject object)
	{
		ObjectComposition def = client.getObjectDefinition(object.getId());
		return def.getImpostorIds() != null ? def.getImpostor() : def;
	}

	/**
	 * @return whether a portal nexus option goes to a teleport spell's destination
	 */
	static boolean isNexusOptionFor(String option, String spell)
	{
		String destination = spell.toLowerCase().replaceFirst(" teleport$", "");
		String o = Text.removeTags(option).trim().toLowerCase();
		return o.equals(destination) || o.equals(NEXUS_OPTION_NAMES.get(destination));
	}

	/**
	 * @return how to use a portal nexus in the scene for a teleport spell's destination: its left-click when that
	 * goes there, otherwise its teleport menu; or null with no nexus around
	 */
	String nexusOption(SlayerData.Route route)
	{
		return route != null && route.routeType() == RouteType.SPELL && nexusInScene() ? nexusHint(route.getValue()) : null;
	}

	private boolean nexusInScene()
	{
		for (TileObject object : teleportObjects)
		{
			if (objectName(object).equals(PORTAL_NEXUS))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return how to take the player's portal nexus to a spell's destination, such as "Great Kourend" (its
	 * left-click) or "Teleport Menu, 6: Kourend Castle"; or null if it's not known to go there
	 */
	String nexusHint(String spell)
	{
		String leftClick = configManager.getConfiguration(SlayerSwapsConfig.GROUP, NEXUS_LEFT_CLICK_KEY);
		if (leftClick != null && isNexusOptionFor(leftClick, spell))
		{
			return leftClick;
		}
		String destination = spell.replaceFirst(" Teleport$", "");
		String key = nexusKeys().get(destination.toLowerCase());
		return key == null ? null : NEXUS_TELEPORT_MENU + ", " + key + ": " + destination;
	}

	private Map<String, String> nexusKeys()
	{
		Map<String, String> keys = new HashMap<>();
		String saved = configManager.getConfiguration(SlayerSwapsConfig.GROUP, NEXUS_KEYS_KEY);
		if (saved != null)
		{
			for (String pair : saved.split(";"))
			{
				int eq = pair.indexOf('=');
				if (eq > 0)
				{
					keys.put(pair.substring(0, eq), pair.substring(eq + 1));
				}
			}
		}
		return keys;
	}

	private void saveNexusKeys(Map<String, String> keys)
	{
		StringBuilder saved = new StringBuilder();
		for (Map.Entry<String, String> e : new TreeMap<>(keys).entrySet())
		{
			saved.append(saved.length() == 0 ? "" : ";").append(e.getKey()).append('=').append(e.getValue());
		}
		if (!saved.toString().equals(configManager.getConfiguration(SlayerSwapsConfig.GROUP, NEXUS_KEYS_KEY)))
		{
			configManager.setConfiguration(SlayerSwapsConfig.GROUP, NEXUS_KEYS_KEY, saved.toString());
		}
	}

	/**
	 * @return names of the objects to outline for the teleport in use
	 */
	Set<String> objectNamesToHighlight()
	{
		Set<String> names = new HashSet<>();
		SlayerData.Route next = nextRoute();
		for (SlayerData.Route route : next == null ? Collections.<SlayerData.Route>emptyList() : Collections.singletonList(next))
		{
			if (route.routeType() == RouteType.FAIRY)
			{
				names.add(FAIRY_RING);
				names.add(SPIRITUAL_FAIRY_TREE);
			}
			else if (route.routeType() == RouteType.SPELL)
			{
				// A house portal nexus can hold teleport spells' destinations
				names.add(PORTAL_NEXUS);
			}
			else if (jewelleryTier(route) != null)
			{
				names.addAll(JEWELLERY_BOXES.keySet());
			}
			else if (route.routeType() == RouteType.OBELISK)
			{
				names.add(OBELISK);
			}
			if (route.getNote() != null && route.getNote().contains(LEVER))
			{
				names.add(LEVER);
			}
		}
		return names;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			teleportObjects.clear();
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::updateTask);
			if (log.isDebugEnabled() && !loggedKonarAreas)
			{
				loggedKonarAreas = true;
				clientThread.invokeLater(this::logKonarAreas);
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varpId = event.getVarpId();
		if (varpId == VarPlayerID.SLAYER_COUNT
			|| varpId == VarPlayerID.SLAYER_TARGET
			|| varpId == VarPlayerID.SLAYER_AREA
			|| event.getVarbitId() == VarbitID.SLAYER_TARGET_BOSSID
			|| event.getVarbitId() == VarbitID.SLAYER_MASTER)
		{
			clientThread.invokeLater(this::updateTask);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(SlayerSwapsConfig.GROUP))
		{
			menusDirty = true;
			String key = event.getKey();
			if (key.equals("randomKonarTask"))
			{
				// Ticking it picks a new random task; unticking goes back to the other test settings
				randomKonarPick = null;
			}
			if (key.equals("simulatedTask") || key.equals("konarTestTask") || key.equals("randomKonarTask"))
			{
				clientThread.invokeLater(() ->
				{
					updateTask();
					chat("Slayer Swaps test mode: " + (testTask() == null ? "off, using your real task"
						: testTask()[0] + (testTask()[1] != null ? " in " + testTask()[1] + " (Konar)" : "")) + ".");
				});
			}
			clientThread.invokeLater(this::updateCarriedRoute);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			updateSlayerHelmet();
		}
		if (event.getContainerId() == InventoryID.INV || event.getContainerId() == InventoryID.WORN)
		{
			updateCarriedRoute();
		}
	}

	private void updateSlayerHelmet()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item head = worn == null ? null : worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx());
		boolean wearing = head != null && head.getId() > 0
			&& client.getItemDefinition(head.getId()).getName().toLowerCase().contains("slayer helmet");
		if (wearing != wearingSlayerHelmet)
		{
			wearingSlayerHelmet = wearing;
			menusDirty = true;
			if (wearing && lastTaskKill != null)
			{
				// Putting the helmet back on asks for guidance again
				resumeGuidance();
			}
		}
	}

	private void updateTask()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		updateSlayerHelmet();

		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		String name = remaining > 0 ? lookupTaskName(client.getVarpValue(VarPlayerID.SLAYER_TARGET)) : null;
		String[] test = testTask();
		boolean simulated = test != null;
		if (simulated)
		{
			// Test mode: use the task picked in settings instead of the real one
			name = test[0];
			remaining = 0;
		}
		SlayerData.TaskData task = data.findTask(name);
		boolean krystilia = !simulated && name != null && client.getVarbitValue(VarbitID.SLAYER_MASTER) == KRYSTILIA;
		String area = simulated ? test[1] : name == null ? null : lookupKonarArea();
		if (krystilia != krystiliaTask || !Objects.equals(area, konarArea))
		{
			krystiliaTask = krystilia;
			konarArea = area;
			menusDirty = true;
			updateCarriedRoute();
		}

		boolean sameTask = Objects.equals(name, taskName);
		if (sameTask && !simulated && name != null && remaining < taskRemaining)
		{
			// A task kill: the player is at the task
			lastTaskKill = Instant.now();
			menusDirty = true;
			updateCarriedRoute();
		}
		taskRemaining = remaining;

		if (!sameTask)
		{
			taskName = name;
			lastTaskKill = null;
			menusDirty = true;
			updateCarriedRoute();
			if (name != null && task == null)
			{
				log.debug("Task {} isn't in the location data", name);
			}
			log.debug("Task is now {} ({} left), master {}, Konar area {}, location {}, routes {}",
				taskName, remaining, client.getVarbitValue(VarbitID.SLAYER_MASTER), konarArea, currentLocation(), currentRoutes());
		}
	}

	// Development aid: logs every slayer area as the game names it in the helper, and whether one of Konar's
	// areas in the location data matches it, so mismatched names show up in the log
	private void logKonarAreas()
	{
		for (int row : client.getDBTableRows(DBTableID.SlayerArea.ID))
		{
			String area = (String) client.getDBTableField(row, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
			boolean known = data.getTasks().stream().anyMatch(t -> t.getKonar() != null
				&& t.getKonar().keySet().stream().anyMatch(a -> sameArea(areaKey(area), areaKey(a), false)));
			log.debug("Konar area: {} -> {}", area, known ? "matches" : "no match");
		}
	}

	/**
	 * @return the task (and Konar area, or null) Test mode pretends to have, or null for the real task. Read
	 * straight from the Testing settings, so what they show is what's used: Random Konar task when ticked, then
	 * Konar task, then Test mode.
	 */
	private String[] testTask()
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
		KonarTestTask konar = config.konarTestTask();
		if (konar != KonarTestTask.OFF)
		{
			return new String[]{konar.getTask(), konar.getArea()};
		}
		SimulatedTask simulated = config.simulatedTask();
		return simulated != SimulatedTask.OFF ? new String[]{simulated.getTask(), null} : null;
	}

	/**
	 * @return the area Konar named for the current task, or null; same lookup as the core Slayer plugin
	 */
	private String lookupKonarArea()
	{
		int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		if (areaId <= 0)
		{
			return null;
		}
		List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
		return rows.isEmpty() ? null
			: (String) client.getDBTableField(rows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0];
	}

	/**
	 * Adds a client-side option to the slayer helmet's right-click menu, worn or in the inventory, showing where
	 * teleports go: "Location" for the current task, or "Master" when there's no task. Hovering it shows the
	 * choices as a submenu; clicking it shows them in the chatbox, to pick by mouse or number key. Nothing is sent to
	 * the server; choosing just stores the choice in config.
	 */
	private void addHelmetChooser(MenuEntry[] entries)
	{
		int helmetIdx = -1;
		for (int i = 0; i < entries.length; i++)
		{
			int itemId = itemIdOf(entries[i]);
			if (itemId > 0 && client.getItemDefinition(itemId).getName().toLowerCase().contains("slayer helmet"))
			{
				helmetIdx = i;
				break;
			}
		}
		if (helmetIdx == -1)
		{
			return;
		}

		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		String option;
		String target;
		String title;
		String selected;
		SlayerData.TaskData task = data.findTask(taskName);
		if (taskName != null)
		{
			if (task == null)
			{
				return;
			}
			List<String> locations = taskLocations(task, true);
			if (locations.isEmpty())
			{
				return;
			}
			selected = currentLocation();
			option = "Location";
			target = selected != null ? selected : konarArea != null ? konarArea : "Choose";
			title = konarArea != null ? "Konar: " + konarArea + ". Where to do " + task.getName() + "?" : "Where to do " + task.getName() + "?";
			for (String location : locations)
			{
				labels.add(location);
				actions.add(() -> chooseLocation(task, location));
			}
		}
		else
		{
			option = "Master";
			target = config.slayerMaster().toString();
			title = "Which slayer master?";
			selected = target;
			for (SlayerMaster master : SlayerMaster.values())
			{
				labels.add(master.toString());
				actions.add(() ->
				{
					configManager.setConfiguration(SlayerSwapsConfig.GROUP, "slayerMaster", master);
					chat("Slayer Swaps: slayer master set to " + master + ".");
				});
			}
		}

		addTeleportChooser(helmetIdx);
		addChooserEntry(helmetIdx, option, target, title, labels, actions, selected);
	}

	/**
	 * Adds "Teleport" to the slayer helmet's menu when the current location can be reached more than one way, to
	 * pick which teleport is swapped and highlighted.
	 */
	private void addTeleportChooser(int helmetIdx)
	{
		String location = currentLocation();
		List<SlayerData.Route> routes = baseRoutes();
		SlayerData.Route next = nextRoute();
		String key = chosenRouteKey();
		if (routes.isEmpty() || next == null || key == null)
		{
			return;
		}
		String who = taskName != null ? taskName : config.slayerMaster().toString();
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		for (SlayerData.Route route : routes)
		{
			String label = Routes.describe(data, route);
			labels.add(label);
			actions.add(() ->
			{
				configManager.setConfiguration(SlayerSwapsConfig.GROUP, key, Routes.key(route));
				chat("Slayer Swaps: " + who + " teleport set to " + label + ".");
			});
		}
		String current = Routes.describe(data, next);
		addChooserEntry(helmetIdx, "Teleport", Routes.describeTeleport(data, next), "How to get to " + location + "?",
			labels, actions, current);
	}

	/**
	 * @return the config key of the teleport chosen for the current task, or for going back to the slayer master
	 */
	private String chosenRouteKey()
	{
		if (taskName == null)
		{
			return SlayerSwapsConfig.ROUTE_KEY_PREFIX + "master_" + config.slayerMaster().name();
		}
		SlayerData.TaskData task = data.findTask(taskName);
		return task == null ? null : routeKey(task);
	}

	/**
	 * Adds an option whose submenu lists the choices, and which shows them in the chatbox when clicked.
	 */
	private void addChooserEntry(int index, String option, String target, String title, List<String> labels,
		List<Runnable> actions, String selected)
	{
		MenuEntry entry = client.getMenu().createMenuEntry(index)
			.setOption(ColorUtil.wrapWithColorTag(option, config.highlightColor()))
			.setTarget(ColorUtil.wrapWithColorTag(target, Color.WHITE))
			.setType(MenuAction.RUNELITE)
			.onClick(e -> openChooser(title, labels, actions));
		Menu sub = entry.createSubMenu();
		for (int i = labels.size() - 1; i >= 0; i--)
		{
			String label = labels.get(i);
			Runnable action = actions.get(i);
			sub.createMenuEntry(0)
				.setOption(label.equals(selected) ? ColorUtil.wrapWithColorTag(label, config.highlightColor()) : label)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> action.run());
		}
	}

	/**
	 * Shows choices in the chatbox, like the game's own "Select an option" dialogs.
	 */
	private void openChooser(String title, List<String> labels, List<Runnable> actions)
	{
		ChatboxTextMenuInput menu = chatboxPanelManager.openTextMenuInput(title);
		for (int i = 0; i < labels.size(); i++)
		{
			menu.option(labels.get(i), actions.get(i));
		}
		chooser = menu.build();
	}

	/**
	 * Closes the chatbox chooser when the player clicks anything outside it, like the game's own dialogs.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuEntry clickedEntry = event.getMenuEntry();
		String target = Text.removeTags(clickedEntry.getTarget()).toLowerCase();
		if (CHECK_TASK.equalsIgnoreCase(Text.removeTags(clickedEntry.getOption()))
			&& (target.contains("slayer helmet") || target.contains("enchanted gem") || target.contains("slayer ring")))
		{
			// Checking the task shows the guidance again, like a fresh start
			resumeGuidance();
		}

		if (chooser == null || chatboxPanelManager.getCurrentInput() != chooser)
		{
			chooser = null;
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		Widget clicked = entry.getWidget();
		Widget container = chatboxPanelManager.getContainerWidget();
		// Our own entries (like the helmet option that opens the chooser) and clicks inside the chooser keep it open
		if (entry.getType() == MenuAction.RUNELITE || clicked != null && container != null
			&& WidgetUtil.componentToInterface(clicked.getId()) == WidgetUtil.componentToInterface(container.getId()))
		{
			return;
		}
		chatboxPanelManager.close();
		chooser = null;
	}

	/**
	 * Stores a location chosen on the slayer helmet.
	 */
	private void chooseLocation(SlayerData.TaskData task, String location)
	{
		if (!task.getLocations().contains(location))
		{
			// A location only Konar assigns isn't one of the task's usual places; it's used anyway while her task lasts
			chat("Slayer Swaps: " + task.getName() + " at " + location + " (Konar's area).");
			return;
		}
		configManager.setConfiguration(SlayerSwapsConfig.GROUP, locationKey(task), location);
		// A new location starts with its best teleport
		configManager.unsetConfiguration(SlayerSwapsConfig.GROUP, routeKey(task));
		chat("Slayer Swaps: " + task.getName() + " set to " + location + ". Change it any time on your slayer helmet.");
	}

	private static String locationKey(SlayerData.TaskData task)
	{
		return SlayerSwapsConfig.LOCATION_KEY_PREFIX + SlayerData.normalize(task.getName());
	}

	private static String routeKey(SlayerData.TaskData task)
	{
		return SlayerSwapsConfig.ROUTE_KEY_PREFIX + SlayerData.normalize(task.getName());
	}

	private void chat(String text)
	{
		String message = new ChatMessageBuilder().append(text).build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
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
	 * @param current true for the player's current task, where Konar's area and Krystilia's Wilderness apply
	 * @return the locations a task can be done at with at least one enabled teleport, best first
	 */
	private List<String> taskLocations(SlayerData.TaskData task, boolean current)
	{
		List<String> locations = new ArrayList<>();
		if (current && konarArea != null)
		{
			for (String location : konarLocations(task))
			{
				if (!enabledRoutes(location).isEmpty())
				{
					locations.add(location);
				}
			}
			return locations;
		}

		boolean krystilia = current && krystiliaTask;
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
	 * @return the locations in the area Konar assigned, from her task list, or by name
	 */
	private List<String> konarLocations(SlayerData.TaskData task)
	{
		return konarLocations(task, konarArea);
	}

	private List<String> konarLocations(SlayerData.TaskData task, String konarArea)
	{
		String area = areaKey(konarArea);
		Map<String, List<String>> areas = task.getKonar() != null ? task.getKonar() : Collections.emptyMap();
		// The game's names are sometimes shorter or longer than the wiki's ("Tapoyauik", "Isle of Souls")
		for (boolean exact : new boolean[]{true, false})
		{
			for (Map.Entry<String, List<String>> e : areas.entrySet())
			{
				if (sameArea(area, areaKey(e.getKey()), exact))
				{
					return e.getValue();
				}
			}
			for (String location : task.getLocations())
			{
				if (sameArea(area, areaKey(location), exact))
				{
					return Collections.singletonList(location);
				}
			}
		}
		for (String location : data.getLocations().keySet())
		{
			if (areaKey(location).equals(area))
			{
				return Collections.singletonList(location);
			}
		}
		log.debug("Konar's area {} for {} isn't in the location data", konarArea, task.getName());
		return Collections.emptyList();
	}

	/**
	 * @return an area or location name in lower-case letters and digits, without the game's "the", "in the" or
	 * "task-only" prefixes
	 */
	private static String areaKey(String area)
	{
		return area.toLowerCase().replaceFirst("^(task-only |in the |the )", "").replaceAll("[^a-z0-9]", "");
	}

	private static boolean sameArea(String a, String b, boolean exact)
	{
		return exact ? a.equals(b) : !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a));
	}

	/**
	 * @return the chosen location if it's usable; otherwise Konar's area or a task's only location; otherwise null
	 */
	private String chosenLocation(SlayerData.TaskData task, List<String> locations)
	{
		if (locations.isEmpty())
		{
			return null;
		}
		String chosen = configManager.getConfiguration(SlayerSwapsConfig.GROUP, locationKey(task));
		if (locations.contains(chosen))
		{
			return chosen;
		}
		// Not chosen: wait for a choice, unless there's nothing to choose between
		return konarArea != null || locations.size() == 1 ? locations.get(0) : null;
	}

	/**
	 * @return true when the current task has places to go but none has been chosen yet
	 */
	boolean awaitingChoice()
	{
		SlayerData.TaskData task = data.findTask(taskName);
		return task != null && !atTask() && !taskLocations(task, true).isEmpty() && currentLocation() == null;
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
		return task == null ? null : chosenLocation(task, taskLocations(task, true));
	}

	/**
	 * @return the enabled teleports to the current location, with the player's chosen teleport first
	 */
	List<SlayerData.Route> currentRoutes()
	{
		if (config.onlyWithSlayerHelmet() && !wearingSlayerHelmet || atTask())
		{
			// Nothing to guide: the player is at the task (until they leave, say to bank, or finish it)
			return Collections.emptyList();
		}

		// The teleport chosen on the slayer helmet goes first; the rest stay as fallbacks if it isn't carried
		List<SlayerData.Route> routes = new ArrayList<>(baseRoutes());
		String key = chosenRouteKey();
		String chosen = key == null ? null : configManager.getConfiguration(SlayerSwapsConfig.GROUP, key);
		for (int i = 0; i < routes.size(); i++)
		{
			if (isChosen(routes.get(i), chosen))
			{
				routes.add(0, routes.remove(i));
				break;
			}
		}
		return routes;
	}

	/**
	 * @return whether a route is the one chosen on the slayer helmet
	 */
	private static boolean isChosen(SlayerData.Route route, String chosen)
	{
		return chosen != null && chosen.equals(Routes.key(route));
	}

	/**
	 * @return the enabled teleports to the slayer master (with no task) or the task's location, best first
	 */
	private List<SlayerData.Route> baseRoutes()
	{
		if (taskName == null)
		{
			SlayerData.MasterData master = data.master(config.slayerMaster().name());
			return master != null ? filterEnabled(master.getRoutes()) : Collections.emptyList();
		}
		String location = currentLocation();
		return location == null ? Collections.emptyList() : enabledRoutes(location);
	}

	/**
	 * @return the teleport to use next: the first one that isn't an item, or is an item being carried
	 */
	SlayerData.Route nextRoute()
	{
		return firstUsable(routesWithHome(), carriedItemIds());
	}

	/**
	 * The current routes, led by a house teleport when the main teleport (the chosen or best one) is used inside the
	 * player's house and they're not there: a spell their portal nexus does, the house fairy ring, or jewellery
	 * they're not carrying but their jewellery box has.
	 */
	List<SlayerData.Route> routesWithHome()
	{
		List<SlayerData.Route> routes = currentRoutes();
		if (routes.isEmpty() || inHouse())
		{
			return routes;
		}
		// The main teleport is done in the house
		String step = houseStep(routes.get(0), carriedItemIds());
		if (step == null)
		{
			return routes;
		}
		// Any way home will do: a carried cape or tablet, or the Teleport to House spell
		List<SlayerData.Route> withHome = new ArrayList<>();
		withHome.add(homeRoute(step));
		withHome.addAll(routes);
		return withHome;
	}

	private static SlayerData.Route homeRoute(String step)
	{
		SlayerData.Route home = new SlayerData.Route();
		home.setType(RouteType.PORTAL.getKey());
		home.setValue(HOME);
		home.setNote(step);
		return home;
	}

	/**
	 * @return what to use in the house for a route, such as "Nexus : Great Kourend", or null if it isn't
	 * done in the house
	 */
	private String houseStep(SlayerData.Route route, List<Integer> carried)
	{
		RouteType type = route.routeType();
		if (type == RouteType.SPELL)
		{
			String hint = nexusHint(route.getValue());
			return hint == null ? null : "Nexus : " + hint;
		}
		if (type == RouteType.FAIRY)
		{
			// A fairy ring nearby is quicker than going home to the house's
			return !fairyRingInScene()
				&& Boolean.parseBoolean(configManager.getConfiguration(SlayerSwapsConfig.GROUP, HOUSE_FAIRY_RING_KEY))
				? "House fairy ring : " + route.getValue() : null;
		}
		if (jewelleryBoxHas(route) && !isCarried(route, carried))
		{
			return "Jewellery box : " + jewelleryHint(route);
		}
		return null;
	}

	private boolean isCarried(SlayerData.Route route, List<Integer> carried)
	{
		for (int itemId : carried)
		{
			if (teleportItems.matches(itemId, route))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return true in the player's house, known by its teleport furniture in an instance
	 */
	private boolean inHouse()
	{
		if (!client.getTopLevelWorldView().isInstance())
		{
			return false;
		}
		for (TileObject object : teleportObjects)
		{
			String name = objectName(object);
			if (name.equals(PORTAL_NEXUS) || name.equals(SPIRITUAL_FAIRY_TREE) || JEWELLERY_BOXES.containsKey(name))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return whether the player's jewellery box (remembered from the house) holds this route's jewellery
	 */
	private boolean jewelleryBoxHas(SlayerData.Route route)
	{
		Integer needed = jewelleryTier(route);
		String tier = configManager.getConfiguration(SlayerSwapsConfig.GROUP, JEWELLERY_BOX_KEY);
		return needed != null && tier != null && Integer.parseInt(tier) >= needed;
	}

	/**
	 * @return the jewellery box tier (1 basic, 2 fancy, 3 ornate) that holds this route's jewellery, or null
	 */
	private static Integer jewelleryTier(SlayerData.Route route)
	{
		RouteType type = route.routeType();
		if (type == RouteType.RING_OF_DUELING)
		{
			return 1;
		}
		if (type == RouteType.AMULET_OF_GLORY)
		{
			return 3;
		}
		return type == RouteType.ITEM ? JEWELLERY_ITEM_TIERS.get(route.getItem()) : null;
	}

	/**
	 * @return the jewellery box destination, with its teleport menu hotkey when it's been seen
	 */
	String jewelleryHint(SlayerData.Route route)
	{
		String key = menuHighlighter.getJewelleryKeys().get(route.getValue().toLowerCase());
		return key == null ? route.getValue() : key + ": " + route.getValue();
	}

	/**
	 * @return the jewellery box destination to pick when the box stands in for jewellery that isn't carried, or null
	 */
	String jewelleryBoxOption(SlayerData.Route route)
	{
		return route != null && jewelleryTier(route) != null && jewelleryBoxInScene()
			&& !isCarried(route, carriedItemIds()) ? jewelleryHint(route) : null;
	}

	/**
	 * @return text to show above a teleport object for the teleport in use, or null
	 */
	String objectLabel(String name, SlayerData.Route next)
	{
		if (name.equals(PORTAL_NEXUS))
		{
			return nexusOption(next);
		}
		if (JEWELLERY_BOXES.containsKey(name) && next != null && jewelleryTier(next) != null)
		{
			return jewelleryHint(next);
		}
		return null;
	}

	/**
	 * @return the first teleport that can be used now: the chosen one if possible, then ones that aren't used up
	 * (like an eternal slayer ring or a max cape), then charged jewellery and tablets
	 */
	private SlayerData.Route firstUsable(List<SlayerData.Route> routes, List<Integer> carried)
	{
		String key = chosenRouteKey();
		String chosen = key == null ? null : configManager.getConfiguration(SlayerSwapsConfig.GROUP, key);
		for (SlayerData.Route route : routes)
		{
			if (isChosen(route, chosen) && isUsable(route, carried))
			{
				return route;
			}
		}
		for (boolean consumable : new boolean[]{false, true})
		{
			for (SlayerData.Route route : routes)
			{
				if (isUsable(route, carried) && isConsumable(route, carried) == consumable)
				{
					return route;
				}
			}
		}
		return routes.isEmpty() ? null : routes.get(0);
	}

	private static boolean isHomeStep(SlayerData.Route route)
	{
		return route.routeType() == RouteType.PORTAL && HOME.equals(route.getValue()) && route.getNote() != null;
	}

	private boolean isUsable(SlayerData.Route route, List<Integer> carried)
	{
		return isHomeStep(route) || !ITEM_ROUTES.contains(route.routeType()) || route.routeType() == RouteType.SPELL
			// In the house, the jewellery box stands in for jewellery that isn't carried
			|| jewelleryTier(route) != null && jewelleryBoxInScene() && jewelleryBoxHas(route)
			|| isCarried(route, carried);
	}

	/**
	 * @return true when the only carried items for a route get used up: charged jewellery like "Necklace of
	 * passage(5)", or teleport tablets and scrolls
	 */
	private boolean isConsumable(SlayerData.Route route, List<Integer> carried)
	{
		boolean any = false;
		for (int itemId : carried)
		{
			if (teleportItems.matches(itemId, route))
			{
				String name = client.getItemDefinition(itemId).getName().toLowerCase();
				if (!CHARGES.matcher(name).find() && !name.endsWith("teleport") && !name.startsWith("teleport to"))
				{
					return false;
				}
				any = true;
			}
		}
		return any;
	}

	String taskName()
	{
		return taskName;
	}

	SlayerData data()
	{
		return data;
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
			if (type != null && isEnabled(type))
			{
				enabled.add(route);
			}
		}
		return enabled;
	}

	/**
	 * The item to outline: the teleport in use (see nextRoute) when it's an item.
	 */
	private void updateCarriedRoute()
	{
		SlayerData.Route next = nextRoute();
		carriedRoute = next != null && ITEM_ROUTES.contains(next.routeType()) ? next : null;
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
		return route != null && (teleportItems.matches(itemId, route)
			|| isHomeStep(route) && teleportItems.matches(itemId, HOUSE_SPELL));
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
		checkLeftTask();
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
		// Only the teleport in use, so menus don't light up for teleports the player isn't using
		SlayerData.Route next = config.highlightMenus() ? nextRoute() : null;
		List<SlayerData.Route> highlight = new ArrayList<>();
		if (next != null)
		{
			highlight.add(next);
			if (isHomeStep(next))
			{
				// Going home: the spell in the spellbook too, not just the capes and tablets
				highlight.add(HOUSE_SPELL);
			}
		}
		menuHighlighter.update(highlight);
		// The item to outline can change with things not tied to an event, like the house furniture nearby
		updateCarriedRoute();
		if (!menuHighlighter.getNexusKeys().isEmpty())
		{
			saveNexusKeys(menuHighlighter.getNexusKeys());
		}
	}

	// Runs after the Menu Entry Swapper (priority 0) and other swap plugins, so our swap wins over theirs
	@Subscribe(priority = -100)
	public void onPostMenuSort(PostMenuSort event)
	{
		// The menu isn't rebuilt while it's open, so swapping now would swap repeatedly
		if (client.isMenuOpen() || client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		List<SlayerData.Route> routes = routesWithHome();
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
			if (route.routeType() == RouteType.FAIRY && swapFairyRing(route.getValue())
				|| route.routeType() == RouteType.SPELL && swapPortalNexus(route.getValue())
				|| jewelleryTier(route) != null && swapJewelleryBox(route.getValue()))
			{
				return;
			}
		}
	}

	/**
	 * Puts a jewellery box's previous-destination option on left-click when it's the destination, otherwise its
	 * teleport menu, where the destination is outlined.
	 */
	private boolean swapJewelleryBox(String destination)
	{
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		int teleportMenuIdx = -1;
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			if (!OBJECT_MENU_TYPES.contains(entry.getType())
				|| !JEWELLERY_BOXES.containsKey(Text.removeTags(entry.getTarget()).toLowerCase()))
			{
				continue;
			}
			String option = Text.removeTags(entry.getOption());
			if (option.equalsIgnoreCase(destination))
			{
				moveToTop(menu, entries, i);
				return true;
			}
			if (option.equalsIgnoreCase(JEWELLERY_TELEPORT_MENU))
			{
				teleportMenuIdx = i;
			}
		}
		if (teleportMenuIdx != -1)
		{
			moveToTop(menu, entries, teleportMenuIdx);
			return true;
		}
		return false;
	}

	/**
	 * Puts the portal nexus option for a spell's destination on left-click, or its teleport menu (where the
	 * destination is outlined) when the nexus's left-click goes somewhere else.
	 */
	private boolean swapPortalNexus(String spell)
	{
		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		int teleportMenuIdx = -1;
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			if (!OBJECT_MENU_TYPES.contains(entry.getType())
				|| !Text.removeTags(entry.getTarget()).equalsIgnoreCase(PORTAL_NEXUS))
			{
				continue;
			}
			if (isNexusOptionFor(entry.getOption(), spell))
			{
				moveToTop(menu, entries, i);
				return true;
			}
			if (Text.removeTags(entry.getOption()).equalsIgnoreCase(NEXUS_TELEPORT_MENU))
			{
				teleportMenuIdx = i;
			}
		}
		if (teleportMenuIdx != -1)
		{
			moveToTop(menu, entries, teleportMenuIdx);
			return true;
		}
		return false;
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

				// A boat route's value is the island; the item's option is the teleport to the boat
				String option = route.routeType() == RouteType.BOAT ? BOAT_OPTION : route.getValue();
				if (optionMatches(entry.getOption(), option))
				{
					moveToTop(menu, entries, i);
					return true;
				}

				Menu sub = entry.getSubMenu();
				if (sub != null)
				{
					for (MenuEntry subEntry : sub.getMenuEntries())
					{
						if (optionMatches(subEntry.getOption(), option))
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
		addHelmetChooser(event.getMenuEntries());
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

	// Not a config method: RuneLite config interfaces only support @ConfigItem methods without arguments
	private boolean isEnabled(RouteType type)
	{
		switch (type)
		{
			case RING:
				return config.useRing();
			case FAIRY:
				return config.useFairy();
			case PORTAL:
				return config.usePortal();
			case MAXCAPE:
				return config.useMaxcape();
			case KARAMJA_GLOVES:
				return config.useKaramjaGloves();
			// These only go to the Wilderness, so they're off along with Wilderness locations
			case BURNING_AMULET:
				return config.useBurningAmulet() && wildernessAllowed();
			case RING_OF_DUELING:
				return config.useRingOfDueling() && wildernessAllowed();
			case AMULET_OF_GLORY:
				return config.useGlory();
			case BOAT:
				return config.useBoat();
			case ITEM:
				return config.useItems();
			case SPELL:
				return config.useSpells();
			case OBELISK:
				return config.useObelisk() && wildernessAllowed();
			default:
				return false;
		}
	}

	private boolean wildernessAllowed()
	{
		return config.wilderness() || krystiliaTask;
	}

	@Provides
	SlayerSwapsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerSwapsConfig.class);
	}
}
