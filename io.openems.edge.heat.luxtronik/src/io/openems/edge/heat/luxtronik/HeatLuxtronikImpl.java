package io.openems.edge.heat.luxtronik;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.DIRECT_1_TO_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.MULTIPLY;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.util.ArrayList;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.CoilElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC2ReadInputsTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.BooleanReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.heat.api.Heat;
import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Alpha Innotec / Novelan Luxtronik Smart Home Interface (SHI) via Modbus TCP.
 *
 * <p>
 * Supports monitoring and explicitly enabled writes to documented holding
 * registers. No write is issued unless a controller writes to a channel.
 * Per-register checks reject illegal values and incompatible operating modes.
 * The SHI firmware itself resets remote settings after 15 minutes without a
 * master request. Smart Grid and SHI must not be active simultaneously.
 */
@Designate(ocd = Config.class, factory = true)
@Component(name = "Heat.Luxtronik.SHI", immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE)
@GenerateTargetsFromReferences("Modbus")
public class HeatLuxtronikImpl extends AbstractOpenemsModbusComponent
		implements HeatLuxtronik, Heat, ModbusComponent, OpenemsComponent, ElectricityMeter {

	private volatile boolean readOnly = true;
	private volatile boolean enableExtendedRegisters;

	@Override
	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY,
			target = "(&(id=${config.modbus_id})(enabled=true))")
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	/** Creates the heat pump driver with monitoring and writable SHI channels. */
	public HeatLuxtronikImpl() {
		super(OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				ElectricityMeter.ChannelId.values(),
				Heat.ChannelId.values(),
				HeatLuxtronik.ChannelId.values(),
				ShiWriteRegister.values());

		for (var register : ShiWriteRegister.values()) {
			IntegerWriteChannel channel = this.channel(register);
			channel.onSetNextWrite(value -> this.validateWrite(register, value));
		}
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.readOnly = config.readOnly();
		this.enableExtendedRegisters = config.enableExtendedRegisters();
		super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	/**
	 * Verifies that an SHI write is explicitly allowed and applicable.
	 *
	 * @param register register to be written
	 * @param value raw register value
	 * @throws OpenemsException when the request is unsafe
	 */
	public void validateWrite(ShiWriteRegister register, Integer value) throws OpenemsException {
		if (value == null) {
			return;
		}
		if (this.readOnly) {
			throw new OpenemsException("Luxtronik SHI is configured as read-only");
		}
		if (register.requiresExtendedSupport() && !this.enableExtendedRegisters) {
			throw new OpenemsException("Luxtronik SHI register " + register.name()
					+ " requires explicit extended-register opt-in");
		}
		try {
			register.validate(value);
		} catch (IllegalArgumentException e) {
			throw new OpenemsException("Luxtronik SHI: " + e.getMessage());
		}
		this.verifyCapability(register);
		var required = register.requiredModeRegister();
		if (required != null) {
			IntegerReadChannel modeChannel = this.channel(required);
			Integer mode = modeChannel.value().get();
			int expected = register.requiredModeValue();
			if (mode == null || mode == 32767 || (expected == -2 //
					? mode != 1 && mode != 2 //
					: mode != expected)) {
				throw new OpenemsException("Luxtronik SHI: set " + required.name()
						+ " to " + (expected == -2 ? "1 or 2" : expected)
						+ " and wait for Modbus read-back before writing " + register.name()
						+ " (actual mode: " + mode + ")");
			}
		}

		if (register == ShiWriteRegister.HOT_WATER_SETPOINT) {
			var lower = this.<Integer>channel(HeatLuxtronik.ChannelId.HOT_WATER_MIN_TENTHS_C).value().get();
			var upper = this.<Integer>channel(HeatLuxtronik.ChannelId.HOT_WATER_MAX_TENTHS_C).value().get();
			if (lower == null || upper == null || lower == 32767 || upper == 32767
					|| value < lower || value > upper) {
				throw new OpenemsException("Luxtronik SHI: DHW setpoint must respect the configured "
						+ "system temperature limits (" + lower + ".." + upper + " deci-C)");
			}
		}
	}

	private void verifyCapability(ShiWriteRegister register) throws OpenemsException {
		HeatLuxtronik.ChannelId capability = switch (register) {
		case HEATING_MODE, HEATING_SETPOINT, HEATING_OFFSET, HEATING_LEVEL, HEATING_BLOCK,
				OVERALL_HEATING_MODE, OVERALL_HEATING_OFFSET, OVERALL_HEATING_LEVEL ->
			HeatLuxtronik.ChannelId.HEATING_ENABLED;
		case HOT_WATER_MODE, HOT_WATER_SETPOINT, HOT_WATER_OFFSET, HOT_WATER_LEVEL,
				HOT_WATER_BLOCK, EXTRA_HOT_WATER -> HeatLuxtronik.ChannelId.HOT_WATER_ENABLED;
		case COOLING_BLOCK -> HeatLuxtronik.ChannelId.COOLING_ENABLED;
		case POOL_BLOCK -> HeatLuxtronik.ChannelId.POOL_ENABLED;
		case MIXER1_HEATING_MODE, MIXER1_HEATING_SETPOINT, MIXER1_HEATING_OFFSET,
				MIXER1_HEATING_LEVEL -> HeatLuxtronik.ChannelId.HEATING_MC1_ENABLED;
		case MIXER2_HEATING_MODE, MIXER2_HEATING_SETPOINT, MIXER2_HEATING_OFFSET,
				MIXER2_HEATING_LEVEL -> HeatLuxtronik.ChannelId.HEATING_MC2_ENABLED;
		case MIXER3_HEATING_MODE, MIXER3_HEATING_SETPOINT, MIXER3_HEATING_OFFSET,
				MIXER3_HEATING_LEVEL -> HeatLuxtronik.ChannelId.HEATING_MC3_ENABLED;
		case MIXER1_COOLING_MODE, MIXER1_COOLING_SETPOINT, MIXER1_COOLING_OFFSET ->
			HeatLuxtronik.ChannelId.COOLING_MC1_ENABLED;
		case MIXER2_COOLING_MODE, MIXER2_COOLING_SETPOINT, MIXER2_COOLING_OFFSET ->
			HeatLuxtronik.ChannelId.COOLING_MC2_ENABLED;
		case MIXER3_COOLING_MODE, MIXER3_COOLING_SETPOINT, MIXER3_COOLING_OFFSET ->
			HeatLuxtronik.ChannelId.COOLING_MC3_ENABLED;
		default -> null;
		};
		if (capability == null) {
			return;
		}
		BooleanReadChannel channel = this.channel(capability);
		if (!Boolean.TRUE.equals(channel.value().get())) {
			throw new OpenemsException("Luxtronik SHI: " + register.name()
					+ " is unavailable because capability " + capability.name() + " is not enabled");
		}
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var protocol = new ModbusProtocol(this,
				new FC4ReadInputRegistersTask(10000, Priority.LOW,
						m(HeatLuxtronik.ChannelId.HEAT_GENERATOR_STATE, new UnsignedWordElement(10000))),
				new FC4ReadInputRegistersTask(10002, Priority.LOW,
						m(HeatLuxtronik.ChannelId.OPERATING_MODE, new UnsignedWordElement(10002)),
						m(HeatLuxtronik.ChannelId.HEATING_STATUS, new UnsignedWordElement(10003)),
						m(HeatLuxtronik.ChannelId.HOT_WATER_STATUS, new UnsignedWordElement(10004))),
				new FC4ReadInputRegistersTask(10100, Priority.LOW,
						new ChannelMapper<>(new UnsignedWordElement(10100))
								.m(HeatLuxtronik.ChannelId.RETURN_ACTUAL_TENTHS_C, DIRECT_1_TO_1)
								.m(Heat.ChannelId.TEMPERATURE, DIRECT_1_TO_1).build(),
						new ChannelMapper<>(new UnsignedWordElement(10101))
								.m(HeatLuxtronik.ChannelId.RETURN_TARGET_TENTHS_C, DIRECT_1_TO_1)
								.m(Heat.ChannelId.TARGET_TEMPERATURE, DIRECT_1_TO_1).build(),
						new DummyRegisterElement(10102, 10104),
						m(HeatLuxtronik.ChannelId.FLOW_ACTUAL_TENTHS_C, new UnsignedWordElement(10105)),
						new DummyRegisterElement(10106, 10107),
						m(HeatLuxtronik.ChannelId.OUTSIDE_TENTHS_C, new SignedWordElement(10108))),
				new FC4ReadInputRegistersTask(10120, Priority.LOW,
						m(HeatLuxtronik.ChannelId.HOT_WATER_ACTUAL_TENTHS_C, new SignedWordElement(10120)),
						m(HeatLuxtronik.ChannelId.HOT_WATER_TARGET_TENTHS_C, new UnsignedWordElement(10121)),
						m(HeatLuxtronik.ChannelId.HOT_WATER_MIN_TENTHS_C, new SignedWordElement(10122)),
						m(HeatLuxtronik.ChannelId.HOT_WATER_MAX_TENTHS_C, new SignedWordElement(10123))),
				new FC4ReadInputRegistersTask(10201, Priority.LOW,
						m(HeatLuxtronik.ChannelId.ERROR_NUMBER, new UnsignedWordElement(10201))),
				new FC4ReadInputRegistersTask(10300, Priority.LOW,
						m(HeatLuxtronik.ChannelId.COMPRESSOR_THERMAL_W,
								new SignedWordElement(10300), MULTIPLY(100)),
						new ChannelMapper<>(new UnsignedWordElement(10301))
								.m(ElectricityMeter.ChannelId.ACTIVE_POWER, MULTIPLY(100))
								.m(HeatLuxtronik.ChannelId.SYSTEM_ELECTRIC_W, MULTIPLY(100))
								.m(HeatLuxtronik.ChannelId.COMPRESSOR_ELECTRIC_W, MULTIPLY(100)).build()));

		if (this.readOnly) {
			return protocol;
		}

		// The SHI capability flags are a prerequisite for safe active control.
		protocol.addTask(new FC2ReadInputsTask(10000, Priority.LOW,
				m(HeatLuxtronik.ChannelId.HEATING_ENABLED, new CoilElement(10000)),
				m(HeatLuxtronik.ChannelId.HOT_WATER_ENABLED, new CoilElement(10001)),
				m(HeatLuxtronik.ChannelId.COOLING_ENABLED, new CoilElement(10002)),
				m(HeatLuxtronik.ChannelId.POOL_ENABLED, new CoilElement(10003)),
				new CoilElement(10004),
				new CoilElement(10005),
				m(HeatLuxtronik.ChannelId.HEATING_MC1_ENABLED, new CoilElement(10006)),
				m(HeatLuxtronik.ChannelId.COOLING_MC1_ENABLED, new CoilElement(10007)),
				m(HeatLuxtronik.ChannelId.HEATING_MC2_ENABLED, new CoilElement(10008)),
				m(HeatLuxtronik.ChannelId.COOLING_MC2_ENABLED, new CoilElement(10009)),
				m(HeatLuxtronik.ChannelId.HEATING_MC3_ENABLED, new CoilElement(10010)),
				m(HeatLuxtronik.ChannelId.COOLING_MC3_ENABLED, new CoilElement(10011))));

		protocol.addTask(new FC4ReadInputRegistersTask(10400, Priority.LOW,
				m(HeatLuxtronik.ChannelId.FIRMWARE_PLATFORM, new UnsignedWordElement(10400)),
				m(HeatLuxtronik.ChannelId.FIRMWARE_REVISION_MAJOR, new UnsignedWordElement(10401)),
				m(HeatLuxtronik.ChannelId.FIRMWARE_REVISION_MINOR, new UnsignedWordElement(10402))));

		// FC3 groups contiguous holding registers for efficient read-back.
		var block = new ArrayList<ModbusElement>();
		int blockStart = -1;
		int lastAddress = -1;
		for (var register : ShiWriteRegister.values()) {
			if (register.requiresExtendedSupport() && !this.enableExtendedRegisters) {
				continue;
			}
			if (blockStart != -1 && register.address() != lastAddress + 1) {
				protocol.addTask(new FC3ReadRegistersTask(blockStart, Priority.LOW,
						block.toArray(new ModbusElement[0])));
				block.clear();
				blockStart = -1;
			}
			if (blockStart == -1) {
				blockStart = register.address();
			}
			if (register.isSigned()) {
				block.add(m(register, new SignedWordElement(register.address())));
				protocol.addTask(new FC6WriteRegisterTask(register.address(),
						m(register, new SignedWordElement(register.address()))));
			} else {
				block.add(m(register, new UnsignedWordElement(register.address())));
				protocol.addTask(new FC6WriteRegisterTask(register.address(),
						m(register, new UnsignedWordElement(register.address()))));
			}
			lastAddress = register.address();
		}
		if (!block.isEmpty()) {
			protocol.addTask(new FC3ReadRegistersTask(blockStart, Priority.LOW,
					block.toArray(new ModbusElement[0])));
		}
		return protocol;
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.CONSUMPTION_METERED;
	}

	@Override
	public String debugLog() {
		return "WW:" + this.channel(HeatLuxtronik.ChannelId.HOT_WATER_ACTUAL_TENTHS_C);
	}
}
