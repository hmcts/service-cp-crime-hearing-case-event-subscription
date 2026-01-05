package uk.gov.hmcts.cp.subscription.services;

import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private MaterialClient materialClient;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        materialClient = mock(MaterialClient.class);
        notificationService = new NotificationService(materialClient);
    }

    @Test
    void shouldCallClientAndLogSuccess() {
        UUID materialId = UUID.randomUUID();
        notificationService.processPcrEvent(materialId);
        verify(materialClient, times(1)).getContentById(materialId);
    }

    @Test
    void shouldThrowFeignExceptionWhenClientFails() {
        UUID materialId = UUID.randomUUID();

        Response response = Response.builder()
                .request(Request.create(Request.HttpMethod.GET, "/dummy", Collections.emptyMap(), null, Charset.defaultCharset(), null))
                .status(500)
                .reason("Internal Server Error")
                .build();

        FeignException feignException = FeignException.errorStatus("getContentById", response);
        doThrow(feignException).when(materialClient).getContentById(materialId);
        assertThrows(FeignException.class, () -> notificationService.processPcrEvent(materialId));
        verify(materialClient, times(1)).getContentById(materialId);
    }
}
