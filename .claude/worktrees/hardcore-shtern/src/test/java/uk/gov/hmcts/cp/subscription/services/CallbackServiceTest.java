package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.cp.subscription.clients.CallbackClient;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private CallbackClient callbackClient;

    @InjectMocks
    private CallbackService callbackService;

    @Test
    void send_to_subscriber_should_post() {
        when(appProperties.getCallbackRetryIntervalMilliSecs()).thenReturn(10);
        when(appProperties.getCallbackRetryTimeoutMilliSecs()).thenReturn(1000);

        EventNotificationPayloadWrapper payloadWrapper = EventNotificationPayloadWrapper.builder().build();
        callbackService.sendToSubscriber("url", payloadWrapper);

        verify(callbackClient).sendNotification("url", payloadWrapper);
    }

    @Test
    void send_to_subscriber_should_retry_until_success() {
        when(appProperties.getCallbackRetryIntervalMilliSecs()).thenReturn(10);
        when(appProperties.getCallbackRetryTimeoutMilliSecs()).thenReturn(500);
        EventNotificationPayloadWrapper payloadWrapper = EventNotificationPayloadWrapper.builder().build();

        doThrow(new RestClientException("intermittent failure"))
                .doNothing()
                .when(callbackClient).sendNotification("url", payloadWrapper);

        callbackService.sendToSubscriber("url", payloadWrapper);

        verify(callbackClient, times(2)).sendNotification("url", payloadWrapper);
    }
}