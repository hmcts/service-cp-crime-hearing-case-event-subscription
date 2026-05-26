package uk.gov.hmcts.cp.servicebus.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Getter
public class RetryServiceConfig {
    private final List<Integer> retryDelayMsecs;

    public RetryServiceConfig(
            @Value("${service-bus.retry-msecs}") final List<Integer> retryDelayMsecs
    ) {
        log.info("RetryConfigService using retryDelay mSecs {}", retryDelayMsecs);
        this.retryDelayMsecs = retryDelayMsecs;
    }
}
