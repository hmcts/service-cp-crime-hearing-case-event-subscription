package uk.gov.hmcts.cp.subscription.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.model.PcrEventPayload;
import uk.gov.hmcts.cp.subscription.services.NotificationService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/notifications/pcr/{materialId}")
    public ResponseEntity<PcrEventPayload> notificationPCR(@PathVariable UUID materialId, @RequestBody @Valid PcrEventPayload pcrEventPayload) {

        notificationService.processPcrEvent(materialId);
        return new ResponseEntity<>(pcrEventPayload, HttpStatus.CREATED);
    }
}
