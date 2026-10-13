package io.openems.edge.heat.luxtronik;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

/**
 * Readable diagnostics for the standard OpenEMS Luxtronik heating controller.
 */
public interface LuxtronikController extends OpenemsComponent {

    enum ChannelId implements io.openems.edge.common.channel.ChannelId {
        DESIRED_OFFSET(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        ACTUAL_OFFSET(Doc.of(OpenemsType.INTEGER).unit(Unit.DEZIDEGREE_CELSIUS)),
        PRICE_EUR_MWH(Doc.of(OpenemsType.INTEGER).unit(Unit.MONEY_PER_MEGAWATT_HOUR)),
        GRID_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER).unit(Unit.WATT)),
        DRY_RUN(Doc.of(OpenemsType.BOOLEAN)),
        CONTROL_ACTIVE(Doc.of(OpenemsType.BOOLEAN)),
        DECISION_REASON(Doc.of(OpenemsType.STRING));

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
