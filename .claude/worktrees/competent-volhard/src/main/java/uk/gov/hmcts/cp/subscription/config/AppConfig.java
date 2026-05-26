package uk.gov.hmcts.cp.subscription.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.subscription.services.ClockService;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;
import uk.gov.hmcts.cp.subscription.util.JwtTokenParser;

import java.time.Clock;
import java.util.List;

@Configuration
public class AppConfig {

    @Bean
    public JwtTokenParser jwtTokenParser(final JsonMapper jsonMapper) {
        return new JwtTokenParser(jsonMapper);
    }

    @Bean
    public RestTemplate restTemplate(final OutboundTracingInterceptor outboundCorrelationIdInterceptor) {
        final RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(outboundCorrelationIdInterceptor));
        return restTemplate;
    }

    @Bean
    public ClockService clockService() {
        return new ClockService(Clock.systemDefaultZone());
    }
}
