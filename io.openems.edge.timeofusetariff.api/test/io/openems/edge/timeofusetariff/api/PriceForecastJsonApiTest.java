package io.openems.edge.timeofusetariff.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.time.Instant;

import org.junit.Test;

import com.google.gson.JsonObject;

import io.openems.common.jsonrpc.base.GenericJsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.jsonrpc.base.JsonrpcResponse;
import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.edge.common.jsonapi.Call;
import io.openems.edge.common.jsonapi.JsonApiBuilder;

/**
 * Verifies the read-only tariff forecast component endpoint.
 */
public class PriceForecastJsonApiTest {

	private static JsonObject response(TimeOfUsePrices prices) {
		var provider = new TimeOfUseTariff() {

			@Override
			public String id() {
				return "testTariff0";
			}

			@Override
			public TimeOfUsePrices getPrices() {
				return prices;
			}
		};

		var builder = new JsonApiBuilder();
		provider.buildJsonApiRoutes(builder);
		var call = new Call<JsonrpcRequest, JsonrpcResponse>(
				new GenericJsonrpcRequest("getPriceForecast", new JsonObject()));
		builder.handle(call);
		return ((JsonrpcResponseSuccess) call.getResponse()).getResult();
	}

	@Test
	public void providesQuarterlyPriceAndTimestamp() {
		var data = response(TimeOfUsePrices.from(Instant.parse("2026-10-25T00:45:00Z"), 100.0, 120.0));
		var prices = data.getAsJsonArray("prices");
		assertEquals(2, prices.size());
		assertEquals("2026-10-25T00:45:00Z",
				prices.get(0).getAsJsonObject().get("timestamp").getAsString());
		assertEquals("2026-10-25T01:00:00Z",
				prices.get(1).getAsJsonObject().get("timestamp").getAsString());
		assertEquals(120.0, prices.get(1).getAsJsonObject().get("price").getAsDouble(), 0.001);
	}

	@Test
	public void returnsEmptyForecastWithoutInventingPrices() {
		var prices = response(TimeOfUsePrices.EMPTY_PRICES).getAsJsonArray("prices");
		assertFalse(prices.iterator().hasNext());
	}
}
