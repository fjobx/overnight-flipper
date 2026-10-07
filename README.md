# Overnight Flipper

Companion plugin for the Overnight Flipper site, a self-hosted tool that suggests lowball Grand Exchange
buy offers to place before bed.

The plugin **only reads** your Grand Exchange offers. It never places, changes or cancels offers.

## What it does

- **Syncs your GE offers** to the site. Buys of items in your latest plan fill in the morning log (filled
  quantity, average price paid), and later sells of those items are recorded as individual sales.
  Offers for items that aren't in a plan are ignored by the site.
- **Side panel** with your latest run item by item (bought, sold, still held, profit), stock you're still
  holding from earlier runs, and your all-time realized profit.

## Setup

1. On the site, open **Settings → RuneLite plugin** and create a plugin key.
2. In RuneLite, open the Overnight Flipper plugin settings, paste the key into **Plugin key**, and turn on
   **Sync with server**. Change **Server URL** if you use a different Overnight Flipper site.
3. Log in (or relog). Your current offers are sent once, then every change as it happens.

## Data sent

Only when **Sync with server** is on, and only to the server URL you set:

- for each GE slot: slot number, item ID, offer state, price, total quantity, quantity traded and gp spent/received
- an anonymous ID for the account (a one-way hash of RuneLite's account hash) so several accounts don't mix
- your plugin key, which identifies you on that site

No player name, location, chat or other player information is sent.
