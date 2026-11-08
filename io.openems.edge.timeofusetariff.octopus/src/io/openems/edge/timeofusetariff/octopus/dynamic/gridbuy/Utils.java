package io.openems.edge.timeofusetariff.octopus.dynamic.gridbuy;

import static io.openems.common.utils.DateUtils.parseZonedDateTimeOrError;
import static io.openems.common.utils.DateUtils.roundDownToQuarter;
import static io.openems.common.utils.JsonUtils.getAsDouble;
import static io.openems.common.utils.JsonUtils.getAsJsonObject;
import static io.openems.common.utils.JsonUtils.getAsOptionalJsonArray;
import static io.openems.common.utils.JsonUtils.getAsOptionalJsonObject;
import static io.openems.common.utils.JsonUtils.getAsOptionalString;
import static io.openems.common.utils.JsonUtils.getAsString;
import static io.openems.common.utils.JsonUtils.parseToJsonObject;
import static java.time.temporal.ChronoUnit.SECONDS;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.time.DelayTimeProvider;
import io.openems.common.bridge.http.time.DelayTimeProviderChain;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.HttpStatus;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

final class Utils {

	private static final String TOKEN_MUTATION = """
			mutation Authentication($email: String!, $password: String!) {
				obtainKrakenToken(input: {email: $email, password: $password}) { token }
			}""";

	private static final String PRICE_QUERY = """
			query Prices($accountNumber: String!) {
				account(accountNumber: $accountNumber) {
					properties { electricityMalos { agreements {
						unitRateForecast { validFrom validTo unitRateInformation {
							... on TimeOfUseProductUnitRateInformation { rates { latestGrossUnitRateCentsPerKwh } }
						} }
					} } }
				}
			}""";

	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 20_000;
	private static final long QUARTER_SECONDS = 900;
	private static final Pattern AUTHORIZATION_ERROR_CODE = Pattern.compile("KT-CT-11\\d{2}");

	/** Kraken API prices are in ct/kWh; multiply by 10 to get EUR/MWh. */
	private static final double CENTS_PER_KWH_TO_CURRENCY_PER_MWH = 10.0;

	static final Duration RATE_LIMIT_DELAY = Duration.ofHours(1);
	static final Duration ERROR_RETRY_DELAY = Duration.ofMinutes(5);
	static final int MIN_REFRESH_MINUTES = 15;

	private Utils() {
	}

	/**
	 * Result of a price update, used to decide on the next request.
	 */
	enum UpdateResult {
		/** Prices were updated. */
		SUCCESS,
		/** HTTP 429; retry after a long delay. */
		RATE_LIMITED,
		/** Credentials rejected; do not retry until the configuration changes. */
		AUTHENTICATION_FAILED,
		/** Any other error; retry after a short delay. */
		FAILED;
	}

	/**
	 * Thrown if the Kraken API rejects the configured credentials.
	 */
	static final class AuthenticationFailedException extends Exception {

		private static final long serialVersionUID = 1L;

		AuthenticationFailedException(String message) {
			super(message);
		}

		AuthenticationFailedException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Delay time provider for the Octopus price endpoint.
	 */
	static class OctopusDelayTimeProvider implements DelayTimeProvider<UpdateResult> {

		private final Duration refreshInterval;

		OctopusDelayTimeProvider(int refreshMinutes) {
			this.refreshInterval = Duration.ofMinutes(Math.max(MIN_REFRESH_MINUTES, refreshMinutes));
		}

		@Override
		public Delay onFirstRunDelay() {
			return Delay.immediate();
		}

		@Override
		public Delay onErrorRunDelay(HttpError error) {
			return this.getDelay(classify(error));
		}

		@Override
		public Delay onSuccessRunDelay(UpdateResult result) {
			return this.getDelay(result == null ? UpdateResult.FAILED : result);
		}

