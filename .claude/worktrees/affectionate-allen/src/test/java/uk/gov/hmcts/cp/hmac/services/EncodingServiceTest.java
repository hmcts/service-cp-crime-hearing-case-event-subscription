package uk.gov.hmcts.cp.hmac.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EncodingServiceTest {

    @InjectMocks
    EncodingService encodingService;

    @Test
    void bytes_should_encode_and_decode() {
        byte[] bytes = "A secret".getBytes(StandardCharsets.UTF_8);
        String encoded = encodingService.encodeWithBase64(bytes);
        byte[] bytesAgain = encodingService.decodeFromBase64(encoded);
        assertThat(bytesAgain).isEqualTo(bytes);
    }
}