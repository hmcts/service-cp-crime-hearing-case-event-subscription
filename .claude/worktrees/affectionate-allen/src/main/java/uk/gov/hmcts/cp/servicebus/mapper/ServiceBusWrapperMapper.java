package uk.gov.hmcts.cp.servicebus.mapper;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.servicebus.model.ServiceBusWrappedMessage;

import java.util.UUID;

@Component
@AllArgsConstructor
public class ServiceBusWrapperMapper {

    public ServiceBusWrappedMessage newWrapper(final UUID correlationId, final int failureCount, final String targetUrl, final String message) {
        return ServiceBusWrappedMessage.builder()
                .correlationId(correlationId)
                .failureCount(failureCount)
                .targetUrl(targetUrl)
                .message(message)
                .build();
    }
}
