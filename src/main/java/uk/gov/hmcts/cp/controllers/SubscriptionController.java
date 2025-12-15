package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.SubscriptionApi;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.services.SubscriptionService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController implements SubscriptionApi {

    private final SubscriptionService subscriptionService;

    @Override
    public ResponseEntity<ClientSubscription> createClientSubscription(final ClientSubscriptionRequest request) {
        log.info("createClientSubscription clientId:{}", "TODO");
        final ClientSubscription response = subscriptionService.saveSubscription(request);
        log.info("createClientSubscription created subscription:{}", response.getClientSubscriptionId());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ClientSubscription> getClientSubscription(final UUID clientSubscriptionId) {
        log.info("getClientSubscription clientId:{}", "TODO");
        final ClientSubscription response = subscriptionService.getSubscription(clientSubscriptionId);
        log.info("createClientSubscription returning subscription:{}", response.getClientSubscriptionId());
        return ResponseEntity.ok(response);
    }
}
