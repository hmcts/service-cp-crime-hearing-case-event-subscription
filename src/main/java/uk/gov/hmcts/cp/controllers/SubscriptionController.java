package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.SubscriptionApi;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.services.SubscriptionService;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController implements SubscriptionApi {

    private final SubscriptionService subscriptionService;

    @Override
    public ResponseEntity<ClientSubscription> createClientSubscription(final ClientSubscriptionRequest request) {
        log.info("createClientSubscription clientId:{}", "TODO");
        ClientSubscription response = subscriptionService.saveSubscription(request);
        return ResponseEntity.ok(response);
    }
}
