package io.openems.edge.timeofusetariff.octopus.dynamic.gridbuy;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Time-Of-Use Tariff Octopus Dynamic Grid-Buy",
		description = "Account-specific Octopus Energy dynamic electricity prices")
@interface Config {
	@AttributeDefinition(name = "Component-ID")
	String id() default "timeofusetariff0";

	@AttributeDefinition(name = "Alias")
	String alias() default "";

	@AttributeDefinition(name = "Enabled")
	boolean enabled() default true;

	@AttributeDefinition(name = "Octopus account number",
			description = "Account number of the electricity contract, e.g. A-XXXXXXXX")
	String accountNumber();

	@AttributeDefinition(name = "Octopus login email")
	String email();

	@AttributeDefinition(name = "Octopus login password", type = AttributeType.PASSWORD)
	String password();

	@AttributeDefinition(name = "Refresh interval in minutes",
			description = "Octopus publishes the following day in the afternoon")
	int refreshMinutes() default 30;

	String webconsole_configurationFactory_nameHint() default "Time-Of-Use Tariff Octopus Dynamic Grid-Buy [{id}]";
}
