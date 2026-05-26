package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.cp.subscription.clients.CallbackClient;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

@Service
@Slf4j
@RequiredArgsConstructor
public class CallbackService {

    private final AppProperties appProperties;
    private final CallbackClient callbackClient;

    public void sendToSubscriber(final String url, final EventNotificationPayloadWrapper payloadWrapper) {
        try {
            waitForCallbackDelivery(url, payloadWrapper);
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException("Callback is not ready", e);
        }
    }

    private void waitForCallbackDelivery(final String url, final EventNotificationPayloadWrapper payloadWrapper) {
        await()
                .pollInterval(Duration.ofMillis(appProperties.getCallbackRetryIntervalMilliSecs()))
                .atMost(Duration.ofMillis(appProperties.getCallbackRetryTimeoutMilliSecs()))
                .until(() -> {
                    try {
                        callbackClient.sendNotification(url, payloadWrapper);
                        return true;
                    } catch (RestClientException e) {
                        log.warn("Callback delivery failed for {}, retrying: {}", url, e.getMessage());
                        return false;
                    }
                });
    }
}
