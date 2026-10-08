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
			// Only the part its list shows, so an option scrolled half out of view isn't outlined over other panels
			Rectangle visible = w.isHidden() ? null : MenuHighlighter.visibleBounds(w, 1);
			if (visible != null)
			{
				graphics.drawRect(visible.x, visible.y, visible.width - 1, visible.height - 1);
			}
		}
		return null;
	}
}
