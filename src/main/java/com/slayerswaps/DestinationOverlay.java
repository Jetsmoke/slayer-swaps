package com.slayerswaps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * A small box saying where teleports go right now and which teleport to use next.
 */
class DestinationOverlay extends OverlayPanel
{
	private final SlayerSwapsPlugin plugin;
	private final SlayerSwapsConfig config;

	@Inject
	DestinationOverlay(SlayerSwapsPlugin plugin, SlayerSwapsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showDestination())
		{
			return null;
		}
		panelComponent.setPreferredSize(new Dimension(ComponentConstants.STANDARD_WIDTH, 0));
		String task = plugin.taskName();
		String prompt = plugin.choicePrompt();
		if (prompt != null)
		{
			// With no task, the master being set up (the teleport back to them), or "No task" before one is chosen
			String title = task != null ? task : config.slayerMaster() == SlayerMaster.NOT_CHOSEN ? "No task"
				: "Back to " + config.slayerMaster();
			line(graphics, title, config.highlightColor());
			line(graphics, "Right-click your slayer helmet", Color.WHITE);
			line(graphics, prompt, Color.WHITE);
			return super.render(graphics);
		}

		SlayerData.Route next = plugin.nextRoute();
		String location = plugin.currentLocation();
		if (location != null && plugin.nearObjective() && !plugin.atTask())
		{
			// Close enough to walk: no more teleports
			line(graphics, task != null ? task : "Back to " + config.slayerMaster(), config.highlightColor());
			line(graphics, Routes.place(location), Color.WHITE);
			String note = plugin.walkNote();
			if (note != null)
			{
				line(graphics, Character.toUpperCase(note.charAt(0)) + note.substring(1), config.highlightColor());
			}
			return super.render(graphics);
		}
		if (next == null || location == null)
		{
			return null;
		}

		line(graphics, task != null ? task : "Back to " + config.slayerMaster(), config.highlightColor());
		// Centred lines under the centred task name, with any step after arriving (like pulling a lever) underneath
		line(graphics, Routes.place(location), Color.WHITE);
		line(graphics, describe(next), config.highlightColor());
		// Only the step to take now: after going home, the box shows the step in the house
		if (next.getNote() != null && next.routeType() != RouteType.PORTAL)
		{
			line(graphics, "then " + next.getNote(), Color.WHITE);
		}
		return super.render(graphics);
	}

	private String describe(SlayerData.Route next)
	{
		// In the house, a portal chamber portal, portal nexus or jewellery box does the job
		String portal = plugin.housePortalOption(next);
		if (portal != null)
		{
			return "Portal : " + portal;
		}
		String nexus = plugin.nexusOption(next);
		if (nexus != null)
		{
			return "Nexus : " + nexus;
		}
		String jewellery = plugin.jewelleryBoxOption(next);
		return jewellery != null ? "Jewellery box : " + jewellery : Routes.describeTeleport(plugin.data(), next);
	}

	/**
	 * Adds a centred line, widening the box to fit it so longer teleports don't run outside it.
	 */
	private void line(Graphics2D graphics, String text, Color color)
	{
		panelComponent.getChildren().add(TitleComponent.builder().text(text).color(color).build());
		int width = graphics.getFontMetrics().stringWidth(text) + 10;
		if (width > panelComponent.getPreferredSize().width)
		{
			panelComponent.setPreferredSize(new Dimension(width, 0));
		}
	}
}
