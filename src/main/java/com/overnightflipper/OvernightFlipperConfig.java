package com.overnightflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(OvernightFlipperConfig.GROUP)
public interface OvernightFlipperConfig extends Config
{
	String GROUP = "overnight-flipper";

	@ConfigItem(
		keyName = "syncEnabled",
		name = "Sync with server",
		description = "Send your Grand Exchange offers to the Overnight Flipper server and show your results in the side panel",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean syncEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "Address of the Overnight Flipper site",
		position = 1
	)
	default String serverUrl()
	{
		return "https://flip.reinvart.ee";
	}

	@ConfigItem(
		keyName = "apiKey",
		name = "Plugin key",
		description = "Your personal key: on the site, open Settings and create a RuneLite plugin key",
		secret = true,
		position = 2
	)
	default String apiKey()
	{
		return "";
	}
}
