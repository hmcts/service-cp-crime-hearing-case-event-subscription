package uk.gov.hmcts.cp.hmac.services;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.security.InvalidKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class HmacSigningServiceTest {
    @Mock
    VaultServiceProperties vaultServiceProperties;

    @InjectMocks
    HmacKeyService hmacKeyService;
    @InjectMocks
    HmacSigningService hmacSigningService;
    @InjectMocks
    EncodingService encodingService;

    String message = "A message";

    @Test
    void real_signature_and_message_should_match() throws InvalidKeyException {
        // Message sent at 14:30:07
        KeyPair keyPair = hmacKeyService.generateKey();
        String message = "{\"cases\":[{\"urn\":\"AAA20713684\"}],\"masterDefendantId\":\"d5ad03d0-5f2d-477b-9aa8-e0beb68f46a3\",\"defendantName\":\"Sarogav Noem\",\"defendantDateOfBirth\":\"2008-12-08\",\"documentId\":\"6deab43e-70f2-4d8e-bc28-04ab39fbeff1\",\"documentGeneratedTimestamp\":\"2026-03-20T14:29:15.453071625Z\",\"prisonEmailAddress\":\"yoiashfield.premiercustody@premier-serco.cjsm.net\"}";
        String signature = hmacSigningService.sign(keyPair.getSecret(), message);
        hmacSigningService.validateSignature(keyPair.getSecret(), message, signature);
        assertThat(signature).isEqualTo("mevCvmfiXXoHoeRbvgat8PtHjnAvOgnXMMUgZ+BKmTE=");

        String encodedSecret = encodingService.encodeWithBase64(keyPair.getSecret());
        assertThat(encodedSecret).isEqualTo("U3R1YiBzdHJpbmcgdXNlZCBwdXJlbHkgZm9yIGRldmVsb3BtZW50IHB1cnBvc2VzLiBUbyBiZSBzZWN1cmVkLg==");
    }

    @Test
    void validate_should_verify_ok() throws InvalidKeyException {
        KeyPair keyPair = hmacKeyService.generateKey();
        String signature = hmacSigningService.sign(keyPair.getSecret(), message);
        log.info("Signature:{}", signature);
        assertThat(signature).isEqualTo("TkkWx3X55YaWF5KB2BwSY4LcoDnFqZFOMrB43hkuFkE=");
        hmacSigningService.validateSignature(keyPair.getSecret(), message, signature);
        // no exception
    }

    @Test
    void validate_should_throw_if_bad_signature() throws InvalidKeyException {
        KeyPair keyPair = hmacKeyService.generateKey();
        assertThrows(InvalidKeyException.class, () -> hmacSigningService.validateSignature(keyPair.getSecret(), message, "bad-signature"));
    }

    @Test
    void validate_should_throw_if_bad_secret() throws InvalidKeyException {
        KeyPair keyPair = hmacKeyService.generateKey();
        String signature = hmacSigningService.sign(keyPair.getSecret(), message);
        assertThat(signature).isEqualTo("TkkWx3X55YaWF5KB2BwSY4LcoDnFqZFOMrB43hkuFkE=");
        assertThrows(InvalidKeyException.class, () -> hmacSigningService.validateSignature("bad-=secret".getBytes(), message, signature));
    }
}
