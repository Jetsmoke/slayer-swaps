package com.slayerteleportswap;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the teleport items that reach the current destination, in the inventory and equipment.
 */
class TeleportItemOverlay extends WidgetItemOverlay
{
	private final SlayerTeleportSwapPlugin plugin;
	private final SlayerTeleportSwapConfig config;
	private final TeleportItems teleportItems;

	@Inject
	TeleportItemOverlay(SlayerTeleportSwapPlugin plugin, SlayerTeleportSwapConfig config, TeleportItems teleportItems)
	{
		this.plugin = plugin;
		this.config = config;
		this.teleportItems = teleportItems;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightItems() || !plugin.isUsefulItem(teleportItems.routeTypes(itemId)))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(config.highlightColor());
		graphics.setStroke(new BasicStroke(1));
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}
}
