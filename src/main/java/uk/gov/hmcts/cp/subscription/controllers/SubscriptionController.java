package uk.gov.hmcts.cp.subscription.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.SubscriptionApi;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController implements SubscriptionApi {

    private final SubscriptionService subscriptionService;

    private static final String CLIENT_ID = "TODO";

    @Override
    @Transactional
    public ResponseEntity<ClientSubscription> createClientSubscription(final ClientSubscriptionRequest request) {
        log.info("createClientSubscription clientId:{}", CLIENT_ID);
        final ClientSubscription response = subscriptionService.saveSubscription(request);
        log.info("createClientSubscription created subscription:{}", response.getClientSubscriptionId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    @Transactional
    public ResponseEntity<ClientSubscription> updateClientSubscription(final UUID clientSubscriptionId, final ClientSubscriptionRequest request) {
        log.info("updateClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, CLIENT_ID);
        final ClientSubscription response = subscriptionService.updateSubscription(clientSubscriptionId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    @Transactional
    public ResponseEntity<ClientSubscription> getClientSubscription(final UUID clientSubscriptionId) {
        log.info("getClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, CLIENT_ID);
        final ClientSubscription response = subscriptionService.getSubscription(clientSubscriptionId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    @Transactional
    public ResponseEntity<Void> deleteClientSubscription(final UUID clientSubscriptionId) {
        log.info("deleteClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, CLIENT_ID);
        subscriptionService.deleteSubscription(clientSubscriptionId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
