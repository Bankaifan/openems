package io.openems.edge.heat.luxtronik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Mode;

class LuxtronikAdvancedControlTest {

    @Test
    void everyWritableShiRegisterCanBeConfiguredWithValidatedRanges() {
        for (var register : ShiWriteRegister.values()) {
            var json = "{\"" + register.name() + "\":{\"normal\":" + register.minimum() + "}}";
            var profile = LuxtronikAdvancedControl.parse(json).get(register);
            assertEquals(register.minimum(), profile.normal());
        }
        assertEquals(ShiWriteRegister.values().length,
                LuxtronikAdvancedControl.parse(allControls()).size());
    }

    private static String allControls() {
        var json = new StringBuilder("{");
        for (var register : ShiWriteRegister.values()) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(register.name()).append("\":{\"normal\":")
                    .append(register.minimum()).append('}');
        }
        return json.append('}').toString();
    }

    @Test
    void rejectOutOfRangeUnknownOrMalformedPlans() {
        assertThrows(IllegalArgumentException.class,
                () -> LuxtronikAdvancedControl.parse("{\"HOT_WATER_SETPOINT\":{\"normal\":900}}"));
        assertThrows(IllegalArgumentException.class,
                () -> LuxtronikAdvancedControl.parse("{\"WHATEVER\":{\"normal\":1}}"));
        assertThrows(IllegalArgumentException.class,
                () -> LuxtronikAdvancedControl.parse("{\"HEATING_BLOCK\":{\"garbage\":1}}"));
        assertThrows(IllegalArgumentException.class,
                () -> LuxtronikAdvancedControl.parse("{\"HEATING_BLOCK\":{\"normal\":1.5}}"));
        assertThrows(IllegalArgumentException.class,
                () -> LuxtronikAdvancedControl.parse("[]"));
        assertTrue(LuxtronikAdvancedControl.parse("{}").isEmpty());
    }

    @Test
    void useConfiguredPriceAndPvProfilesWithoutInventingSensorValues() {
        var profile = LuxtronikAdvancedControl.parse(
                "{\"MIXER1_HEATING_OFFSET\":{\"normal\":0,\"cheap\":10,\"expensive\":-5,\"pv\":15}}")
                .get(ShiWriteRegister.MIXER1_HEATING_OFFSET);
        assertNull(LuxtronikAdvancedControl.target(profile, Mode.MONITOR, 50.0, -3000,
                200, 350, 1000));
        assertEquals(0, LuxtronikAdvancedControl.target(profile, Mode.PRICE, null, null,
                200, 350, 1000));
        assertEquals(10, LuxtronikAdvancedControl.target(profile, Mode.PRICE, 190.0, null,
                200, 350, 1000));
        assertEquals(-5, LuxtronikAdvancedControl.target(profile, Mode.PRICE, 400.0, null,
                200, 350, 1000));
        assertEquals(15, LuxtronikAdvancedControl.target(profile, Mode.PRICE_OR_PV, 400.0, -2000,
                200, 350, 1000));
        assertEquals(0, LuxtronikAdvancedControl.target(profile, Mode.PV_SURPLUS, null, null,
                200, 350, 1000));
    }
}
