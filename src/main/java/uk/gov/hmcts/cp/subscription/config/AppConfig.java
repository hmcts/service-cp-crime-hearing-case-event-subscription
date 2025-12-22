package uk.gov.hmcts.cp.subscription.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }
}
