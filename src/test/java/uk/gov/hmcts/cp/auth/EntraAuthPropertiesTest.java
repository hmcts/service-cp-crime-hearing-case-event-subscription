package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.config.EnvironmentName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntraAuthPropertiesTest {

    private static final String TENANT = "e2995d11-9947-4e78-9de6-d44e0603518e";
    private static final String AUDIENCE = "bff71afb-9651-445e-bec7-158796787815";

    @ParameterizedTest
    @EnumSource(value = EnvironmentName.class, names = {"DEV", "STE", "SIT", "PRP", "PRD"})
    @DisplayName("a non-enforcing mode fails startup in a deployed environment")
    void nonEnforcingModeIsRejectedInDeployedEnvironments(final EnvironmentName environment) {
        assertThatThrownBy(() -> build(AuthMode.OBSERVE, TENANT, AUDIENCE, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not permitted in environment");

        assertThatThrownBy(() -> build(AuthMode.OFF, TENANT, AUDIENCE, environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EnvironmentName.class, names = {"DEVELOPER", "UNKNOWN"})
    @DisplayName("a non-enforcing mode remains available only off-platform — a developer machine or an unset environment")
    void nonEnforcingModeIsAllowedOutsideDeployedEnvironments(final EnvironmentName environment) {
        assertThatCode(() -> build(AuthMode.OBSERVE, TENANT, AUDIENCE, environment))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a blank audience fails startup rather than accepting tokens for any resource")
    void blankAudienceFailsStartup() {
        assertThatThrownBy(() -> build(AuthMode.ENFORCE, TENANT, "", EnvironmentName.DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.audience must be set");
    }

    @Test
    void blankTenantFailsStartup() {
        assertThatThrownBy(() -> build(AuthMode.ENFORCE, "", AUDIENCE, EnvironmentName.DEV))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.tenant-id must be set");
    }

    @Test
    @DisplayName("issuer and jwks uri are derived from the tenant when not set explicitly")
    void derivesIssuerAndJwksUriFromTenant() {
        final EntraAuthProperties properties = build(AuthMode.ENFORCE, TENANT, AUDIENCE, EnvironmentName.DEV);

        assertThat(properties.getIssuer())
                .isEqualTo("https://login.microsoftonline.com/" + TENANT + "/v2.0");
        assertThat(properties.getJwksUri())
                .isEqualTo("https://login.microsoftonline.com/" + TENANT + "/discovery/v2.0/keys");
    }

    @Test
    @DisplayName("clock skew is capped so a large value cannot effectively disable expiry")
    void clockSkewIsCapped() {
        final EntraAuthProperties properties = new EntraAuthProperties(
                AuthMode.ENFORCE, TENANT, AUDIENCE, "", "", 86_400, 600,
                new AppProperties(EnvironmentName.DEV, false));

        assertThat(properties.getClockSkewSeconds()).isEqualTo(300);
    }

    private static EntraAuthProperties build(final AuthMode mode,
                                             final String tenantId,
                                             final String audience,
                                             final EnvironmentName environment) {
        return new EntraAuthProperties(mode, tenantId, audience, "", "", 60, 600,
                new AppProperties(environment, false));
    }
}
