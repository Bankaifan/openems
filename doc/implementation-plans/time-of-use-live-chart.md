# Generic Time-of-Use price forecast chart — implementation plan

## Purpose
Expose a provider-independent 15-minute electricity-price forecast directly in the OpenEMS Edge Live view, even without an ESS Time-of-Use controller. This is a separate contribution from the Octopus tariff provider.

## Findings in OpenEMS 2026.10
- The existing `ScheduleStateAndPriceChartComponent` visualizes the **ESS controller's optimization schedule**, fetched via `getSchedule`. It is not a generic tariff-provider forecast.
- `TimeOfUseTariff#getPrices()` returns `TimeOfUsePrices` at 15-minute resolution with timestamps and prices in `_meta/Currency/MWh`.
- The live widget's `QuarterlyPrices` channel is **only the current price**, not the whole forecast.
- The normal Live page uses component-driven widgets and Angular/Chart.js; do not hardcode a provider such as Octopus in the UI.

## Proposed architecture
1. **Backend (shared tariff API):** expose a read-only, authenticated JSON-RPC endpoint for each configured `TimeOfUseTariff` provider, returning an ordered collection of `{timestamp, price}` entries. Reuse an existing generic API path if a compatible one is identified. Preserve timestamps from the original `TimeOfUsePrices`; do not guess missing values.
2. **Frontend:** add a Chart.js-based standalone live widget for tariff providers using the project's existing chart utilities. Show upcoming 15-minute price steps, local-time axis, currency converted from EUR/MWh to ct/kWh when appropriate, clear empty/error states, and a highlighted current interval.
3. **Registration:** connect the widget to the generic `TimeOfUseTariff` component factory/interface and navigation, avoiding ESS-controller dependencies.
4. **Tests:** validate DST, no data, stale data, non-contiguous periods, negative prices, unit conversion, changing provider, and permission/response failures. Verify responsive layout and lint/TypeScript build.

## Scope
- Forecast of **unit electricity price**, not accumulated electricity cost or realized consumption-weighted costs.
- Generic for Octopus, Tibber, aWATTar, ENTSO-E, and any provider implementing `TimeOfUseTariff`.
- No tariff mutation, device control or assumption about network-fee inclusion.

## Acceptance criteria
- A configured TimeOfUseTariff provider is visualized on the Live dashboard independent of an ESS optimization controller.
- Proper quarter-hour mapping across DST and correct readable unit conversion.
- Explicit empty and API-failure handling without a fabricated forecast.
- CI success (Checkstyle, Java tests, OSGi resolution, UI build), plus local UI verification.

Implementation is pending; this file records the verified integration points and avoids suggesting a chart exists before the forecast is exposed.
