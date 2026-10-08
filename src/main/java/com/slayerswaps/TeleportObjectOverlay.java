package com.slayerswaps;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
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
	// A fairy ring's mushrooms circle its tile about a tile and a half out
	private static final double FAIRY_RING_RADIUS = 1.6 * Perspective.LOCAL_TILE_SIZE;

	private final Client client;
	private final SlayerSwapsPlugin plugin;
	private final SlayerSwapsConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	TeleportObjectOverlay(Client client, SlayerSwapsPlugin plugin, SlayerSwapsConfig config, ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
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
				if (name.equals("fairy ring"))
				{
					// Its model is mostly the butterflies above it, so circle the ring on the ground instead
					drawGroundRing(graphics, object);
				}
				else
				{
					outlineRenderer.drawOutline(object, 2, config.highlightColor(), 4);
				}
				// Where it goes, then how: its left-click or the teleport menu hotkey
				List<String> lines = plugin.objectLabel(object, name, next);
				if (lines == null)
				{
					continue;
				}
				int lineHeight = graphics.getFontMetrics().getHeight();
				for (int i = 0; i < lines.size(); i++)
				{
					Point text = object.getCanvasTextLocation(graphics, lines.get(i), 250);
					if (text != null)
					{
						Point line = new Point(text.getX(), text.getY() + (i - lines.size() + 1) * lineHeight);
						OverlayUtil.renderTextLocation(graphics, line, lines.get(i), config.highlightColor());
					}
				}
			}
		}
		return null;
	}

	private void drawGroundRing(Graphics2D graphics, TileObject object)
	{
		LocalPoint centre = object.getLocalLocation();
		Polygon ring = new Polygon();
		for (int i = 0; i < 32; i++)
		{
			double angle = 2 * Math.PI * i / 32;
			LocalPoint edge = new LocalPoint(centre.getX() + (int) (Math.cos(angle) * FAIRY_RING_RADIUS),
				centre.getY() + (int) (Math.sin(angle) * FAIRY_RING_RADIUS), centre.getWorldView());
			Point p = Perspective.localToCanvas(client, edge, object.getPlane());
			if (p == null)
			{
				return;
			}
			ring.addPoint(p.getX(), p.getY());
		}
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(config.highlightColor());
		graphics.setStroke(new BasicStroke(2));
		graphics.drawPolygon(ring);
	}
}
