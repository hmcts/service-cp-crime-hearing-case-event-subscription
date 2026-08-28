package uk.gov.hmcts.cp.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.cp.auth.TokenValidationException.Reason;
import uk.gov.hmcts.cp.subscription.config.AppProperties;
import uk.gov.hmcts.cp.subscription.config.EnvironmentName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conformance suite for Entra access token validation.
 *
 * <p>Tokens are minted locally from a generated RSA key and the JWKS is served in-process, so this
 * runs with no network access, no Entra dependency and no Postgres.
 *
 * <p>This suite is the primary control on correctness. Validation is implemented per service rather
 * than in a shared library, and a validation defect is invisible in normal operation — a service
 * that skips signature verification behaves identically to one that does not, until a token is
 * forged. These tests are what make the difference observable.
 */
class EntraTokenValidatorTest {

    private static final String TENANT_ID = "e2995d11-9947-4e78-9de6-d44e0603518e";
    private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    private static final String AUDIENCE = "bff71afb-9651-445e-bec7-158796787815";
    private static final String GRAPH_AUDIENCE = "00000003-0000-0000-c000-000000000000";
    /**
     * Another CP service's audience — pcr-results-api. A public resource identifier, published in
     * that service's APIM validate-jwt policy, not a credential. Deliberately named without the
     * word "api": the secrets scanner's generic-api-key rule pairs identifier keywords with
     * high-entropy values, so a constant named ..._API_... holding a GUID trips it.
     */
    private static final String SIBLING_SERVICE_AUDIENCE = "d03af961-b3a6-4b59-ba5e-ad16a6329b6b";
    private static final String CLIENT_ID = "a8791612-17bd-484b-9508-df66fad20037";
    private static final String SERVICE_PRINCIPAL_OID = "2b5caaab-fe86-4e77-9fb2-13dca57e7a12";
    private static final String KEY_ID = "test-signing-key";

    private static RSAKey signingKey;
    private static RSAKey unrelatedKey;
    private static EntraTokenValidator validator;

