package com.slayerswaps;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SlayerSwapsConfig.GROUP)
public interface SlayerSwapsConfig extends Config
{
	String GROUP = "slayerswaps";
	// Each task's location, chosen on the slayer helmet, is stored under this prefix + SlayerData.normalize(task)
	String LOCATION_KEY_PREFIX = "location_";
	// Each task's teleport (see Routes.key), chosen on the slayer helmet, or unset for the best one carried
	String ROUTE_KEY_PREFIX = "route_";

	@ConfigSection(
		name = "Teleports",
		description = "Which teleports to swap or highlight",
		position = 10
	)
	String teleportsSection = "teleports";

	@ConfigSection(
		name = "Wilderness",
		description = "Wilderness locations and the teleports that only go there",
		position = 15
	)
	String wildernessSection = "wilderness";

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
		return SlayerMaster.NOT_CHOSEN;
	}

	@ConfigItem(
		keyName = "wilderness",
		name = "Wilderness locations",
		description = "Use Wilderness locations and teleports. Tasks from Krystilia always use them, since they must be done in the Wilderness.",
		position = 0,
		section = wildernessSection
	)
	default boolean wilderness()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlyWithSlayerHelmet",
		name = "Only with slayer helmet",
		description = "Only swap and highlight teleports while you're wearing a slayer helmet or black mask (any variant)",
		position = 3
	)
	default boolean onlyWithSlayerHelmet()
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

	@ConfigItem(keyName = "useBurningAmulet", name = "Burning amulet", description = "Burning amulet Wilderness teleports. Only used while Wilderness locations is on, or for Krystilia tasks", position = 1, section = wildernessSection)
	default boolean useBurningAmulet()
	{
		return true;
	}

	@ConfigItem(keyName = "useRingOfDueling", name = "Ring of dueling", description = "Ring of dueling teleport to Ferox Enclave. Only used while Wilderness locations is on, or for Krystilia tasks", position = 2, section = wildernessSection)
	default boolean useRingOfDueling()
	{
		return true;
	}

	@ConfigItem(keyName = "useObelisk", name = "Wilderness obelisks", description = "Highlight the level to pick in the house or Wilderness obelisk's destination menu. Choosing a destination needs the hard Wilderness diary. Only used while Wilderness locations is on, or for Krystilia tasks", position = 3, section = wildernessSection)
	default boolean useObelisk()
	{
		return false;
	}

	@ConfigItem(keyName = "useGlory", name = "Amulet of glory", description = "Amulet of glory teleports, including eternal and trimmed", position = 7, section = teleportsSection)
	default boolean useGlory()
	{
		return true;
	}

	@ConfigItem(keyName = "useItems", name = "Other teleport items", description = "Hilts, talismans, necklaces, capes, scrolls and other teleport items", position = 9, section = teleportsSection)
	default boolean useItems()
	{
		return true;
	}

	@ConfigItem(keyName = "useSpells", name = "Spells and tablets", description = "Highlight teleport spells in the spellbook and their tablets", position = 10, section = teleportsSection)
	default boolean useSpells()
	{
		return true;
	}

	@ConfigItem(keyName = "portalNexus", name = "Portal nexus", description = "Your house has a portal nexus with the teleport spells you use: go home and use it for spell teleports. Its hotkeys show once you've opened its teleport menu", position = 11, section = teleportsSection)
	default boolean portalNexus()
	{
		return false;
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

	@ConfigItem(keyName = "highlightObjects", name = "Highlight objects", description = "Outline fairy rings, Wilderness obelisks and levers that lead to your task", position = 2, section = highlightsSection)
	default boolean highlightObjects()
	{
		return true;
	}

	@ConfigItem(keyName = "showDestination", name = "Show next teleport", description = "Show a box with where your task is and the teleport to use next", position = 3, section = highlightsSection)
	default boolean showDestination()
	{
		return true;
	}

	@ConfigItem(keyName = "taskArrow", name = "Arrow to task", description = "Once you're close, an arrow on the minimap points the way to your task's monsters (or your slayer master), until they're in sight", position = 4, section = highlightsSection)
	default boolean taskArrow()
	{
		return true;
	}

	@ConfigItem(keyName = "markOnMap", name = "Mark on world map", description = "Mark where your task's monsters (or your slayer master) are on the world map", position = 6, section = highlightsSection)
	default boolean markOnMap()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightTaskMonsters", name = "Highlight task monsters", description = "Outline the monsters that count for your task", position = 5, section = highlightsSection)
	default boolean highlightTaskMonsters()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightColor", name = "Highlight colour", description = "Colour of the outlines", position = 10, section = highlightsSection)
	default Color highlightColor()
	{
		return Color.GREEN;
	}
}
