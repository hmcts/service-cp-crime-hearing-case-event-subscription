package uk.gov.hmcts.cp.subscription.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.STE;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.DEV;
import static uk.gov.hmcts.cp.subscription.config.EnvironmentName.DEVELOPER;

@Getter
@Service
@Slf4j
public class AppProperties {

    private final EnvironmentName environmentName;
    private final boolean hearingEventJsonEnabledInEnv;

    public AppProperties(
            @Value("${environment.name}") final EnvironmentName environmentName,
            @Value("${hearing-event.json.enabled}") final boolean hearingEventJsonEnabled) {
        this.environmentName = environmentName;
        this.hearingEventJsonEnabledInEnv = hearingEventJsonEnabled &&
                (environmentName == DEVELOPER || environmentName == STE || environmentName == DEV);
        log.info("Initialised AppProperties with environmentName:{}", environmentName);
        log.info("Initialised AppProperties with hearingEventJsonEnabledInEnv:{}", this.hearingEventJsonEnabledInEnv);
    }
}
