package io.openems.edge.heat.luxtronik;

/**
 * Stateless mode selection for SHI heating-curve offsets.
 *
 * <p>
 * This is not a compressor-power setpoint or a replacement for the OpenEMS
 * energy optimizer. Missing inputs never trigger a positive heating offset.
 */
public final class LuxtronikControlPolicy {

    public enum Mode {
        MONITOR,
        MANUAL,
        PRICE,
        PV_SURPLUS,
        PRICE_OR_PV
    }

    public record Settings(Mode mode, int manualOffset, int boostOffset, int reduceOffset, //
            int cheapPriceEurMwh, int expensivePriceEurMwh, int minExportWatts) {
        public Settings {
            if (mode == null || Math.abs(manualOffset) > 30 || boostOffset < 0 || boostOffset > 30
                    || reduceOffset < -30 || reduceOffset > 0
                    || cheapPriceEurMwh < 0 || expensivePriceEurMwh <= cheapPriceEurMwh
                    || minExportWatts < 0) {
                throw new IllegalArgumentException("Invalid Luxtronik control settings");
            }
        }
    }

    public record Decision(boolean usable, int offsetTenthsKelvin, String reason) {
    }

    private LuxtronikControlPolicy() {
    }

    /**
     * Resolves a bounded offset based on existing OpenEMS measurements.
     *
     * @param settings validated controller configuration
     * @param price current grid-buy price in EUR/MWh, or null
     * @param gridWatts grid power (+buy, -export), or null
     * @return decision to display or apply
     */
    public static Decision decide(Settings settings, Double price, Integer gridWatts) {
        switch (settings.mode()) {
        case MONITOR:
            return new Decision(false, 0, "Monitoring only");
        case MANUAL:
            return new Decision(true, settings.manualOffset(), "Manual heating-curve offset");
        case PRICE:
            if (!isValidPrice(price)) {
                return new Decision(false, 0, "No valid current electricity price");
            }
            return new Decision(true, priceOffset(settings, price), "Electricity tariff");
        case PV_SURPLUS:
            if (gridWatts == null) {
                return new Decision(false, 0, "No grid exchange measurement");
            }
            return new Decision(true, isExporting(settings, gridWatts) ? settings.boostOffset() : 0,
                    "Measured grid export");
        case PRICE_OR_PV:
            boolean validPrice = isValidPrice(price);
            boolean validGrid = gridWatts != null;
            if (!validPrice && !validGrid) {
                return new Decision(false, 0, "Neither tariff nor grid export available");
            }
            if (validGrid && isExporting(settings, gridWatts)
                    || validPrice && price <= settings.cheapPriceEurMwh()) {
                return new Decision(true, settings.boostOffset(), "Cheap tariff or measured surplus");
            }
            if (validPrice && price >= settings.expensivePriceEurMwh()) {
                return new Decision(true, settings.reduceOffset(), "Expensive tariff");
            }
            return new Decision(true, 0, "Normal price and no verified surplus");
        default:
            throw new IllegalStateException("Unexpected Luxtronik mode: " + settings.mode());
        }
    }

    private static boolean isExporting(Settings settings, int gridWatts) {
        return gridWatts <= -settings.minExportWatts();
    }

    private static int priceOffset(Settings settings, double price) {
        if (price <= settings.cheapPriceEurMwh()) {
            return settings.boostOffset();
        }
        if (price >= settings.expensivePriceEurMwh()) {
            return settings.reduceOffset();
        }
        return 0;
    }

    private static boolean isValidPrice(Double price) {
        return price != null && Double.isFinite(price) && price >= 0;
    }
}
