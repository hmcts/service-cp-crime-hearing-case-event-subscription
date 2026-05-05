package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceBusClientFactoryTest {

    private static final String EMULATOR_ADMIN = "Endpoint=sb://localhost:5300;SharedAccessKeyName=key;SharedAccessKey=key;UseDevelopmentEmulator=true;";
    private static final String EMULATOR_MESSAGING = "Endpoint=sb://localhost;SharedAccessKeyName=key;SharedAccessKey=key;UseDevelopmentEmulator=true;";
    private static final String AZURE_MESSAGING = "https://servicebus.example.com";
    private static final UUID CLIENT_ID = UUID.randomUUID();

    private static VaultServiceProperties vaultProperties() {
        return new VaultServiceProperties(false, "https://vault.example.com/", CLIENT_ID);
    }

    @Test
    void senderClient_buildsNonNullClientUsingEmulatorConnectionString() {
        final ServiceBusProperties properties = new ServiceBusProperties(EMULATOR_ADMIN, EMULATOR_MESSAGING, 5);
        final ServiceBusClientFactory factory = new ServiceBusClientFactory(properties, vaultProperties());
        try (ServiceBusSenderClient sender = factory.senderClient("test-queue")) {
            assertThat(sender).isNotNull();
        }
    }

    @Test
    void processorClientBuilder_returnsNonNullBuilderForEmulatorQueue() {
        final ServiceBusProperties properties = new ServiceBusProperties(EMULATOR_ADMIN, EMULATOR_MESSAGING, 5);
        final ServiceBusClientFactory factory = new ServiceBusClientFactory(properties, vaultProperties());
        assertThat(factory.processorClientBuilder("processor-queue")).isNotNull();
    }

    @Test
    void processorClientBuilder_returnsNonNullBuilderForAzureQueue() {
        final ServiceBusProperties properties = new ServiceBusProperties(EMULATOR_ADMIN, AZURE_MESSAGING, 5);
        final ServiceBusClientFactory factory = new ServiceBusClientFactory(properties, vaultProperties());
        assertThat(factory.processorClientBuilder("processor-queue")).isNotNull();
    }
}
