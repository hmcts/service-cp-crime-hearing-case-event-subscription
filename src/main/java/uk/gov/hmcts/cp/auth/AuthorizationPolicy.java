package uk.gov.hmcts.cp.auth;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Decides which requests need a token, and that the caller holds a recognised role.
 *
 * <p><b>Deny by default:</b> every request is validated unless its path is listed as exempt below.
 * Exemptions are enumerated, never inferred from a prefix — a prefix rule silently exempts endpoints
 * added later.
 *
 * <p>Any recognised role satisfies any operation, matching the gateway policy. Per-operation role
 * separation belongs in an operation-scoped gateway policy rather than here — one place instead of
 * one per service. See {@code docs/jwt-validation-spec.md}.
 */
@Service
public class AuthorizationPolicy {

    /** The only two application roles Entra issues for this API. */
    public static final String ROLE_READ = "app.read";
    public static final String ROLE_WRITE = "app.write";

    private static final Set<String> KNOWN_ROLES = Set.of(ROLE_READ, ROLE_WRITE);

    /** Infrastructure and non-production endpoints, carrying no case data. */
    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of("/", "");
    private static final List<String> PUBLIC_PATH_ROOTS = List.of("/actuator", "/mock-callback");

    /**
     * Endpoints called by platform producers rather than by a consuming client. Internal calls are
     * not token-validated in this estate, and these producers send no {@code Authorization} header,
     * so requiring any role would stop the integration.
     *
     * <p><b>Protected by network and gateway controls, not by this service.</b> Adding an entry is a
     * security change and must be reviewed as one.
     *
     * <p>TODO - internal endpoints must stay reachable through the gateway without a token while not
     * being reachable by consumers, which means segregating them onto an internal-only API or
     * product. Until then "internal" describes intent, not reachability. Tracked on AMP-941.
     */
    private static final Set<String> INTERNAL_UNVALIDATED_PATHS = Set.of("/notifications");

    /** True when the path is reachable without a token — see the two lists above. */
    public boolean isExemptFromValidation(final String requestUri) {
        final String path = stripTrailingSlash(requestUri);
        if (PUBLIC_EXACT_PATHS.contains(path) || INTERNAL_UNVALIDATED_PATHS.contains(path)) {
            return true;
        }
        return PUBLIC_PATH_ROOTS.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    /** The roles this API recognises; a caller needs at least one of them. */
    public Set<String> knownRoles() {
        return KNOWN_ROLES;
    }

    /**
     * @throws TokenValidationException with {@code INSUFFICIENT_ROLE} when the caller holds no role
     *         this API recognises
     */
    public void assertAuthorized(final ValidatedCaller caller) throws TokenValidationException {
        if (KNOWN_ROLES.stream().noneMatch(caller::hasRole)) {
            throw new TokenValidationException(TokenValidationException.Reason.INSUFFICIENT_ROLE);
        }
    }

    /**
     * Strips a single trailing slash. Traversal and encoding are already normalised by the servlet
     * container before {@code getRequestURI()} reaches here.
     */
    private static String stripTrailingSlash(final String requestUri) {
        if (requestUri == null) {
            return "";
        }
        final String trimmed = requestUri.trim();
        return trimmed.length() > 1 && trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }
}
