package com.slayerswaps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines the monsters that count for the current task.
 */
class TaskMonsterOverlay extends Overlay
{
	private final SlayerSwapsPlugin plugin;
	private final SlayerSwapsConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	TaskMonsterOverlay(SlayerSwapsPlugin plugin, SlayerSwapsConfig config, ModelOutlineRenderer outlineRenderer)
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
		if (config.highlightObjects())
		{
			// The quetzal to ride next, outlined like any other step
			for (NPC quetzal : plugin.quetzalsToHighlight())
			{
				outlineRenderer.drawOutline(quetzal, 2, config.highlightColor(), 4);
			}
		}
		if (!config.highlightTaskMonsters())
		{
			return null;
		}
		// 40% fainter than the highlight colour, so the monsters stay easy to see
		Color c = config.highlightColor();
		Color colour = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (c.getAlpha() * 0.6));
		for (NPC npc : plugin.taskNpcs())
		{
			if (!npc.isDead())
			{
				outlineRenderer.drawOutline(npc, 2, colour, 4);
			}
		}
		return null;
	}
}
