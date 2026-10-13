package io.openems.edge.heat.luxtronik;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Mode;

/**
 * Controller parameters are edited in the standard OpenEMS component editor.
 * The initial configuration cannot write to the heat pump.
 */
@ObjectClassDefinition(//
        name = "Luxtronik SHI Heating Controller", //
        description = "Manual, price- and PV-based SHI heating-curve offsets. Defaults to monitor-only.")
@interface LuxtronikControllerConfig {

    @AttributeDefinition(name = "Component ID")
    String id() default "ctrlHeatLuxtronik0";

    @AttributeDefinition(name = "Alias")
    String alias() default "Luxtronik heating control";

    @AttributeDefinition(name = "Enabled")
    boolean enabled() default true;

    @AttributeDefinition(name = "Luxtronik heat pump component ID")
    String heat_id() default "heatLuxtronik0";

    @AttributeDefinition(name = "Control mode",
            description = "MONITOR never writes; MANUAL, PRICE, PV_SURPLUS or PRICE_OR_PV require dry-run to be disabled")
    Mode mode() default Mode.MONITOR;

    @AttributeDefinition(name = "Dry run (no device writes)",
            description = "Keep enabled while evaluating the proposed offsets and the hardware compatibility")
    boolean dryRun() default true;

    @AttributeDefinition(name = "Manual offset [0.1 K]", description = "-30..30, i.e. -3.0..+3.0 Kelvin")
    int manualOffsetTenthsK() default 0;

    @AttributeDefinition(name = "Cheap/surplus offset [0.1 K]", description = "0..30")
    int boostOffsetTenthsK() default 10;

    @AttributeDefinition(name = "Expensive offset [0.1 K]", description = "-30..0")
    int reduceOffsetTenthsK() default -10;

    @AttributeDefinition(name = "Cheap electricity price [EUR/MWh]", description = "200 means 20 ct/kWh")
    int cheapPriceEurMwh() default 200;

    @AttributeDefinition(name = "Expensive electricity price [EUR/MWh]", description = "350 means 35 ct/kWh")
    int expensivePriceEurMwh() default 350;

    @AttributeDefinition(name = "Minimum grid export for PV boosting [W]",
            description = "Requires a configured OpenEMS grid meter; grid export is negative")
    int minGridExportWatts() default 1000;

    @AttributeDefinition(name = "Minimum time between changes [minutes]", description = "Avoid short cycling")
    int minChangeMinutes() default 15;

    String webconsole_configurationFactory_nameHint() default "Luxtronik controller [{id}]";
}
