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

    public int getDelayMsecs(final int failureCount) {
        final List<Integer> retryConfig = retryServiceConfig.getRetryDelayMsecs();
        final int retryIndex = failureCount < retryConfig.size() ? failureCount : retryConfig.size() - 1;
        final int retryDelayMsecs = retryConfig.get(retryIndex);
        log.info("retry delay {} mSsecs", retryDelayMsecs);
        return retryDelayMsecs;
    }

    public OffsetDateTime getNextTryTime(final int failureCount) {
        return clockService.nowOffsetUTC().plus(Duration.ofMillis(getDelayMsecs(failureCount)));
    }
}
