package uk.gov.hmcts.cp.subscription.http.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JwtHelper {

    public static String bearerTokenWithAzp(final UUID clientId) {
        final String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        final String payload = "{\"azp\":\"" + clientId + "\"}";
        final String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        final String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + headerB64 + "." + payloadB64 + ".sig";
    }
}