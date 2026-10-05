package com.slayerteleportswap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Outlines the favourite matching the task's fairy ring code in the fairy ring log, for rings like the
 * house's Spiritual Fairy Tree that don't offer favourites in their right-click menu.
 */
class FairyRingFavouriteOverlay extends Overlay
{
	static final int[] FAVE_ROWS = {
		InterfaceID.FairyringsLog.FAVE_1, InterfaceID.FairyringsLog.FAVE_2, InterfaceID.FairyringsLog.FAVE_3,
		InterfaceID.FairyringsLog.FAVE_4, InterfaceID.FairyringsLog.FAVE_5, InterfaceID.FairyringsLog.FAVE_6,
		InterfaceID.FairyringsLog.FAVE_7, InterfaceID.FairyringsLog.FAVE_8, InterfaceID.FairyringsLog.FAVE_9,
		InterfaceID.FairyringsLog.FAVE_10};
	static final int[] FAVE_CODES = {
		InterfaceID.FairyringsLog.FAVE_CODE_1, InterfaceID.FairyringsLog.FAVE_CODE_2, InterfaceID.FairyringsLog.FAVE_CODE_3,
		InterfaceID.FairyringsLog.FAVE_CODE_4, InterfaceID.FairyringsLog.FAVE_CODE_5, InterfaceID.FairyringsLog.FAVE_CODE_6,
		InterfaceID.FairyringsLog.FAVE_CODE_7, InterfaceID.FairyringsLog.FAVE_CODE_8, InterfaceID.FairyringsLog.FAVE_CODE_9,
		InterfaceID.FairyringsLog.FAVE_CODE_10};

	private static final Color COLOR = Color.GREEN;

	private final Client client;
	private final SlayerTeleportSwapPlugin plugin;

	@Inject
	FairyRingFavouriteOverlay(Client client, SlayerTeleportSwapPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String code = plugin.fairyCodeToHighlight();
		if (code == null)
		{
			return null;
		}

		for (int i = 0; i < FAVE_CODES.length; i++)
		{
			Widget codeWidget = client.getWidget(FAVE_CODES[i]);
			if (codeWidget == null || codeWidget.isHidden() || !code.equalsIgnoreCase(normalizeCode(codeWidget.getText())))
			{
				continue;
			}

			Widget row = client.getWidget(FAVE_ROWS[i]);
			Rectangle bounds = (row != null && !row.isHidden() ? row : codeWidget).getBounds();
			graphics.setColor(COLOR);
			graphics.setStroke(new BasicStroke(2));
			graphics.draw(bounds);
			return null;
		}
		return null;
	}

	static String normalizeCode(String text)
	{
		return text == null ? "" : Text.removeTags(text).replace(" ", "");
	}
}
