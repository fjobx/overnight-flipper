package com.overnightflipper;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response of GET /api/plugin/summary.
 */
class Summary
{
	String email;
	Run run;
	List<Item> holding;
	Totals totals;

	static class Run
	{
		int id;
		@SerializedName("created_at")
		double createdAt;
		long budget;
		String risk;
		List<Item> items;
		long profit;
		@SerializedName("expected_profit")
		long expectedProfit;
	}

	static class Item
	{
		@SerializedName("item_id")
		int itemId;
		String name;
		String status;
		@SerializedName("plan_qty")
		int planQty;
		@SerializedName("buy_price")
		int buyPrice;
		@SerializedName("sell_price")
		int sellPrice;
		@SerializedName("expected_profit")
		long expectedProfit;
		int filled;
		@SerializedName("avg_buy")
		Integer avgBuy;
		int sold;
		@SerializedName("avg_sell")
		Integer avgSell;
		int held;
		long profit;
		boolean synced;
		/**
		 * Newest GE offer the plugin reported for this item, null if none.
		 */
		Offer offer;
	}

	static class Offer
	{
		String state;
		long price;
		@SerializedName("total_qty")
		int totalQty;
		@SerializedName("traded_qty")
		int tradedQty;
	}

	static class Totals
	{
		long profit;
		int nights;
	}
}
