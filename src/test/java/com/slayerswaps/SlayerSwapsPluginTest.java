package com.slayerswaps;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SlayerSwapsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SlayerSwapsPlugin.class);
		RuneLite.main(args);
	}
}