		Delay getDelay(UpdateResult result) {
			return switch (result) {
			case SUCCESS -> DelayTimeProviderChain.fixedDelay(this.refreshInterval) //
					.plusRandomDelay(60, SECONDS) //
					.getDelay();
			case RATE_LIMITED -> DelayTimeProviderChain.fixedDelay(RATE_LIMIT_DELAY) //
					.plusRandomDelay(60, SECONDS) //
					.getDelay();
			case AUTHENTICATION_FAILED -> Delay.infinite();
			case FAILED -> DelayTimeProviderChain.fixedDelay(ERROR_RETRY_DELAY) //
					.plusRandomDelay(30, SECONDS) //
					.getDelay();
			};
		}
	}

	/**
	 * Classifies an error of a request chain.
	 *
	 * <p>
	 * Walks the cause chain, because errors of the token request arrive wrapped
	 * in completion exceptions.
	 *
	 * @param error the {@link Throwable}
	 * @return the {@link UpdateResult}
	 */
	static UpdateResult classify(Throwable error) {
		for (var t = error; t != null; t = t.getCause()) {
			if (t instanceof AuthenticationFailedException) {
				return UpdateResult.AUTHENTICATION_FAILED;
			}
			if (t instanceof HttpError.ResponseError re) {
				final var code = re.status.code();
				if (code == HttpStatus.TOO_MANY_REQUESTS.code()) {
					return UpdateResult.RATE_LIMITED;
				}
				if (code == HttpStatus.UNAUTHORIZED.code() || code == HttpStatus.FORBIDDEN.code()) {
					return UpdateResult.AUTHENTICATION_FAILED;
				}
				return UpdateResult.FAILED;
			}
		}
		return UpdateResult.FAILED;
	}

	/**
	 * Gets the HTTP status code of the first response error in the cause chain.
	 *
	 * @param error the {@link Throwable}
	 * @return the status code or -1 if there is none
	 */
	static int getHttpStatusCode(Throwable error) {
		for (var t = error; t != null; t = t.getCause()) {
			if (t instanceof HttpError.ResponseError re) {
				return re.status.code();
			}
		}
		return -1;
	}

	/**
	 * Creates the endpoint to obtain a Kraken API token.
	 *
	 * @param country  the {@link Country}
	 * @param email    the account email
	 * @param password the account password
	 * @return the {@link BridgeHttp.Endpoint}
	 */
	static BridgeHttp.Endpoint createTokenEndpoint(Country country, String email, String password) {
		return createGraphQlEndpoint(country, TOKEN_MUTATION, JsonUtils.buildJsonObject() //
				.addProperty("email", email) //
				.addProperty("password", password) //
				.build());
	}

	/**
	 * Creates the endpoint to query the unit rate forecast of an account.
	 *
	 * @param country       the {@link Country}
	 * @param accountNumber the account number
	 * @return the {@link BridgeHttp.Endpoint}
	 */
	static BridgeHttp.Endpoint createPriceEndpoint(Country country, String accountNumber) {
		return createGraphQlEndpoint(country, PRICE_QUERY, JsonUtils.buildJsonObject() //
				.addProperty("accountNumber", accountNumber) //
				.build());
	}

	private static BridgeHttp.Endpoint createGraphQlEndpoint(Country country, String query, JsonObject variables) {
		return BridgeHttp.create(country.getApiUrl()) //
				.setConnectTimeout(CONNECT_TIMEOUT_MS) //
				.setReadTimeout(READ_TIMEOUT_MS) //
				.setBodyJson(JsonUtils.buildJsonObject() //
						.addProperty("query", query) //
						.add("variables", variables) //
						.build()) //
				.build();
	}

	/**
	 * Whether a GraphQL response contains errors.
	 *
	 * @param jsonData the response body
	 * @return true if the body cannot be parsed or contains errors
	 */
	static boolean hasGraphQlErrors(String jsonData) {
		if (jsonData == null) {
			return true;
		}
		try {
			return hasGraphQlErrors(parseToJsonObject(jsonData));
		} catch (OpenemsNamedException e) {
			return true;
		}
	}

