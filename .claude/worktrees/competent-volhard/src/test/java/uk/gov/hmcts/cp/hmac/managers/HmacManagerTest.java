package uk.gov.hmcts.cp.hmac.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.hmac.services.EncodingService;
import uk.gov.hmcts.cp.hmac.services.HmacKeyService;
import uk.gov.hmcts.cp.hmac.services.HmacSigningService;
import uk.gov.hmcts.cp.vault.SecretStoreServiceAzureImpl;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HmacManagerTest {

    @Mock
    HmacKeyService hmacKeyService;
    @Mock
    SecretStoreServiceAzureImpl secretStoreService;
    @Mock
    EncodingService encodingService;
    @Mock
    HmacSigningService hmacSigningService;

    @InjectMocks
    HmacManager hmacManager;

    String keyId = "kid-v1";

    @Test
    void create_new_key_should_return_key_pair() {
        KeyPair keyPair = KeyPair.builder().keyId(keyId).secret("secret".getBytes()).build();
        when(hmacKeyService.generateKey()).thenReturn(keyPair);
        when(encodingService.encodeWithBase64(keyPair.getSecret())).thenReturn("encoded-secret");
        hmacManager.createAndStoreNewKey();
        verify(secretStoreService).setSecret(keyId, "encoded-secret");
    }

    @Test
    void get_signature_should_return_signature() {
        when(secretStoreService.getSecret(keyId)).thenReturn(Optional.of("encodedSecret"));
        when(encodingService.decodeFromBase64("encodedSecret")).thenReturn("secret".getBytes());
        when(hmacSigningService.sign("secret".getBytes(), "payload")).thenReturn("signature");

        String signature = hmacManager.getSignature(keyId, "payload");

        assertThat(signature).isEqualTo("signature");
    }
}