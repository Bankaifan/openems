package io.openems.edge.heat.luxtronik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;

class HeatLuxtronikImplTest {

	@Test
	void testReadOnlyNeverSchedulesWrites() {
		var component = new HeatLuxtronikImpl();
		var tasks = component.defineModbusProtocol().getTaskManager().getTasks();
		assertTrue(tasks.stream().noneMatch(FC6WriteRegisterTask.class::isInstance));
		assertThrows(OpenemsException.class,
				() -> component.validateWrite(ShiWriteRegister.HEATING_MODE, 2));
	}

	@Test
	void testWriteProtocolRequiresExplicitOptIn() throws Exception {
		var component = new HeatLuxtronikImpl();
		var readOnly = HeatLuxtronikImpl.class.getDeclaredField("readOnly");
		readOnly.setAccessible(true);
		readOnly.setBoolean(component, false);
		var tasks = component.defineModbusProtocol().getTaskManager().getTasks();
		long expected = Arrays.stream(ShiWriteRegister.values())
				.filter(register -> !register.requiresExtendedSupport()).count();
		assertEquals(expected, tasks.stream().filter(FC6WriteRegisterTask.class::isInstance).count());
		assertThrows(OpenemsException.class,
				() -> component.validateWrite(ShiWriteRegister.HEATING_MODE, 4));
		assertThrows(OpenemsException.class,
				() -> component.validateWrite(ShiWriteRegister.HEATING_OFFSET, 201));
		assertThrows(OpenemsException.class,
				() -> component.validateWrite(ShiWriteRegister.OVERALL_HEATING_MODE, 2));
	}

	@Test
	void testExtendedProtocolExplicitOptIn() throws Exception {
		var component = new HeatLuxtronikImpl();
		var readOnly = HeatLuxtronikImpl.class.getDeclaredField("readOnly");
		readOnly.setAccessible(true);
		readOnly.setBoolean(component, false);
		var extended = HeatLuxtronikImpl.class.getDeclaredField("enableExtendedRegisters");
		extended.setAccessible(true);
		extended.setBoolean(component, true);
		var tasks = component.defineModbusProtocol().getTaskManager().getTasks();
		assertEquals(ShiWriteRegister.values().length,
				tasks.stream().filter(FC6WriteRegisterTask.class::isInstance).count());
	}
}
