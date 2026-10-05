package com.slayerswaps;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

/**
 * Finds the option for the current destination in open teleport menus (max cape, construction cape, fairy ring
 * log, item dialogs, Wilderness obelisk, boat teleport) and teleport spells in the spellbook. An interface only counts
 * as a menu for a route if it shows several of that route's known destinations, so a destination name appearing
 * elsewhere isn't highlighted.
 */
@Singleton
class MenuHighlighter
{
	private static final Pattern KEY_PREFIX = Pattern.compile("^[0-9a-z]\\s*[.:)]\\s+");
	private static final Pattern FAIRY_CODE = Pattern.compile("^[a-d][i-l][p-s]$");

	private static final Set<String> PORTALS = ImmutableSet.of("home", "rimmington", "taverley", "pollnivneach",
		"hosidius", "aldarin", "rellekka", "brimhaven", "yanille", "prifddinas");
	private static final Set<String> MAXCAPE = ImmutableSet.<String>builder().addAll(PORTALS)
		.add("warrior's guild", "fishing guild", "crafting guild", "farming guild", "otto's grotto",
			"feldip hunter area", "wilderness hunter area", "hunter guild", "the pandemonium", "boat", "last boat")
		.build();
	private static final Map<RouteType, Set<String>> FAMILIES = ImmutableMap.<RouteType, Set<String>>builder()
		.put(RouteType.PORTAL, PORTALS)
		.put(RouteType.MAXCAPE, MAXCAPE)
		.put(RouteType.BURNING_AMULET, ImmutableSet.of("chaos temple", "bandit camp", "lava maze"))
		.put(RouteType.RING_OF_DUELING, ImmutableSet.of("emir's arena", "castle wars", "ferox enclave", "fortis colosseum"))
		.put(RouteType.KARAMJA_GLOVES, ImmutableSet.of("gem mine", "slayer master"))
		.put(RouteType.AMULET_OF_GLORY, ImmutableSet.of("edgeville", "karamja", "draynor village", "al kharid"))
		// From the wiki's "Select Obelisk destination" interface image ("1: Level 13 Wilderness" and so on)
		.put(RouteType.OBELISK, ImmutableSet.of("level 13 wilderness", "level 19 wilderness", "level 27 wilderness",
			"level 35 wilderness", "level 44 wilderness", "level 50 wilderness"))
		.build();

	// Portal nexus destinations, from the wiki's teleport menu image and the teleport spells it can hold
	private static final Set<String> NEXUS = ImmutableSet.of("varrock", "grand exchange", "falador", "lumbridge",
		"civitas illa fortis", "kourend castle", "waterbirth island", "kharyrll", "west ardougne", "ardougne", "camelot",
		"seers' village", "watchtower", "yanille", "trollheim", "ape atoll", "catherby", "barrows", "fishing guild",
		"senntisten", "annakarl", "carrallanger", "ghorrock", "lassar", "paddewwa", "dareeyak", "salve graveyard",
		"harmony island", "cemetery", "arceuus library", "draynor manor", "battlefront", "mind altar",
		"fenkenstrain's castle", "marim", "lunar isle", "ourania", "barbarian outpost", "khazard", "ice plateau",
		"great kourend", "seers' village");

	// Jewellery box teleport menu destinations: dueling ring, games necklace, skills necklace, combat bracelet, glory
	// and ring of wealth teleports
	private static final Set<String> JEWELLERY = ImmutableSet.of("emir's arena", "castle wars", "ferox enclave",
		"fortis colosseum", "burthorpe", "barbarian outpost", "corporeal beast", "tears of guthix", "wintertodt camp",
		"fishing guild", "mining guild", "crafting guild", "cooking guild", "woodcutting guild", "farming guild",
		"warriors' guild", "champions' guild", "monastery", "ranging guild", "edgeville", "karamja", "draynor village",
		"al kharid", "miscellania", "grand exchange", "falador park", "dondakan");

	private final Client client;

	private static final Pattern KEYED_OPTION = Pattern.compile("^([0-9A-Za-z])\\s*[:.]\\s+(.+)$");

	private SlayerData data;
	private List<Widget> highlighted = Collections.emptyList();
	// The portal nexus teleport menu's hotkeys by destination (lower-case), from the last time it was open
	private Map<String, String> nexusKeys = Collections.emptyMap();
	// The same for the house jewellery box's teleport menu
	private Map<String, String> jewelleryKeys = Collections.emptyMap();

	@Inject
	MenuHighlighter(Client client)
	{
		this.client = client;
	}

	void setData(SlayerData data)
	{
		this.data = data;
	}

	List<Widget> getHighlighted()
	{
		return highlighted;
	}

	Map<String, String> getNexusKeys()
	{
		return nexusKeys;
	}

	Map<String, String> getJewelleryKeys()
	{
		return jewelleryKeys;
	}

	void clear()
	{
		highlighted = Collections.emptyList();
	}

