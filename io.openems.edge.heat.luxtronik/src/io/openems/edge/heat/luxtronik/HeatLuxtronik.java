package io.openems.edge.heat.luxtronik;

import io.openems.common.types.OpenemsType;
import io.openems.common.channel.Unit;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Luxtronik SHI input registers. Temperature channels are in 0.1 degrees C;
 * power channels in 0.1 kW. No holding registers or Modbus write tasks exist.
 */
public interface HeatLuxtronik extends OpenemsComponent {
    enum ChannelId implements io.openems.edge.common.channel.ChannelId {
        HEATPUMP_STATUS(Doc.of(OpenemsType.INTEGER)),
        OPERATING_MODE(Doc.of(OpenemsType.INTEGER)),
        RETURN_TARGET_TENTHS_C(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        RETURN_ACTUAL_TENTHS_C(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        FLOW_ACTUAL_TENTHS_C(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        OUTSIDE_TENTHS_C(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        HOT_WATER_ACTUAL_TENTHS_C(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        HOT_WATER_TARGET_TENTHS_C(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        COMPRESSOR_THERMAL_W(Doc.of(OpenemsType.INTEGER).unit(Unit.WATT)),
        COMPRESSOR_ELECTRIC_W(Doc.of(OpenemsType.INTEGER).unit(Unit.WATT)),
        ERROR_NUMBER(Doc.of(OpenemsType.INTEGER));



        private final Doc doc;

        ChannelId(Doc doc) {
            this.doc = doc;
        }

        @Override
        public Doc doc() {
            return this.doc;
        }
    }
}
