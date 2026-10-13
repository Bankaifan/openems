import { displayPrice, normalizeForecast } from "./forecast-utils";

describe("Tariff forecast normalization", () => {
    const now = Date.parse("2026-10-13T10:00:00Z");

    it("filters invalid and expired prices and sorts by timestamp", () => {
        expect(normalizeForecast([
            { timestamp: "2026-10-13T10:30:00Z", price: 40 },
            { timestamp: "2026-10-13T09:30:00Z", price: 10 },
            { timestamp: "2026-10-13T10:15:00Z", price: 30 },
            { timestamp: "invalid", price: 20 },
            { timestamp: "2026-10-13T10:45:00Z", price: "50" },
        ], now).map(e => e.price)).toEqual([30, 40]);
    });

    it("returns an empty forecast for invalid API responses", () => {
        expect(normalizeForecast(null, now)).toEqual([]);
        expect(normalizeForecast({}, now)).toEqual([]);
    });

    it("converts EUR/MWh to ct/kWh and retains other currencies", () => {
        expect(displayPrice(210, "EUR")).toBe(21);
        expect(displayPrice(210, "GBP")).toBe(210);
    });
});
