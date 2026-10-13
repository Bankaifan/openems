package io.openems.edge.heat.luxtronik;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.MULTIPLY;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;

/**
 * Read-only SHI driver for Luxtronik 3.9x. Uses function code 04 exclusively.
 * Modbus input register addresses are zero-based.
 */
@Designate(ocd = Config.class, factory = true)
@Component(name = "Heat.Luxtronik.SHI", immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@GenerateTargetsFromReferences("Modbus")
public class HeatLuxtronikImpl extends AbstractOpenemsModbusComponent
        implements HeatLuxtronik, ModbusComponent, OpenemsComponent, ElectricityMeter {

    @Override
    @Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY,
            target = "(&(id=${config.modbus_id})(enabled=true))")
    protected void setModbus(BridgeModbus modbus) {
        super.setModbus(modbus);
    }

    public HeatLuxtronikImpl() {
        super(OpenemsComponent.ChannelId.values(),
                ModbusComponent.ChannelId.values(), ElectricityMeter.ChannelId.values(),
                HeatLuxtronik.ChannelId.values());
    }

    @Activate
    private void activate(ComponentContext context, Config config) throws OpenemsException {
        super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId());
    }

    @Override
    @Deactivate
    protected void deactivate() {
        super.deactivate();
    }

    @Override
    public MeterType getMeterType() {
        return MeterType.CONSUMPTION_METERED;
    }

    @Override
    protected ModbusProtocol defineModbusProtocol() {
        return new ModbusProtocol(this,
                new FC4ReadInputRegistersTask(10002, Priority.LOW,
                        m(HeatLuxtronik.ChannelId.OPERATING_MODE, new UnsignedWordElement(10002))),
                new FC4ReadInputRegistersTask(10100, Priority.LOW,
                        m(HeatLuxtronik.ChannelId.RETURN_TARGET_TENTHS_C, new SignedWordElement(10100)),
                        m(HeatLuxtronik.ChannelId.RETURN_ACTUAL_TENTHS_C, new SignedWordElement(10101)),
                        new DummyRegisterElement(10102, 10104),
                        m(HeatLuxtronik.ChannelId.FLOW_ACTUAL_TENTHS_C, new SignedWordElement(10105)),
                        new DummyRegisterElement(10106, 10107),
                        m(HeatLuxtronik.ChannelId.OUTSIDE_TENTHS_C, new SignedWordElement(10108))),
                new FC4ReadInputRegistersTask(10120, Priority.LOW,
                        m(HeatLuxtronik.ChannelId.HOT_WATER_ACTUAL_TENTHS_C, new SignedWordElement(10120)),
                        m(HeatLuxtronik.ChannelId.HOT_WATER_TARGET_TENTHS_C, new SignedWordElement(10121))),
                new FC4ReadInputRegistersTask(10201, Priority.LOW,
                        m(HeatLuxtronik.ChannelId.ERROR_NUMBER, new UnsignedWordElement(10201))),
                new FC4ReadInputRegistersTask(10300, Priority.LOW,
                        m(HeatLuxtronik.ChannelId.COMPRESSOR_THERMAL_W, new UnsignedWordElement(10300), MULTIPLY(100)),
                        m(ElectricityMeter.ChannelId.ACTIVE_POWER, new UnsignedWordElement(10301), MULTIPLY(100)))
        );
    }

    @Override
    public String debugLog() {
        return "WW:" + this.channel(HeatLuxtronik.ChannelId.HOT_WATER_ACTUAL_TENTHS_C);
    }
}
