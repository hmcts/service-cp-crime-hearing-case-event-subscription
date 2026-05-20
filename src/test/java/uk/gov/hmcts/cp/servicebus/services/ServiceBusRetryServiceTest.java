package uk.gov.hmcts.cp.servicebus.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.servicebus.config.RetryServiceConfig;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceBusRetryServiceTest {
    @Mock
    RetryServiceConfig retryServiceConfig;
    @Mock
    ClockService clockService;

    @InjectMocks
    ServiceBusRetryService retryService;

    @Test
    void retry_delay_should_be_correct() {
        when(retryServiceConfig.getRetryDelays()).thenReturn(List.of(Duration.ofMillis(1), Duration.ofMillis(2)));

        assertThat(retryService.getRetryDelay(0)).isEqualTo(Duration.ofMillis(1));
        assertThat(retryService.getRetryDelay(1)).isEqualTo(Duration.ofMillis(2));
        assertThat(retryService.getRetryDelay(2)).isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void retry_time_should_be_correct() {
        OffsetDateTime now = OffsetDateTime.now();
        when(retryServiceConfig.getRetryDelays()).thenReturn(List.of(Duration.ofMillis(1), Duration.ofHours(1)));
        when(clockService.nowOffsetUTC()).thenReturn(now);

        assertThat(retryService.getNextTryTime(0)).isEqualTo(now.plus(Duration.ofMillis(1)));
        assertThat(retryService.getNextTryTime(1)).isEqualTo(now.plus(Duration.ofHours(1)));
        assertThat(retryService.getNextTryTime(2)).isEqualTo(now.plus(Duration.ofHours(1)));
    }
}
