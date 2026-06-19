package uk.gov.hmcts.cp.subscription.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    @Test
    void developer_environment_should_follow_switch() {
        assertThat(new AppProperties(EnvironmentName.DEVELOPER, false, true).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.DEVELOPER, true, true).isHearingEventJsonEnabledInEnv()).isTrue();
    }

    @Test
    void dev_environment_should_follow_switch() {
        assertThat(new AppProperties(EnvironmentName.DEV, false, true).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.DEV, true, true).isHearingEventJsonEnabledInEnv()).isTrue();
    }

    @Test
    void sit_environment_should_always_be_off() {
        assertThat(new AppProperties(EnvironmentName.SIT, false, true).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.SIT, true, true).isHearingEventJsonEnabledInEnv()).isFalse();
    }

    @Test
    void prp_environment_should_always_be_off() {
        assertThat(new AppProperties(EnvironmentName.PRP, false, true).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.PRP, true, true).isHearingEventJsonEnabledInEnv()).isFalse();
    }

    @Test
    void prd_environment_should_always_be_off() {
        assertThat(new AppProperties(EnvironmentName.PRD, false, true).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.PRD, true, true).isHearingEventJsonEnabledInEnv()).isFalse();
    }

    @Test
    void hearing_event_enabled_should_reflect_the_configured_flag() {
        assertThat(new AppProperties(EnvironmentName.DEV, false, true).isHearingEventEnabled()).isTrue();
        assertThat(new AppProperties(EnvironmentName.DEV, false, false).isHearingEventEnabled()).isFalse();
    }
}
