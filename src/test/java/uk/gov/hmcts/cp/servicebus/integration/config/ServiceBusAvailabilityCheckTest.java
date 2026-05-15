package uk.gov.hmcts.cp.servicebus.integration.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceBusAvailabilityCheckTest {

    @Test
    void throwsIllegalStateException_withHelpfulMessage_whenServiceBusUnreachable() {
        assertThatThrownBy(() ->
                ServiceBusAvailabilityCheck.assertServiceBusReachable("localhost", 9))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost:5672")
                .hasMessageContaining("docker compose -f docker/docker-compose.yml up -d");
    }
}
