package com.slayerteleportswap;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SlayerTeleportSwapConfig.GROUP)
public interface SlayerTeleportSwapConfig extends Config
{
	String GROUP = "slayerteleportswap";
	// Per-task chosen location is stored under this prefix + the normalized task name
	String LOCATION_KEY_PREFIX = "location_";

	@ConfigSection(
		name = "Teleports",
		description = "Which teleports to swap or highlight",
		position = 10
	)
	String teleportsSection = "teleports";

	@ConfigSection(
		name = "Highlights",
		description = "Outlines on items and teleport menus",
		position = 20
	)
	String highlightsSection = "highlights";

	@ConfigItem(
		keyName = "slayerMaster",
		name = "Slayer master",
		description = "Where teleports take you when you have no task",
		position = 0
	)
	default SlayerMaster slayerMaster()
	{
		return SlayerMaster.MORTIMER;
	}

	@ConfigItem(
		keyName = "wilderness",
		name = "Wilderness locations",
		description = "Use Wilderness locations and teleports. Tasks from Krystilia always use them, since they must be done in the Wilderness.",
		position = 1
	)
	default boolean wilderness()
	{
		return false;
	}

	@ConfigItem(
		keyName = "locationPicker",
		name = "Location picker",
		description = "Add a 'Slayer location' option to teleport items and fairy rings when your task can be done in more than one place",
		position = 2
	)
	default boolean locationPicker()
	{
		return true;
	}

	@ConfigItem(keyName = "useRing", name = "Slayer ring", description = "Swap the slayer ring's left-click", position = 0, section = teleportsSection)
	default boolean useRing()
	{
		return true;
	}

	@ConfigItem(keyName = "useFairy", name = "Fairy rings", description = "Swap fairy ring left-click to the code, and highlight it in the fairy ring log", position = 1, section = teleportsSection)
	default boolean useFairy()
	{
		return true;
	}

	@ConfigItem(keyName = "usePortal", name = "House portals", description = "Construction cape and max cape teleports to house portal towns", position = 2, section = teleportsSection)
	default boolean usePortal()
	{
		return true;
	}

	@ConfigItem(keyName = "useMaxcape", name = "Max cape", description = "Max cape guild and skilling teleports", position = 3, section = teleportsSection)
	default boolean useMaxcape()
	{
		return true;
	}

	@ConfigItem(keyName = "useKaramjaGloves", name = "Karamja gloves", description = "Karamja gloves 4 teleport to Duradel", position = 4, section = teleportsSection)
	default boolean useKaramjaGloves()
	{
		return true;
	}

	@ConfigItem(keyName = "useBurningAmulet", name = "Burning amulet", description = "Burning amulet Wilderness teleports (needs Wilderness locations on)", position = 5, section = teleportsSection)
	default boolean useBurningAmulet()
	{
		return true;
	}

	@ConfigItem(keyName = "useRingOfDueling", name = "Ring of dueling", description = "Ring of dueling teleport to Ferox Enclave (needs Wilderness locations on)", position = 6, section = teleportsSection)
	default boolean useRingOfDueling()
	{
		return true;
	}

	@ConfigItem(keyName = "useGlory", name = "Amulet of glory", description = "Amulet of glory teleports, including eternal and trimmed", position = 7, section = teleportsSection)
	default boolean useGlory()
	{
		return true;
	}

	@ConfigItem(keyName = "useBoat", name = "Teleport to Boat", description = "Highlight the boat to teleport to when your task is on an island your boat can be moored at", position = 8, section = teleportsSection)
	default boolean useBoat()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightItems", name = "Highlight items", description = "Outline the teleport items to use in your inventory and equipment", position = 0, section = highlightsSection)
	default boolean highlightItems()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightMenus", name = "Highlight teleport menus", description = "Outline the destination to pick in teleport menus, such as max cape, fairy ring log and boat teleports", position = 1, section = highlightsSection)
	default boolean highlightMenus()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightColor", name = "Highlight colour", description = "Colour of the outlines", position = 2, section = highlightsSection)
	default Color highlightColor()
	{
		return Color.GREEN;
	}

	default boolean isEnabled(RouteType type)
	{
		switch (type)
		{
			case RING:
				return useRing();
			case FAIRY:
				return useFairy();
			case PORTAL:
				return usePortal();
			case MAXCAPE:
				return useMaxcape();
			case KARAMJA_GLOVES:
				return useKaramjaGloves();
			case BURNING_AMULET:
				return useBurningAmulet();
			case RING_OF_DUELING:
				return useRingOfDueling();
			case AMULET_OF_GLORY:
				return useGlory();
			case BOAT:
				return useBoat();
			default:
				return false;
		}
	}
}
