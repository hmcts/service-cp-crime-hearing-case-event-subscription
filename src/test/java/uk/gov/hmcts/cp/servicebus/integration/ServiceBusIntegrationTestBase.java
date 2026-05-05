package uk.gov.hmcts.cp.servicebus.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusAdminService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusProcessorService;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
        "vault.enabled=false",
        "service-bus.auto-start-processors=false"
})
public class ServiceBusIntegrationTestBase {

    @Autowired
    protected ServiceBusAdminService adminService;
    @Autowired
    protected JsonMapper jsonMapper;
    @Autowired
    protected ServiceBusClientService clientService;
    @Autowired
    protected ServiceBusProcessorService processorService;
    @Autowired
    protected ServiceBusTestService testService;

    protected void prepareQueue(final String queueName) {
        assertThat(adminService.isServiceBusReady()).isTrue();
        processorService.stopMessageProcessor(queueName);
        testService.dropQueueIfExists(queueName);
        adminService.createQueue(queueName);
        processorService.startMessageProcessor(queueName);
    }
}
