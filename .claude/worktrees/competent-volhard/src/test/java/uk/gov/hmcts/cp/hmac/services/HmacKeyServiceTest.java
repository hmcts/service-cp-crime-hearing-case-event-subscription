package uk.gov.hmcts.cp.hmac.services;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.hmac.services.HmacKeyService.STUB_KEY_ID;
import static uk.gov.hmcts.cp.hmac.services.HmacKeyService.STUB_SECRET_STRING;

@Slf4j
@ExtendWith(MockitoExtension.class)
class HmacKeyServiceTest {

    @Mock
    private VaultServiceProperties vaultServiceProperties;

    @InjectMocks
    private HmacKeyService service;

    private static final int KEY_PAIR_ATTEMPTS = 100000;

    @Test
    void generateKey_should_generate_random_when_vault_enabled() {
        when(vaultServiceProperties.isVaultEnabled()).thenReturn(true);
        KeyPair keyPair = service.generateKey();
        assertThat(keyPair.getKeyId()).matches("^kid-v1-([a-z0-9\\-]{36})$");
        assertThat(keyPair.getSecret()).hasSize(32);
    }

    @Test
    void generateKey_should_return_distinct_when_vault_enabled() {
        PrintStream originalStdOut = System.out;
        captureStdOut();
        given(vaultServiceProperties.isVaultEnabled()).willReturn(true);
        Set<String> keyIds = new HashSet<>();
        Set<String> secrets = new HashSet<>();
        for (int n = 0; n < KEY_PAIR_ATTEMPTS; n++) {
            KeyPair keyPair = service.generateKey();
            keyIds.add(keyPair.getKeyId());
            secrets.add(new String(keyPair.getSecret()));
        }
        assertThat(keyIds).hasSize(KEY_PAIR_ATTEMPTS);
        assertThat(secrets).hasSize(KEY_PAIR_ATTEMPTS);
        System.setOut(originalStdOut);
    }

    @Test
    void generateKey_should_always_return_same_when_vault_disabled() {
        Set<String> keyIds = new HashSet<>();
        Set<String> secrets = new HashSet<>();
        for (int n = 0; n < KEY_PAIR_ATTEMPTS; n++) {
            KeyPair keyPair = service.generateKey();
            keyIds.add(keyPair.getKeyId());
            secrets.add(new String(keyPair.getSecret()));
        }
        assertThat(keyIds).hasSize(1);
        assertThat(secrets).hasSize(1);
    }

    @Test
    void generateKey_should_use_hardcoded_when_vault_disabled() {
        KeyPair keyPair = service.generateKey();
        assertThat(keyPair.getKeyId()).isEqualTo(STUB_KEY_ID);
        assertThat(keyPair.getSecret()).isEqualTo(STUB_SECRET_STRING.getBytes(StandardCharsets.UTF_8));
        String encodedSecret = new EncodingService().encodeWithBase64(keyPair.getSecret());
        assertThat(encodedSecret).isEqualTo("U3R1YiBzdHJpbmcgdXNlZCBwdXJlbHkgZm9yIGRldmVsb3BtZW50IHB1cnBvc2VzLiBUbyBiZSBzZWN1cmVkLg==");
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut, true, StandardCharsets.UTF_8));
        return capturedStdOut;
    }
}

