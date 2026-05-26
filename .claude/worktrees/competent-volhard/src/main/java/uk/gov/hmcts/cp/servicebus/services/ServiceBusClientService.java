package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusMapper;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusWrapperMapper;
import uk.gov.hmcts.cp.servicebus.model.ServiceBusWrappedMessage;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@Service
@AllArgsConstructor
@Slf4j
public class ServiceBusClientService {

    private final ServiceBusConfigService configService;
    private final ServiceBusWrapperMapper wrapperMapper;
    private final ServiceBusMapper mapper;
    private final JsonMapper jsonMapper;
    private final ServiceBusRetryService retryService;

    public void queueMessage(final String queueName, final String targetUrl, final String messageString, final int failureCount) {
        final ServiceBusSenderClient serviceBusSenderClient = configService.senderClient(queueName);
        final UUID correlationId = UUID.fromString(MDC.get(CORRELATION_ID_KEY));
        final ServiceBusWrappedMessage wrappedMessage = wrapperMapper.newWrapper(correlationId, failureCount, targetUrl, messageString);
        final OffsetDateTime nextTryTime = retryService.getNextTryTime(failureCount);
        final ServiceBusMessage serviceBusMessage = mapper.newMessage(jsonMapper.toJson(wrappedMessage), nextTryTime);
        log.info("Sending message to queue {} ...", queueName);
        serviceBusSenderClient.sendMessage(serviceBusMessage);
        serviceBusSenderClient.close();
        log.info("Sent message to queue:{} with failCount:{} nextTryTime:{}", queueName, failureCount, nextTryTime);
    }
}
