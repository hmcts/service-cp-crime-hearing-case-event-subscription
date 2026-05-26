package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;

import java.time.Duration;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class ServiceBusAdminService {

    private final ServiceBusConfigService configService;

    public boolean isServiceBusReady() {
        try {
            final List<String> queues = configService.adminClient().listQueues().stream().map(QueueProperties::getName).toList();
            log.info("ServiceBus has queues:{}", queues);
            return true;
        } catch (Exception e) {
            log.info("ServiceBus is not available. Error:{}", e.getMessage());
            return false;
        }
    }

    public void createQueue(final String queueName) {
        final ServiceBusAdministrationClient adminClient = configService.adminClient();
        if (adminClient.getQueueExists(queueName)) {
            log.info("Queue {} already exists", queueName);
        } else {
            log.info("Creating queue {}", queueName);
            final CreateQueueOptions createQueueOptions = new CreateQueueOptions();
            createQueueOptions.setDefaultMessageTimeToLive(Duration.ofHours(1));
            createQueueOptions.setMaxDeliveryCount(1);
            adminClient.createQueue(queueName, createQueueOptions);
        }
    }
}
