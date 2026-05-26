package uk.gov.hmcts.cp.subscription.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.SubscriptionApi;
import uk.gov.hmcts.cp.openapi.model.ClientSubscription;
import uk.gov.hmcts.cp.openapi.model.ClientSubscriptionRequest;
import uk.gov.hmcts.cp.openapi.model.EventTypeResponse;
import uk.gov.hmcts.cp.subscription.services.EventTypeService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;

import java.util.UUID;

import static uk.gov.hmcts.cp.filters.ClientIdResolutionFilter.MDC_CLIENT_ID;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController implements SubscriptionApi {

    private final SubscriptionService subscriptionService;
    private final EventTypeService eventTypeService;

    @Override
    public ResponseEntity<ClientSubscription> createClientSubscription(
            final ClientSubscriptionRequest clientSubscriptionRequest,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("createClientSubscription callbackUrl:{} clientId:{}",
                Encode.forJava(clientSubscriptionRequest.getNotificationEndpoint().getCallbackUrl()), clientId);
        final ClientSubscription response = subscriptionService.createSubscription(clientSubscriptionRequest, clientId);
        log.info("createClientSubscription created subscription:{}", response.getClientSubscriptionId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ClientSubscription> updateClientSubscription(
            final UUID clientSubscriptionId,
            final ClientSubscriptionRequest clientSubscriptionRequest,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("updateClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, clientId);
        final ClientSubscription response = subscriptionService.updateSubscription(
                clientSubscriptionId, clientSubscriptionRequest, clientId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ClientSubscription> getClientSubscription(
            final UUID clientSubscriptionId,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("getClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, clientId);
        final ClientSubscription response = subscriptionService.getSubscription(clientSubscriptionId, clientId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> deleteClientSubscription(
            final UUID clientSubscriptionId,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("deleteClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, clientId);
        subscriptionService.deleteSubscription(clientSubscriptionId, clientId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    public ResponseEntity<EventTypeResponse> getEventTypes() {
        final EventTypeResponse eventTypes = eventTypeService.getAllEventTypes();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(eventTypes);
    }
}
