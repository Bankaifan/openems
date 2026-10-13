package io.openems.edge.heat.luxtronik;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Alpha Innotec Luxtronik SHI",
        description = "Modbus TCP heat pump monitoring and optional SHI holding-register control.")
@interface Config {
    @AttributeDefinition(name = "Component ID")
    String id() default "heatLuxtronik0";

    @AttributeDefinition(name = "Alias")
    String alias() default "Alpha Innotec Luxtronik";

    @AttributeDefinition(name = "Enabled")
    boolean enabled() default true;

    @AttributeDefinition(name = "Modbus bridge ID")
    String modbus_id() default "modbusLuxtronik0";

    @AttributeDefinition(name = "Read Only", description = "Keep enabled for monitoring only. Disable only after checking SHI firmware and safety prerequisites.")
    boolean readOnly() default true;

    @AttributeDefinition(name = "Extended SHI registers", description = "Enable optional registers only after verifying firmware compatibility (e.g. Luxtronik 3.92).")
    boolean enableExtendedRegisters() default false;

    @AttributeDefinition(name = "Modbus unit ID")
    int modbusUnitId() default 1;
}
