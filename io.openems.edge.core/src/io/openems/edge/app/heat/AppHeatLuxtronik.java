package io.openems.edge.app.heat;

import static io.openems.edge.app.common.props.CommonProps.alias;
import static io.openems.edge.core.appmanager.formly.enums.InputType.NUMBER;

import java.util.Map;
import java.util.function.Function;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.JsonElement;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.function.ThrowingTriFunction;
import io.openems.common.session.Language;
import io.openems.common.session.Role;
import io.openems.common.types.EdgeConfig;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.app.common.props.CommonProps;
import io.openems.edge.app.common.props.CommunicationProps;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.host.Host;
import io.openems.edge.core.appmanager.AbstractOpenemsApp;
import io.openems.edge.core.appmanager.AbstractOpenemsAppWithProps;
import io.openems.edge.core.appmanager.AppConfiguration;
import io.openems.edge.core.appmanager.AppDef;
import io.openems.edge.core.appmanager.ComponentUtil;
import io.openems.edge.core.appmanager.ConfigurationTarget;
import io.openems.edge.core.appmanager.HostSupplier;
import io.openems.edge.core.appmanager.Nameable;
import io.openems.edge.core.appmanager.OpenemsApp;
import io.openems.edge.core.appmanager.OpenemsAppCardinality;
import io.openems.edge.core.appmanager.OpenemsAppCategory;
import io.openems.edge.core.appmanager.OpenemsAppPermissions;
import io.openems.edge.core.appmanager.TranslationUtil;
import io.openems.edge.core.appmanager.Type;
import io.openems.edge.core.appmanager.Type.Parameter.BundleParameter;
import io.openems.edge.core.appmanager.dependency.Tasks;
import io.openems.edge.core.appmanager.dependency.aggregatetask.SchedulerByCentralOrderConfiguration.SchedulerComponent;
import io.openems.edge.core.appmanager.formly.JsonFormlyUtil;

/**
 * Standard App Manager integration for Luxtronik SHI heat pumps.
 *
 * <p>
 * Creates a Modbus bridge, the Luxtronik device and a standard OpenEMS
 * heating controller. The controller defaults to monitor-only and dry-run.
 */
