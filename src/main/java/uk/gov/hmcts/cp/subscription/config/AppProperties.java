package uk.gov.hmcts.cp.subscription.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Getter
@Service
@Slf4j
public class AppProperties {

    private final EnvironmentName environmentName;
    private final boolean hearingEventDisabledInDev;
    private final boolean hearingEventJsonEnabled;

    public AppProperties(
            @Value("${environment.name}") final EnvironmentName environmentName,
            @Value("${hearing-event.disabled-in-dev}") final boolean hearingEventDisabledInDev,
            @Value("${hearing-event.json.enabled}") final boolean hearingEventJsonEnabled
) {
        this.environmentName = environmentName;
        this.hearingEventDisabledInDev = hearingEventDisabledInDev;
        this.hearingEventJsonEnabled = hearingEventJsonEnabled;
        log.info("Initialised AppProperties with environmentName:{}", environmentName);
        log.info("Initialised AppProperties with hearingEventDisabledInDev:{}", this.hearingEventDisabledInDev);
        log.info("Initialised AppProperties with hearingEventJsonEnabled:{}", this.hearingEventJsonEnabled);
    }
}
