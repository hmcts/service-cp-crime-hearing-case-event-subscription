package uk.gov.hmcts.cp.subscription.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.owasp.encoder.Encode;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.hmac.services.HmacSigningService;
import uk.gov.hmcts.cp.audit.annotation.AuditExclude;
import uk.gov.hmcts.cp.vault.SecretStoreServiceInterface;

import java.security.InvalidKeyException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper.KEY_ID_HEADER;
import static uk.gov.hmcts.cp.subscription.model.EventNotificationPayloadWrapper.SIGNATURE_HEADER;

@RestController
@RequiredArgsConstructor
@Slf4j
@AuditExclude
@ConditionalOnExpression(
    "'${environment.name}' == 'DEVELOPER' || '${environment.name}' == 'DEV' || '${environment.name}' == 'SIT'"
)
public class MockCallbackController {

    private final SecretStoreServiceInterface secretStoreService;
    private final HmacSigningService hmacSigningService;
    private final EncodingService encodingService;

    private final List<String> receivedCallbacks = new CopyOnWriteArrayList<>();

    @PostMapping("/mock-callback")
    public ResponseEntity<Void> mockCallback(
            @RequestHeader(KEY_ID_HEADER) final String keyId,
            @RequestHeader(SIGNATURE_HEADER) final String signature,
            @RequestBody final String body) {

        log.info("Received mock callback for keyId:{}", Encode.forJava(keyId));

        final String encodedSecret = secretStoreService.getSecret(keyId)
                .orElse(null);

        if (encodedSecret == null) {
            log.warn("No secret found for keyId:{}", Encode.forJava(keyId));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            final byte[] secret = encodingService.decodeFromBase64(encodedSecret);
            hmacSigningService.validateSignature(secret, body, signature);
            receivedCallbacks.add(body);
            log.info("Signature validated successfully for keyId:{} total received:{}", Encode.forJava(keyId), receivedCallbacks.size());
            return ResponseEntity.ok().build();
        } catch (InvalidKeyException e) {
            log.warn("Signature validation failed for keyId:{}", Encode.forJava(keyId));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping(value = "/mock-callback/received", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getReceivedCallbacks() {
        final String json = "[" + String.join(",", receivedCallbacks) + "]";
        log.info("Returning {} received callbacks", receivedCallbacks.size());
        return ResponseEntity.ok(json);
    }

    @DeleteMapping("/mock-callback/received")
    public ResponseEntity<Void> clearReceivedCallbacks() {
        receivedCallbacks.clear();
        log.info("Cleared received callbacks");
        return ResponseEntity.ok().build();
    }
}
