package uk.gov.hmcts.cp.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import uk.gov.hmcts.cp.openapi.api.InternalApi;
import uk.gov.hmcts.cp.openapi.api.NotificationApi;
import uk.gov.hmcts.cp.openapi.api.SubscriptionApi;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationPolicyTest {

    /**
     * Every path the OpenAPI contract publishes, read by reflection from the generated API
     * interfaces rather than restated here. A new operation therefore arrives in this suite on the
     * next contract bump, with no list to remember to update.
     *
     * <p>The source matters: these come from the <i>contract</i>, which is independent of
     * {@link AuthorizationPolicy}. Deriving them from the policy's own exemption lists instead would
     * make the deny-by-default test tautological — widen an exemption and it would keep passing.
     */
    static Stream<String> contractPaths() {
        return Stream.of(SubscriptionApi.class, NotificationApi.class, InternalApi.class)
                .flatMap(api -> Arrays.stream(api.getMethods()))
                .map(AuthorizationPolicyTest::mappedPaths)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .map(AuthorizationPolicyTest::withConcreteIds)
                .distinct()
                .sorted();
    }

    /** The contract paths a consuming client calls — everything except the internal endpoint. */
    static Stream<String> clientFacingContractPaths() {
        return contractPaths().filter(path -> !INTERNAL_PATH.equals(path));
    }

    private static String[] mappedPaths(final Method method) {
        // findMergedAnnotation also resolves @GetMapping and friends, which are meta-annotated
        // with @RequestMapping, so this does not depend on which form the generator emitted.
        final RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        return mapping == null || mapping.value().length == 0 ? null : mapping.value();
    }

    /** {@code /client-subscriptions/{id}} is never a real request URI; the filter sees a concrete one. */
    private static String withConcreteIds(final String templated) {
        return PATH_VARIABLE.matcher(templated).replaceAll(CONCRETE_ID);
    }

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");
    private static final String CONCRETE_ID = "11111111-1111-1111-1111-111111111111";

    /** Called by Progression / Hearing NOWs, not by a consuming client. Exempt by decision. */
    private static final String INTERNAL_PATH = "/notifications";

    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    @DisplayName("path discovery actually found the contract, so the deny-by-default test cannot pass vacuously")
    void contractDiscoveryIsNotEmpty() {
        // Without this, a generator change that stopped emitting @RequestMapping would empty the
        // stream and every parameterised case below would silently pass having asserted nothing.
        assertThat(contractPaths()).contains("/event-types", "/client-subscriptions", INTERNAL_PATH);
        assertThat(clientFacingContractPaths()).hasSizeGreaterThanOrEqualTo(6);
    }

    @ParameterizedTest
    @MethodSource("clientFacingContractPaths")
    @DisplayName("deny by default: every client-facing path in the contract requires a token")
    void everyClientFacingPathRequiresAToken(final String path) {
        assertThat(policy.isExemptFromValidation(path))
                .withFailMessage("path %s must require a token", path)
                .isFalse();
    }

    @Test
    @DisplayName("/event-types is protected — the old prefix-based filter silently skipped it")
    void eventTypesIsProtected() {
        assertThat(policy.isExemptFromValidation("/event-types")).isFalse();
    }

    @Test
    @DisplayName("the inbound notification endpoint is exempt — an internal call, not token-validated")
    void internalNotificationEndpointIsExempt() {
        // Called by Progression / Hearing NOWs. Excluded by decision: internal calls are not
        // token-validated in this estate. Protected by network controls, not by this service.
        assertThat(policy.isExemptFromValidation(INTERNAL_PATH)).isTrue();
    }

    @Test
    @DisplayName("the exemption is exact — a client-facing path under the same prefix is still protected")
    void notificationExemptionDoesNotLeak() {
        assertThat(policy.isExemptFromValidation("/notifications/../client-subscriptions")).isFalse();
        assertThat(policy.isExemptFromValidation("/notificationsx")).isFalse();
        assertThat(policy.isExemptFromValidation("/notifications/anything")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/",
        "/actuator",
        "/actuator/health",
        "/actuator/health/",
        "/actuator/prometheus",
        "/mock-callback",
        "/mock-callback/received"
    })
    void publicPathsDoNotRequireAToken(final String path) {
        assertThat(policy.isExemptFromValidation(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator-evil",
        "/actuatorx/health",
        "/mock-callbackx",
        "/client-subscriptions"
    })
    @DisplayName("a path that merely starts with a public root is not public")
    void publicPrefixDoesNotLeakToNeighbouringPaths(final String path) {
        assertThat(policy.isExemptFromValidation(path)).isFalse();
    }

    // ------------------------------------------------------------------ roles

    @ParameterizedTest
    @ValueSource(strings = {AuthorizationPolicy.ROLE_READ, AuthorizationPolicy.ROLE_WRITE})
    @DisplayName("either role Entra issues satisfies any operation, matching the deployed APIM policy")
    void anyKnownRoleIsAccepted(final String role) {
        assertThatCode(() -> policy.assertAuthorized(caller(role))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("app.read alone may write — read/write separation is not enforced here")
    void readRoleMayWrite() {
        // Deliberate: app.write is not yet assigned to the calling clients, so requiring it would
        // 403 every write. Separation belongs in an operation-scoped APIM policy once it is.
        assertThatCode(() -> policy.assertAuthorized(caller(AuthorizationPolicy.ROLE_READ)))
                .doesNotThrowAnyException();
    }

    @Test
    void callerWithAnUnrecognisedRoleIsRejected() {
        assertThatThrownBy(() -> policy.assertAuthorized(caller("app.somethingelse")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(ex -> ((TokenValidationException) ex).getReason())
                .isEqualTo(TokenValidationException.Reason.INSUFFICIENT_ROLE);
    }

    @Test
    void callerWithNoRolesIsRejected() {
        assertThatThrownBy(() -> policy.assertAuthorized(new ValidatedCaller(UUID.randomUUID(), List.of(), true)))
                .isInstanceOf(TokenValidationException.class);
    }

    @Test
    @DisplayName("only app.read and app.write are recognised")
    void onlyTwoRolesExist() {
        assertThat(policy.knownRoles())
                .containsExactlyInAnyOrder(AuthorizationPolicy.ROLE_READ, AuthorizationPolicy.ROLE_WRITE);
    }

    private static ValidatedCaller caller(final String... roles) {
        return new ValidatedCaller(UUID.randomUUID(), List.of(roles), true);
    }
}
