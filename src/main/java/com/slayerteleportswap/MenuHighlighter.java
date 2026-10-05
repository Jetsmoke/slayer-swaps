package com.slayerteleportswap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

/**
 * Finds the option for the current destination in open teleport menus (max cape, construction cape, fairy ring
 * log, item dialogs, boat teleport). An interface only counts as a teleport menu for a route type if it shows
 * several of that type's known destinations, so a destination name appearing elsewhere isn't highlighted.
 */
@Slf4j
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
		.build();

	private final Client client;

	private List<Widget> highlighted = Collections.emptyList();

	@Inject
	MenuHighlighter(Client client)
	{
		this.client = client;
	}

	List<Widget> getHighlighted()
	{
		return highlighted;
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

		// Text widgets of every open interface, grouped by interface
		Map<Integer, List<Widget>> byGroup = new HashMap<>();
		Widget[] roots = client.getWidgetRoots();
		if (roots != null)
		{
			for (Widget root : roots)
			{
				collect(root, byGroup);
			}
		}

		List<Widget> found = new ArrayList<>();
		for (List<Widget> widgets : byGroup.values())
		{
			for (SlayerData.Route route : routes)
			{
				RouteType type = route.routeType();
				if (type != null && isMenuFor(type, widgets))
				{
					for (Widget w : widgets)
					{
						if (matches(type, route.getValue(), label(w)))
						{
							found.add(w);
						}
					}
				}
			}
		}
		highlighted = found;
	}

	private static void collect(Widget w, Map<Integer, List<Widget>> byGroup)
	{
		if (w == null || w.isHidden())
		{
			return;
		}

		String text = w.getText();
		if (text != null && !text.isEmpty())
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

	private static boolean isMenuFor(RouteType type, List<Widget> widgets)
	{
		if (type == RouteType.FAIRY)
		{
			return widgets.stream().filter(w -> FAIRY_CODE.matcher(label(w).replace(" ", "")).matches()).count() >= 2;
		}
		if (type == RouteType.BOAT)
		{
			return widgets.stream().anyMatch(w -> label(w).contains("boat"));
		}

		Set<String> family = FAMILIES.get(type);
		if (family == null)
		{
			return false;
		}
		return widgets.stream().map(MenuHighlighter::label).filter(family::contains).distinct().count() >= 2;
	}

	private static boolean matches(RouteType type, String value, String label)
	{
		String target = value.toLowerCase();
		switch (type)
		{
			case FAIRY:
				return label.replace(" ", "").equals(target);
			case BOAT:
				return label.contains(target);
			default:
				return label.equals(target);
		}
	}

	/**
	 * Text of a widget, lower-cased with tags and any hotkey prefix like "1. " or "B: " removed.
	 */
	static String label(Widget w)
	{
		String text = Text.removeTags(w.getText()).trim().toLowerCase();
		return KEY_PREFIX.matcher(text).replaceFirst("");
	}
}
