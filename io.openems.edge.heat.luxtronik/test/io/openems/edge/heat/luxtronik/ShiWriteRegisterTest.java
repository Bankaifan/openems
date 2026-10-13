package io.openems.edge.heat.luxtronik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

public class ShiWriteRegisterTest {

    @Test
    public void testAllAddressesUniqueAndBoundsValid() {
        var addresses = new HashSet<Integer>();
        for (var register : ShiWriteRegister.values()) {
            assertTrue(addresses.add(register.address()), "Duplicate register: " + register.address());
            assertTrue(register.minimum() <= register.maximum());
            assertEquals(register.minimum(), register.validate(register.minimum()));
            assertEquals(register.maximum(), register.validate(register.maximum()));
            assertThrows(IllegalArgumentException.class,
                    () -> register.validate(register.minimum() - 1));
            assertThrows(IllegalArgumentException.class,
                    () -> register.validate(register.maximum() + 1));
        }
    }

    @Test
    public void testHeatingAndHotWaterRanges() {
        assertEquals(10001, ShiWriteRegister.HEATING_SETPOINT.address());
        assertEquals(150, ShiWriteRegister.HEATING_SETPOINT.validate(150));
        assertEquals(750, ShiWriteRegister.HEATING_SETPOINT.validate(750));
        assertThrows(IllegalArgumentException.class,
                () -> ShiWriteRegister.HEATING_SETPOINT.validate(149));
        assertThrows(IllegalArgumentException.class,
                () -> ShiWriteRegister.HOT_WATER_SETPOINT.validate(299));
        assertThrows(IllegalArgumentException.class,
                () -> ShiWriteRegister.HOT_WATER_SETPOINT.validate(751));
    }

    @Test
    public void testSignedOffsetEncoding() {
        assertEquals(0xffce, ShiWriteRegister.HEATING_OFFSET.toModbusWord(-50));
        assertEquals(200, ShiWriteRegister.HEATING_OFFSET.toModbusWord(200));
        assertThrows(IllegalArgumentException.class,
                () -> ShiWriteRegister.HEATING_OFFSET.toModbusWord(-201));
        assertThrows(IllegalArgumentException.class,
                () -> ShiWriteRegister.MIXER1_HEATING_OFFSET.validate(51));
    }

    @Test
    public void testPowerAndBlockRanges() {
        assertEquals(10041, ShiWriteRegister.POWER_LIMIT_TENTHS_KW.address());
        assertEquals(300, ShiWriteRegister.POWER_LIMIT_TENTHS_KW.validate(300));
        assertThrows(IllegalArgumentException.class,
                () -> ShiWriteRegister.POWER_LIMIT_MODE.validate(3));
        assertThrows(IllegalArgumentException.class,
                () -> ShiWriteRegister.HEATING_BLOCK.validate(2));
        assertEquals(10051, ShiWriteRegister.HOT_WATER_BLOCK.address());
    }
}
