package com.slayerteleportswap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SlayerTeleportSwapPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SlayerTeleportSwapPlugin.class);
		RuneLite.main(args);
	}
}
