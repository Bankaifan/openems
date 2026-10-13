package io.openems.edge.app.heat;

import static io.openems.edge.common.test.DummyUser.DUMMY_ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import io.openems.common.session.Role;
import io.openems.common.types.EdgeConfig;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.core.appmanager.AppManagerTestBundle;
import io.openems.edge.core.appmanager.Apps;
import io.openems.edge.core.appmanager.jsonrpc.AddAppInstance;

class AppHeatLuxtronikTest {

    private static final String IP = "192.168.2.118";

    private AppManagerTestBundle appManagerTestBundle;
    private AppHeatLuxtronik app;

    @BeforeEach
    void beforeEach() throws Exception {
        this.appManagerTestBundle = new AppManagerTestBundle(null, null, t -> List.of(//
                this.app = Apps.heatLuxtronik(t) //
        ), null, new AppManagerTestBundle.PseudoComponentManagerFactory());
        this.appManagerTestBundle.addComponentAggregateTask();
    }

    @Test
    void testExpectedProperties() {
        this.appManagerTestBundle //
                .withApp(this.app) //
                .hasOnlyProperties( //
                        AppHeatLuxtronik.Property.HEAT_ID, //
                        AppHeatLuxtronik.Property.MODBUS_ID, //
                        AppHeatLuxtronik.Property.ALIAS, //
                        AppHeatLuxtronik.Property.IP, //
                        AppHeatLuxtronik.Property.READ_ONLY, //
                        AppHeatLuxtronik.Property.EXTENDED_REGISTERS);
    }

    @Test
    void testDefaults() {
        this.appManagerTestBundle.withApp(this.app) //
                .assertThatProperty(AppHeatLuxtronik.Property.READ_ONLY) //
                .hasDefaultValue(true);
        this.appManagerTestBundle.withApp(this.app) //
                .assertThatProperty(AppHeatLuxtronik.Property.EXTENDED_REGISTERS) //
                .hasDefaultValue(false);
    }

    @Test
    void testCreateAppWithSafeDefaults() throws Exception {
        this.appManagerTestBundle.sut.handleAddAppInstanceRequest(DUMMY_ADMIN, //
                new AddAppInstance.Request(this.app.getAppId(), "key", "alias", properties(true, false)));

        assertEquals(1, this.appManagerTestBundle.sut.getInstantiatedApps().size());

        this.appManagerTestBundle.assertComponentsExist( //
                new EdgeConfig.Component("heatLuxtronik0", null, "Heat.Luxtronik.SHI",
                        JsonUtils.buildJsonObject() //
                                .addProperty("readOnly", true) //
                                .addProperty("enableExtendedRegisters", false) //
                                .addProperty("modbus.id", "modbusLuxtronik0") //
                                .build()), //
                new EdgeConfig.Component("modbusLuxtronik0", null, "Bridge.Modbus.Tcp",
                        JsonUtils.buildJsonObject() //
                                .addProperty("ip", IP) //
                                .addProperty("port", 502) //
                                .build()));
    }

    @Test
    void testCreateWritableApp() throws Exception {
        this.appManagerTestBundle.sut.handleAddAppInstanceRequest(DUMMY_ADMIN, //
                new AddAppInstance.Request(this.app.getAppId(), "key", "alias", properties(false, true)));

        this.appManagerTestBundle.assertComponentsExist( //
                new EdgeConfig.Component("heatLuxtronik0", null, "Heat.Luxtronik.SHI",
                        JsonUtils.buildJsonObject() //
                                .addProperty("readOnly", false) //
                                .addProperty("enableExtendedRegisters", true) //
                                .addProperty("modbus.id", "modbusLuxtronik0") //
                                .build()), //
                new EdgeConfig.Component("modbusLuxtronik0", null, "Bridge.Modbus.Tcp",
                        JsonUtils.buildJsonObject() //
                                .addProperty("ip", IP) //
                                .addProperty("port", 502) //
                                .build()));
    }

    @Test
    void testPermissions() {
        this.appManagerTestBundle.assertPermissions(this.app, properties(true, false)) //
                .canSeeWithOnlyRoles(Role.ADMIN) //
                .canDeleteWithOnlyRoles(Role.ADMIN) //
                .canInstallWithOnlyRoles(Role.ADMIN);
    }

    private static JsonObject properties(boolean readOnly, boolean extended) {
        return JsonUtils.buildJsonObject() //
                .addProperty("IP", IP) //
                .addProperty("READ_ONLY", readOnly) //
                .addProperty("EXTENDED_REGISTERS", extended) //
                .build();
    }
}
