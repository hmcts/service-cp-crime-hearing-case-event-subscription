package uk.gov.hmcts.cp.subscription.integration.helpers;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mints genuinely signed Entra-shaped access tokens for integration tests.
 *
 * <p>A keypair is generated once per JVM and its public half is served to the application through
 * {@link #jwkSource()}, so integration tests exercise the real validation path — signature, issuer,
 * audience, expiry, app-only and roles — rather than bypassing it. Nothing here reaches Entra.
 *
 * <p>The claim set mirrors a real app-only token, including the absence of {@code idtyp}: Entra does
 * not emit that claim unless it is explicitly enabled, so tests must not depend on it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JwtHelper {

    public static final String TENANT_ID = "e2995d11-9947-4e78-9de6-d44e0603518e";
    public static final String AUDIENCE = "bff71afb-9651-445e-bec7-158796787815";
    public static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    public static final String ROLE_READ = "app.read";
    public static final String ROLE_WRITE = "app.write";

    private static final String KEY_ID = "integration-test-key";
    private static final RSAKey SIGNING_KEY = generateSigningKey();

    /** The public half of the test signing key, as the application's JWKS. */
    public static JWKSource<SecurityContext> jwkSource() {
        return new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY.toPublicJWK()));
    }

    /** A valid token for the given client, carrying both read and write roles. */
    public static String bearerTokenWithAzp(final String clientId) {
        return bearerToken(clientId, List.of(ROLE_READ, ROLE_WRITE));
    }

    /**
     * A token whose claims are the dev-shaped defaults with the customiser applied, so a test can
     * vary one thing — audience, expiry, roles, subject — and leave the rest realistic.
     */
    public static String bearerToken(final Consumer<JWTClaimsSet.Builder> customiser) {
        final JWTClaimsSet.Builder claims = devClaims(UUID.randomUUID().toString(),
                List.of(ROLE_READ, ROLE_WRITE));
        customiser.accept(claims);
        return "Bearer " + sign(claims.build());
    }

    /**
     * Correctly formed and correctly signed — but by a key the application's JWKS does not publish.
     * The shape an attacker can always produce.
     */
    public static String bearerTokenSignedByUnknownKey() {
        final JWTClaimsSet.Builder claims = devClaims(UUID.randomUUID().toString(),
                List.of(ROLE_READ, ROLE_WRITE));
        try {
            final RSAKey otherKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
            final SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims.build());
            jwt.sign(new RSASSASigner(otherKey));
            return "Bearer " + jwt.serialize();
        } catch (final JOSEException e) {
            throw new IllegalStateException("failed to sign with an unknown key", e);
        }
    }

    public static String bearerToken(final String clientId, final List<String> roles) {
        return "Bearer " + sign(devClaims(clientId, roles).build());
    }

    /** The claim set of a genuine Entra app-only token for the dev tenant. No idtyp — Entra omits it. */
    private static JWTClaimsSet.Builder devClaims(final String clientId, final List<String> roles) {
        final String servicePrincipalObjectId = UUID.randomUUID().toString();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(servicePrincipalObjectId)
                .claim("oid", servicePrincipalObjectId)
                .claim("azp", clientId)
                .claim("azpacr", "1")
                .claim("tid", TENANT_ID)
                .claim("ver", "2.0")
                .claim("roles", roles)
                .issueTime(secondsFromNow(-60))
                .notBeforeTime(secondsFromNow(-60))
                .expirationTime(secondsFromNow(3600));
    }

    private static String sign(final JWTClaimsSet claims) {
        try {
            final SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims);
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (final JOSEException e) {
            throw new IllegalStateException("failed to sign test token", e);
        }
    }

    private static RSAKey generateSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (final JOSEException e) {
            throw new IllegalStateException("failed to generate test signing key", e);
        }
    }

    private static Date secondsFromNow(final long seconds) {
        return Date.from(Instant.now().plus(seconds, ChronoUnit.SECONDS));
    }
}
