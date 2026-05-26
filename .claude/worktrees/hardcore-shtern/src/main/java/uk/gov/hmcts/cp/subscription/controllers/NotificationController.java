package uk.gov.hmcts.cp.subscription.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import uk.gov.hmcts.cp.filters.ClientIdResolutionFilter;
import uk.gov.hmcts.cp.openapi.api.InternalApi;
import uk.gov.hmcts.cp.openapi.api.NotificationApi;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService;
import uk.gov.hmcts.cp.servicebus.services.ServiceBusClientService;
import uk.gov.hmcts.cp.subscription.managers.NotificationManager;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;
import static uk.gov.hmcts.cp.servicebus.config.ServiceBusConfigService.PCR_INBOUND_QUEUE;

/**
 * Handles PCR notification events and document retrieval for subscribers.
 * Delegates orchestration to NotificationManager; builds HTTP responses only.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class NotificationController implements InternalApi, NotificationApi {

    private final ServiceBusConfigService serviceBusConfig;
    private final ServiceBusClientService clientService;
    private final NotificationManager notificationManager;
    private final JsonMapper jsonMapper;
    private final HttpServletRequest httpRequest;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    @Override
    public ResponseEntity<Void> createNotification(
            @Valid @RequestBody final EventPayload eventPayload,
            // note the X-Correlation-Id is handled by TracingFilter but we need to declare it because its in the spec
            @RequestHeader(value = "X-Correlation-Id", required = false) final UUID xCorrelationId) {
        log.info("Received notification request from Progression/HearingNows Service - eventId: {}, materialId: {}, eventType: {}",
                eventPayload.getEventId(),
                eventPayload.getMaterialId(),
                eventPayload.getEventType());
        if (serviceBusConfig.isEnabled()) {
            final String pcrEventjson = jsonMapper.toJson(eventPayload);
            clientService.queueMessage(PCR_INBOUND_QUEUE, null, pcrEventjson, 0);
        } else {
            notificationManager.processPcrNotification(eventPayload);
        }
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @Override
    public ResponseEntity<Resource> getDocument(
            @NotNull @PathVariable("clientSubscriptionId") final UUID clientSubscriptionId,
            @NotNull @PathVariable("documentId") final UUID documentId,
            @RequestHeader(value = CORRELATION_ID_KEY, required = false) final UUID xCorrelationId) {
        final UUID clientId = UUID.fromString(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID));
        log.info("getDocument request clientId:{} clientSubscriptionId:{} documentId:{}", clientId, clientSubscriptionId, documentId);
        final DocumentContent content = notificationManager.getPcrDocumentContent(clientSubscriptionId, clientId, documentId);
        final Resource resource = new ByteArrayResource(content.getBody());
        final HttpHeaders headers = getHttpHeaders(content);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    private @NonNull HttpHeaders getHttpHeaders(final DocumentContent content) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(content.getContentType());
        headers.setContentLength(content.getBody().length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodeFileName(content.getFileName()) + "\"");
        return headers;
    }

    private String encodeFileName(final String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", " ");
    }
}
