package com.slayerswaps;

import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;

/**
 * Decides which teleport items serve which routes. Most items are matched by name, so every charge count and
 * variant (eternal glory, trimmed capes, ornament kits) counts as the same item.
 */
@Singleton
class TeleportItems
{
	private static final Set<Integer> SLAYER_RINGS = ImmutableSet.of(
		ItemID.SLAYER_RING_1, ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_4,
		ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_6, ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_8,
		ItemID.SLAYER_RING_ETERNAL);
	private static final Set<Integer> BURNING_AMULETS = ImmutableSet.of(
		ItemID.BURNING_AMULET_1, ItemID.BURNING_AMULET_2, ItemID.BURNING_AMULET_3, ItemID.BURNING_AMULET_4,
		ItemID.BURNING_AMULET_5);
	private static final Set<Integer> RINGS_OF_DUELING = ImmutableSet.of(
		ItemID.RING_OF_DUELING_1, ItemID.RING_OF_DUELING_2, ItemID.RING_OF_DUELING_3, ItemID.RING_OF_DUELING_4,
		ItemID.RING_OF_DUELING_5, ItemID.RING_OF_DUELING_6, ItemID.RING_OF_DUELING_7, ItemID.RING_OF_DUELING_8);

	private final Client client;
	private final Map<Integer, String> names = new HashMap<>();
	private SlayerData data;

	@Inject
	TeleportItems(Client client)
	{
		this.client = client;
	}

	void setData(SlayerData data)
	{
		this.data = data;
	}

	private String name(int itemId)
	{
		return names.computeIfAbsent(itemId, id -> client.getItemDefinition(id).getName().toLowerCase());
	}

	/**
	 * @return true if this item can be used for the route. Call on the client thread.
	 */
	boolean matches(int itemId, SlayerData.Route route)
	{
		RouteType type = route.routeType();
		if (itemId <= 0 || type == null)
		{
			return false;
		}

		String name = name(itemId);
		String value = route.getValue() == null ? "" : route.getValue().toLowerCase();
		switch (type)
		{
			case RING:
				return SLAYER_RINGS.contains(itemId);
			case KARAMJA_GLOVES:
				return itemId == ItemID.ATJUN_GLOVES_ELITE;
			case BURNING_AMULET:
				return BURNING_AMULETS.contains(itemId);
			case RING_OF_DUELING:
				return RINGS_OF_DUELING.contains(itemId);
			case AMULET_OF_GLORY:
				// "Amulet of glory(1-6)", "Amulet of glory(t1-t6)" and "Amulet of eternal glory"
				return name.startsWith("amulet of") && name.contains("glory");
			case PORTAL:
				// Construction cape, max cape, or a redirected house tablet like "Rimmington teleport"
				return isMaxCape(name) || name.startsWith("construct. cape") || name.equals(value + " teleport")
					|| value.equals("home") && name.equals("teleport to house");
			case MAXCAPE:
				return isMaxCape(name);
			case BOAT:
				return isMaxCape(name) || name.startsWith("sailing cape") || name.equals("teleport to boat");
			case SPELL:
				// The spell's tablet has the same name
				return name.equals(value);
			case ITEM:
				SlayerData.ItemData item = data == null ? null : data.getItems().get(route.getItem());
				if (item != null)
				{
					for (String prefix : item.getNames())
					{
						if (name.startsWith(prefix))
						{
							return true;
						}
					}
				}
				return false;
			default:
				return false;
		}
	}

	/**
	 * @return true if the item is any teleport item the plugin knows, for placing the location picker
	 */
	boolean isTeleportItem(int itemId)
	{
		if (itemId <= 0)
		{
			return false;
		}
		if (SLAYER_RINGS.contains(itemId) || BURNING_AMULETS.contains(itemId) || RINGS_OF_DUELING.contains(itemId)
			|| itemId == ItemID.ATJUN_GLOVES_ELITE)
		{
			return true;
		}
		String name = name(itemId);
		if (isMaxCape(name) || name.startsWith("construct. cape") || name.contains("teleport")
			|| (name.startsWith("amulet of") && name.contains("glory")))
		{
			return true;
		}
		if (data != null)
		{
			for (SlayerData.ItemData item : data.getItems().values())
			{
				for (String prefix : item.getNames())
				{
					if (name.startsWith(prefix))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean isMaxCape(String name)
	{
		return name.contains("max cape");
	}
}
