package uk.gov.hmcts.cp.subscription.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static java.util.Objects.nonNull;

@Slf4j
@RequiredArgsConstructor
public class JwtTokenParser {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AZP_JSON_POINTER = "/azp";

    private final JsonMapper jsonMapper;

    public UUID extractClientIdFromToken(final HttpServletRequest request) {
        final String authHeader = nonNull(request) ? request.getHeader("Authorization") : null;
        final String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        final String[] chunks = token.split("\\.");
        final Base64.Decoder decoder = Base64.getUrlDecoder();
        final String payloadJson = new String(decoder.decode(chunks[1]), StandardCharsets.UTF_8);
        final UUID clientId = jsonMapper.getUUIDAtPath(payloadJson, AZP_JSON_POINTER);
        log.info("Authorization extracted client ID {} from JWT azp claim", clientId);
        return clientId;
    }
}
