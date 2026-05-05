package uk.gov.hmcts.cp.servicebus.config;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.servicebus.admin.ServiceBusAdminInterface;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceBusPropertiesTest {

    private static final String EMULATOR_ADMIN_CONNECTION = "Endpoint=sb://localhost:5300;SharedAccessKeyName=x;SharedAccessKey=x;UseDevelopmentEmulator=true;";
    private static final String EMULATOR_CONNECTION = "Endpoint=sb://localhost;SharedAccessKeyName=x;SharedAccessKey=x;UseDevelopmentEmulator=true;";
    private static final String HTTPS_ENDPOINT = "https://test.servicebus.windows.net";
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ServiceBusAdminInterface adminClientFor(String adminConnection, String connection) {
        final ServiceBusProperties configService = new ServiceBusProperties(adminConnection, connection, 5);
        final VaultServiceProperties vaultProperties = new VaultServiceProperties(false, "", CLIENT_ID);
        return new ServiceBusAdminConfiguration(configService, vaultProperties).serviceBusAdmin();
    }

    @Test
    void service_bus_admin_uses_emulator_when_connection_string_has_emulator_flag() {
        assertThat(adminClientFor(EMULATOR_ADMIN_CONNECTION, EMULATOR_CONNECTION)).isNotNull();
    }

    @Test
    void service_bus_admin_uses_azure_when_connection_string_is_https() {
        assertThat(adminClientFor(EMULATOR_ADMIN_CONNECTION, HTTPS_ENDPOINT)).isNotNull();
    }

    @Test
    void emulator_connection_string_is_detected_as_emulator() {
        final ServiceBusProperties configService = new ServiceBusProperties(EMULATOR_ADMIN_CONNECTION, EMULATOR_CONNECTION, 5);
        assertThat(configService.isEmulator()).isTrue();
    }

    @Test
    void azure_connection_string_is_detected_as_not_emulator() {
        final ServiceBusProperties configService = new ServiceBusProperties(EMULATOR_ADMIN_CONNECTION, HTTPS_ENDPOINT, 5);
        assertThat(configService.isEmulator()).isFalse();
    }
}
