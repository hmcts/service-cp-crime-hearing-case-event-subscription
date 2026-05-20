package uk.gov.hmcts.cp.servicebus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RetryServiceConfig.class)
@TestPropertySource(properties = {
        "service-bus.retry-durations=500ms,30s,5m,1h,4h"
})
class RetryServiceConfigTest {

    @Autowired
    RetryServiceConfig retryServiceConfig;

    @Test
    void retry_durations_should_parse_all_time_units_correctly() {
        assertThat(retryServiceConfig.getRetryDelays()).containsExactly(
                Duration.ofMillis(500),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                Duration.ofHours(4)
        );
    }

    @Test
    void should_fail_startup_when_retry_durations_is_empty() {
        assertThatThrownBy(() ->
                new RetryServiceConfig(java.util.List.of())
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void should_fail_startup_when_retry_durations_contains_negative_duration() {
        assertThatThrownBy(() ->
                new RetryServiceConfig(java.util.List.of(Duration.ofSeconds(1), Duration.ofSeconds(-1)))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain negative durations");
    }
}
