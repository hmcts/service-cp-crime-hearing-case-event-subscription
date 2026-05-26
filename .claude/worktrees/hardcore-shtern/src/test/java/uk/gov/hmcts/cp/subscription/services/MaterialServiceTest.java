package uk.gov.hmcts.cp.subscription.services;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.model.MaterialMetadata;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {
    @Mock
    AppProperties appProperties;
    @Mock
    MaterialClient materialClient;

    @InjectMocks
    MaterialService materialService;

    UUID materialId = randomUUID();
    MaterialMetadata materialMetadata = new MaterialMetadata();

    @Test
    void material_metadata_should_return(){
        when(materialClient.getMetadata(materialId)).thenReturn(materialMetadata);
        MaterialMetadata response = materialService.getMaterialMetadata(materialId);
        assertThat(response).isEqualTo(materialMetadata);
    }

    @Test
    void material_ready_should_be_returned() {
        when(appProperties.getMaterialRetryIntervalMilliSecs()).thenReturn(100);
        when(appProperties.getMaterialRetryTimeoutMilliSecs()).thenReturn(400);
        materialMetadata.setMaterialId(materialId);
        when(materialClient.getMetadata(any(UUID.class))).thenReturn(materialMetadata);

        MaterialMetadata response = materialService.waitForMaterialMetadata(materialId);

        verify(materialClient, times(1)).getMetadata(materialId);
        assertThat(response.getMaterialId()).isEqualTo(materialId);
    }

    @Test
    void material_not_ready_should_throw_timeout_exception() {
        when(appProperties.getMaterialRetryIntervalMilliSecs()).thenReturn(100);
        when(appProperties.getMaterialRetryTimeoutMilliSecs()).thenReturn(400);
        when(materialClient.getMetadata(materialId)).thenReturn(null);

        assertThrows(ConditionTimeoutException.class, () -> materialService.waitForMaterialMetadata(materialId));

        verify(materialClient, times(3)).getMetadata(materialId);
    }

    @Test
    void material_404_should_retry_until_timeout() {
        when(appProperties.getMaterialRetryIntervalMilliSecs()).thenReturn(100);
        when(appProperties.getMaterialRetryTimeoutMilliSecs()).thenReturn(400);
        when(materialClient.getMetadata(materialId))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThrows(ConditionTimeoutException.class, () -> materialService.waitForMaterialMetadata(materialId));

        verify(materialClient, atLeast(2)).getMetadata(materialId);
    }

    @Test
    void material_404_then_ready_should_return_metadata() {
        when(appProperties.getMaterialRetryIntervalMilliSecs()).thenReturn(100);
        when(appProperties.getMaterialRetryTimeoutMilliSecs()).thenReturn(1000);
        materialMetadata.setMaterialId(materialId);
        when(materialClient.getMetadata(materialId))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND))
                .thenReturn(materialMetadata);

        MaterialMetadata response = materialService.waitForMaterialMetadata(materialId);

        assertThat(response.getMaterialId()).isEqualTo(materialId);
        verify(materialClient, times(2)).getMetadata(materialId);
    }

    @Test
    void material_network_error_should_retry_until_timeout() {
        when(appProperties.getMaterialRetryIntervalMilliSecs()).thenReturn(100);
        when(appProperties.getMaterialRetryTimeoutMilliSecs()).thenReturn(400);
        when(materialClient.getMetadata(materialId))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(ConditionTimeoutException.class, () -> materialService.waitForMaterialMetadata(materialId));

        verify(materialClient, atLeast(2)).getMetadata(materialId);
    }

    @Test
    void material_500_should_retry_until_timeout() {
        when(appProperties.getMaterialRetryIntervalMilliSecs()).thenReturn(100);
        when(appProperties.getMaterialRetryTimeoutMilliSecs()).thenReturn(400);
        when(materialClient.getMetadata(materialId))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ConditionTimeoutException.class, () -> materialService.waitForMaterialMetadata(materialId));

        verify(materialClient, atLeast(2)).getMetadata(materialId);
    }

    @Test
    void poll_should_return_true_when_metadata_available() {
        materialMetadata.setMaterialId(materialId);
        when(materialClient.getMetadata(materialId)).thenReturn(materialMetadata);
        final AtomicReference<MaterialMetadata> ref = new AtomicReference<>();

        boolean result = materialService.pollMaterialMetadata(materialId, Map.of(), ref);

        assertThat(result).isTrue();
        assertThat(ref.get().getMaterialId()).isEqualTo(materialId);
    }

    @Test
    void poll_should_return_false_when_exception_thrown() {
        when(materialClient.getMetadata(materialId))
                .thenThrow(new RuntimeException("Connection refused"));

        boolean result = materialService.pollMaterialMetadata(materialId, Map.of(), new AtomicReference<>());

        assertThat(result).isFalse();
    }

    @Test
    void poll_should_return_false_when_response_is_null() {
        when(materialClient.getMetadata(materialId)).thenReturn(null);

        boolean result = materialService.pollMaterialMetadata(materialId, Map.of(), new AtomicReference<>());

        assertThat(result).isFalse();
    }
}