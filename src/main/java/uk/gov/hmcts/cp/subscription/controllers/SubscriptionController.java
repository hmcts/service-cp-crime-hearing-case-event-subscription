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
import uk.gov.hmcts.cp.openapi.model.HmacCredentials;
import uk.gov.hmcts.cp.openapi.model.RotateSecretRequest;
import uk.gov.hmcts.cp.subscription.services.EventTypeService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionService;
import uk.gov.hmcts.cp.subscription.services.SubscriptionValidationService;

import java.util.UUID;

import static uk.gov.hmcts.cp.filters.ClientIdResolutionFilter.MDC_CLIENT_ID;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController implements SubscriptionApi {

    private final SubscriptionService subscriptionService;
    private final EventTypeService eventTypeService;
    private final SubscriptionValidationService subscriptionValidationService;

    @Override
    public ResponseEntity<ClientSubscription> createClientSubscription(
            final ClientSubscriptionRequest clientSubscriptionRequest,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("createClientSubscription callbackUrl:{} clientId:{}",
                Encode.forJava(clientSubscriptionRequest.getNotificationEndpoint().getCallbackUrl()), clientId);
        subscriptionValidationService.validateClientDoesNotExist(clientId);
        final ClientSubscription response = subscriptionService.createClientSubscription(clientSubscriptionRequest, clientId);
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
        subscriptionValidationService.validateClientSubscriptionExists(clientId, clientSubscriptionId);
        final ClientSubscription response = subscriptionService.updateClientSubscription(clientId, clientSubscriptionId, clientSubscriptionRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ClientSubscription> getClientSubscription(
            final UUID clientSubscriptionId,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("getClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, clientId);
        subscriptionValidationService.validateClientSubscriptionExists(clientId, clientSubscriptionId);
        final ClientSubscription response = subscriptionService.getClientSubscription(clientId, clientSubscriptionId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> deleteClientSubscription(
            final UUID clientSubscriptionId,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("deleteClientSubscription clientSubscriptionId:{} clientId:{}", clientSubscriptionId, clientId);
        subscriptionValidationService.validateClientSubscriptionExists(clientId, clientSubscriptionId);
        subscriptionService.deleteClientSubscription(clientId, clientSubscriptionId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    public ResponseEntity<HmacCredentials> rotateClientSubscriptionSecret(
            final UUID clientSubscriptionId,
            final RotateSecretRequest rotateSecretRequest,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(MDC_CLIENT_ID));
        log.info("rotateClientSubscriptionSecret clientSubscriptionId:{} clientId:{}", clientSubscriptionId, clientId);
        subscriptionValidationService.validateClientSubscriptionExists(clientId, clientSubscriptionId);
        final HmacCredentials response = subscriptionService.rotateSubscriptionSecret(clientId, clientSubscriptionId, rotateSecretRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EventTypeResponse> getEventTypes() {
        final EventTypeResponse eventTypes = eventTypeService.getAllEventTypes();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(eventTypes);
    }
}