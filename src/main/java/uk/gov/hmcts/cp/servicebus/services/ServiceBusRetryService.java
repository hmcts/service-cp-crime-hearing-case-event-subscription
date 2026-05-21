package uk.gov.hmcts.cp.servicebus.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.servicebus.config.RetryServiceConfig;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@AllArgsConstructor
@Service
public class ServiceBusRetryService {

    private RetryServiceConfig retryServiceConfig;
    private ClockService clockService;

    public Duration getRetryDelay(final int failureCount) {
        final List<Duration> retryConfig = retryServiceConfig.getRetryDelays();
        final int retryIndex = failureCount < retryConfig.size() ? failureCount : retryConfig.size() - 1;
        final Duration retryDelay = retryConfig.get(retryIndex);
        log.info("retry delay {}", retryDelay);
        return retryDelay;
    }

    public OffsetDateTime getNextTryTime(final int failureCount) {
        return clockService.nowOffsetUTC().plus(getRetryDelay(failureCount));
    }
}
