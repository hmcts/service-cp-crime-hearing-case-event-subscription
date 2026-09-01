package uk.gov.hmcts.cp.subscription.http;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end check that token validation works against a <b>running</b> service, using a
 * <b>real</b> token obtained from Entra rather than a locally minted one.
 *
 * <p>This is the only test that proves the whole chain: Entra issues a token, the service fetches
 * that tenant's real JWKS over the network, and the signature verifies. Everything else in the
 * codebase stubs the JWKS in-process, so a wrong tenant, a wrong audience or blocked egress to
 * {@code login.microsoftonline.com} would pass every other test and fail in a deployed environment.
 *
 * <p><b>Manual only — {@link Disabled} so the pipeline never runs it.</b> It needs a real client
 * secret and outbound internet access, neither of which belongs in CI.
 *
 * <h2>Running it</h2>
 *
 * Configuration comes from environment variables. Keep them in a git-ignored {@code .env} at the
 * repository root and let <a href="https://direnv.net">direnv</a> load it — {@code .env} and
 * {@code .envrc} are both already in {@code .gitignore}, so the secret cannot be committed by
 * accident. One-time setup:
 *
 * <pre>
 * cp .env.example .env          # then fill in ENTRA_CLIENT_ID and ENTRA_CLIENT_SECRET
 * printf 'dotenv\n' &gt; .envrc    # the direnv directive that loads .env
 * direnv allow                  # re-run whenever .envrc changes
 * </pre>
 *
 * The client needs an <b>app role assignment</b> ({@code app.read} / {@code app.write}) on this
 * API's app registration, admin-consented. A registration alone is not enough: without the
 * assignment Entra issues a token carrying no {@code roles}, and every case then fails on the role
 * check rather than on whatever it was meant to exercise.
 *
 * <p>{@code direnv} exports the variables on {@code cd} into the repository. Check with
 * {@code direnv status}, or {@code env | grep ENTRA_} to confirm. Values may be quoted and
 * {@code #} comments are ignored.
 *
 *
 * Then point the service it at a deployed one as below, and run via command line:
 *
 * <pre>
 * cd apiTest &amp;&amp; gradle test --no-daemon \
 *   --tests 'uk.gov.hmcts.cp.subscription.http.EntraTokenValidationApiTest' \
 *   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'
 * </pre>
 *
 * <b>{@code -Djunit.jupiter.conditions.deactivate} is what lifts the {@link Disabled} above.</b>
 * Without it the class simply reports {@code SKIPPED} and nothing runs.
 *
 * <b>{@code --no-daemon} matters.</b> A long-lived Gradle daemon keeps the environment it started
 * with, so test JVMs it forks can miss variables direnv exported afterwards — the symptom is
 * {@code @BeforeAll} complaining the credentials are unset when {@code env} plainly shows them.
 * Alternatively {@code ./gradlew --stop} once, then run normally.
 *
 * <p>Without direnv, plain {@code export} works just as well. Tenant and audience default to the dev
 * values below, so pointing the test at SIT or prod is three variables.
 *
 * <h2>Running against a deployed service</h2>
 *
 * Set {@code SERVICE_BASE_URL} to the service's ingress, <b>including the route prefix</b> — the
 * chart routes this service at {@code /hrds} and rewrites it away, so paths hang off that:
 *
 * <pre>
 * SERVICE_BASE_URL=https://&lt;ingress-host&gt;/hrds
 * </pre>
 *
 * Three things to get right, or the result will mislead:
 *
 * <ol>
 *   <li><b>Go direct to the ingress, not through APIM or WAF.</b> APIM validates the token itself, so a bad
 *       token is rejected at the gateway and never reaches the service. The test would still see a
 *       401 and pass while proving nothing about this service. If you must go via APIM, set
 *       {@code SERVICE_SUBSCRIPTION_KEY} — and read the 401s as the gateway's, not the service's.</li>
 *   <li><b>Tenant and audience must match that environment.</b></li>
 *   <li><b>{@code SERVICE_AUTH_MODE} must match the deployment.</b> Deployed environments are always
 *       {@code ENFORCE} — the startup guard refuses anything else in DEV, STE, SIT, PRP and PRD — so
 *       the default is correct and you should not need to change it.</li>
 * </ol>
 *
 * The credentials must also be a client the target environment's Entra will issue a token for, with
 * a role assignment for this API. A client that works in dev is not automatically consented in prod.
 *
 * <h2>TLS against an internal ingress</h2>
 *
 * Internal hosts serve certificates from a private CA that no JDK ships, so an {@code https}
 * {@code SERVICE_BASE_URL} would fail the handshake with {@code PKIX path building failed}. Rather
 * than maintain a truststore, skip verification -- the {@code curl -k} equivalent:
 *
 * <pre>
 * SERVICE_TLS_INSECURE=true
 * </pre>
 *
 * This is scoped to the call to the service under test. The Entra token request keeps full
 * verification, because that request carries the client secret and a permissive trust manager there
 * would expose it. The flag is also refused against a live-looking host, and logs a warning on every
 * call so a passing run can never be mistaken for a verified one.
 *
 * <p><b>SERVICE_AUTH_MODE must match how the service was started.</b> The expected outcome for a bad
 * token is different in each mode, so the assertions switch on it: under {@code OBSERVE} and
 * {@code OFF} an invalid token is <i>allowed through</i>, which is the point of those modes and
 * exactly what this test should confirm rather than fail on.
 */
@Slf4j
@Disabled("Manual only: needs a real Entra client secret and internet access. See the class javadoc.")
class EntraTokenValidationApiTest extends BaseTest {

    /**
     * Skips certificate verification for calls to the service under test — the {@code curl -k}
     * escape hatch, for an internal ingress whose private CA you do not have to hand.
     *
     * <p>Deliberately narrow: it applies only to the service call, never to the Entra token
     * request, because that request carries the client secret and must stay authenticated. Set it
     * in {@code .env} for any internal https target; it is off by default so the insecure path is
     * always a visible choice rather than a silent one.
     */
    private static final boolean TLS_INSECURE = Boolean.parseBoolean(env("SERVICE_TLS_INSECURE", "false"));

    /** Dev tenant and this API's audience. Overridable so the test can be pointed at SIT or prod. */
    private static final String TENANT_ID = env("ENTRA_TENANT_ID", "e2995d11-9947-4e78-9de6-d44e0603518e");
    private static final String AUDIENCE = env("ENTRA_AUDIENCE", "bff71afb-9651-445e-bec7-158796787815");

    /** No defaults: the client id identifies the caller, and the secret is a credential. */
    private static final String CLIENT_ID = env("ENTRA_CLIENT_ID", null);
    private static final String CLIENT_SECRET = env("ENTRA_CLIENT_SECRET", null);

    private static final String BASE_URL = env("SERVICE_BASE_URL", "http://localhost:8082");

    /**
     * Only needed when {@code SERVICE_BASE_URL} points at APIM rather than straight at the ingress.
     * See the deployed-service notes in the class javadoc — going through APIM tests the gateway's
     * validation, not this service's.
     */
    private static final String SUBSCRIPTION_KEY = env("SERVICE_SUBSCRIPTION_KEY", null);

    /** The mode the service under test is running in — the expected outcomes depend on it. */
    private static final AuthMode MODE =
            AuthMode.valueOf(env("SERVICE_AUTH_MODE", "ENFORCE").toUpperCase(Locale.ROOT));

    /** A token-protected endpoint that needs no fixture data. */
    private static final String PROTECTED_PATH = "/event-types";

    private enum AuthMode { OFF, OBSERVE, ENFORCE }

    @BeforeAll
    static void requireCredentials() {
        if (CLIENT_ID == null || CLIENT_SECRET == null) {
            throw new IllegalStateException(
                    "ENTRA_CLIENT_ID and ENTRA_CLIENT_SECRET must be set. See the class javadoc.");
        }
        log.info("Service {}, tenant {}, audience {}, mode {}", BASE_URL, TENANT_ID, AUDIENCE, MODE);
    }

    // ------------------------------------------------------------ the token Entra actually issues

    @Test
    @DisplayName("a real Entra token is accepted, and its claims are the shape the service expects")
    void realEntraTokenIsAccepted() {
        final String token = acquireToken(AUDIENCE + "/.default");

        // Fail loudly on the two claim shapes that would otherwise cause a confusing 401 later.
        final String payload = decodePayload(token);
        assertThat(payload)
                .as("aud must be the bare GUID, not api://... — see docs/jwt-validation-spec.md")
                .contains("\"aud\":\"" + AUDIENCE + "\"");
        assertThat(payload).as("app-only tokens carry roles").contains("\"roles\"");
        log.info("Entra returned claims: {}", payload);

        assertThat(get(PROTECTED_PATH, "Bearer " + token).getStatusCode())
                .as("a valid token must be accepted in every mode")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a real token for a different resource is rejected on audience")
    void tokenForAnotherResourceIsRejected() {
        // A genuine, correctly signed Entra token from the same tenant — but minted for Graph.
        // Only the audience check rejects this, which is why it is the highest-value claim.
        final String graphToken = acquireToken("https://graph.microsoft.com/.default");

        assertStatus("real token, wrong audience", "Bearer " + graphToken, HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------ forgeries

    @Test
    @DisplayName("a token with a tampered signature is rejected")
    void tamperedTokenIsRejected() {
        final String token = acquireToken(AUDIENCE + "/.default");
        final String tampered = token.substring(0, token.length() - 6) + "AAAAAA";

        // Under OFF and OBSERVE the claims are still readable, so the request is allowed through --
        // that is what those modes mean, and confirming it is the point.
        assertStatus("tampered signature", "Bearer " + tampered, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an unsigned token (alg: none) is rejected")
    void unsignedTokenIsRejected() {
        final String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        final String claims = base64Url("{\"azp\":\"" + CLIENT_ID + "\",\"aud\":\"" + AUDIENCE + "\"}");

        assertStatus("alg:none", "Bearer " + header + "." + claims + ".", HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------ mode-independent behaviour

    @Test
    @DisplayName("no Authorization header is rejected in every mode, with a Bearer challenge")
    void missingTokenIsAlwaysRejected() {
        final ResponseEntity<String> response = get(PROTECTED_PATH, null);

        assertThat(response.getStatusCode())
                .as("a missing token is rejected even in OFF and OBSERVE")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate"))
                .as("RFC 6750 challenge").startsWith("Bearer");
    }

    @Test
    @DisplayName("an exempt path needs no token in any mode")
    void exemptPathNeedsNoToken() {
        final ResponseEntity<String> response = get("/actuator/health", null);

        // Deployed ingresses and APIM products routinely do not expose actuator. A 404 means "not
        // routed here", which is not a validation failure — abort rather than report a false one.
        assumeTrue(response.getStatusCode() != HttpStatus.NOT_FOUND,
                "/actuator/health is not routed at " + BASE_URL + " — nothing to assert");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a rejection never echoes the token")
    void rejectionDoesNotEchoTheToken() {
        final String token = acquireToken(AUDIENCE + "/.default");
        final String tampered = token.substring(0, token.length() - 6) + "AAAAAA";

        final ResponseEntity<String> response = get(PROTECTED_PATH, "Bearer " + tampered);
        final String body = response.getBody() == null ? "" : response.getBody();

        assertThat(body).doesNotContain(tampered).doesNotContain(token);
    }

    // ------------------------------------------------------------ helpers

    /**
     * Asserts the outcome the running service should produce for a bad token, which depends on the
     * mode it was started in: ENFORCE rejects, OBSERVE and OFF allow the request through after
     * logging what would have been rejected.
     */
    private void assertStatus(final String what, final String authorization, final HttpStatus whenEnforcing) {
        final HttpStatus expected = MODE == AuthMode.ENFORCE ? whenEnforcing : HttpStatus.OK;
        final ResponseEntity<String> response = get(PROTECTED_PATH, authorization);

        log.info("{} in mode {} -> {} (expected {})", what, MODE, response.getStatusCode(), expected);
        assertThat(response.getStatusCode())
                .as("%s, service running in %s", what, MODE)
                .isEqualTo(expected);
    }

    private ResponseEntity<String> get(final String path, final String authorization) {
        RestClient.RequestHeadersSpec<?> request = serviceClient()
                .get()
                .uri(BASE_URL + path);
        if (authorization != null) {
            request = request.header(AUTHORIZATION, authorization);
        }
        if (SUBSCRIPTION_KEY != null) {
            request = request.header("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY);
        }
        return request.retrieve()
                // Do not throw on 4xx: the status is what is being asserted.
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);
    }

    /**
     * The client used for the service under test. Verification is disabled only when
     * {@code SERVICE_TLS_INSECURE=true}, and never for a live-looking host — accepting any
     * certificate would also stop the test noticing it had reached the wrong endpoint entirely,
     * which already caused one round of misleading results against the APIM gateway.
     */
    private static RestClient serviceClient() {
        if (!TLS_INSECURE) {
            return RestClient.create();
        }
        final String host = BASE_URL.toLowerCase(Locale.ROOT);
        if (host.contains("prd") || host.contains(".lv.") || host.contains("live")) {
            throw new IllegalStateException(
                    "SERVICE_TLS_INSECURE must not be used against a live host: " + BASE_URL);
        }
        log.warn("TLS VERIFICATION DISABLED for {} — SERVICE_TLS_INSECURE is set. "
                + "Certificates are not checked, so this run does not prove which host answered.", BASE_URL);
        try {
            final TrustManager[] trustAll = {new X509TrustManager() {
                @Override
                public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                    // deliberately permissive: see SERVICE_TLS_INSECURE
                }

                @Override
                public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                    // deliberately permissive: see SERVICE_TLS_INSECURE
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, null);
            // The JDK client checks the hostname inside the engine, so a permissive trust manager
            // alone is not enough to match curl -k.
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            return RestClient.builder()
                    .requestFactory(new JdkClientHttpRequestFactory(
                            HttpClient.newBuilder().sslContext(sslContext).build()))
                    .build();
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("could not build an insecure SSL context", e);
        }
    }

    /** Client credentials grant against the configured tenant — the same call a consumer makes. */
    private String acquireToken(final String scope) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", CLIENT_ID);
        form.add("client_secret", CLIENT_SECRET);
        form.add("scope", scope);

        final String response = RestClient.create()
                .post()
                .uri("https://login.microsoftonline.com/" + TENANT_ID + "/oauth2/v2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        final String token = jsonMapper.getStringAtPath(response, "/access_token");
        assertThat(token).as("Entra returned no access_token for scope %s", scope).isNotBlank();
        return token;
    }

    private static String decodePayload(final String token) {
        final String segment = token.split("\\.")[1];
        return new String(java.util.Base64.getUrlDecoder()
                .decode(segment + "=".repeat((4 - segment.length() % 4) % 4)),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String base64Url(final String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String env(final String name, final String fallback) {
        final String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
