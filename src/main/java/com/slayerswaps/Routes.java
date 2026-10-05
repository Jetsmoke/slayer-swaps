package com.slayerswaps;

/**
 * Human-readable descriptions of routes, for the side panel and chat messages.
 */
final class Routes
{
	private Routes()
	{
	}

	static String describe(SlayerData data, SlayerData.Route route)
	{
		RouteType type = route.routeType();
		String value = route.getValue();
		if (type == null)
		{
			return route.getType() + ": " + value;
		}
		switch (type)
		{
			case FAIRY:
				return "Fairy ring " + value;
			case SPELL:
				return value + " (spell/tablet)";
			case ITEM:
				SlayerData.ItemData item = data.getItems().get(route.getItem());
				String label = item != null ? item.getLabel() : route.getItem();
				return value == null ? label : label + ": " + value;
			case NETWORK:
				SlayerData.NetworkData network = data.getNetworks().get(route.getItem());
				return (network != null ? network.getLabel() : route.getItem()) + ": " + value;
			case PORTAL:
				return "House portal: " + value;
			case BOAT:
				return "Boat moored at " + value;
			default:
				return type.getDisplayName() + ": " + value;
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
