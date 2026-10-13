package io.openems.edge.heat.luxtronik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Mode;
import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Settings;

class LuxtronikControlPolicyTest {

    private static Settings settings(Mode mode) {
        return new Settings(mode, 5, 10, -10, 200, 350, 1000);
    }

    @Test
    void monitorNeverWritesAndManualIsBounded() {
        assertFalse(LuxtronikControlPolicy.decide(settings(Mode.MONITOR), 100.0, -5000).usable());
        assertEquals(5, LuxtronikControlPolicy.decide(settings(Mode.MANUAL), null, null)
                .offsetTenthsKelvin());
        assertThrows(IllegalArgumentException.class,
                () -> new Settings(Mode.MANUAL, 31, 10, -10, 200, 350, 1000));
        assertThrows(IllegalArgumentException.class,
                () -> new Settings(Mode.MANUAL, 0, 10, -10, 350, 200, 1000));
    }

    @Test
    void priceConditionsAndInvalidData() {
        assertEquals(10, LuxtronikControlPolicy.decide(settings(Mode.PRICE), 150.0, null)
                .offsetTenthsKelvin());
        assertEquals(0, LuxtronikControlPolicy.decide(settings(Mode.PRICE), 260.0, null)
                .offsetTenthsKelvin());
        assertEquals(-10, LuxtronikControlPolicy.decide(settings(Mode.PRICE), 370.0, null)
                .offsetTenthsKelvin());
        assertFalse(LuxtronikControlPolicy.decide(settings(Mode.PRICE), null, null).usable());
        assertFalse(LuxtronikControlPolicy.decide(settings(Mode.PRICE), Double.NaN, null).usable());
        assertFalse(LuxtronikControlPolicy.decide(settings(Mode.PRICE), -25.0, null).usable());
    }

    @Test
    void gridExportOnlyAndUnknownMeasurements() {
        assertEquals(10, LuxtronikControlPolicy.decide(settings(Mode.PV_SURPLUS), null, -1000)
                .offsetTenthsKelvin());
        assertEquals(0, LuxtronikControlPolicy.decide(settings(Mode.PV_SURPLUS), null, -999)
                .offsetTenthsKelvin());
        assertFalse(LuxtronikControlPolicy.decide(settings(Mode.PV_SURPLUS), null, null).usable());
    }

    @Test
    void combinedModesUseOnlyVerifiedInputs() {
        assertTrue(LuxtronikControlPolicy.decide(settings(Mode.PRICE_OR_PV), null, -1500).usable());
        assertEquals(10, LuxtronikControlPolicy.decide(settings(Mode.PRICE_OR_PV), 420.0, -1500)
                .offsetTenthsKelvin());
        assertEquals(-10, LuxtronikControlPolicy.decide(settings(Mode.PRICE_OR_PV), 420.0, 500)
                .offsetTenthsKelvin());
        assertEquals(0, LuxtronikControlPolicy.decide(settings(Mode.PRICE_OR_PV), null, 500)
                .offsetTenthsKelvin());
        assertFalse(LuxtronikControlPolicy.decide(settings(Mode.PRICE_OR_PV), null, null).usable());
    }
}
