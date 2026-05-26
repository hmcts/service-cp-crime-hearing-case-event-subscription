package uk.gov.hmcts.cp.subscription.integration.config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@Slf4j
public class TestContainersInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER = new PostgreSQLContainer(
            "postgres")
            .withDatabaseName("appdb")
            .withUsername("postgres")
            .withPassword("postgres");


    @SneakyThrows
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        POSTGRE_SQL_CONTAINER.start();
        TestPropertyValues.of(
                "spring.datasource.url=" + POSTGRE_SQL_CONTAINER.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRE_SQL_CONTAINER.getUsername(),
                "spring.datasource.password=" + POSTGRE_SQL_CONTAINER.getPassword(),
                "subscription.oauth-enabled=true",
                "material-client.cjscppuid=11111111-2222-3333-4444-666666666666",
                "material-client.retry.intervalMilliSecs=100",
                "material-client.retry.timeoutMilliSecs=500"
        ).applyTo(applicationContext.getEnvironment());
    }
}

