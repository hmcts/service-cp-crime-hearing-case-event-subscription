package uk.gov.hmcts.cp.subscription.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    @Test
    void developer_environment_should_follow_switch() {
        assertThat(new AppProperties(EnvironmentName.DEVELOPER, false).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.DEVELOPER, true).isHearingEventJsonEnabledInEnv()).isTrue();
    }

    @Test
    void dev_environment_should_follow_switch() {
        assertThat(new AppProperties(EnvironmentName.DEV, false).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.DEV, true).isHearingEventJsonEnabledInEnv()).isTrue();
    }

    @Test
    void sit_environment_should_follow_switch() {
        assertThat(new AppProperties(EnvironmentName.SIT, false).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.SIT, true).isHearingEventJsonEnabledInEnv()).isTrue();
    }

    @Test
    void prp_environment_should_always_be_off() {
        assertThat(new AppProperties(EnvironmentName.PRP, false).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.PRP, true).isHearingEventJsonEnabledInEnv()).isFalse();
    }

    @Test
    void prd_environment_should_always_be_off() {
        assertThat(new AppProperties(EnvironmentName.PRD, false).isHearingEventJsonEnabledInEnv()).isFalse();
        assertThat(new AppProperties(EnvironmentName.PRD, true).isHearingEventJsonEnabledInEnv()).isFalse();
    }
}
