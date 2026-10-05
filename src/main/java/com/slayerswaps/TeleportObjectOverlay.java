package com.slayerswaps;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines the objects in the world that teleport to the current destination, such as fairy rings,
 * Wilderness obelisks and the Wilderness levers.
 */
class TeleportObjectOverlay extends Overlay
{
	private final SlayerSwapsPlugin plugin;
	private final SlayerSwapsConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	TeleportObjectOverlay(SlayerSwapsPlugin plugin, SlayerSwapsConfig config, ModelOutlineRenderer outlineRenderer)
	{
		this.plugin = plugin;
		this.config = config;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightObjects())
		{
			return null;
		}
		Set<String> names = plugin.objectNamesToHighlight();
		if (names.isEmpty())
		{
			return null;
		}
		SlayerData.Route next = plugin.nextRoute();
		for (TileObject object : plugin.teleportObjects())
		{
			String name = plugin.objectName(object);
			if (names.contains(name))
			{
				outlineRenderer.drawOutline(object, 2, config.highlightColor(), 4);
				// What to pick there, with the teleport menu hotkey when it's known
				String label = plugin.objectLabel(name, next);
				Point text = label == null ? null : object.getCanvasTextLocation(graphics, label, 250);
				if (text != null)
				{
					OverlayUtil.renderTextLocation(graphics, text, label, config.highlightColor());
				}
			}
		}
		return null;
	}
}
