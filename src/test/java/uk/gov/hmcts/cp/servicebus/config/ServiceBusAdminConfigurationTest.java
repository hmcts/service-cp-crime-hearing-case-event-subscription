package uk.gov.hmcts.cp.servicebus.config;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.servicebus.admin.ServiceBusAdminAzureImpl;
import uk.gov.hmcts.cp.servicebus.admin.ServiceBusAdminEmulatorImpl;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceBusAdminConfigurationTest {

    private static final String EMULATOR_ADMIN = "Endpoint=sb://localhost:5300;SharedAccessKeyName=x;SharedAccessKey=x;UseDevelopmentEmulator=true;";
    private static final String EMULATOR_CONNECTION = "Endpoint=sb://localhost;SharedAccessKeyName=x;SharedAccessKey=x;UseDevelopmentEmulator=true;";
    private static final String HTTPS_CONNECTION = "https://test.servicebus.windows.net";
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void emulator_connection_string_creates_emulator_impl() {
        assertThat(configFor(EMULATOR_ADMIN, EMULATOR_CONNECTION).serviceBusAdmin())
                .isInstanceOf(ServiceBusAdminEmulatorImpl.class);
    }

    @Test
    void azure_connection_string_creates_azure_impl() {
        assertThat(configFor(EMULATOR_ADMIN, HTTPS_CONNECTION).serviceBusAdmin())
                .isInstanceOf(ServiceBusAdminAzureImpl.class);
    }

    private ServiceBusAdminConfiguration configFor(String adminConnection, String connection) {
        final ServiceBusProperties properties = new ServiceBusProperties(adminConnection, connection, 5);
        final VaultServiceProperties vaultProperties = new VaultServiceProperties(false, "", CLIENT_ID);
        return new ServiceBusAdminConfiguration(properties, vaultProperties);
    }
}