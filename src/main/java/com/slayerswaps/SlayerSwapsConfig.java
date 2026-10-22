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
		description = "Which teleports to use",
		position = 10
	)
	String teleportsSection = "teleports";

	@ConfigItem(
		keyName = "slayerMaster",
		name = "Slayer master",
		description = "Where teleports take you when you have no task",
		position = 0,
		hidden = true
	)
	default SlayerMaster slayerMaster()
	{
		return SlayerMaster.NOT_CHOSEN;
	}

	@ConfigItem(
		keyName = "onlyWithSlayerHelmet",
		name = "Only with slayer helmet",
		description = "Only while wearing a slayer helmet or black mask",
		position = 3
	)
	default boolean onlyWithSlayerHelmet()
	{
		return true;
	}

	@ConfigItem(keyName = "useJewellery", name = "Jewellery", description = "Slayer ring, glory, games necklace and other teleport jewellery", position = 0, section = teleportsSection)
	default boolean useJewellery()
	{
		return true;
	}

	@ConfigItem(keyName = "useItems", name = "Capes and items", description = "Max cape, diary items, hilts, scrolls and other teleport items", position = 1, section = teleportsSection)
	default boolean useItems()
	{
		return true;
	}

	@ConfigItem(keyName = "useFairy", name = "Fairy rings", description = "Fairy ring codes", position = 2, section = teleportsSection)
	default boolean useFairy()
	{
		return true;
	}

	@ConfigItem(keyName = "useSpells", name = "Spells and tablets", description = "Teleport spells and their tablets", position = 3, section = teleportsSection)
	default boolean useSpells()
	{
		return true;
	}

	@ConfigItem(keyName = "useHouse", name = "House teleports", description = "Portals, portal nexus, jewellery box, fairy ring and obelisk in your house", position = 4, section = teleportsSection)
	default boolean useHouse()
	{
		return true;
	}

	@ConfigSection(
		name = "Highlights",
		description = "Outlines, the minimap arrow and the world map marker",
		position = 20
	)
	String highlightsSection = "highlights";

	@ConfigItem(keyName = "npcHighlights", name = "NPC highlights", description = "Outline your task's monsters or slayer master, with a minimap arrow and world map marker", position = 0, section = highlightsSection)
	default boolean npcHighlights()
	{
		return true;
	}

	@ConfigItem(keyName = "objectHighlights", name = "Object highlights", description = "Outline the teleport to use, in your inventory, menus and the world", position = 1, section = highlightsSection)
	default boolean objectHighlights()
	{
		return true;
	}

	@ConfigItem(keyName = "showDestination", name = "Objective overlay", description = "Your task, where it is and what to do next", position = 2, section = highlightsSection)
	default boolean showDestination()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightColor", name = "Highlight colour", description = "Colour of the outlines", position = 3, section = highlightsSection)
	default Color highlightColor()
	{
		return Color.GREEN;
	}
}
