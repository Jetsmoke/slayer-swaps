package com.slayerswaps;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * An arrow on the minimap pointing toward the task's monsters (or the way in to them, or the slayer master) once
 * the player is close enough to walk there.
 */
class MinimapArrowOverlay extends Overlay
{
	// Pixels from the player's dot on the minimap to the arrow's tail and tip, inside the minimap's edge
	private static final int TAIL = 50;
	private static final int TIP = 62;
	private static final int HEAD = 7;

	private final Client client;
	private final SlayerSwapsPlugin plugin;
	private final SlayerSwapsConfig config;

	@Inject
	MinimapArrowOverlay(Client client, SlayerSwapsPlugin plugin, SlayerSwapsConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldPoint target = plugin.arrowTarget();
		WorldPoint at = plugin.playerLocation();
		Player player = client.getLocalPlayer();
		if (target == null || at == null || player == null)
		{
			return null;
		}
		// A point a few tiles toward the target, so the minimap's rotation and zoom give the direction on screen
		LocalPoint here = player.getLocalLocation();
		double dx = target.getX() - at.getX();
		double dy = target.getY() - at.getY();
		double length = Math.hypot(dx, dy);
		if (length == 0)
		{
			return null;
		}
		int step = 4 * Perspective.LOCAL_TILE_SIZE;
		LocalPoint toward = new LocalPoint(here.getX() + (int) (dx / length * step), here.getY() + (int) (dy / length * step),
			here.getWorldView());
		Point from = Perspective.localToMinimap(client, here);
		Point to = Perspective.localToMinimap(client, toward);
		if (from == null || to == null)
		{
			return null;
		}
		double sx = to.getX() - from.getX();
		double sy = to.getY() - from.getY();
		double screenLength = Math.hypot(sx, sy);
		if (screenLength == 0)
		{
			return null;
		}
		sx /= screenLength;
		sy /= screenLength;

		int tailX = (int) (from.getX() + sx * TAIL);
		int tailY = (int) (from.getY() + sy * TAIL);
		int tipX = (int) (from.getX() + sx * TIP);
		int tipY = (int) (from.getY() + sy * TIP);
		Polygon head = new Polygon();
		head.addPoint(tipX, tipY);
		head.addPoint((int) (tipX - sx * HEAD - sy * HEAD * 0.6), (int) (tipY - sy * HEAD + sx * HEAD * 0.6));
		head.addPoint((int) (tipX - sx * HEAD + sy * HEAD * 0.6), (int) (tipY - sy * HEAD - sx * HEAD * 0.6));
		int shaftEndX = (int) (tipX - sx * HEAD * 0.8);
		int shaftEndY = (int) (tipY - sy * HEAD * 0.8);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// A dark outline first, so the arrow shows on any minimap colours
		graphics.setColor(Color.BLACK);
		graphics.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawLine(tailX, tailY, shaftEndX, shaftEndY);
		graphics.drawPolygon(head);
		graphics.setColor(config.highlightColor());
		graphics.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawLine(tailX, tailY, shaftEndX, shaftEndY);
		graphics.fillPolygon(head);
		return null;
	}
}
