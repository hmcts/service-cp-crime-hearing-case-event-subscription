package uk.gov.hmcts.cp.filters;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.gov.hmcts.cp.auth.AuthMode;
import uk.gov.hmcts.cp.auth.AuthorizationPolicy;
import uk.gov.hmcts.cp.auth.EntraAuthProperties;
import uk.gov.hmcts.cp.auth.EntraTokenValidator;
import uk.gov.hmcts.cp.auth.TokenValidationException;
import uk.gov.hmcts.cp.auth.TokenValidationException.Reason;
import uk.gov.hmcts.cp.auth.ValidatedCaller;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.config.EnvironmentName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientIdResolutionFilterTest {

    private static final String TOKEN_HEADER = "Bearer a.valid.token";
    private static final UUID CLIENT_ID = UUID.fromString("a8791612-17bd-484b-9508-df66fad20037");
    private static final String SUBSCRIPTIONS_PATH = "/client-subscriptions";

    @Mock
    private EntraTokenValidator tokenValidator;
    @Mock
    private FilterChain filterChain;

    private MeterRegistry meterRegistry;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ---------------------------------------------------------------- deny by default

    @Test
    @DisplayName("public paths bypass the filter without a token")
    void publicPathIsNotFiltered() throws Exception {
        final MockHttpServletRequest request = get("/actuator/health");

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    @DisplayName("the inbound notification endpoint bypasses the filter without a token — internal call")
    void internalNotificationEndpointIsNotFiltered() throws Exception {
        final MockHttpServletRequest request = post("/notifications");

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tokenValidator, never()).validate(any());
        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    // ---------------------------------------------------------------- enforce

    @Test
    void validTokenPopulatesClientIdAndProceeds() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        when(tokenValidator.validate(TOKEN_HEADER)).thenReturn(validCaller());

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(meterRegistry.counter("cp.auth.success").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("the client id is removed from MDC after the request, so it cannot leak between requests")
    void clientIdIsClearedFromMdcAfterRequest() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        when(tokenValidator.validate(TOKEN_HEADER)).thenReturn(validCaller());

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        assertThat(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)).isNull();
    }

    @Test
    void authenticationFailureIsRejectedWith401AndChallenge() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        when(tokenValidator.validate(TOKEN_HEADER))
                .thenThrow(new TokenValidationException(Reason.INVALID_SIGNATURE));

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("error=\"invalid_token\"");
        assertThat(meterRegistry.counter("cp.auth.failure", "reason", "INVALID_SIGNATURE").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("an authorisation failure is 403, not 401")
    void authorisationFailureIsRejectedWith403() throws Exception {
        final MockHttpServletRequest request = post(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        when(tokenValidator.validate(TOKEN_HEADER))
                .thenThrow(new TokenValidationException(Reason.INSUFFICIENT_ROLE));

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("insufficient_scope");
    }

    @Test
    @DisplayName("a missing token gets a bare Bearer challenge per RFC 6750")
    void missingTokenGetsBareChallenge() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        when(tokenValidator.validate(null))
                .thenThrow(new TokenValidationException(Reason.MISSING_AUTHORIZATION_HEADER));

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("the rejection body never contains the token")
    void rejectionBodyDoesNotContainToken() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        when(tokenValidator.validate(TOKEN_HEADER))
                .thenThrow(new TokenValidationException(Reason.INVALID_CLAIMS));

        filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

        assertThat(response.getContentAsString()).doesNotContain("a.valid.token");
    }

    @Test
    @DisplayName("CRLF in the request path cannot forge a log record")
    void rejectionLogIsNotInjectable() throws Exception {
        // CWE-117. The path is attacker-controlled and both rejection paths log it, so newlines must
        // be stripped — otherwise a caller fabricates log lines of their own. CodeQL flagged the
        // observe path; this covers the reject path, which fires on every 401.
        final Logger filterLogger = (Logger) LoggerFactory.getLogger(ClientIdResolutionFilter.class);
        final ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        filterLogger.addAppender(captured);
        try {
            final MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/client-subscriptions\r\nWARN forged entry");
            request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
            when(tokenValidator.validate(TOKEN_HEADER))
                    .thenThrow(new TokenValidationException(Reason.INVALID_SIGNATURE));

            filter(AuthMode.ENFORCE).doFilter(request, response, filterChain);

            assertThat(captured.list)
                    .isNotEmpty()
                    .allSatisfy(event -> assertThat(event.getFormattedMessage())
                            .doesNotContain("\r")
                            .doesNotContain("\n"));
        } finally {
            filterLogger.detachAppender(captured);
        }
    }

    @Test
    @DisplayName("an X-Client-Id header is ignored — identity comes only from the token")
    void clientIdHeaderIsIgnored() throws Exception {
        final UUID attackerSupplied = UUID.fromString("99999999-9999-9999-9999-999999999999");
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        request.addHeader("X-Client-Id", attackerSupplied.toString());
        when(tokenValidator.validate(TOKEN_HEADER)).thenReturn(validCaller());

        final List<String> observed = new java.util.ArrayList<>();
        filter(AuthMode.ENFORCE).doFilter(request, response, (req, res) ->
                observed.add(MDC.get(ClientIdResolutionFilter.MDC_CLIENT_ID)));

        assertThat(observed).containsExactly(CLIENT_ID.toString());
    }

    // ---------------------------------------------------------------- observe

    @Test
    @DisplayName("observe mode lets an invalid token through and records what would have been rejected")
    void observeModeAllowsInvalidTokenAndCounts() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        when(tokenValidator.validate(TOKEN_HEADER))
                .thenThrow(new TokenValidationException(Reason.INVALID_SIGNATURE));
        when(tokenValidator.unverifiedClientIdForNonEnforcingModesOnly(TOKEN_HEADER)).thenReturn(CLIENT_ID);

        filter(AuthMode.OBSERVE).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(meterRegistry.counter("cp.auth.observed.failure", "reason", "INVALID_SIGNATURE").count())
                .isEqualTo(1.0d);
    }

    @Test
    @DisplayName("observe mode still rejects a request with no token at all, matching previous behaviour")
    void observeModeRejectsMissingToken() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        when(tokenValidator.validate(null)).thenThrow(new TokenValidationException(Reason.MISSING_AUTHORIZATION_HEADER));
        when(tokenValidator.unverifiedClientIdForNonEnforcingModesOnly(null))
                .thenThrow(new TokenValidationException(Reason.MISSING_AUTHORIZATION_HEADER));

        filter(AuthMode.OBSERVE).doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- off

    @Test
    @DisplayName("off mode does not validate at all")
    void offModeSkipsValidation() throws Exception {
        final MockHttpServletRequest request = get(SUBSCRIPTIONS_PATH);
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_HEADER);
        when(tokenValidator.unverifiedClientIdForNonEnforcingModesOnly(TOKEN_HEADER)).thenReturn(CLIENT_ID);

        filter(AuthMode.OFF).doFilter(request, response, filterChain);

        verify(tokenValidator, never()).validate(any());
        verify(filterChain).doFilter(request, response);
    }

    // ---------------------------------------------------------------- helpers

    private ClientIdResolutionFilter filter(final AuthMode mode) {
        return new ClientIdResolutionFilter(
                tokenValidator,
                new AuthorizationPolicy(),
                properties(mode),
                meterRegistry);
    }

    private static EntraAuthProperties properties(final AuthMode mode) {
        return new EntraAuthProperties(mode,
                "e2995d11-9947-4e78-9de6-d44e0603518e",
                "bff71afb-9651-445e-bec7-158796787815",
                "", "", 60, 600,
                new AppProperties(EnvironmentName.DEVELOPER, false));
    }

    private static ValidatedCaller validCaller() {
        return new ValidatedCaller(CLIENT_ID, List.of(AuthorizationPolicy.ROLE_READ), true);
    }

    private static MockHttpServletRequest get(final String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static MockHttpServletRequest post(final String uri) {
        return new MockHttpServletRequest("POST", uri);
    }
}
