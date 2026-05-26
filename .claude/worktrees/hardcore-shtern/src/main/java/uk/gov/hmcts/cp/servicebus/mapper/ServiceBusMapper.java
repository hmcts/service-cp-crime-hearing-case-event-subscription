package uk.gov.hmcts.cp.servicebus.mapper;

import com.azure.messaging.servicebus.ServiceBusMessage;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@AllArgsConstructor
public class ServiceBusMapper {

    public ServiceBusMessage newMessage(final String wrappedMessage, final OffsetDateTime nextTryTime) {
        final ServiceBusMessage serviceBusMessage = new ServiceBusMessage(wrappedMessage);
        serviceBusMessage.setScheduledEnqueueTime(nextTryTime);
        return serviceBusMessage;
    }
}
