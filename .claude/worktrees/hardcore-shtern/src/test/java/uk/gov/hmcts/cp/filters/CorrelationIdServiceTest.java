package uk.gov.hmcts.cp.filters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CorrelationIdServiceTest {

    @InjectMocks
    CorrelationIdService correlationIdService;

    @Test
    void random_should_return_uuid() {
        assertThat(correlationIdService.random()).isNotNull();
    }

    @Test
    void random_should_return_string() {
        assertThat(correlationIdService.randomString()).isNotNull();
    }
}