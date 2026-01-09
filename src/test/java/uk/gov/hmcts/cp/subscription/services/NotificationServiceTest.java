package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private MaterialClient materialClient;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldCallClientAndLogSuccess() {
        UUID materialId = UUID.randomUUID();
        when(materialClient.getContentById(any())).thenReturn(any());
        byte[] document = notificationService.processPcrEvent(materialId);
        assertThat(document).isNullOrEmpty();
        verify(materialClient, times(1)).getContentById(any());
    }
}
