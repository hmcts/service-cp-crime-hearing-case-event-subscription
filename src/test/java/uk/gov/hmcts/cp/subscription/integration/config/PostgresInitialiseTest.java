package uk.gov.hmcts.cp.subscription.integration.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresInitialiseTest {

    @Test
    void throwsIllegalStateException_withHelpfulMessage_whenPostgresUnreachable() {
        assertThatThrownBy(() ->
                PostgresInitialise.assertPostgresReachable(
                        "jdbc:postgresql://localhost:9/appdb", "postgres", "postgres"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appdb")
                .hasMessageContaining("localhost:5433")
                .hasMessageContaining("docker compose -f docker/docker-compose.yml up -d");
    }
}
