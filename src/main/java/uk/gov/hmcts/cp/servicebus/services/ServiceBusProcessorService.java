package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties;
import uk.gov.hmcts.cp.servicebus.model.ServiceBusWrappedMessage;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_INBOUND_QUEUE;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusProperties.NOTIFICATIONS_OUTBOUND_QUEUE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceBusProcessorService {

    private static final int MAX_WAIT_SECONDS = 120;
    private static final int POLL_SECONDS = 1;

    private final ServiceBusAdminService adminService;
    private final ServiceBusProperties properties;
    private final ServiceBusClientFactory clientFactory;
    private final ServiceBusClientService clientService;
    private final JsonMapper jsonMapper;
    private final ServiceBusHandlers serviceBusHandlers;

    @Value("${service-bus.auto-start-processors:true}")
    private boolean autoStartProcessors;

    private Map<String, ServiceBusProcessorClient> processorClients = new HashMap<>();

    @PostConstruct
    public void initialiseServiceBus() {
        if (!autoStartProcessors) {
            log.info("service-bus.auto-start-processors=false — skipping queue initialisation");
            return;
        }
        try {
            await()
                    .atMost(Duration.ofSeconds(MAX_WAIT_SECONDS))
                    .pollInterval(Duration.ofSeconds(POLL_SECONDS))
                    .until(adminService::isServiceBusReady);
            log.info("createServiceBusQueues creating service bus queues");
            adminService.createQueue(NOTIFICATIONS_INBOUND_QUEUE);
            startMessageProcessor(NOTIFICATIONS_INBOUND_QUEUE);
            adminService.createQueue(NOTIFICATIONS_OUTBOUND_QUEUE);
            startMessageProcessor(NOTIFICATIONS_OUTBOUND_QUEUE);
        } catch (Exception e) {
            log.error("Failed to initialise serviceBus. {}", e.getMessage());
        }
    }

    public void stopMessageProcessor(final String queueName) {
        final ServiceBusProcessorClient processorClient = processorClients.remove(queueName);
        if (processorClient != null) {
            log.info("Service Bus Processor {} is being stopped", queueName);
            processorClient.close();
        } else {
            log.info("Service Bus Processor {} is not running", queueName);
        }
    }

    @SneakyThrows
    public void startMessageProcessor(final String queueName) {
        log.info("starting service bus processor {}", queueName);
        final ServiceBusProcessorClient processorClient = clientFactory
                .processorClientBuilder(queueName)
                .processMessage(context -> handleMessage(queueName, context))
                .processError(context -> handleError(queueName, context))
                .buildProcessorClient();
        processorClient.start();
        processorClients.put(queueName, processorClient);
    }

    public void handleMessage(final String queueName, final ServiceBusReceivedMessageContext context) {
        final String wrappedMessageString = context.getMessage().getBody().toString();
        final ServiceBusWrappedMessage queueMessage = jsonMapper.fromJson(wrappedMessageString, ServiceBusWrappedMessage.class);
        log.info("handleMessage processing {} with targetUrl:{}", queueName, queueMessage.getTargetUrl());
        try {
            MDC.put(CORRELATION_ID_KEY, queueMessage.getCorrelationId().toString());
            serviceBusHandlers.handleMessage(queueName, queueMessage.getTargetUrl(), queueMessage.getMessage());
        } catch (Exception exception) {
            final int failureCount = queueMessage.getFailureCount() + 1;
            log.error("handleMessage failureCount:{} of {} tries with exception.", failureCount, properties.getMaxTries(), exception);
            if (failureCount >= properties.getMaxTries()) {
                log.error("handleMessage FAILED FINALLY");
                throw exception;
            }
            clientService.queueMessage(queueName, queueMessage.getTargetUrl(), queueMessage.getMessage(), failureCount);
            // Because we added a new message and swallowed the error then the current message will be dropped
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    public void handleError(final String queueName, final ServiceBusErrorContext errorContext) {
        // We should only be called when failCount has exceeded maxTries and message go to DLQ
        log.error("handleError unexpected error on {} moving to DLQ", queueName, errorContext.getException());
    }
}
