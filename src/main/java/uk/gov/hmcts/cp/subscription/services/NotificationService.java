package uk.gov.hmcts.cp.subscription.services;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.subscription.clients.MaterialClient;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final MaterialClient client;

    public void processPcrEvent(final UUID materialId) {
        try {
            client.getContentById(materialId);
            log.info("Successfully received document.");
        } catch (FeignException ex) {
            log.error("Failed to get document.", ex);
            throw ex;
        }
    }
}
