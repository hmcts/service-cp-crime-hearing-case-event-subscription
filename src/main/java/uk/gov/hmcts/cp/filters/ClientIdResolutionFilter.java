package uk.gov.hmcts.cp.filters;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.cp.auth.AuthMode;
import uk.gov.hmcts.cp.auth.AuthorizationPolicy;
import uk.gov.hmcts.cp.auth.EntraAuthProperties;
import uk.gov.hmcts.cp.auth.EntraTokenValidator;
import uk.gov.hmcts.cp.auth.TokenValidationException;
import uk.gov.hmcts.cp.auth.ValidatedCaller;

import java.io.IOException;

/**
 * Authenticates the caller from its Entra access token and publishes the resolved client id to MDC.
 *
 * <p>Ordered between {@link TracingFilter} (HIGHEST_PRECEDENCE) and the audit library's
 * {@code AuditFilter} (HIGHEST_PRECEDENCE + 10) so that {@link #MDC_CLIENT_ID} is populated before
 * the request audit event is built and is still populated when the response event is built — the
 * MDC entry is cleared on the way out, which happens after the audit filter regains control.
 *
 * <p>Trade-off, unchanged: auth runs before auditing, so requests rejected here produce no audit
 * event. They are logged and counted instead. Resolving this properly is tracked separately.
 *
 * <p>This filter is <b>deny by default</b> — see {@link AuthorizationPolicy}. It no longer accepts
 * client identity from a request header under any configuration: a forgeable header defeats every
 * other check in the pipeline.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@Slf4j
public class ClientIdResolutionFilter extends OncePerRequestFilter {

    public static final String MDC_CLIENT_ID = "clientId";

    private static final String METRIC_SUCCESS = "cp.auth.success";
    private static final String METRIC_FAILURE = "cp.auth.failure";
    private static final String METRIC_OBSERVED_FAILURE = "cp.auth.observed.failure";
    private static final String TAG_REASON = "reason";

    private final EntraTokenValidator tokenValidator;
    private final AuthorizationPolicy authorizationPolicy;
    private final EntraAuthProperties authProperties;
    private final MeterRegistry meterRegistry;

    public ClientIdResolutionFilter(final EntraTokenValidator tokenValidator,
                                    final AuthorizationPolicy authorizationPolicy,
                                    final EntraAuthProperties authProperties,
                                    final MeterRegistry meterRegistry) {
        this.tokenValidator = tokenValidator;
        this.authorizationPolicy = authorizationPolicy;
        this.authProperties = authProperties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(@Nonnull final HttpServletRequest request) {
        return authorizationPolicy.isExemptFromValidation(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request,
                                    @Nonnull final HttpServletResponse response,
                                    @Nonnull final FilterChain filterChain) throws ServletException, IOException {
        final ValidatedCaller caller;
        try {
            caller = resolveCaller(request);
        } catch (final TokenValidationException ex) {
            reject(request, response, ex);
            return;
        }

        MDC.put(MDC_CLIENT_ID, caller.clientId().toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CLIENT_ID);
        }
    }

    private ValidatedCaller resolveCaller(final HttpServletRequest request) throws TokenValidationException {
        final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authProperties.getMode() == AuthMode.OFF) {
            return unverifiedCaller(authorizationHeader);
        }

        try {
            final ValidatedCaller caller = tokenValidator.validate(authorizationHeader);
            authorizationPolicy.assertAuthorized(caller);
            meterRegistry.counter(METRIC_SUCCESS).increment();
            return caller;
        } catch (final TokenValidationException ex) {
            if (authProperties.getMode() == AuthMode.OBSERVE) {
                return observeOnly(request, authorizationHeader, ex);
            }
            throw ex;
        }
    }

    /**
     * Records what enforcement <i>would</i> have rejected and lets the request through, so broken
     * clients can be identified before enforcement is switched on. Logs enough to identify the
     * client, and never the token.
     */
    private ValidatedCaller observeOnly(final HttpServletRequest request,
                                        final String authorizationHeader,
                                        final TokenValidationException ex) throws TokenValidationException {
        meterRegistry.counter(METRIC_OBSERVED_FAILURE, TAG_REASON, ex.getReason().name()).increment();
        final ValidatedCaller caller = unverifiedCaller(authorizationHeader);
        final String safeMethod = sanitizeForLog(request.getMethod());
        final String safeRequestUri = sanitizeForLog(request.getRequestURI());
        log.warn("AUTH OBSERVE: would have rejected {} {} reason:{} unverifiedClientId:{}",
                safeMethod, safeRequestUri, ex.getReason(), caller.clientId());
        return caller;
    }

    private ValidatedCaller unverifiedCaller(final String authorizationHeader) throws TokenValidationException {
        return new ValidatedCaller(
                tokenValidator.unverifiedClientIdForNonEnforcingModesOnly(authorizationHeader),
                java.util.List.of(),
                false);
    }

    /**
     * Strips CR and LF so an attacker cannot forge log records by embedding newlines in a request
     * path or method (CWE-117). Both values below are attacker-controlled and both rejection paths
     * log them, so both must go through here.
     */
    private static String sanitizeForLog(final String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private void reject(final HttpServletRequest request,
                        final HttpServletResponse response,
                        final TokenValidationException ex) throws IOException {

        final TokenValidationException.Reason reason = ex.getReason();
        meterRegistry.counter(METRIC_FAILURE, TAG_REASON, reason.name()).increment();
        log.warn("AUTH REJECT: {} {} reason:{}",
                sanitizeForLog(request.getMethod()), sanitizeForLog(request.getRequestURI()), reason);

        final int status = reason.isAuthenticationFailure()
                ? HttpServletResponse.SC_UNAUTHORIZED
                : HttpServletResponse.SC_FORBIDDEN;

        response.setStatus(status);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge(reason));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Coarse body only. Never echo the token, a claim value, or an internal class name.
        response.getWriter().write(
                "{\"error\":\"" + reason.getErrorCode() + "\",\"message\":\"" + reason.getDescription() + "\"}");
    }

    /**
     * RFC 6750 s3 — a request with no credentials gets a bare challenge; anything else names the
     * error so a client can tell "you sent nothing" from "you sent something wrong".
     */
    private static String challenge(final TokenValidationException.Reason reason) {
        if (reason == TokenValidationException.Reason.MISSING_AUTHORIZATION_HEADER) {
            return "Bearer";
        }
        return "Bearer error=\"" + reason.getErrorCode() + "\", error_description=\"" + reason.getDescription() + "\"";
    }
}
