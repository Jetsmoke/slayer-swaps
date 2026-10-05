package com.slayerswaps;

/**
 * Short descriptions of routes, for the next teleport box, slayer helmet menus and chat messages.
 */
final class Routes
{
	private Routes()
	{
	}

	/**
	 * @return a short "Type : Destination" description, with any step after arriving
	 */
	static String describe(SlayerData data, SlayerData.Route route)
	{
		String text = describeTeleport(data, route);
		return route.getNote() == null ? text : text + ", then " + route.getNote();
	}

	/**
	 * @return a short "Type : Destination" description, such as "House : Brimhaven" or "Fairy ring : BKP"
	 */
	static String describeTeleport(SlayerData data, SlayerData.Route route)
	{
		RouteType type = route.routeType();
		String value = route.getValue();
		if (type == null)
		{
			return route.getType() + " : " + value;
		}
		switch (type)
		{
			case SPELL:
				return "Spell : " + value.replaceFirst(" Teleport$", "");
			case ITEM:
				SlayerData.ItemData item = data.getItems().get(route.getItem());
				String label = item != null ? item.getLabel() : route.getItem();
				return value == null ? label : label + " : " + value;
			case PORTAL:
				return "House : " + value;
			case RING:
				return "Slayer ring : " + value;
			case MAXCAPE:
				return "Max cape : " + value;
			case KARAMJA_GLOVES:
				return "Karamja gloves : " + value;
			case AMULET_OF_GLORY:
				return "Glory : " + value;
			case RING_OF_DUELING:
				return "Dueling ring : " + value;
			case BURNING_AMULET:
				return "Burning amulet : " + value;
			case FAIRY:
				return "Fairy ring : " + value;
			case BOAT:
				return "Boat : " + value;
			case OBELISK:
				return "Obelisk : " + value.replaceFirst(" Wilderness$", "");
			default:
				return type.getDisplayName() + " : " + value;
		}
	}

	/**
	 * Stable key for remembering a chosen route in config.
	 */
	static String key(SlayerData.Route route)
	{
		return route.getType() + "|" + (route.getItem() == null ? "" : route.getItem()) + "|"
			+ (route.getValue() == null ? "" : route.getValue());
	}
}
