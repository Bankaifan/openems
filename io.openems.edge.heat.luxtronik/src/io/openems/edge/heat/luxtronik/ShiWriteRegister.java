package io.openems.edge.heat.luxtronik;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;

/**
 * Writable Luxtronik SHI holding registers, using zero-based Modbus addresses.
 * Values are raw register values (temperatures in tenths of degrees/Kelvin).
 * Source: ait-deutschland SHI operating manual 83026903aUK.
 */
public enum ShiWriteRegister implements io.openems.edge.common.channel.ChannelId {
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

    /**
     * True for optional controls requiring firmware-specific support.
     *
     * @return true for advanced controls
     */
    public boolean requiresExtendedSupport() {
        return switch (this) {
        case HEATING_LEVEL, HOT_WATER_LEVEL, MIXER1_HEATING_LEVEL, MIXER2_HEATING_LEVEL,
                MIXER3_HEATING_LEVEL, OVERALL_HEATING_MODE, OVERALL_HEATING_OFFSET,
                OVERALL_HEATING_LEVEL, CIRCULATION_START, EXTRA_HOT_WATER -> true;
        default -> false;
        };
    }

    /** Gets the prerequisite mode register.
     *
     * @return required mode register or null */
    public ShiWriteRegister requiredModeRegister() {
        return switch (this) {
        case HEATING_SETPOINT, HEATING_OFFSET, HEATING_LEVEL -> HEATING_MODE;
        case HOT_WATER_SETPOINT, HOT_WATER_OFFSET, HOT_WATER_LEVEL -> HOT_WATER_MODE;
        case MIXER1_HEATING_SETPOINT, MIXER1_HEATING_OFFSET, MIXER1_HEATING_LEVEL -> MIXER1_HEATING_MODE;
        case MIXER1_COOLING_SETPOINT, MIXER1_COOLING_OFFSET -> MIXER1_COOLING_MODE;
        case MIXER2_HEATING_SETPOINT, MIXER2_HEATING_OFFSET, MIXER2_HEATING_LEVEL -> MIXER2_HEATING_MODE;
        case MIXER2_COOLING_SETPOINT, MIXER2_COOLING_OFFSET -> MIXER2_COOLING_MODE;
        case MIXER3_HEATING_SETPOINT, MIXER3_HEATING_OFFSET, MIXER3_HEATING_LEVEL -> MIXER3_HEATING_MODE;
        case MIXER3_COOLING_SETPOINT, MIXER3_COOLING_OFFSET -> MIXER3_COOLING_MODE;
        case POWER_LIMIT_TENTHS_KW -> POWER_LIMIT_MODE;
        case OVERALL_HEATING_OFFSET, OVERALL_HEATING_LEVEL -> OVERALL_HEATING_MODE;
        default -> null;
        };
    }

    /** Gets the required mode (or -2 for both power-limiting modes).
     *
     * @return expected mode value or -1 when not applicable */
    public int requiredModeValue() {
        return switch (this) {
        case HEATING_SETPOINT, HOT_WATER_SETPOINT, MIXER1_HEATING_SETPOINT, MIXER1_COOLING_SETPOINT,
                MIXER2_HEATING_SETPOINT, MIXER2_COOLING_SETPOINT, MIXER3_HEATING_SETPOINT,
                MIXER3_COOLING_SETPOINT -> 1;
        case HEATING_OFFSET, HOT_WATER_OFFSET, MIXER1_HEATING_OFFSET, MIXER1_COOLING_OFFSET,
                MIXER2_HEATING_OFFSET, MIXER2_COOLING_OFFSET, MIXER3_HEATING_OFFSET,
                MIXER3_COOLING_OFFSET, OVERALL_HEATING_OFFSET -> 2;
        case HEATING_LEVEL, HOT_WATER_LEVEL, MIXER1_HEATING_LEVEL, MIXER2_HEATING_LEVEL,
                MIXER3_HEATING_LEVEL, OVERALL_HEATING_LEVEL -> 3;
        case POWER_LIMIT_TENTHS_KW -> -2; // either 1 (soft) or 2 (hard)
        default -> -1;
        };
    }

    /** Checks the signedness of the SHI register.
     *
     * @return true for signed INT16 registers */
    public boolean isSigned() {
        return this.minimum < 0 || this == HEATING_OFFSET || this == HOT_WATER_OFFSET;
    }

    private final Doc doc;
    private final int address;
    private final int minimum;
    private final int maximum;

    ShiWriteRegister(int address, int minimum, int maximum) {
        this.address = address;
        var channelDoc = Doc.of(OpenemsType.INTEGER).accessMode(AccessMode.READ_WRITE);
        if (minimum < 0 || this.name().contains("SETPOINT")) {
            channelDoc.unit(Unit.DEZIDEGREE_CELSIUS);
        }
        this.doc = channelDoc;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public Doc doc() {
        return this.doc;
    }

    /** Gets the zero-based Modbus holding register address.
     *
     * @return register address */
    public int address() {
        return this.address;
    }

    /** Gets the permitted minimum (raw units).
     *
     * @return minimum value */
    public int minimum() {
        return this.minimum;
    }

    /** Gets the permitted maximum (raw units).
     *
     * @return maximum value */
    public int maximum() {
        return this.maximum;
    }

    /** Validates a raw register value without silently clamping.
     *
     * @param value requested value
     * @return value if permitted */
    public int validate(int value) {
        if (this == OVERALL_HEATING_MODE && value == 1) {
            throw new IllegalArgumentException("Overall heating mode 1 is reserved");
        }
        if (value < this.minimum || value > this.maximum) {
            throw new IllegalArgumentException(this.name() + " must be between "
                    + this.minimum + " and " + this.maximum + ", got " + value);
        }
        return value;
    }

    /** Encodes a validated signed or unsigned value as a Modbus word.
     *
     * @param value requested value
     * @return raw unsigned 16-bit word */
    public int toModbusWord(int value) {
        return this.validate(value) & 0xffff;
    }
}
