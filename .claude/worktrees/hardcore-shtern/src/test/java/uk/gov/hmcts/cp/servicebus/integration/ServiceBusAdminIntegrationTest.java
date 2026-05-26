package uk.gov.hmcts.cp.servicebus.integration;

import com.azure.messaging.servicebus.administration.models.QueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusAdminService;
import uk.gov.hmcts.cp.subscription.integration.config.TestContainersInitialise;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ContextConfiguration(initializers = TestContainersInitialise.class)
@TestPropertySource(properties = {
        "vault.enabled=false"
})
public class ServiceBusAdminIntegrationTest {

    @Autowired
    ServiceBusTestService testService;
    @Autowired
    ServiceBusConfigService configService;
    @Autowired
    ServiceBusAdminService adminService;

    @BeforeEach
    void beforeEach() {
        assertThat(adminService.isServiceBusReady()).isTrue();
        log.info("ServiceBus is up and running");
    }

    @Test
    void admin_client_should_create_new_queue_and_delete() {
        String queueName = "pcr.inbound";
        testService.dropQueueIfExists(queueName);
        adminService.createQueue(queueName);
        List<String> queues = configService.adminClient().listQueues().stream().map(QueueProperties::getName).toList();
        assertThat(queues.contains(queueName));

        adminService.createQueue(queueName);
        testService.dropQueueIfExists(queueName);
    }
}
