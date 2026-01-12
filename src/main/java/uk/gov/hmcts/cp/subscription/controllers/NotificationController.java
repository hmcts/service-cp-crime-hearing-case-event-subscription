package uk.gov.hmcts.cp.subscription.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.NotificationApi;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import uk.gov.hmcts.cp.subscription.services.NotificationService;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @Override
    @Transactional
    public ResponseEntity<Void> createNotificationPCR(final PcrEventPayload pcrEventPayload) {
        notificationService.processPcrEvent(pcrEventPayload.getMaterialId());
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }
}
