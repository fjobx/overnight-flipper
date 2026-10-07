package com.overnightflipper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@PluginDescriptor(
	name = "Overnight Flipper",
	description = "Syncs your Grand Exchange offers to your Overnight Flipper site and shows your profit",
	tags = {"ge", "grand exchange", "flipping", "merching", "profit"}
)
public class OvernightFlipperPlugin extends Plugin
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	@Inject
	private Client client;

	@Inject
	private OvernightFlipperConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	private OvernightFlipperPanel panel;
	private NavigationButton navButton;
	/**
	 * Doesn't follow redirects: a redirect means a login page is in the way, and following it would
	 * turn the POST into a GET of that page that looks like a success.
	 */
	private OkHttpClient http;

	/**
	 * Latest unsent state per account and slot; a newer report for a slot replaces an unsent older one.
	 */
	private final Map<String, Map<Integer, OfferUpdate>> pending = new ConcurrentHashMap<>();
	private final AtomicBoolean sending = new AtomicBoolean();
	/**
	 * No sends before this time (epoch ms), so a server that's down or a key it rejects isn't retried every few seconds.
	 */
	private volatile long retryAt;

	@Override
	protected void startUp()
	{
		http = httpClient.newBuilder().followRedirects(false).build();
		panel = new OvernightFlipperPanel(this::refreshSummary, config);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Overnight Flipper")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		refreshSummary();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		pending.clear();
		panel = null;
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		if (!syncReady())
		{
			return;
		}
		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			return;
		}
		GrandExchangeOffer offer = event.getOffer();
		OfferUpdate update = new OfferUpdate(event.getSlot(), offer.getItemId(), offer.getState().name(),
			offer.getPrice(), offer.getTotalQuantity(), offer.getQuantitySold(), offer.getSpent());
		pending.computeIfAbsent(accountId(accountHash), k -> new ConcurrentHashMap<>()).put(event.getSlot(), update);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (OvernightFlipperConfig.GROUP.equals(event.getGroup()))
		{
			retryAt = 0;
			refreshSummary();
		}
	}

	/**
	 * Sends what changed since the last send. Runs every few seconds so a burst of updates (e.g. login,
	 * where every slot reports at once) goes out as one request.
	 */
	@Schedule(period = 3, unit = ChronoUnit.SECONDS)
	public void flush()
	{
		if (!syncReady() || pending.isEmpty() || System.currentTimeMillis() < retryAt
			|| !sending.compareAndSet(false, true))
		{
			return;
		}
		String account = pending.keySet().iterator().next();
		Map<Integer, OfferUpdate> slots = pending.get(account);
		List<OfferUpdate> batch = new ArrayList<>(slots.values());
		if (batch.isEmpty())
		{
			pending.remove(account);
			sending.set(false);
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("account", account);
		body.add("offers", gson.toJsonTree(batch));
		Request request = request("api/plugin/offers");
		if (request == null)
		{
			sending.set(false);
			return;
		}
		request = request.newBuilder().post(RequestBody.create(JSON, gson.toJson(body))).build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Offer sync failed", e);
				retryAt = System.currentTimeMillis() + 30_000;
				status("Can't reach the server, will retry", true);
				sending.set(false);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						// a rejected key or blocked path won't fix itself; wait longer, or until the settings change
						retryAt = System.currentTimeMillis() + (response.code() < 500 ? 300_000 : 30_000);
						status(errorText(response), true);
						return;
					}
					retryAt = 0;
					// drop what was sent, unless the slot changed again meanwhile
					batch.forEach(u -> slots.remove(u.slot, u));
					pending.computeIfPresent(account, (k, v) -> v.isEmpty() ? null : v);
					refreshSummary();
				}
				finally
				{
					sending.set(false);
				}
			}
		});
	}

	void refreshSummary()
	{
		if (!config.syncEnabled())
		{
			status("Sync is off. Turn it on in the plugin settings.", false);
			return;
		}
		if (config.apiKey().trim().isEmpty())
		{
			status("Add your plugin key in the plugin settings (create one on the site under Settings).", false);
			return;
		}
		Request request = request("api/plugin/summary");
		if (request == null)
		{
			return;
		}
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Summary fetch failed", e);
				status("Can't reach the server", true);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					ResponseBody body = response.body();
					if (!response.isSuccessful() || body == null)
					{
						status(errorText(response), true);
						return;
					}
					Summary summary = gson.fromJson(body.charStream(), Summary.class);
					SwingUtilities.invokeLater(() ->
					{
						if (panel != null)
						{
							panel.show(summary);
						}
					});
				}
				catch (RuntimeException e)
				{
					log.debug("Bad summary response", e);
					status("Unexpected answer from the server", true);
				}
			}
		});
	}

	private boolean syncReady()
	{
		return config.syncEnabled() && !config.apiKey().trim().isEmpty();
	}

	private Request request(String path)
	{
		HttpUrl base = HttpUrl.parse(config.serverUrl().trim());
		HttpUrl url = base == null ? null : base.resolve(base.encodedPath().endsWith("/") ? path : base.encodedPath() + "/" + path);
		if (url == null)
		{
			status("The server URL in the plugin settings isn't valid", true);
			return null;
		}
		return new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + config.apiKey().trim())
			.build();
	}

	private static String errorText(Response response)
	{
		switch (response.code())
		{
			case 401:
				return "The server didn't accept your plugin key. Create a new one on the site.";
			case 302:
			case 403:
				return "The server's login page blocked the plugin (ask the site admin to let /api/plugin through).";
			default:
				return "Server error " + response.code();
		}
	}

	private void status(String text, boolean error)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.status(text, error);
			}
		});
	}

	/**
	 * Tells a user's accounts apart without sending the account hash itself.
	 */
	private static String accountId(long accountHash)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(("overnight-flipper:" + accountHash).getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 8; i++)
			{
				sb.append(String.format("%02x", digest[i]));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	@Provides
	OvernightFlipperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OvernightFlipperConfig.class);
	}
}
