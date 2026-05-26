package uk.gov.hmcts.cp.servicebus.services;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusMapper;
import uk.gov.hmcts.cp.servicebus.mapper.ServiceBusWrapperMapper;
import uk.gov.hmcts.cp.servicebus.model.ServiceBusWrappedMessage;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_INBOUND_QUEUE;

@ExtendWith(MockitoExtension.class)
class ServiceBusClientServiceTest {

    @Mock
    ServiceBusConfigService configService;
    @Mock
    ServiceBusWrapperMapper wrapperMapper;
    @Mock
    ServiceBusRetryService retryService;
    @Mock
    ServiceBusMapper mapper;
    @Mock
    JsonMapper jsonMapper;

    @InjectMocks
    ServiceBusClientService clientService;

    @Mock
    ServiceBusSenderClient senderClient;

    OffsetDateTime nextTryTime = OffsetDateTime.now();
    String callbackUrl = "http://callback";
    ServiceBusWrappedMessage wrappedMessage = ServiceBusWrappedMessage.builder().build();
    ServiceBusMessage serviceBusMessage = new ServiceBusMessage("wrapped-Message");

    @Test
    void queue_message_should_pass_to_queue() {
        UUID correlationId = UUID.randomUUID();
        MDC.put(CORRELATION_ID_KEY, correlationId.toString());
        when(configService.senderClient(PCR_INBOUND_QUEUE)).thenReturn(senderClient);
        when(wrapperMapper.newWrapper(correlationId, 1, callbackUrl, "message")).thenReturn(wrappedMessage);
        when(jsonMapper.toJson(wrappedMessage)).thenReturn("wrapped-message");
        when(retryService.getNextTryTime(1)).thenReturn(nextTryTime);
        when(mapper.newMessage("wrapped-message", nextTryTime)).thenReturn(serviceBusMessage);

        clientService.queueMessage(PCR_INBOUND_QUEUE, callbackUrl, "message", 1);

        verify(senderClient).sendMessage(serviceBusMessage);
        verify(senderClient).close();
        MDC.remove("correlationId");
    }
}