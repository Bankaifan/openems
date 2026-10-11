package io.openems.edge.timeofusetariff.api;

import java.time.temporal.ChronoUnit;

import org.osgi.annotation.versioning.ProviderType;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openems.common.jsonrpc.base.GenericJsonrpcResponseSuccess;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.jsonapi.JsonApiBuilder;

/**
 * Provides quarterly Time-of-Use Tariff prices.
 */
@ProviderType
public interface TimeOfUseTariff extends ComponentJsonApi {

	/**
	 * Gives electricity prices; one value per 15 minutes.
	 *
	 * <p>
	 * E.g. if called at 10:05, the first value stands for 10:00 to 10:15; second
	 * value for 10:15 to 10:30.
	 *
	 * @return the prices; {@link TimeOfUsePrices#empty(java.time.ZonedDateTime)} if
	 *         no prices are known
	 */
	public TimeOfUsePrices getPrices();

	@Override
	public default void buildJsonApiRoutes(JsonApiBuilder builder) {
		builder.handleRequest("getPriceForecast", call -> {
			var forecast = this.getPrices();
			var entries = new JsonArray();
			if (!forecast.isEmpty()) {
				var time = forecast.getFirstTime();
				for (var price : forecast.asArray()) {
					var entry = new JsonObject();
					entry.addProperty("timestamp", time.toString());
					if (price != null && Double.isFinite(price)) {
						entry.addProperty("price", price);
					}
					entries.add(entry);
					time = time.plus(15, ChronoUnit.MINUTES);
				}
			}
			var result = new JsonObject();
			result.add("prices", entries);
			return new GenericJsonrpcResponseSuccess(call.getRequest().getId(), result);
		});
	}

}
