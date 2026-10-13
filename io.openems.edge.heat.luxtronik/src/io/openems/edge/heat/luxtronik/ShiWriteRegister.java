package io.openems.edge.heat.luxtronik;

/**
 * Writable Luxtronik SHI holding registers, using zero-based Modbus addresses.
 * Values are raw register values (temperatures in tenths of degrees/Kelvin).
 * Source: ait-deutschland SHI operating manual 83026903aUK.
 */
public enum ShiWriteRegister {
    HEATING_MODE(10000, 0, 3),
    HEATING_SETPOINT(10001, 150, 750),
    HEATING_OFFSET(10002, -200, 200),
    HEATING_LEVEL(10003, 0, 3),
    HOT_WATER_MODE(10005, 0, 3),
    HOT_WATER_SETPOINT(10006, 300, 750),
    HOT_WATER_OFFSET(10007, -200, 200),
    HOT_WATER_LEVEL(10008, 0, 3),
    MIXER1_HEATING_MODE(10010, 0, 3),
    MIXER1_HEATING_SETPOINT(10011, 200, 650),
    MIXER1_HEATING_OFFSET(10012, -50, 50),
    MIXER1_HEATING_LEVEL(10013, 0, 3),
    MIXER1_COOLING_MODE(10015, 0, 2),
    MIXER1_COOLING_SETPOINT(10016, 50, 250),
    MIXER1_COOLING_OFFSET(10017, -50, 50),
    MIXER2_HEATING_MODE(10020, 0, 3),
    MIXER2_HEATING_SETPOINT(10021, 200, 650),
    MIXER2_HEATING_OFFSET(10022, -50, 50),
    MIXER2_HEATING_LEVEL(10023, 0, 3),
    MIXER2_COOLING_MODE(10025, 0, 2),
    MIXER2_COOLING_SETPOINT(10026, 50, 250),
    MIXER2_COOLING_OFFSET(10027, -50, 50),
    MIXER3_HEATING_MODE(10030, 0, 3),
    MIXER3_HEATING_SETPOINT(10031, 200, 650),
    MIXER3_HEATING_OFFSET(10032, -50, 50),
    MIXER3_HEATING_LEVEL(10033, 0, 3),
    MIXER3_COOLING_MODE(10035, 0, 2),
    MIXER3_COOLING_SETPOINT(10036, 50, 250),
    MIXER3_COOLING_OFFSET(10037, -50, 50),
    POWER_LIMIT_MODE(10040, 0, 2),
    POWER_LIMIT_TENTHS_KW(10041, 0, 300),
    HEATING_BLOCK(10050, 0, 1),
    HOT_WATER_BLOCK(10051, 0, 1),
    COOLING_BLOCK(10052, 0, 1),
    POOL_BLOCK(10053, 0, 1),
    ROOM_SETPOINT(10060, 150, 300),
    OVERALL_HEATING_MODE(10065, 0, 3),
    OVERALL_HEATING_OFFSET(10066, -50, 50),
    OVERALL_HEATING_LEVEL(10067, 0, 3),
    CIRCULATION_START(10070, 0, 1),
    EXTRA_HOT_WATER(10071, 0, 1);

    private final int address;
    private final int minimum;
    private final int maximum;

    ShiWriteRegister(int address, int minimum, int maximum) {
        this.address = address;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public int address() {
        return this.address;
    }

    public int minimum() {
        return this.minimum;
    }

    public int maximum() {
        return this.maximum;
    }

    public int validate(int value) {
        if (value < this.minimum || value > this.maximum) {
            throw new IllegalArgumentException(this.name() + " must be between "
                    + this.minimum + " and " + this.maximum + ", got " + value);
        }
        return value;
    }

    public int toModbusWord(int value) {
        return this.validate(value) & 0xffff;
    }
}
