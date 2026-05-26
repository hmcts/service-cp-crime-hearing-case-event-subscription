package uk.gov.hmcts.cp.subscription.http;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lets send a notification to the notification service without any subscribers
 * We should still poll material service waiting for the material document to be created
 * But then not send any notifications
 */
@Slf4j
class ServiceBusQueueTest extends BaseTest {

    @BeforeEach
    void beforeEach() {
        try {
            restClient.get()
                    .uri(subscriptionsBaseUrl + "/actuator/health")
                    .retrieve();
        } catch (Exception e) {
            log.error("Service not running on 8082 - run docker compose to bring up service");
        }
    }

    @Test
    void post_notification_should_get_material_and_send_callback() {
        final String postUrl = subscriptionsBaseUrl + "/notifications";
        String correlationId = UUID.randomUUID().toString();
        ResponseEntity<String> postResponse = restClient.post()
                .uri(postUrl)
                .header(CORRELATION_ID_KEY, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(notificationBody())
                .retrieve()
                .toEntity(String.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(postResponse.getHeaders().getFirst(CORRELATION_ID_KEY)).isEqualTo(correlationId);
        log.info("postResponse:{}", postResponse.getBody());
    }

    @SneakyThrows
    private String notificationBody() {
        return new String(getClass().getClassLoader()
                .getResourceAsStream("files/notification-body.json")
                .readAllBytes());
    }
}