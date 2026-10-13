package io.openems.edge.heat.luxtronik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.bridge.modbus.api.task.FC2ReadInputsTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;

class HeatLuxtronikImplTest {

	@Test
	void testReadOnlyNeverSchedulesWrites() {
		var component = new HeatLuxtronikImpl();
		var tasks = component.defineModbusProtocol().getTaskManager().getTasks();
		assertTrue(tasks.stream().noneMatch(FC6WriteRegisterTask.class::isInstance));
        assertTrue(tasks.stream().anyMatch(FC2ReadInputsTask.class::isInstance));
        assertTrue(tasks.stream().anyMatch(FC3ReadRegistersTask.class::isInstance));
		assertThrows(OpenemsException.class,
				() -> component.validateWrite(ShiWriteRegister.HEATING_MODE, 2));
	}

    @Test
    void rejectedValuesMustNeverRemainQueued() {
        var component = new HeatLuxtronikImpl();
        IntegerWriteChannel mode = component.channel(ShiWriteRegister.HEATING_MODE);
        assertThrows(OpenemsException.class, () -> mode.setNextWriteValue(2));
        assertTrue(mode.getNextWriteValue().isEmpty());
        assertThrows(OpenemsException.class, () -> mode.setNextWriteValue(32767));
        assertTrue(mode.getNextWriteValue().isEmpty());
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
