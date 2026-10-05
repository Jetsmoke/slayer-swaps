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
		name = "Testing",
		description = "For trying the plugin out",
		position = 30,
		closedByDefault = true
	)
	String testingSection = "testing";

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
		description = "Only swap and highlight teleports while you're wearing a slayer helmet (any variant)",
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

	@ConfigItem(keyName = "useBoat", name = "Teleport to Boat", description = "Highlight the boat to teleport to when your task is on an island your boat can be moored at", position = 8, section = teleportsSection)
	default boolean useBoat()
	{
		return true;
	}

	@ConfigItem(keyName = "simulatedTask", name = "Test mode", description = "Pretend to have this task, to see its swaps and highlights. Your real task is used when this, Konar task and Random Konar task are all off.", position = 0, section = testingSection)
	default SimulatedTask simulatedTask()
	{
		return SimulatedTask.OFF;
	}

	@ConfigItem(keyName = "konarTestTask", name = "Konar task", description = "Pretend to have this Konar task in this one of her areas. Used instead of Test mode while it isn\'t Off.", position = 1, section = testingSection)
	default KonarTestTask konarTestTask()
	{
		return KonarTestTask.OFF;
	}

	@ConfigItem(keyName = "randomKonarTask", name = "Random Konar task", description = "While ticked, pretend to have a random Konar task in a random one of her areas (said in chat). Untick and tick again for another; untick to go back to the settings above.", position = 2, section = testingSection)
	default boolean randomKonarTask()
	{
		return false;
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

	@ConfigItem(keyName = "highlightColor", name = "Highlight colour", description = "Colour of the outlines", position = 4, section = highlightsSection)
	default Color highlightColor()
	{
		return Color.GREEN;
	}
}
