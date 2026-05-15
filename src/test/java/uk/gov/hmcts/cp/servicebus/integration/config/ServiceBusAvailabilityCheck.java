package uk.gov.hmcts.cp.servicebus.integration.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.Socket;

public class ServiceBusAvailabilityCheck implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        assertServiceBusReachable("localhost", 5672);
    }

    static void assertServiceBusReachable(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
        } catch (IOException e) {
            throw new IllegalStateException(
                    "\n\n*** Integration tests require the Service Bus emulator on localhost:5672 ***\n"
                    + "Start the full stack:\n"
                    + "  docker compose -f docker/docker-compose.yml up -d\n"
                    + "Or start the emulator only:\n"
                    + "  docker compose -f docker/docker-compose.yml up -d sqledge servicebus-emulator\n\n",
                    e);
        }
    }
}
