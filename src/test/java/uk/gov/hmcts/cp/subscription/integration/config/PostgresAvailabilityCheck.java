package uk.gov.hmcts.cp.subscription.integration.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgresAvailabilityCheck implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        assertPostgresReachable("jdbc:postgresql://localhost:5432/appdb", "postgres", "postgres");
        TestPropertyValues.of(
                "subscription.oauth-enabled=true",
                "material-client.cjscppuid=11111111-2222-3333-4444-666666666666",
                "material-client.retry.intervalMilliSecs=100",
                "material-client.retry.timeoutMilliSecs=500"
        ).applyTo(ctx.getEnvironment());
    }

    static void assertPostgresReachable(String url, String user, String password) {
        try {
            DriverManager.getConnection(url, user, password).close();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "\n\n*** Integration tests require PostgreSQL on localhost:5432 ***\n"
                    + "Start the full stack:\n"
                    + "  docker compose -f docker/docker-compose.yml up -d\n"
                    + "Or start postgres only:\n"
                    + "  docker compose -f docker/docker-compose.yml up -d postgres\n\n",
                    e);
        }
    }
}