	/**
	 * Rescans open interfaces for the given routes' options. Call on the client thread.
	 */
	void update(Collection<SlayerData.Route> routes)
	{
		if (routes.isEmpty())
		{
			clear();
			return;
		}

		// Labelled widgets of every open interface, grouped by interface
		Map<Integer, List<Widget>> byGroup = new HashMap<>();
		Widget[] roots = client.getWidgetRoots();
		if (roots != null)
		{
			for (Widget root : roots)
			{
				collect(root, byGroup);
			}
		}

		for (List<Widget> widgets : byGroup.values())
		{
			if (isNexus(widgets))
			{
				Map<String, String> keys = keys(widgets);
				nexusKeys = keys.isEmpty() ? nexusKeys : keys;
			}
			else if (countKnown(widgets, JEWELLERY) >= 3)
			{
				Map<String, String> keys = keys(widgets);
				jewelleryKeys = keys.isEmpty() ? jewelleryKeys : keys;
			}
		}

		List<Widget> found = new ArrayList<>();
		for (Map.Entry<Integer, List<Widget>> group : byGroup.entrySet())
		{
			List<Widget> widgets = group.getValue();
			for (SlayerData.Route route : routes)
			{
				RouteType type = route.routeType();
				if (type == null || route.getValue() == null || !isMenuFor(type, route, widgets)
					// Spells only in the spellbook or portal nexus: the bank also lists tablets named after the spells
					|| type == RouteType.SPELL && group.getKey() != InterfaceID.MAGIC_SPELLBOOK && !isNexus(widgets))
				{
					continue;
				}
				for (Widget w : widgets)
				{
					if (matches(type, route.getValue(), w) && onScreen(w))
					{
						found.add(w);
					}
				}
			}
		}
		highlighted = found;
	}

	/**
	 * @return false for widgets scrolled out of their list, which are still there but clipped out of sight
	 */
	private static boolean onScreen(Widget w)
	{
		Rectangle bounds = w.getBounds();
		if (bounds == null || bounds.isEmpty())
		{
			return false;
		}
		for (Widget parent = w.getParent(); parent != null; parent = parent.getParent())
		{
			Rectangle visible = parent.getBounds();
			if (visible != null && !visible.isEmpty() && !visible.intersects(bounds))
			{
				return false;
			}
		}
		return true;
	}

	private static void collect(Widget w, Map<Integer, List<Widget>> byGroup)
	{
		if (w == null || w.isHidden())
		{
			return;
		}

		if (!isEmpty(w.getText()) || !isEmpty(w.getName()))
		{
			byGroup.computeIfAbsent(WidgetUtil.componentToInterface(w.getId()), k -> new ArrayList<>()).add(w);
		}

		collectAll(w.getStaticChildren(), byGroup);
		collectAll(w.getDynamicChildren(), byGroup);
		collectAll(w.getNestedChildren(), byGroup);
	}

	private static void collectAll(Widget[] children, Map<Integer, List<Widget>> byGroup)
	{
		if (children != null)
		{
			for (Widget child : children)
			{
				collect(child, byGroup);
			}
		}
	}

	private boolean isMenuFor(RouteType type, SlayerData.Route route, List<Widget> widgets)
	{
		switch (type)
		{
			case FAIRY:
				return widgets.stream().filter(w -> FAIRY_CODE.matcher(label(w).replace(" ", "")).matches()).count() >= 2;
			case BOAT:
				return widgets.stream().anyMatch(w -> label(w).contains("boat"));
			case SPELL:
				// The spellbook (several widgets named after teleport spells), or a house portal nexus's teleport menu
				return widgets.stream().filter(w -> name(w).endsWith(" teleport")).count() >= 3 || isNexus(widgets);
			case ITEM:
				SlayerData.ItemData item = data == null ? null : data.getItems().get(route.getItem());
				return item != null && countKnown(widgets, lower(item.getOptions())) >= 2;
			default:
				Set<String> family = FAMILIES.get(type);
				return family != null && countKnown(widgets, family) >= 2;
		}
	}

	/**
	 * @return hotkeys of a menu's options ("1: Varrock") by option, lower-case
	 */
	private static Map<String, String> keys(List<Widget> widgets)
	{
		Map<String, String> keys = new HashMap<>();
		for (Widget w : widgets)
		{
			Matcher m = KEYED_OPTION.matcher(isEmpty(w.getText()) ? "" : Text.removeTags(w.getText()).trim());
			if (m.matches())
			{
				keys.put(m.group(2).trim().toLowerCase(), m.group(1).toUpperCase());
			}
		}
		return keys;
	}

	private static boolean isNexus(List<Widget> widgets)
	{
		return countKnown(widgets, NEXUS) >= 2;
	}

	private static long countKnown(List<Widget> widgets, Set<String> known)
	{
		return widgets.stream().map(MenuHighlighter::label).filter(known::contains).distinct().count();
	}

	private static Set<String> lower(List<String> values)
	{
		Set<String> out = new HashSet<>();
		for (String v : values)
		{
			out.add(v.toLowerCase());
		}
		return out;
	}

	private static boolean matches(RouteType type, String value, Widget w)
	{
		String target = value.toLowerCase();
		switch (type)
		{
			case FAIRY:
				return label(w).replace(" ", "").equals(target);
			case BOAT:
				return label(w).contains(target);
			case SPELL:
				// The portal nexus lists the destination without "Teleport" ("6: Kourend Castle"), sometimes renamed
				return name(w).equals(target) || SlayerSwapsPlugin.isNexusOptionFor(label(w), target);
			default:
				return label(w).equals(target);
		}
	}

	/**
	 * Text of a widget, lower-cased with tags and any hotkey prefix like "1. " or "B: " removed.
	 */
	static String label(Widget w)
	{
		if (isEmpty(w.getText()))
		{
			return "";
		}
		String text = Text.removeTags(w.getText()).trim().toLowerCase();
		return KEY_PREFIX.matcher(text).replaceFirst("");
	}

	private static String name(Widget w)
	{
		return isEmpty(w.getName()) ? "" : Text.removeTags(w.getName()).trim().toLowerCase();
	}

	private static boolean isEmpty(String s)
	{
		return s == null || s.isEmpty();
	}
}
