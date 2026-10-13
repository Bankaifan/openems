package io.openems.edge.heat.luxtronik;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Alpha Innotec Luxtronik SHI (read only)",
        description = "Read-only Modbus TCP heat pump monitoring. No writes or remote control.")
@interface Config {
    @AttributeDefinition(name = "Component ID")
    String id() default "heatLuxtronik0";

    @AttributeDefinition(name = "Alias")
    String alias() default "Alpha Innotec Luxtronik";

    @AttributeDefinition(name = "Enabled")
    boolean enabled() default true;

    @AttributeDefinition(name = "Modbus bridge ID")
    String modbus_id() default "modbusLuxtronik0";

    @AttributeDefinition(name = "Modbus unit ID")
    int modbusUnitId() default 1;
}
