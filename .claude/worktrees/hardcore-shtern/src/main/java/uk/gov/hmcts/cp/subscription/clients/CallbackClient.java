package uk.gov.hmcts.cp.subscription.clients;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import static com.azure.core.http.ContentType.APPLICATION_JSON;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper.KEY_ID_HEADER;
import static uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper.SIGNATURE_HEADER;

@Service
@AllArgsConstructor
@Slf4j
public class CallbackClient {

    private JsonMapper jsonMapper;
    private RestTemplate restTemplate;

    public void sendNotification(final String url, final EventNotificationPayloadWrapper payloadWrapper) {
        log.info("Sending notification to {}", url);
        final HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON);
        headers.set(KEY_ID_HEADER, payloadWrapper.getKeyId());
        headers.set(SIGNATURE_HEADER, payloadWrapper.getSignature());
        final String payloadJson = jsonMapper.toJson(payloadWrapper.getPayload());
        final HttpEntity<String> req = new HttpEntity<>(payloadJson, headers);
        log.info("Sending signature:{} for payload:{}", payloadWrapper.getSignature(), payloadJson);
        restTemplate.exchange(url, POST, req, String.class);
    }
}
