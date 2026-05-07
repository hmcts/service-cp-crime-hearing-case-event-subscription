package uk.gov.hmcts.cp.subscription.integration.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresAvailabilityCheckTest {

    @Test
    void throwsIllegalStateException_withHelpfulMessage_whenPostgresUnreachable() {
        // OS-reserved port 9 — connection refused immediately, no wait
        assertThatThrownBy(() ->
                PostgresAvailabilityCheck.assertPostgresReachable(
                        "jdbc:postgresql://localhost:9/appdb", "postgres", "postgres"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost:5432")
                .hasMessageContaining("docker compose -f docker/docker-compose.yml up -d");
    }
}