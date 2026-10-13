export interface ForecastEntry {
    timestamp: string;
    price: number;
}

export function normalizeForecast(entries: unknown, now: number): ForecastEntry[] {
    if (!Array.isArray(entries)) {
        return [];
    }
    return entries.filter((entry): entry is ForecastEntry =>
        entry != null && typeof entry.timestamp === "string"
        && Number.isFinite(Date.parse(entry.timestamp))
        && Date.parse(entry.timestamp) + 15 * 60 * 1000 > now
        && typeof entry.price === "number" && Number.isFinite(entry.price))
        .sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp));
}

export function displayPrice(pricePerMwh: number, currency: string): number {
    return currency === "EUR" ? pricePerMwh / 10 : pricePerMwh;
}
