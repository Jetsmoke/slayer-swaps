package com.slayerswaps;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
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
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slayer Swaps",
	description = "Automatically swaps menu entries, and highlights teleport items and spells to quickly teleport to your slayer task. Supports choosing your preferred slayer master and preferred task location. To start, right-click your slayer helmet, black mask or enchanted gem.",
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
	private MinimapArrowOverlay minimapArrowOverlay;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private TaskMonsterOverlay taskMonsterOverlay;

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
	private static final String JEWELLERY_KEYS_KEY = "jewelleryKeys";
	// The jewellery box teleport menu's hotkeys, the same for every box (seen in the ornate box's menu)
	private static final Map<String, String> JEWELLERY_KEYS = ImmutableMap.<String, String>builder()
		.put("emir's arena", "1").put("castle wars", "2").put("ferox enclave", "3").put("fortis colosseum", "4")
		.put("burthorpe", "5").put("barbarian outpost", "6").put("corporeal beast", "7").put("tears of guthix", "8")
		.put("wintertodt camp", "9").put("warriors' guild", "A").put("champions' guild", "B").put("monastery", "C")
		.put("ranging guild", "D").put("fishing guild", "E").put("mining guild", "F").put("crafting guild", "G")
		.put("cooking guild", "H").put("woodcutting guild", "I").put("farming guild", "J").put("miscellania", "K")
		.put("grand exchange", "L").put("falador park", "M").put("dondakan's rock", "N").put("edgeville", "O")
		.put("karamja", "P").put("draynor village", "Q").put("al kharid", "R")
		.build();
	// Teleports the jewellery box names differently from the jewellery itself
	private static final Map<String, String> JEWELLERY_MENU_NAMES = ImmutableMap.of(
		"falador", "falador park",
		"dondakan", "dondakan's rock");
	private static final String HOUSE_PORTAL_SUFFIX = " portal";
	private static final String OBELISK_KEYS_KEY = "obeliskKeys";
	private static final String HOME = "Home";
	private static final SlayerData.Route HOUSE_SPELL = new SlayerData.Route();
	static
	{
		HOUSE_SPELL.setType(RouteType.SPELL.getKey());
		HOUSE_SPELL.setValue("Teleport to House");
	}
	// Slayer helmet, enchanted gem and slayer ring option that shows the task
	private static final String CHECK_TASK = "Check";
	// The quetzals you ride at each landing site
	private static final Set<Integer> QUETZAL_IDS = ImmutableSet.of(NpcID.QUETZAL_FORTIS, NpcID.QUETZAL_TEOMAT,
		NpcID.QUETZAL_SUNSETCOAST, NpcID.QUETZAL_HUNTERGUILD, NpcID.QUETZAL_CAMTORUM, NpcID.QUETZAL_COLOSSALWYRM,
		NpcID.QUETZAL_OUTERFORTIS, NpcID.QUETZAL_COLOSSEUM, NpcID.QUETZAL_ALDARIN, NpcID.QUETZAL_QUETZACALLIGORGE,
		NpcID.QUETZAL_SALVAGEROVERLOOK);
	// The surface map ends here; underground areas, Zanaris and instances are north of it
	private static final int SURFACE_MAX_Y = 4200;
	// An objective underground this close is in the same underground area as the player
	private static final int SAME_AREA_DISTANCE = 150;
	// This close to the objective (or the way in), walk instead of teleporting
	private static final int NEAR_DISTANCE = 100;
	// Moving this far in one tick is a teleport
	private static final int TELEPORT_JUMP = 30;
	// A teleport that lands this close to the objective is on the way there, even if it isn't the one chosen
	private static final int EN_ROUTE_DISTANCE = 300;
	// This close, the monsters are in sight and the arrow goes away
	private static final int ARRIVED_DISTANCE = 15;
	// Lines the chatbox chooser shows at once
	private static final int CHOOSER_LINES = 5;
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
	// Teleport spells whose portal nexus and portal chamber name is the place, not the spell (from the nexus's menu)
	private static final Map<String, String> NEXUS_OPTION_NAMES = ImmutableMap.<String, String>builder()
		.put("kourend castle", "great kourend")
		.put("camelot", "seers' village")
		.put("moonclan", "lunar isle")
		.put("barbarian", "barbarian outpost")
		.put("khazard", "port khazard")
		.put("waterbirth", "waterbirth island")
		.put("fenkenstrain's castle", "fenken' castle")
		.build();
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
	private boolean loggedKonarAreas;
	// The chatbox chooser opened from the slayer helmet, while it's open
	private ChatboxTextMenuInput chooser;
	// Close to the task's monsters (or the slayer master) or the way in to them: walk there instead of teleporting
	private boolean nearObjective;
	// The player's tile last tick, to notice teleports
	private WorldPoint lastTile;
	// Teleported somewhere much closer to the objective, by any teleport: walk (or take the last step) from there
	private boolean teleportedToward;
	// The world map marker on the objective, while there is one
	private WorldMapPoint mapMarker;
	// The task's monsters in the scene, tracked as they spawn and despawn
	private final Set<NPC> taskNpcs = new HashSet<>();
	// Lower-case names of the task's monsters
	private Set<String> taskMonsterNames = Collections.emptySet();
	// The quetzals at landing sites in the scene, to outline when the next step is a quetzal ride
	private final Set<NPC> quetzals = new HashSet<>();

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
		overlayManager.add(minimapArrowOverlay);
		overlayManager.add(taskMonsterOverlay);
		migrateOldChoices();
		TaskOverride.refresh = () -> clientThread.invokeLater(this::updateTask);

		clientThread.invokeLater(this::updateTask);
	}

	@Override
	protected void shutDown()
	{
		TaskOverride.refresh = null;
		overlayManager.remove(menuHighlightOverlay);
		overlayManager.remove(teleportItemOverlay);
		overlayManager.remove(destinationOverlay);
		overlayManager.remove(teleportObjectOverlay);
		overlayManager.remove(minimapArrowOverlay);
		overlayManager.remove(taskMonsterOverlay);
		teleportObjects.clear();
		taskNpcs.clear();
		quetzals.clear();
		nearObjective = false;
		if (mapMarker != null)
		{
			worldMapPointManager.remove(mapMarker);
			mapMarker = null;
		}
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
		// Development builds remembered portal chamber portals from other players' houses
		configManager.unsetConfiguration(SlayerSwapsConfig.GROUP, "housePortals");
		// The jewellery box's teleport menu was once mistaken for the portal nexus's, saving its hotkeys as the nexus's
		if (nexusKeys().containsKey("emir's arena"))
		{
			configManager.unsetConfiguration(SlayerSwapsConfig.GROUP, NEXUS_KEYS_KEY);
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
		teleportedToward = false;
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
		if (TELEPORT_OBJECT_NAMES.contains(objectName(object)) || isHousePortal(objectName(object)))
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

	/**
	 * @return whether an object is a portal chamber's portal, like "Kourend Castle Portal": named for a place and
	 * ending in "portal", but not the portal nexus or the house's exit portal
	 */
	private static boolean isHousePortal(String name)
	{
		return name.endsWith(HOUSE_PORTAL_SUFFIX) && !name.equals(PORTAL_NEXUS);
	}

	/**
	 * @return the name (lower-case) of a portal chamber portal in the scene to a teleport spell's destination, or
	 * null. Only ones in sight count, as in a friend's house: there's no telling the player's own house from another's,
	 * so going home relies on the portal nexus
	 */
	private String housePortalFor(String spell)
	{
		String destination = spell.toLowerCase().replaceFirst(" teleport$", "");
		for (TileObject object : teleportObjects)
		{
			String name = objectName(object);
			if (isHousePortal(name))
			{
				String portal = name.substring(0, name.length() - HOUSE_PORTAL_SUFFIX.length());
				if (portal.equals(destination) || portal.equals(NEXUS_OPTION_NAMES.get(destination)))
				{
					return name;
				}
			}
		}
		return null;
	}

	/**
	 * @return the destination of a portal chamber portal in the scene for a teleport spell, such as "Kourend
	 * Castle", or null
	 */
	String housePortalOption(SlayerData.Route route)
	{
		if (route == null || route.routeType() != RouteType.SPELL)
		{
			return null;
		}
		String portal = housePortalFor(route.getValue());
		for (TileObject object : teleportObjects)
		{
			ObjectComposition def = definition(object);
			if (portal != null && def != null && portal.equalsIgnoreCase(def.getName()))
			{
				// The place as the portal names it: "Lunar Isle" for Moonclan Teleport
				return def.getName().substring(0, def.getName().length() - HOUSE_PORTAL_SUFFIX.length());
			}
		}
		return null;
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
	 * @return where a portal nexus in the scene takes the player for a teleport spell, such as "Kourend Castle", or
	 * null with no nexus around
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
	 * @return the player's portal nexus destination for a spell, such as "Great Kourend" (its left-click) or
	 * "Kourend Castle" (in its teleport menu); or null if it's not known to go there
	 */
	String nexusHint(String spell)
	{
		if (nexusLeftClickGoesTo(spell))
		{
			return configManager.getConfiguration(SlayerSwapsConfig.GROUP, NEXUS_LEFT_CLICK_KEY);
		}
		String destination = spell.replaceFirst(" Teleport$", "");
		// Known from its teleport menu, or the player says their house has one
		return nexusKey(spell) != null || config.portalNexus() ? destination : null;
	}

	/**
	 * @return the portal nexus menu hotkey for a spell's destination, under the spell's name or the place's
	 * ("Lunar Isle" for Moonclan Teleport), or null if the menu hasn't been seen
	 */
	private String nexusKey(String spell)
	{
		String destination = spell.toLowerCase().replaceFirst(" teleport$", "");
		Map<String, String> keys = nexusKeys();
		String key = keys.get(destination);
		return key != null ? key : keys.get(NEXUS_OPTION_NAMES.get(destination));
	}

	private boolean nexusLeftClickGoesTo(String spell)
	{
		String leftClick = configManager.getConfiguration(SlayerSwapsConfig.GROUP, NEXUS_LEFT_CLICK_KEY);
		return leftClick != null && isNexusOptionFor(leftClick, spell);
	}

	private Map<String, String> nexusKeys()
	{
		return savedKeys(NEXUS_KEYS_KEY);
	}

	/**
	 * @return a teleport menu's hotkeys by destination (lower-case), saved from the last time it was open
	 */
	private Map<String, String> savedKeys(String configKey)
	{
		Map<String, String> keys = new HashMap<>();
		String saved = configManager.getConfiguration(SlayerSwapsConfig.GROUP, configKey);
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

	private void saveKeys(String configKey, Map<String, String> keys)
	{
		StringBuilder saved = new StringBuilder();
		for (Map.Entry<String, String> e : new TreeMap<>(keys).entrySet())
		{
			saved.append(saved.length() == 0 ? "" : ";").append(e.getKey()).append('=').append(e.getValue());
		}
		if (!saved.toString().equals(configManager.getConfiguration(SlayerSwapsConfig.GROUP, configKey)))
		{
			configManager.setConfiguration(SlayerSwapsConfig.GROUP, configKey, saved.toString());
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
				// The house's portal chamber portal to the place, or else its portal nexus
				String portal = housePortalFor(route.getValue());
				names.add(portal != null ? portal : PORTAL_NEXUS);
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

	/**
	 * @return whether a lower-case item name is a slayer helmet (any variant) or black mask (any charge or imbue)
	 */
	static boolean isSlayerHeadgear(String name)
	{
		return name.contains("slayer helmet") || name.startsWith("black mask");
	}

	/**
	 * @return whether a lower-case item name is slayer gear the plugin's options go on: slayer headgear, or an
	 * enchanted or eternal gem
	 */
	static boolean isSlayerGear(String name)
	{
		return isSlayerHeadgear(name) || name.equals("enchanted gem") || name.equals("eternal gem");
	}

	private void updateSlayerHelmet()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item head = worn == null ? null : worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx());
		boolean wearing = head != null && head.getId() > 0
			&& isSlayerHeadgear(client.getItemDefinition(head.getId()).getName().toLowerCase());
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
		String[] test = TaskOverride.task;
		boolean simulated = test != null;
		if (simulated)
		{
			// Testing: the task picked in the testing plugin instead of the real one
			name = test[0];
			remaining = 0;
		}
		if (TaskOverride.done)
		{
			// Testing: the task is done, so the way back to the slayer master shows
			name = null;
			remaining = 0;
			simulated = true;
		}
		SlayerData.TaskData task = data.findTask(name);
		boolean krystilia = !simulated && name != null && client.getVarbitValue(VarbitID.SLAYER_MASTER) == KRYSTILIA;
		String area = name == null ? null : simulated ? test[1] : lookupKonarArea();
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
			teleportedToward = false;
			updateTaskNpcs();
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
			if (itemId > 0 && isSlayerGear(client.getItemDefinition(itemId).getName().toLowerCase()))
			{
				helmetIdx = i;
				break;
			}
		}
		if (helmetIdx == -1)
		{
			return;
		}

		SlayerData.TaskData task = data.findTask(taskName);
		if (taskName == null)
		{
			// Entries added later at the same index sit lower in the menu: Master, then Teleport below it
			addMasterChooser(helmetIdx);
			addTeleportChooser(helmetIdx);
			return;
		}
		// Master first, at the top, then Location and Teleport below it
		addMasterChooser(helmetIdx);
		List<String> locations = task == null ? Collections.emptyList() : taskLocations(task, true);
		if (!locations.isEmpty())
		{
			List<String> labels = new ArrayList<>();
			List<Runnable> actions = new ArrayList<>();
			String selected = currentLocation() == null ? null : Routes.place(currentLocation());
			String target = selected != null ? selected : konarArea != null ? konarArea : "Choose";
			String title = konarArea != null ? "Konar: " + konarArea + ". Where to do " + task.getName() + "?"
				: "Where to do " + task.getName() + "?";
			for (String location : locations)
			{
				labels.add(Routes.place(location));
				actions.add(() -> chooseLocation(task, location));
			}
			addChooserEntry(helmetIdx, "Location", target, title, labels, actions, selected);
			addTeleportChooser(helmetIdx);
		}
	}

	/**
	 * Adds "Master" to the slayer helmet's menu, to pick the slayer master to go back to after a task.
	 */
	private void addMasterChooser(int helmetIdx)
	{
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		String target = config.slayerMaster() == SlayerMaster.NOT_CHOSEN ? "Choose" : config.slayerMaster().toString();
		for (SlayerMaster master : SlayerMaster.values())
		{
			if (master == SlayerMaster.NOT_CHOSEN)
			{
				continue;
			}
			labels.add(master.toString());
			actions.add(() ->
			{
				configManager.setConfiguration(SlayerSwapsConfig.GROUP, "slayerMaster", master);
				chat("Slayer Swaps: slayer master set to " + master + ".");
			});
		}
		addChooserEntry(helmetIdx, "Master", target, "Which slayer master?", labels, actions, target);
	}

	/**
	 * Adds "Teleport" to the slayer helmet's menu when the current location can be reached more than one way, to
	 * pick which teleport is swapped and highlighted.
	 */
	private void addTeleportChooser(int helmetIdx)
	{
		String location = currentLocation();
		List<SlayerData.Route> routes = baseRoutes();
		String key = chosenRouteKey();
		// The teleport chosen, or else the best one carried; shown even while guidance is paused (helmet off, at the
		// task, or close enough to walk), since the choice still stands
		String chosen = key == null ? null : configManager.getConfiguration(SlayerSwapsConfig.GROUP, key);
		SlayerData.Route next = routes.stream().filter(r -> isChosen(r, chosen)).findFirst()
			.orElse(chosen == null && !(taskName == null && awaitingChoice()) ? firstUsable(routes, carriedItemIds()) : null);
		if (routes.isEmpty() || key == null)
		{
			// Still listed, so the menu always has the same three options: the teleports come with a location
			client.getMenu().createMenuEntry(helmetIdx)
				.setOption(ColorUtil.wrapWithColorTag("Teleport", config.highlightColor()))
				.setTarget(ColorUtil.wrapWithColorTag(taskName == null ? "Choose a master first" : "Choose a location first",
					Color.WHITE))
				.setType(MenuAction.RUNELITE);
			return;
		}
		String who = taskName != null ? taskName : config.slayerMaster().toString();
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		for (SlayerData.Route route : routes)
		{
			labels.add(Routes.shortName(data, route));
			actions.add(() ->
			{
				configManager.setConfiguration(SlayerSwapsConfig.GROUP, key, Routes.key(route));
				chat("Slayer Swaps: " + who + " teleport set to " + Routes.describe(data, route) + ".");
			});
		}
		// Before a slayer master's first teleport is chosen there's no next one yet
		String current = next == null ? null : Routes.shortName(data, next);
		addChooserEntry(helmetIdx, "Teleport", next == null ? "Choose" : current,
			"How to get to " + Routes.place(location) + "?", labels, actions, current);
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
		openChooser(title, labels, actions, 0);
	}

	/**
	 * Shows one page of choices: the chatbox fits five lines, so longer lists show four with "More..." for the
	 * next page, like the game's own dialogs.
	 */
	private void openChooser(String title, List<String> labels, List<Runnable> actions, int start)
	{
		ChatboxTextMenuInput menu = chatboxPanelManager.openTextMenuInput(title);
		boolean paged = labels.size() > CHOOSER_LINES;
		int end = paged ? Math.min(start + CHOOSER_LINES - 1, labels.size()) : labels.size();
		for (int i = start; i < end; i++)
		{
			menu.option(labels.get(i), actions.get(i));
		}
		if (paged)
		{
			int next = end < labels.size() ? end : 0;
			menu.option("More...", () -> openChooser(title, labels, actions, next));
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
			&& (isSlayerGear(target) || target.contains("slayer ring")))
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
		teleportedToward = false;
		if (!task.getLocations().contains(location))
		{
			// A location only Konar assigns isn't one of the task's usual places; it's used anyway while her task lasts
			chat("Slayer Swaps: " + task.getName() + " at " + Routes.place(location) + " (Konar's area).");
			return;
		}
		configManager.setConfiguration(SlayerSwapsConfig.GROUP, locationKey(task), location);
		// A new location starts with its best teleport
		configManager.unsetConfiguration(SlayerSwapsConfig.GROUP, routeKey(task));
		chat("Slayer Swaps: " + task.getName() + " set to " + Routes.place(location) + ". Change it any time on your slayer helmet.");
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
	 * @return true when there's a choice to make on the slayer helmet before teleports are swapped
	 */
	boolean awaitingChoice()
	{
		return choicePrompt() != null;
	}

	/**
	 * @return what's still to be chosen on the slayer helmet, such as "to choose a location": a location for a
	 * task with several, a slayer master with no task, and the first time back to each master, its teleport;
	 * or null
	 */
	String choicePrompt()
	{
		if (taskName != null)
		{
			SlayerData.TaskData task = data.findTask(taskName);
			return task != null && !atTask() && !taskLocations(task, true).isEmpty() && currentLocation() == null
				? "to choose a location" : null;
		}
		if (config.slayerMaster() == SlayerMaster.NOT_CHOSEN)
		{
			return "to choose your slayer master";
		}
		return baseRoutes().size() > 1 && configManager.getConfiguration(SlayerSwapsConfig.GROUP, chosenRouteKey()) == null
			? "to choose a teleport" : null;
	}

	/**
	 * @return the tile the current task's monsters are around at the chosen location, or with no task the slayer
	 * master's; null when the wiki doesn't say
	 */
	WorldPoint objective()
	{
		if (taskName == null)
		{
			SlayerData.MasterData master = data.master(config.slayerMaster().name());
			return master == null || master.getSpot() == null ? null : master.getSpot().toWorldPoint();
		}
		SlayerData.TaskData task = data.findTask(taskName);
		String location = currentLocation();
		SlayerData.Spot spot = task == null || location == null || task.getSpots() == null ? null
			: task.getSpots().get(location);
		return spot == null ? null : spot.toWorldPoint();
	}

	/**
	 * @return where to walk now: the objective, or from the surface the way in to an objective underground; null
	 * when that isn't known, or the objective is in another underground area
	 */
	WorldPoint heading()
	{
		WorldPoint target = objective();
		WorldPoint player = playerLocation();
		if (target == null || player == null)
		{
			return null;
		}
		boolean targetOnSurface = target.getY() < SURFACE_MAX_Y;
		boolean playerOnSurface = player.getY() < SURFACE_MAX_Y;
		if (targetOnSurface == playerOnSurface)
		{
			// Underground areas all share one part of the map, so only one that's close is the same area
			return targetOnSurface || player.distanceTo2D(target) <= SAME_AREA_DISTANCE ? target : null;
		}
		SlayerData.LocationData location = playerOnSurface ? data.location(currentLocation()) : null;
		return location == null || location.getEntrance() == null ? null : location.getEntrance().toWorldPoint();
	}

	/**
	 * @return the player's tile, outside of instances too
	 */
	WorldPoint playerLocation()
	{
		Player player = client.getLocalPlayer();
		return player == null ? null : WorldPoint.fromLocalInstance(client, player.getLocalLocation());
	}

	private void updateNearObjective()
	{
		WorldPoint heading = heading();
		WorldPoint player = playerLocation();
		WorldPoint before = lastTile;
		lastTile = player;
		if (heading == null)
		{
			teleportedToward = false;
		}
		else if (before != null && player != null && before.distanceTo2D(player) > TELEPORT_JUMP)
		{
			// A teleport: one of the location's other teleports counts too, when it lands much closer. Going home
			// or anywhere farther away doesn't
			int was = before.distanceTo2D(heading);
			int now = player.distanceTo2D(heading);
			teleportedToward = now <= EN_ROUTE_DISTANCE && now < was - TELEPORT_JUMP;
		}
		boolean near = heading != null && player != null
			&& (player.distanceTo2D(heading) <= NEAR_DISTANCE || teleportedToward || atQuetzal())
			// Where the wiki doesn't say where the monsters are, seeing them is arriving
			|| taskName != null && objective() == null && !taskNpcs.isEmpty();
		if (near != nearObjective)
		{
			nearObjective = near;
			menusDirty = true;
			updateCarriedRoute();
		}
	}

	/**
	 * @return after a teleport that lands on the way, the step left that the location's teleports share, like
	 * "take the quetzal to the Teomat" for Ralos' Rise; null when it's a walk
	 */
	String walkNote()
	{
		if (!teleportedToward && !atQuetzal())
		{
			return null;
		}
		WorldPoint heading = heading();
		WorldPoint player = playerLocation();
		if (heading == null || player == null || player.distanceTo2D(heading) <= NEAR_DISTANCE)
		{
			return null;
		}
		String note = null;
		for (SlayerData.Route route : baseRoutes())
		{
			if (route.getNote() != null)
			{
				if (note != null && !note.equals(route.getNote()))
				{
					return null;
				}
				note = route.getNote();
			}
		}
		return note;
	}

	/**
	 * @return the quetzal landing site to fly to, to outline on the quetzal map: the next teleport's, or after a
	 * teleport that lands on the way, the one the location's teleports share; or null
	 */
	String quetzalStop()
	{
		SlayerData.Route next = nextRoute();
		if (next != null && next.getQuetzal() != null)
		{
			return next.getQuetzal();
		}
		if (walkNote() == null)
		{
			return null;
		}
		for (SlayerData.Route route : baseRoutes())
		{
			if (route.getQuetzal() != null)
			{
				return route.getQuetzal();
			}
		}
		return null;
	}

	/**
	 * Keeps a marker on the world map at the task's monsters (or the slayer master), which also points from the map's
	 * edge when it's out of view.
	 */
	private void updateMapMarker()
	{
		WorldPoint at = config.markOnMap() && !awaitingChoice() ? objective() : null;
		String location = at == null ? null : currentLocation();
		String tooltip = location == null ? null
			: (taskName != null ? taskName : config.slayerMaster().toString()) + ": " + Routes.place(location);
		if (mapMarker != null && at != null && at.equals(mapMarker.getWorldPoint()) && tooltip.equals(mapMarker.getTooltip()))
		{
			return;
		}
		if (mapMarker != null)
		{
			worldMapPointManager.remove(mapMarker);
			mapMarker = null;
		}
		if (at != null && tooltip != null)
		{
			mapMarker = WorldMapPoint.builder()
				.worldPoint(at)
				.image(markerImage(config.highlightColor()))
				.tooltip(tooltip)
				.jumpOnClick(true)
				.snapToEdge(true)
				.build();
			worldMapPointManager.add(mapMarker);
		}
	}

	private static BufferedImage markerImage(Color colour)
	{
		BufferedImage image = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.fillOval(0, 0, 15, 15);
		g.setColor(colour);
		g.fillOval(2, 2, 11, 11);
		g.dispose();
		return image;
	}

	boolean nearObjective()
	{
		return nearObjective;
	}

	/**
	 * @return where the minimap arrow points, or null to hide it: close to the objective, until the monsters are
	 * in sight, while guidance is on
	 */
	WorldPoint arrowTarget()
	{
		if (!config.taskArrow() || !nearObjective || atTask() || awaitingChoice()
			|| config.onlyWithSlayerHelmet() && !wearingSlayerHelmet)
		{
			return null;
		}
		WorldPoint heading = heading();
		WorldPoint player = playerLocation();
		// Not while there's a step like a quetzal ride to take first: the arrow would point to a long run
		return heading == null || player == null || player.distanceTo2D(heading) <= ARRIVED_DISTANCE
			|| walkNote() != null ? null : heading;
	}

	/**
	 * @return the task's monsters in the scene, to highlight
	 */
	Set<NPC> taskNpcs()
	{
		return taskNpcs;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (isTaskNpc(event.getNpc()))
		{
			taskNpcs.add(event.getNpc());
		}
		if (isQuetzal(event.getNpc()))
		{
			quetzals.add(event.getNpc());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		taskNpcs.remove(event.getNpc());
		quetzals.remove(event.getNpc());
	}

	/**
	 * @return whether an NPC is a quetzal to ride: Renu, a landing site's quetzal by ID, or one with the ride's own
	 * options ("Travel" and "Last-destination")
	 */
	private static boolean isQuetzal(NPC npc)
	{
		// The quetzal is Renu wherever it lands
		if (QUETZAL_IDS.contains(npc.getId()) || "Renu".equals(npc.getName()))
		{
			return true;
		}
		NPCComposition def = npc.getTransformedComposition();
		List<String> actions = def == null || def.getActions() == null ? Collections.emptyList() : Arrays.asList(def.getActions());
		return actions.contains("Travel") && actions.contains("Last-destination");
	}

	/**
	 * @return true next to a quetzal landing site when the way to the location goes by quetzal, however the player
	 * got there (logging in there, say): the ride is next, not another teleport
	 */
	private boolean atQuetzal()
	{
		if (quetzals.isEmpty())
		{
			return false;
		}
		for (SlayerData.Route route : baseRoutes())
		{
			if (route.getQuetzal() != null)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the quetzals to outline: the landing site's, when the next step is a quetzal ride
	 */
	Set<NPC> quetzalsToHighlight()
	{
		return quetzalStop() != null && !atTask() && !(config.onlyWithSlayerHelmet() && !wearingSlayerHelmet)
			? quetzals : Collections.emptySet();
	}

	private void updateTaskNpcs()
	{
		SlayerData.TaskData task = data.findTask(taskName);
		Set<String> names = new HashSet<>();
		if (task != null)
		{
			names.add(singular(task.getName().toLowerCase()));
			for (String monster : task.getMonsters() == null ? Collections.<String>emptyList() : task.getMonsters())
			{
				// "Skeleton Hellhound (Vet'ion)" is called "Skeleton Hellhound" in the game
				names.add(monster.replaceFirst("\\s*\\(.*\\)$", "").toLowerCase());
			}
		}
		taskMonsterNames = names;
		taskNpcs.clear();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (isTaskNpc(npc))
				{
					taskNpcs.add(npc);
				}
			}
		}
	}

	/**
	 * @return whether an NPC is one of the task's monsters that can be attacked: its name is one the wiki gives the
	 * task, or contains the task's name ("Ogre chieftain" for Ogres)
	 */
	private boolean isTaskNpc(NPC npc)
	{
		if (taskMonsterNames.isEmpty() || npc.getName() == null)
		{
			return false;
		}
		NPCComposition def = npc.getTransformedComposition();
		if (def == null || def.getActions() == null || Arrays.stream(def.getActions()).noneMatch("Attack"::equals))
		{
			return false;
		}
		String name = Text.removeTags(npc.getName()).toLowerCase();
		for (String monster : taskMonsterNames)
		{
			if (name.equals(monster) || Pattern.compile("\\b" + Pattern.quote(monster) + "\\b").matcher(name).find())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return a task's name for one monster: "Jellies" is "jelly", "Wolves" is "wolf", "Ogres" is "ogre"
	 */
	static String singular(String name)
	{
		if (name.endsWith("ies"))
		{
			return name.substring(0, name.length() - 3) + "y";
		}
		if (name.endsWith("ves"))
		{
			return name.substring(0, name.length() - 3) + "f";
		}
		return name.endsWith("s") && !name.endsWith("ss") ? name.substring(0, name.length() - 1) : name;
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
		if (taskName == null && awaitingChoice())
		{
			// The first time back to a slayer master, wait for a teleport to be chosen
			return Collections.emptyList();
		}
		if (nearObjective)
		{
			// Close enough to walk: no more teleports (the minimap arrow points the way)
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
		// The main teleport is done in the house: the chosen one, or else the best one usable without going home, so
		// a carried max cape beats going home to the jewellery box for a combat bracelet
		List<Integer> carried = carriedItemIds();
		String key = chosenRouteKey();
		String chosen = key == null ? null : configManager.getConfiguration(SlayerSwapsConfig.GROUP, key);
		SlayerData.Route main = isChosen(routes.get(0), chosen) ? routes.get(0) : firstUsable(routes, carried);
		String step = houseStep(main, carried);
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
			return "Jewellery box : " + route.getValue();
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
			if (name.equals(PORTAL_NEXUS) || name.equals(SPIRITUAL_FAIRY_TREE) || JEWELLERY_BOXES.containsKey(name)
				|| isHousePortal(name))
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
	 * @return the jewellery box destination to pick when the box stands in for jewellery that isn't carried, or null
	 */
	String jewelleryBoxOption(SlayerData.Route route)
	{
		return route != null && jewelleryTier(route) != null && jewelleryBoxInScene()
			&& !isCarried(route, carriedItemIds()) ? route.getValue() : null;
	}

	/**
	 * @return the lines to show above a fairy ring (its code), or a portal nexus, jewellery box or Wilderness obelisk
	 * for the teleport in use: "Press 6" for its teleport menu hotkey once the menu's been seen, otherwise "Click
	 * here"; or null
	 */
	List<String> objectLabel(TileObject object, String name, SlayerData.Route next)
	{
		if ((name.equals(FAIRY_RING) || name.equals(SPIRITUAL_FAIRY_TREE)) && next != null
			&& next.routeType() == RouteType.FAIRY)
		{
			return Collections.singletonList(next.getValue());
		}
		if (isHousePortal(name) && next != null && next.routeType() == RouteType.SPELL)
		{
			return Collections.singletonList("Click here");
		}
		if (name.equals(PORTAL_NEXUS))
		{
			String destination = nexusOption(next);
			if (destination == null)
			{
				return null;
			}
			String key = nexusKey(next.getValue());
			return objectLines(nexusLeftClickGoesTo(next.getValue()), key);
		}
		if (JEWELLERY_BOXES.containsKey(name) && next != null && jewelleryTier(next) != null)
		{
			String destination = next.getValue();
			ObjectComposition def = definition(object);
			boolean leftClick = def != null && def.getActions() != null
				&& Arrays.stream(def.getActions()).anyMatch(destination::equalsIgnoreCase);
			return objectLines(leftClick, jewelleryKey(destination));
		}
		if (name.equals(OBELISK) && next != null && next.routeType() == RouteType.OBELISK)
		{
			return objectLines(false, savedKeys(OBELISK_KEYS_KEY).get(next.getValue().toLowerCase()));
		}
		return null;
	}

	/**
	 * @return the jewellery box teleport menu's hotkey for a destination: the box's order is fixed, so it's known
	 * before the menu has been opened
	 */
	private String jewelleryKey(String destination)
	{
		String name = destination.toLowerCase();
		name = JEWELLERY_MENU_NAMES.getOrDefault(name, name);
		String key = savedKeys(JEWELLERY_KEYS_KEY).get(name);
		return key != null ? key : JEWELLERY_KEYS.get(name);
	}

	/**
	 * @return "Click here" when the left-click goes there or the menu's hotkey isn't known yet, otherwise "Press 6"
	 */
	private static List<String> objectLines(boolean leftClick, String key)
	{
		return Collections.singletonList(leftClick || key == null ? "Click here" : "Press " + key);
	}

	/**
	 * @return the first teleport that can be used now: the chosen one if possible, then ones that aren't used up
	 * (like an eternal slayer ring or a max cape), then charged jewellery and tablets
	 */
	private SlayerData.Route firstUsable(List<SlayerData.Route> routes, List<Integer> carried)
	{
		String key = chosenRouteKey();
		String chosen = key == null ? null : configManager.getConfiguration(SlayerSwapsConfig.GROUP, key);
		if (!routes.isEmpty() && isHomeStep(routes.get(0)))
		{
			return routes.get(0);
		}
		// The teleport chosen on the slayer helmet, even when it isn't carried, so the choice shows what to bring
		for (SlayerData.Route route : routes)
		{
			if (isChosen(route, chosen))
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
		updateNearObjective();
		updateMapMarker();
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
		String quetzal = quetzalStop();
		if (quetzal != null && config.highlightMenus())
		{
			SlayerData.Route ride = new SlayerData.Route();
			ride.setType(RouteType.QUETZAL.getKey());
			ride.setValue(quetzal);
			highlight.add(ride);
		}
		menuHighlighter.update(highlight);
		// The item to outline can change with things not tied to an event, like the house furniture nearby
		updateCarriedRoute();
		if (!menuHighlighter.getNexusKeys().isEmpty())
		{
			saveKeys(NEXUS_KEYS_KEY, menuHighlighter.getNexusKeys());
		}
		if (!menuHighlighter.getJewelleryKeys().isEmpty())
		{
			saveKeys(JEWELLERY_KEYS_KEY, menuHighlighter.getJewelleryKeys());
		}
		if (!menuHighlighter.getObeliskKeys().isEmpty())
		{
			saveKeys(OBELISK_KEYS_KEY, menuHighlighter.getObeliskKeys());
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
