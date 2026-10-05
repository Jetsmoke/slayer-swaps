package com.slayerteleportswap;

import com.google.common.collect.ImmutableSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;

/**
 * Which teleport items serve which route types. Capes come in many variants, so they're matched by name.
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
	private final Map<Integer, Set<RouteType>> cache = new HashMap<>();

	@Inject
	TeleportItems(Client client)
	{
		this.client = client;
	}

	/**
	 * @return the route types an item can teleport by; empty if it isn't a supported teleport item
	 */
	Set<RouteType> routeTypes(int itemId)
	{
		if (itemId <= 0)
		{
			return EnumSet.noneOf(RouteType.class);
		}
		return cache.computeIfAbsent(itemId, this::lookup);
	}

	private Set<RouteType> lookup(int itemId)
	{
		Set<RouteType> types = EnumSet.noneOf(RouteType.class);
		if (SLAYER_RINGS.contains(itemId))
		{
			types.add(RouteType.RING);
		}
		if (itemId == ItemID.ATJUN_GLOVES_ELITE)
		{
			types.add(RouteType.KARAMJA_GLOVES);
		}
		if (BURNING_AMULETS.contains(itemId))
		{
			types.add(RouteType.BURNING_AMULET);
		}
		if (RINGS_OF_DUELING.contains(itemId))
		{
			types.add(RouteType.RING_OF_DUELING);
		}

		String name = client.getItemDefinition(itemId).getName().toLowerCase();
		if (name.contains("max cape"))
		{
			types.add(RouteType.MAXCAPE);
			types.add(RouteType.PORTAL);
			types.add(RouteType.BOAT);
		}
		else if (name.startsWith("construct. cape"))
		{
			types.add(RouteType.PORTAL);
		}
		else if (name.startsWith("amulet of") && name.contains("glory"))
		{
			// "Amulet of glory(1-6)", "Amulet of glory(t1-t6)" and "Amulet of eternal glory"
			types.add(RouteType.AMULET_OF_GLORY);
		}
		else if (name.startsWith("sailing cape"))
		{
			types.add(RouteType.BOAT);
		}
		return types;
	}
}
