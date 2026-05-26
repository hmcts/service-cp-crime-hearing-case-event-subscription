package uk.gov.hmcts.cp.servicebus.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceBusWrappedMessage {
    private UUID correlationId;
    private int failureCount;
    private String targetUrl;
    private String message;
}
