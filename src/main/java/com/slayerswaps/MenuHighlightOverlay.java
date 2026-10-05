package com.slayerswaps;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines the destination to pick in open teleport menus.
 */
class MenuHighlightOverlay extends Overlay
{
	private final MenuHighlighter highlighter;
	private final SlayerSwapsConfig config;

	@Inject
	MenuHighlightOverlay(MenuHighlighter highlighter, SlayerSwapsConfig config)
	{
		this.highlighter = highlighter;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightMenus())
		{
			return null;
		}

		graphics.setColor(config.highlightColor());
		graphics.setStroke(new BasicStroke(2));
		for (Widget w : highlighter.getHighlighted())
		{
			if (!w.isHidden())
			{
				Rectangle bounds = w.getBounds();
				graphics.drawRect(bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2);
			}
		}
		return null;
	}
}
