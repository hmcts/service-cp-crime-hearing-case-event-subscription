package uk.gov.hmcts.cp.subscription.config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@Slf4j
public class TestContainersInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer(
            "postgres")
            .withDatabaseName("appdb")
            .withUsername("postgres")
            .withPassword("postgres");


    @SneakyThrows
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        postgreSQLContainer.start();
        TestPropertyValues.of(
                "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                "spring.datasource.username=" + postgreSQLContainer.getUsername(),
                "spring.datasource.password=" + postgreSQLContainer.getPassword()
        ).applyTo(applicationContext.getEnvironment());
    }
}