	private static boolean hasGraphQlErrors(JsonObject json) {
		return getAsOptionalJsonArray(json, "errors") //
				.map(errors -> !errors.isEmpty()) //
				.orElse(false);
	}

	/**
	 * Whether a GraphQL response reports an authorization error. Kraken uses error
	 * codes of the form "KT-CT-11xx" (four digits) for authentication and
	 * authorization errors.
	 *
	 * @param jsonData the response body
	 * @return true if the response contains an authorization error
	 */
	static boolean hasGraphQlAuthorizationError(String jsonData) {
		if (jsonData == null) {
			return false;
		}
		try {
			return getAsOptionalJsonArray(parseToJsonObject(jsonData), "errors") //
					.map(errors -> errors.asList().stream() //
							.map(e -> getAsOptionalJsonObject(e, "extensions") //
									.flatMap(x -> getAsOptionalString(x, "errorCode")) //
									.orElse("")) //
							.anyMatch(code -> AUTHORIZATION_ERROR_CODE.matcher(code).matches())) //
					.orElse(false);
		} catch (OpenemsNamedException e) {
			return false;
		}
	}

	/**
	 * Parses the token from the obtainKrakenToken mutation response.
	 *
	 * @param jsonData the response body
	 * @return the token
	 * @throws AuthenticationFailedException if the credentials were rejected
	 * @throws OpenemsNamedException         on parse error or other GraphQL
	 *                                       errors
	 */
	static String parseToken(String jsonData) throws AuthenticationFailedException, OpenemsNamedException {
		final var json = parseToJsonObject(jsonData);
		final var token = getAsOptionalJsonObject(json, "data") //
				.flatMap(data -> getAsOptionalJsonObject(data, "obtainKrakenToken")) //
				.flatMap(result -> getAsOptionalString(result, "token")) //
				.filter(t -> !t.isBlank());
		if (token.isEmpty()) {
			if (hasGraphQlErrors(json) && !hasGraphQlAuthorizationError(jsonData)) {
				// e.g. temporary server error; retry later
				throw new OpenemsException("GraphQL errors in obtainKrakenToken response");
			}
			// Kraken reports invalid credentials as GraphQL authorization errors
			throw new AuthenticationFailedException("No token in obtainKrakenToken response");
		}
		return token.get();
	}

	/**
	 * Parses the account unit rate forecast to {@link TimeOfUsePrices}.
	 *
	 * <p>
	 * The account may contain several agreements, e.g. around a contract change
	 * or for different supply points. Empty forecasts are skipped. The agreement
	 * that is active at {@code now} is preferred (if several are active, the most
	 * recently started one); other agreements only fill the time after it. Prices before {@code now} are dropped and the result is
	 * cut at the first gap, so the returned forecast is always continuous.
	 * Individual invalid slots and quarters with conflicting prices within one
	 * agreement are skipped instead of rejecting the whole forecast.
	 *
	 * <p>
	 * Prices are converted from gross ct/kWh to currency/MWh.
	 *
	 * @param jsonData the response body
	 * @param now      the current time
	 * @return the {@link TimeOfUsePrices}
	 * @throws AuthenticationFailedException if access to the account was denied
	 * @throws OpenemsNamedException         if the response is invalid or
	 *                                       contains no current or future prices
	 */
	static TimeOfUsePrices parsePrices(String jsonData, Instant now)
			throws AuthenticationFailedException, OpenemsNamedException {
		if (hasGraphQlAuthorizationError(jsonData)) {
			throw new AuthenticationFailedException("Access to the account was denied");
		}
		final var json = parseToJsonObject(jsonData);
		if (hasGraphQlErrors(json)) {
			throw new OpenemsException("GraphQL errors in Octopus price response");
		}
		final var account = getAsJsonObject(getAsJsonObject(json, "data"), "account");
		final var currentQuarter = roundDownToQuarter(now);

		final var agreements = new ArrayList<NavigableMap<Instant, Double>>();
		for (var property : array(account, "properties")) {
			for (var malo : array(property, "electricityMalos")) {
				for (var agreement : array(malo, "agreements")) {
					final var slots = parseForecast(array(agreement, "unitRateForecast"));
					if (!slots.isEmpty()) {
						agreements.add(slots);
					}
				}
			}
		}

		final var merged = mergeAgreements(agreements, currentQuarter);
		final var result = ImmutableSortedMap.<Instant, Double>naturalOrder();
		Instant expected = null;
		for (var entry : merged.tailMap(currentQuarter, true).entrySet()) {
			if (expected != null && !entry.getKey().equals(expected)) {
				break; // gap: keep the continuous part only
			}
			result.put(entry.getKey(), entry.getValue());
			expected = entry.getKey().plusSeconds(QUARTER_SECONDS);
		}

		final var prices = TimeOfUsePrices.from(result.build());
		if (prices.isEmpty()) {
			throw new OpenemsException("No current or future prices in Octopus price response");
		}
		return prices;
	}

