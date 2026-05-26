package uk.gov.hmcts.cp.servicebus.integration;

import com.azure.messaging.servicebus.administration.models.QueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;

import java.util.List;

@Slf4j
@Service
public class ServiceBusTestService {

    @Autowired
    ServiceBusConfigService configService;

    public void dropQueueIfExists(String queueName) {
        List<String> queues = configService.adminClient().listQueues().stream().map(QueueProperties::getName).toList();
        if (queues.contains(queueName)) {
            configService.adminClient().deleteQueue(queueName);
        }
    }
}
