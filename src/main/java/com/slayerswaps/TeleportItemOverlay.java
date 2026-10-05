package com.slayerswaps;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the teleport items that reach the current destination, in the inventory and equipment.
 */
class TeleportItemOverlay extends WidgetItemOverlay
{
	private final SlayerSwapsPlugin plugin;
	private final SlayerSwapsConfig config;
	private final ItemManager itemManager;

	@Inject
	TeleportItemOverlay(SlayerSwapsPlugin plugin, SlayerSwapsConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightItems() || !plugin.shouldHighlight(itemId))
		{
			return;
		}

		// Trace the item's own shape, like the core Inventory Tags plugin, rather than boxing the slot
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.highlightColor());
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
	}
}