	/**
	 * Merges the agreements, preferring the one active at the current quarter.
	 *
	 * @param agreements     the parsed agreements
	 * @param currentQuarter the current quarter
	 * @return the merged prices
	 */
	private static NavigableMap<Instant, Double> mergeAgreements(List<NavigableMap<Instant, Double>> agreements,
			Instant currentQuarter) {
		// Active agreements first (the most recently started one wins), then the
		// others by start time
		agreements.sort((a, b) -> {
			final var aActive = a.containsKey(currentQuarter);
			final var bActive = b.containsKey(currentQuarter);
			if (aActive != bActive) {
				return aActive ? -1 : 1;
			}
			return aActive //
					? b.firstKey().compareTo(a.firstKey()) //
					: a.firstKey().compareTo(b.firstKey());
		});
		final var merged = new TreeMap<Instant, Double>();
		for (var agreement : agreements) {
			if (merged.isEmpty()) {
				merged.putAll(agreement);
				continue;
			}
			// Later agreements only continue after the preferred data
			agreement.tailMap(merged.lastKey(), false).forEach(merged::putIfAbsent);
		}
		return merged;
	}

	/**
	 * Parses one unitRateForecast into quarter-hourly prices.
	 *
	 * @param forecast the unitRateForecast array
	 * @return the prices per quarter; invalid or conflicting slots are skipped
	 */
	private static NavigableMap<Instant, Double> parseForecast(JsonArray forecast) {
		final var result = new TreeMap<Instant, Double>();
		final var conflicts = new HashSet<Instant>();
		for (var slot : forecast) {
			try {
				final var start = parseZonedDateTimeOrError(getAsString(slot, "validFrom")).toInstant();
				final var end = parseZonedDateTimeOrError(getAsString(slot, "validTo")).toInstant();
				final var seconds = end.getEpochSecond() - start.getEpochSecond();
				if (seconds <= 0 || seconds % QUARTER_SECONDS != 0 || start.getEpochSecond() % QUARTER_SECONDS != 0) {
					continue;
				}
				final var rates = array(getAsJsonObject(slot, "unitRateInformation"), "rates");
				if (rates.size() != 1) {
					continue; // missing or ambiguous rate
				}
				final var price = getAsDouble(rates.get(0), "latestGrossUnitRateCentsPerKwh")
						* CENTS_PER_KWH_TO_CURRENCY_PER_MWH;
				if (!Double.isFinite(price)) {
					continue;
				}
				for (var t = start; t.isBefore(end); t = t.plusSeconds(QUARTER_SECONDS)) {
					final var previous = result.putIfAbsent(t, price);
					if (previous != null && Double.compare(previous, price) != 0) {
						conflicts.add(t);
					}
				}
			} catch (OpenemsNamedException e) {
				// skip invalid slot
			}
		}
		conflicts.forEach(result::remove);
		return result;
	}

	private static JsonArray array(JsonElement element, String member) {
		return getAsOptionalJsonArray(element, member).orElseGet(JsonArray::new);
	}
}