@Component(name = "App.Heat.Luxtronik")
public class AppHeatLuxtronik
        extends AbstractOpenemsAppWithProps<AppHeatLuxtronik, AppHeatLuxtronik.Property, BundleParameter>
        implements OpenemsApp, HostSupplier {

    public enum Property implements Type<Property, AppHeatLuxtronik, BundleParameter>, Nameable {
        HEAT_ID(AppDef.componentId("heatLuxtronik0")),
        MODBUS_ID(AppDef.componentId("modbusLuxtronik0")),
        CONTROL_ID(AppDef.componentId("ctrlHeatLuxtronik0")),
        ALIAS(alias()),
        IP(AppDef.copyOfGeneric(CommunicationProps.excludingIp()) //
                .setRequired(true)),
        READ_ONLY(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(true) //
                .setTranslatedLabelWithAppPrefix(".readOnly.label") //
                .setTranslatedDescriptionWithAppPrefix(".readOnly.description") //
                .setField(JsonFormlyUtil::buildCheckboxFromNameable) //
                .setRequired(true))),
        CONTROL_MODE(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue("MONITOR") //
                .setTranslatedLabelWithAppPrefix(".controlMode.label") //
                .setTranslatedDescriptionWithAppPrefix(".controlMode.description") //
                .setField(JsonFormlyUtil::buildSelectFromNameable, (app, property, l, parameter, field) -> //
                        field.setOptions(java.util.List.of("MONITOR", "MANUAL", "PRICE", //
                                "PV_SURPLUS", "PRICE_OR_PV"))) //
                .setRequired(true))), //
        CONTROL_DRY_RUN(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(true) //
                .setTranslatedLabelWithAppPrefix(".controlDryRun.label") //
                .setTranslatedDescriptionWithAppPrefix(".controlDryRun.description") //
                .setField(JsonFormlyUtil::buildCheckboxFromNameable) //
                .setRequired(true))), //
        MANUAL_OFFSET(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(0) //
                .setTranslatedLabelWithAppPrefix(".manualOffset.label") //
                .setField(JsonFormlyUtil::buildInputFromNameable, (app, property, l, parameter, field) -> //
                        field.setInputType(NUMBER).setMin(-30).setMax(30)) //
                .setRequired(true))), //
        BOOST_OFFSET(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(10) //
                .setTranslatedLabelWithAppPrefix(".boostOffset.label") //
                .setField(JsonFormlyUtil::buildInputFromNameable, (app, property, l, parameter, field) -> //
                        field.setInputType(NUMBER).setMin(0).setMax(30)) //
                .setRequired(true))), //
        REDUCE_OFFSET(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(-10) //
                .setTranslatedLabelWithAppPrefix(".reduceOffset.label") //
                .setField(JsonFormlyUtil::buildInputFromNameable, (app, property, l, parameter, field) -> //
                        field.setInputType(NUMBER).setMin(-30).setMax(0)) //
                .setRequired(true))), //
        CHEAP_PRICE(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(200) //
                .setTranslatedLabelWithAppPrefix(".cheapPrice.label") //
                .setField(JsonFormlyUtil::buildInputFromNameable, (app, property, l, parameter, field) -> //
                        field.setInputType(NUMBER).setMin(0).setMax(1000)) //
                .setRequired(true))), //
        EXPENSIVE_PRICE(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(350) //
                .setTranslatedLabelWithAppPrefix(".expensivePrice.label") //
                .setField(JsonFormlyUtil::buildInputFromNameable, (app, property, l, parameter, field) -> //
                        field.setInputType(NUMBER).setMin(1).setMax(1000)) //
                .setRequired(true))), //
        MIN_GRID_EXPORT(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(1000) //
                .setTranslatedLabelWithAppPrefix(".minGridExport.label") //
                .setField(JsonFormlyUtil::buildInputFromNameable, (app, property, l, parameter, field) -> //
                        field.setInputType(NUMBER).setMin(0).setMax(30000)) //
                .setRequired(true))), //
        EXTENDED_REGISTERS(AppDef.copyOfGeneric(CommonProps.defaultDef(), appDef -> appDef //
                .setDefaultValue(false) //
                .setTranslatedLabelWithAppPrefix(".extendedRegisters.label") //
                .setTranslatedDescriptionWithAppPrefix(".extendedRegisters.description") //
                .setField(JsonFormlyUtil::buildCheckboxFromNameable) //
                .setRequired(true)));

        private final AppDef<? super AppHeatLuxtronik, ? super Property, ? super BundleParameter> def;

        Property(AppDef<? super AppHeatLuxtronik, ? super Property, ? super BundleParameter> def) {
            this.def = def;
        }

        @Override
        public Type<Property, AppHeatLuxtronik, BundleParameter> self() {
            return this;
        }

        @Override
        public AppDef<? super AppHeatLuxtronik, ? super Property, ? super BundleParameter> def() {
            return this.def;
        }

        @Override
        public Function<GetParameterValues<AppHeatLuxtronik>, BundleParameter> getParamter() {
            return Parameter.functionOf(AbstractOpenemsApp::getTranslationBundle);
        }
    }

    private final Host host;

    @Activate
    public AppHeatLuxtronik(//
            @Reference ComponentManager componentManager, //
            ComponentContext componentContext, //
            @Reference ConfigurationAdmin cm, //
            @Reference ComponentUtil componentUtil, //
            @Reference Host host //
    ) {
        super(componentManager, componentContext, cm, componentUtil);
        this.host = host;
    }

    @Override
    protected ThrowingTriFunction<ConfigurationTarget, Map<Property, JsonElement>, Language, AppConfiguration, OpenemsNamedException> appPropertyConfigurationFactory() {
        return (t, p, l) -> {
            var bundle = AbstractOpenemsApp.getTranslationBundle(l);
            var heatId = this.getId(t, p, Property.HEAT_ID);
            var modbusId = this.getId(t, p, Property.MODBUS_ID);
            var controlId = this.getId(t, p, Property.CONTROL_ID);
            var alias = this.getString(p, l, Property.ALIAS);
            var ip = this.getString(p, l, Property.IP);
            var readOnly = this.getBoolean(p, Property.READ_ONLY);
            var extended = this.getBoolean(p, Property.EXTENDED_REGISTERS);
            var controlMode = this.getString(p, l, Property.CONTROL_MODE);
            var dryRun = this.getBoolean(p, Property.CONTROL_DRY_RUN);
            var manualOffset = this.getInt(p, Property.MANUAL_OFFSET);
            var boostOffset = this.getInt(p, Property.BOOST_OFFSET);
            var reduceOffset = this.getInt(p, Property.REDUCE_OFFSET);
            var cheapPrice = this.getInt(p, Property.CHEAP_PRICE);
            var expensivePrice = this.getInt(p, Property.EXPENSIVE_PRICE);
            var minGridExport = this.getInt(p, Property.MIN_GRID_EXPORT);


            var components = java.util.List.of(//
                    new EdgeConfig.Component(heatId, alias, "Heat.Luxtronik.SHI", JsonUtils.buildJsonObject() //
                            .addProperty("readOnly", readOnly) //
                            .addProperty("enableExtendedRegisters", extended) //
                            .addProperty("modbus.id", modbusId) //
                            .build()), //
                    new EdgeConfig.Component(modbusId, //
                            TranslationUtil.getTranslation(bundle, "App.Heat.Luxtronik.modbus.alias"), //
                            "Bridge.Modbus.Tcp", JsonUtils.buildJsonObject() //
                                    .addProperty("ip", ip) //
                                    .onlyIf(t == ConfigurationTarget.ADD, b -> b.addProperty("port", 502)) //
                                    .build()), //
                    new EdgeConfig.Component(controlId, "Luxtronik heating control", //
                            "Controller.Heat.Luxtronik.SHI", JsonUtils.buildJsonObject() //
                                    .addProperty("heat.id", heatId) //
                                    .addProperty("mode", controlMode) //
                                    .addProperty("dryRun", dryRun) //
                                    .addProperty("manualOffsetTenthsK", manualOffset) //
                                    .addProperty("boostOffsetTenthsK", boostOffset) //
                                    .addProperty("reduceOffsetTenthsK", reduceOffset) //
                                    .addProperty("cheapPriceEurMwh", cheapPrice) //
                                    .addProperty("expensivePriceEurMwh", expensivePrice) //
                                    .addProperty("minGridExportWatts", minGridExport) //
                                    .build()));

            return AppConfiguration.create() //
                    .addTask(Tasks.component(components)) //
                    .addTask(Tasks.schedulerByCentralOrder(//
                            new SchedulerComponent(controlId, "Controller.Heat.Luxtronik.SHI", this.getAppId()))) //
                    .build();
        };
    }

    @Override
    public OpenemsAppPermissions getAppPermissions() {
        return OpenemsAppPermissions.create() //
                .setCanInstall(Role.ADMIN) //
                .setCanSee(Role.ADMIN) //
                .setCanDelete(Role.ADMIN) //
                .build();
    }

    @Override
    public OpenemsAppCategory[] getCategories() {
        return new OpenemsAppCategory[] { OpenemsAppCategory.HEAT };
    }

    @Override
    public OpenemsAppCardinality getCardinality() {
        return OpenemsAppCardinality.MULTIPLE;
    }

    @Override
    protected AppHeatLuxtronik getApp() {
        return this;
    }

    @Override
    protected Property[] propertyValues() {
        return Property.values();
    }

    @Override
    public Host getHost() {
        return this.host;
    }
}
