package uk.gov.hmcts.cp.servicebus.integration;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusAdminService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusProcessorService;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

@Slf4j
@SpringBootTest
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
}
