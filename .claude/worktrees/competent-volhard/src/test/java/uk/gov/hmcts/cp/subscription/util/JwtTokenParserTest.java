package uk.gov.hmcts.cp.subscription.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenParserTest {

    private static final String AZP_UUID = "120d6d4a-d47b-4a40-a782-0065f41de050";

    @Mock
    private HttpServletRequest request;

    private final JsonMapper jsonMapper = new JsonMapper();
    private final JwtTokenParser parser = new JwtTokenParser(jsonMapper);

    @Test
    void null_clientid_should_throw_invalid_uuid_error() {
        String token = buildTestToken(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        Exception e = assertThrows(Exception.class, () -> parser.extractClientIdFromToken(request));
        assertThat(e.getMessage()).isEqualTo("Invalid UUID string: null");
    }

    @Test
    void jwt_token_with_azp_is_extracted() {
        String token = buildTestToken(AZP_UUID);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        UUID clientId = parser.extractClientIdFromToken(request);

        assertThat(clientId).isEqualTo(UUID.fromString(AZP_UUID));
    }

    private static String buildTestToken(String azpValue) {
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String payload = "{\"azp\":\"" + azpValue + "\"}";
        String headerB64 = Base64.getUrlEncoder().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return headerB64 + "." + payloadB64 + ".sig";
    }
}
