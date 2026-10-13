package io.openems.edge.heat.luxtronik;

import static io.openems.edge.common.channel.ChannelUtils.setValue;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Duration;
import java.time.Instant;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.common.channel.BooleanReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Decision;
import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Mode;
import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Settings;
import io.openems.edge.timeofusetariff.api.TariffManager;

/**
 * Native OpenEMS controller for Luxtronik SHI heating-curve offsets.
 *
 * <p>
 * Uses existing OpenEMS electricity prices and metering; never claims the heat
 * pump can track a continuous electrical power setpoint. It is a normal
 * scheduled OpenEMS Controller, not a second automation platform.
 */
@Designate(ocd = LuxtronikControllerConfig.class, factory = true)
@Component(//
        name = "Controller.Heat.Luxtronik.SHI", //
        immediate = true, //
        configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@GenerateTargetsFromReferences("Heat")
public class LuxtronikControllerImpl extends AbstractOpenemsComponent
        implements LuxtronikController, Controller, OpenemsComponent {

    @Reference
    private ComponentManager componentManager;

    @Reference
    private Sum sum;

    @Reference(policy = DYNAMIC, cardinality = OPTIONAL)
    private volatile TariffManager tariffManager;

    @Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
            target = "(&(id=${config.heat_id})(enabled=true))")
    private HeatLuxtronik heat;

    private volatile LuxtronikControllerConfig config;
    private Instant lastOffsetWrite;
    private boolean ownsControl;

    public LuxtronikControllerImpl() {
        super(//
                OpenemsComponent.ChannelId.values(), //
                Controller.ChannelId.values(), //
                LuxtronikController.ChannelId.values());
    }

    @Activate
    private void activate(ComponentContext context, LuxtronikControllerConfig config) throws OpenemsException {
        this.applyConfig(config);
        super.activate(context, config.id(), config.alias(), config.enabled());
    }

    @Modified
    private void modified(ComponentContext context, LuxtronikControllerConfig config) throws OpenemsException {
        this.applyConfig(config);
        super.modified(context, config.id(), config.alias(), config.enabled());
    }

    private void applyConfig(LuxtronikControllerConfig config) throws OpenemsException {
        if (config.minChangeMinutes() < 1 || config.minChangeMinutes() > 120) {
            throw new OpenemsException("Luxtronik controller change interval must be 1..120 min");
        }
        try {
            this.settings(config);
        } catch (IllegalArgumentException e) {
            throw new OpenemsException("Luxtronik controller: " + e.getMessage());
        }
        this.config = config;
    }

    private Settings settings(LuxtronikControllerConfig config) {
        return new Settings(config.mode(), config.manualOffsetTenthsK(),
                config.boostOffsetTenthsK(), config.reduceOffsetTenthsK(),
                config.cheapPriceEurMwh(), config.expensivePriceEurMwh(),
                config.minGridExportWatts());
    }

    @Override
    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public void run() throws OpenemsNamedException {
        var cfg = this.config;
        if (cfg == null) {
            return;
        }
        var clock = this.componentManager.getClock();
        var now = clock.instant();
        Double price = null;
        if (this.tariffManager != null) {
            var prices = this.tariffManager.getGridBuyDayAheadPrices();
            if (prices != null) {
                price = prices.getAt(now);
            }
        }
        Integer gridWatts = ((IntegerReadChannel) this.sum.channel(Sum.ChannelId.GRID_ACTIVE_POWER))
                .value().get();

        var decision = LuxtronikControlPolicy.decide(this.settings(cfg), price, gridWatts);
        var currentMode = ((IntegerReadChannel) this.heat.channel(ShiWriteRegister.HEATING_MODE))
                .value().get();
        var currentOffset = ((IntegerReadChannel) this.heat.channel(ShiWriteRegister.HEATING_OFFSET))
                .value().get();
        boolean heatingSupported = Boolean.TRUE.equals(
                ((BooleanReadChannel) this.heat.channel(HeatLuxtronik.ChannelId.HEATING_ENABLED))
                        .value().get());

        setValue(this, LuxtronikController.ChannelId.DESIRED_OFFSET,
                decision.usable() ? decision.offsetTenthsKelvin() : null);
        setValue(this, LuxtronikController.ChannelId.ACTUAL_OFFSET,
                currentOffset == null || currentOffset == 32767 ? null : currentOffset);
        setValue(this, LuxtronikController.ChannelId.PRICE_EUR_MWH,
                price == null || !Double.isFinite(price) ? null : (int) Math.round(price));
        setValue(this, LuxtronikController.ChannelId.GRID_ACTIVE_POWER, gridWatts);
        setValue(this, LuxtronikController.ChannelId.DRY_RUN, cfg.dryRun());
        setValue(this, LuxtronikController.ChannelId.CONTROL_ACTIVE, this.ownsControl);

        if (cfg.mode() == Mode.MONITOR) {
            if (this.ownsControl && !cfg.dryRun() && Integer.valueOf(2).equals(currentMode)) {
                this.setMode(0);
                this.ownsControl = false;
                setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                        "Released SHI heating mode to device control");
            } else {
                setValue(this, LuxtronikController.ChannelId.DECISION_REASON, "Monitor only: no writes");
            }
            return;
        }
        if (!heatingSupported) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "SHI heating capability not available: no writes");
            return;
        }
        if (!decision.usable()) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    decision.reason() + ": no writes");
            return;
        }
        if (currentMode == null || currentMode == 32767 || currentOffset == null
                || currentOffset == 32767) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "SHI mode/offset readback unavailable: no writes");
            return;
        }
        if (cfg.dryRun()) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "DRY RUN: " + decision.reason() + ", would request "
                            + decision.offsetTenthsKelvin() + " dK");
            return;
        }
        // Do not seize the heating curve merely to apply a neutral offset.
        if (currentMode == 0 && decision.offsetTenthsKelvin() == 0 && !this.ownsControl) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "Native SHI control retained (neutral offset)");
            return;
        }
        // Refuse to override a different SHI master already using mode 2.
        if (currentMode == 2 && !this.ownsControl) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "SHI offset mode already active: external master suspected");
            return;
        }
        if (currentMode != 2) {
            this.setMode(2);
            this.ownsControl = true;
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "Requested SHI mode 2; awaiting Modbus readback");
            return;
        }
        if (currentOffset == decision.offsetTenthsKelvin()) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "Current offset matches requested value");
            return;
        }
        if (this.lastOffsetWrite != null && now.isBefore(this.lastOffsetWrite
                .plus(Duration.ofMinutes(cfg.minChangeMinutes())))) {
            setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                    "Waiting for minimum heating adjustment interval");
            return;
        }
        IntegerWriteChannel offset = this.heat.channel(ShiWriteRegister.HEATING_OFFSET);
        offset.setNextWriteValue(decision.offsetTenthsKelvin());
        this.lastOffsetWrite = now;
        setValue(this, LuxtronikController.ChannelId.DECISION_REASON,
                "Requested " + decision.offsetTenthsKelvin() + " dK: " + decision.reason());
    }

    private void setMode(int mode) throws OpenemsNamedException {
        IntegerWriteChannel channel = this.heat.channel(ShiWriteRegister.HEATING_MODE);
        channel.setNextWriteValue(mode);
    }
}
