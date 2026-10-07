package com.overnightflipper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

class OvernightFlipperPanel extends PluginPanel
{
	private static final Color GOOD = new Color(63, 174, 106);
	private static final Color BAD = new Color(230, 103, 103);
	private static final Color BUSY = new Color(57, 135, 229);
	private static final Color SELL = new Color(217, 89, 38);
	private static final Color MUTED = new Color(143, 142, 134);
	private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);
	private static final NumberFormat FULL = NumberFormat.getIntegerInstance(Locale.US);
	/**
	 * Width for wrapping text inside a card: panel width minus the panel's and the card's padding.
	 */
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 50;

	private final JLabel statusLabel = new JLabel();
	private final JPanel content = new JPanel();

	OvernightFlipperPanel(Runnable onRefresh, OvernightFlipperConfig config)
	{
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setLayout(new BorderLayout(0, 8));

		JPanel top = new JPanel(new BorderLayout(0, 6));
		JLabel title = new JLabel("Overnight Flipper");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(e -> onRefresh.run());
		JButton site = new JButton("Open site");
		site.addActionListener(e -> LinkBrowser.browse(config.serverUrl().trim()));
		buttons.add(refresh);
		buttons.add(site);
		top.add(buttons, BorderLayout.CENTER);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		top.add(statusLabel, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		add(content, BorderLayout.CENTER);
	}

	void status(String text, boolean error)
	{
		statusLabel.setText(wrap(text, PluginPanel.PANEL_WIDTH - 30));
		statusLabel.setForeground(error ? BAD : ColorScheme.LIGHT_GRAY_COLOR);
		if (!error)
		{
			content.removeAll();
			content.revalidate();
			content.repaint();
		}
	}

	void show(Summary s)
	{
		statusLabel.setText(wrap("Logged in as " + esc(s.email), PluginPanel.PANEL_WIDTH - 30));
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.removeAll();

		Summary.Run run = s.run;
		JPanel head = card();
		if (run != null)
		{
			String when = RUN_TIME.format(Instant.ofEpochMilli((long) (run.createdAt * 1000)).atZone(ZoneId.systemDefault()));
			head.add(title("Run #" + run.id + " · " + when));
			head.add(row("Budget", gp(run.budget, false) + " · " + run.risk, Color.WHITE));
			head.add(row("Expected", gp(run.expectedProfit, true), MUTED));
			head.add(row("Realized", gp(run.profit, true), profitColor(run.profit)));
		}
		if (s.totals != null)
		{
			head.add(row("All time", gp(s.totals.profit, true) + " · " + s.totals.nights
				+ (s.totals.nights == 1 ? " night" : " nights"), profitColor(s.totals.profit)));
		}
		content.add(head);

		if (run == null)
		{
			content.add(note("No analysis runs yet. Run one on the site."));
		}
		else
		{
			content.add(heading("Plan"));
			addItems(run.items, "Nothing to show for this run.");
		}

		if (s.holding != null && !s.holding.isEmpty())
		{
			content.add(heading("Still holding from earlier runs"));
			addItems(s.holding, "");
		}

		content.revalidate();
		content.repaint();
	}

	private void addItems(List<Summary.Item> items, String empty)
	{
		if (items == null || items.isEmpty())
		{
			if (!empty.isEmpty())
			{
				content.add(note(empty));
			}
			return;
		}
		for (Summary.Item i : items)
		{
			content.add(itemCard(i));
			content.add(Box.createVerticalStrut(6));
		}
	}

	private Component itemCard(Summary.Item i)
	{
		JPanel c = card();
		c.add(title(i.name));
		JLabel state = small(offerText(i));
		state.setForeground(offerColor(i));
		state.setBorder(new EmptyBorder(1, 0, 3, 0));
		c.add(state);
		if (i.filled > 0 || i.offer != null)
		{
			c.add(new FillBar(i.planQty > 0 ? Math.min(1.0, (double) i.filled / i.planQty) : 0));
		}

		c.add(row("Buy at", price(i.buyPrice), Color.WHITE));
		c.add(row("Quantity", FULL.format(i.planQty), Color.WHITE));
		c.add(row("Sell at", price(i.sellPrice), Color.WHITE));
		if (i.filled > 0)
		{
			c.add(row("Bought", FULL.format(i.filled) + " (" + Math.round(100.0 * i.filled / Math.max(1, i.planQty)) + "%)", Color.WHITE));
			c.add(row("Paid", price(i.avgBuy != null ? i.avgBuy : i.buyPrice) + " ea", Color.WHITE));
		}
		if (i.sold > 0)
		{
			c.add(row("Sold", FULL.format(i.sold), Color.WHITE));
			c.add(row("Sold for", price(i.avgSell != null ? i.avgSell : i.sellPrice) + " ea", Color.WHITE));
		}
		if (i.held > 0)
		{
			c.add(row("Held", FULL.format(i.held), Color.WHITE));
		}
		if (i.filled > 0)
		{
			c.add(row("Profit", gp(i.profit, true), profitColor(i.profit)));
		}
		else
		{
			c.add(row("Expected", gp(i.expectedProfit, true), MUTED));
		}
		return c;
	}

	private static String offerText(Summary.Item i)
	{
		if ("done".equals(i.status))
		{
			return "Done";
		}
		Summary.Offer o = i.offer;
		if (o == null)
		{
			return i.filled > 0 ? "Logged on the site" : "Not placed yet";
		}
		switch (o.state)
		{
			case "BUYING":
				return "Buying";
			case "BOUGHT":
				return "Buy complete";
			case "CANCELLED_BUY":
				return "Buy cancelled";
			case "SELLING":
				return "Selling " + FULL.format(o.tradedQty) + "/" + FULL.format(o.totalQty) + " @ " + price(o.price);
			case "SOLD":
				return "Sold";
			case "CANCELLED_SELL":
				return "Sell cancelled";
			default:
				return o.state;
		}
	}

	private static Color offerColor(Summary.Item i)
	{
		if ("done".equals(i.status) || i.offer == null)
		{
			return MUTED;
		}
		switch (i.offer.state)
		{
			case "BUYING":
				return BUSY;
			case "SELLING":
				return SELL;
			case "BOUGHT":
			case "SOLD":
				return GOOD;
			default:
				return MUTED;
		}
	}

	private static JPanel card()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(6, 8, 7, 8));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static Component title(String text)
	{
		JLabel l = new JLabel(wrap(esc(text), TEXT_WIDTH));
		l.setForeground(Color.WHITE);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	/**
	 * One "label ... value" line; the value is right-aligned so long numbers never push the label off.
	 */
	private static Component row(String label, String value, Color valueColor)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		JLabel l = small(label);
		JLabel v = small(value);
		v.setForeground(valueColor);
		v.setHorizontalAlignment(JLabel.RIGHT);
		p.add(l, BorderLayout.WEST);
		p.add(v, BorderLayout.CENTER);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	private static Component heading(String text)
	{
		JLabel l = new JLabel(wrap(text, TEXT_WIDTH));
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(new EmptyBorder(10, 0, 4, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static Component note(String text)
	{
		JLabel l = new JLabel(wrap(text, TEXT_WIDTH));
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(new EmptyBorder(6, 0, 0, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static Color profitColor(long gp)
	{
		return gp > 0 ? GOOD : gp < 0 ? BAD : ColorScheme.LIGHT_GRAY_COLOR;
	}

	/**
	 * Exact price, as you'd type it into the GE, shortened only when it can't fit.
	 */
	private static String price(long p)
	{
		return p < 100_000_000L ? FULL.format(p) : gp(p, false);
	}

	/**
	 * Short gp amount like the site shows it: 950, 12.3k, 1.25m.
	 */
	static String gp(long n, boolean sign)
	{
		long a = Math.abs(n);
		String s = n < 0 ? "-" : sign && n > 0 ? "+" : "";
		if (a >= 1_000_000_000L)
		{
			return s + trim(String.format(Locale.US, "%.2f", a / 1e9)) + "b";
		}
		if (a >= 1_000_000L)
		{
			return s + trim(String.format(Locale.US, "%.2f", a / 1e6)) + "m";
		}
		if (a >= 10_000L)
		{
			return s + trim(String.format(Locale.US, "%.1f", a / 1e3)) + "k";
		}
		return s + FULL.format(a);
	}

	private static String trim(String num)
	{
		return num.contains(".") ? num.replaceAll("0+$", "").replaceAll("\\.$", "") : num;
	}

	private static String wrap(String html, int width)
	{
		return "<html><body style='width:" + width + "px'>" + html + "</body></html>";
	}

	private static String esc(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Thin bar showing how much of the planned quantity has filled.
	 */
	private static class FillBar extends JComponent
	{
		private final double fraction;

		FillBar(double fraction)
		{
			this.fraction = fraction;
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setPreferredSize(new Dimension(10, 8));  // 4px bar + 4px gap below
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), 4);
			g.setColor(GOOD);
			g.fillRect(0, 0, (int) Math.round(getWidth() * fraction), 4);
		}
	}
}