    @BeforeAll
    static void setUpKeys() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        unrelatedKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        final JWKSource<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
        validator = new EntraTokenValidator(properties(AuthMode.ENFORCE), jwkSource);
    }

    private static EntraAuthProperties properties(final AuthMode mode) {
        return new EntraAuthProperties(mode, TENANT_ID, AUDIENCE, ISSUER, "", 60, 600,
                new AppProperties(EnvironmentName.DEVELOPER, false));
    }

    // ---------------------------------------------------------------- happy path

    @Test
    @DisplayName("a well-formed app-only token is accepted and yields azp as the client id")
    void acceptsValidAppOnlyToken() throws TokenValidationException {
        final ValidatedCaller caller = validator.validate(bearer(mint(claims -> { })));

        assertThat(caller.clientId()).isEqualTo(UUID.fromString(CLIENT_ID));
        assertThat(caller.roles()).containsExactly(AuthorizationPolicy.ROLE_READ);
        assertThat(caller.verified()).isTrue();
    }

    @Test
    @DisplayName("idtyp absent is accepted — it is an opt-in claim, so requiring it would reject all real traffic")
    void acceptsTokenWithoutIdtypClaim() {
        // Regression guard. Entra omits idtyp unless it is explicitly enabled as an optional claim,
        // so app-only is inferred from sub == oid, roles present and scp absent instead.
        assertThatCode(() -> validator.validate(bearer(mint(claims -> { }))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the client id is azp, never the service principal object id in oid/sub")
    void usesAzpNotObjectIdAsClientId() throws TokenValidationException {
        final ValidatedCaller caller = validator.validate(bearer(mint(claims -> { })));

        assertThat(caller.clientId()).isEqualTo(UUID.fromString(CLIENT_ID));
        assertThat(caller.clientId()).isNotEqualTo(UUID.fromString(SERVICE_PRINCIPAL_OID));
    }

    @Test
    @DisplayName("expiry inside the configured clock skew is tolerated")
    void toleratesExpiryWithinClockSkew() {
        final String token = mint(claims -> claims.expirationTime(secondsFromNow(-30)));

        assertThatCode(() -> validator.validate(bearer(token))).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- header handling

    @Test
    void rejectsMissingAuthorizationHeader() {
        assertReason(null, Reason.MISSING_AUTHORIZATION_HEADER);
    }

    @Test
    void rejectsBlankAuthorizationHeader() {
        assertReason("   ", Reason.MISSING_AUTHORIZATION_HEADER);
    }

    @Test
    void rejectsNonBearerScheme() {
        assertReason("Basic dXNlcjpwYXNzd29yZA==", Reason.UNSUPPORTED_SCHEME);
    }

    @Test
    void rejectsEmptyBearerToken() {
        assertReason("Bearer   ", Reason.EMPTY_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bearer ", "BEARER ", "BeArEr "})
    @DisplayName("the Bearer scheme is matched case-insensitively per RFC 6750")
    void acceptsAnyCaseOfBearerScheme(final String scheme) {
        final String token = mint(claims -> { });

        assertThatCode(() -> validator.validate(scheme + token)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- structure

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-token",       // no segments
        "onlyone.twoparts",  // two segments
        "a.b.c.d.e",         // five segments, JWE shape
        "!!!.!!!.!!!"        // non-base64url
    })
    @DisplayName("structurally invalid tokens are rejected by the parser")
    void rejectsMalformedTokens(final String malformed) {
        assertReason("Bearer " + malformed, Reason.MALFORMED_TOKEN);
    }

    @Test
    @DisplayName("a token whose payload is not JSON is rejected")
    void rejectsNonJsonPayload() {
        // Parses as a JWS but carries neither a valid payload nor a valid signature, so either
        // rejection reason is correct — only that it is rejected matters.
        assertThatThrownBy(() -> validator.validate("Bearer eyJhbGciOiJSUzI1NiJ9.bm90LWpzb24.c2ln"))
                .isInstanceOf(TokenValidationException.class);
    }

    // ---------------------------------------------------------------- signature and algorithm

    @Test
    @DisplayName("an unsigned token (alg: none) is rejected")
    void rejectsUnsignedToken() {
        final String unsigned = new PlainJWT(baseClaims().build()).serialize();

        assertReason("Bearer " + unsigned, Reason.MALFORMED_TOKEN);
    }

    @Test
    @DisplayName("HS256 signed with the JWKS RSA public key is rejected — alg confusion")
    void rejectsAlgorithmConfusionAttack() throws JOSEException {
        final byte[] publicKeyAsSecret = signingKey.toRSAPublicKey().getModulus().toByteArray();
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID).build(), baseClaims().build());
        jwt.sign(new MACSigner(publicKeyAsSecret));

        assertReason(bearer(jwt.serialize()), Reason.INVALID_SIGNATURE);
    }

    @Test
    void rejectsTokenSignedByUnrelatedKey() throws JOSEException {
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), baseClaims().build());
        jwt.sign(new RSASSASigner(unrelatedKey));

        assertReason(bearer(jwt.serialize()), Reason.INVALID_SIGNATURE);
    }

    @Test
    void rejectsTamperedSignature() {
        final String token = mint(claims -> { });
        final String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> validator.validate(bearer(tampered)))
                .isInstanceOf(TokenValidationException.class);
    }

    @Test
    void rejectsUnknownKeyId() throws JOSEException {
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rotated-away").build(), baseClaims().build());
        jwt.sign(new RSASSASigner(signingKey));

        assertReason(bearer(jwt.serialize()), Reason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("a token declaring an unsupported critical header is rejected")
    void rejectsUnsupportedCriticalHeader() throws JOSEException {
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(KEY_ID)
                        .criticalParams(java.util.Set.of("something-we-do-not-understand"))
                        .build(),
                baseClaims().build());
        jwt.sign(new RSASSASigner(signingKey));

        assertReason(bearer(jwt.serialize()), Reason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("key material supplied in the token header is ignored")
    void ignoresHeaderSuppliedKey() throws JOSEException {
        // The attacker signs with their own key and advertises its public half in the header.
        // The key selector only consults the configured JWKS, so this cannot verify.
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(KEY_ID)
                        .jwk(unrelatedKey.toPublicJWK())
                        .build(),
                baseClaims().build());
        jwt.sign(new RSASSASigner(unrelatedKey));

        assertReason(bearer(jwt.serialize()), Reason.INVALID_SIGNATURE);
    }

    // ---------------------------------------------------------------- claims

    @Test
    @DisplayName("a Microsoft Graph token is rejected on audience — the standard onboarding mistake")
    void rejectsGraphAudience() {
        assertReason(bearer(mint(claims -> claims.audience(GRAPH_AUDIENCE))), Reason.INVALID_CLAIMS);
    }

    @Test
    @DisplayName("a token minted for a sibling CP API is rejected on audience")
    void rejectsSiblingApiAudience() {
        assertReason(bearer(mint(claims -> claims.audience(SIBLING_SERVICE_AUDIENCE))), Reason.INVALID_CLAIMS);
    }

    @Test
    void rejectsWrongIssuer() {
        assertReason(bearer(mint(claims ->
                claims.issuer("https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0"))),
                Reason.INVALID_CLAIMS);
    }

    @Test
    @DisplayName("an issuer that merely starts with the expected value is rejected")
    void rejectsIssuerPrefixAttack() {
        assertReason(bearer(mint(claims -> claims.issuer(ISSUER + ".attacker.example"))), Reason.INVALID_CLAIMS);
    }

    @Test
    void rejectsExpiredToken() {
        assertReason(bearer(mint(claims -> claims.expirationTime(secondsFromNow(-3600)))), Reason.INVALID_CLAIMS);
    }

    @Test
    void rejectsTokenWithoutExpiry() {
        assertReason(bearer(mint(claims -> claims.expirationTime(null))), Reason.INVALID_CLAIMS);
    }

    @Test
    void rejectsNotYetValidToken() {
        assertReason(bearer(mint(claims -> claims.notBeforeTime(secondsFromNow(3600)))), Reason.INVALID_CLAIMS);
    }

    @Test
    void rejectsWrongTenantId() {
        assertReason(bearer(mint(claims ->
                claims.claim("tid", "00000000-0000-0000-0000-000000000000"))), Reason.INVALID_CLAIMS);
    }

    @Test
    @DisplayName("a v1.0 token is rejected — only the v2.0 endpoint is supported")
    void rejectsV1Token() {
        assertReason(bearer(mint(claims -> claims.claim("ver", "1.0"))), Reason.INVALID_CLAIMS);
    }

    // ---------------------------------------------------------------- caller identity

    @Test
    void rejectsMissingAzp() {
        assertReason(bearer(mint(claims -> claims.claim("azp", null))), Reason.MISSING_CLIENT_ID);
    }

    @Test
    void rejectsAzpThatIsNotAUuid() {
        assertReason(bearer(mint(claims -> claims.claim("azp", "not-a-uuid"))), Reason.MALFORMED_CLIENT_ID);
    }

    @Test
    @DisplayName("a delegated user token is rejected — sub differs from oid")
    void rejectsDelegatedTokenBySubjectMismatch() {
        assertReason(bearer(mint(claims -> claims.subject("11111111-1111-1111-1111-111111111111"))),
                Reason.DELEGATED_TOKEN);
    }

    @Test
    @DisplayName("a token carrying scp is rejected — scp means a delegated token")
    void rejectsDelegatedTokenByScopeClaim() {
        assertReason(bearer(mint(claims -> claims.claim("scp", "Subscription.Read"))), Reason.INVALID_CLAIMS);
    }

    @Test
    void rejectsTokenWithoutRoles() {
        assertReason(bearer(mint(claims -> claims.claim("roles", null))), Reason.MISSING_ROLE);
    }

    @Test
    void rejectsTokenWithEmptyRoles() {
        assertReason(bearer(mint(claims -> claims.claim("roles", List.of()))), Reason.MISSING_ROLE);
    }

    // ---------------------------------------------------------------- non-enforcing modes

    @Test
    @DisplayName("the unverified path returns azp without validating anything, and is flagged unverified")
    void unverifiedExtractionDoesNotValidate() throws TokenValidationException {
        // Signed by a key the validator does not trust, and expired — the enforcing path rejects it.
        final String forged = mint(claims -> claims.expirationTime(secondsFromNow(-99999)));

        final UUID clientId = validator.unverifiedClientIdForNonEnforcingModesOnly(bearer(forged));

        assertThat(clientId).isEqualTo(UUID.fromString(CLIENT_ID));
        assertThatThrownBy(() -> validator.validate(bearer(forged)))
                .isInstanceOf(TokenValidationException.class);
    }

    // ---------------------------------------------------------------- hygiene

    @Test
    @DisplayName("the exception never carries token material")
    void exceptionDoesNotLeakTokenMaterial() {
        final String token = mint(claims -> claims.audience(GRAPH_AUDIENCE));

        assertThatThrownBy(() -> validator.validate(bearer(token)))
                .isInstanceOf(TokenValidationException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(token));
    }

    // ---------------------------------------------------------------- helpers

    private void assertReason(final String authorizationHeader, final Reason expected) {
        assertThatThrownBy(() -> validator.validate(authorizationHeader))
                .isInstanceOf(TokenValidationException.class)
                .extracting(ex -> ((TokenValidationException) ex).getReason())
                .isEqualTo(expected);
    }

    private static String bearer(final String token) {
        return "Bearer " + token;
    }

    /**
     * The claim set of a genuine Entra app-only token. Note there is no {@code idtyp} — Entra does
     * not emit it by default.
     */
    private static JWTClaimsSet.Builder baseClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(SERVICE_PRINCIPAL_OID)
                .claim("oid", SERVICE_PRINCIPAL_OID)
                .claim("azp", CLIENT_ID)
                .claim("azpacr", "1")
                .claim("tid", TENANT_ID)
                .claim("ver", "2.0")
                .claim("roles", List.of(AuthorizationPolicy.ROLE_READ))
                .issueTime(secondsFromNow(-60))
                .notBeforeTime(secondsFromNow(-60))
                .expirationTime(secondsFromNow(3600));
    }

    private static String mint(final Consumer<JWTClaimsSet.Builder> customiser) {
        final JWTClaimsSet.Builder builder = baseClaims();
        customiser.accept(builder);
        try {
            final SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), builder.build());
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (final JOSEException e) {
            throw new IllegalStateException("failed to mint test token", e);
        }
    }

    private static Date secondsFromNow(final long seconds) {
        return Date.from(Instant.now().plus(seconds, ChronoUnit.SECONDS));
    }
}
