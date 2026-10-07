package com.overnightflipper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OvernightFlipperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OvernightFlipperPlugin.class);
		RuneLite.main(args);
	}
}
