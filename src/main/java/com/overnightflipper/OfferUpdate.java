package com.overnightflipper;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/**
 * State of one Grand Exchange slot, as sent to POST /api/plugin/offers.
 */
@AllArgsConstructor
@EqualsAndHashCode
class OfferUpdate
{
	int slot;
	@SerializedName("item_id")
	int itemId;
	String state;
	long price;
	@SerializedName("total_qty")
	int totalQty;
	@SerializedName("traded_qty")
	int tradedQty;
	long spent;
}
