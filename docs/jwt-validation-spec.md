# Entra access token validation

How this service authenticates a caller, and — more usefully — **why it does what it does**.

This document deliberately does not restate the rules. The rules are the code and its tests, which
cannot drift from the behaviour:

| Question | Where the answer lives |
|---|---|
| What is validated, and how? | `uk.gov.hmcts.cp.auth.EntraTokenValidator` |
| Which requests need a token? | `uk.gov.hmcts.cp.auth.AuthorizationPolicy` |
| What is rejected, and with what status? | `EntraTokenValidatorTest` — the conformance suite |
| Delivery plan, Entra work, APIM changes | [AMP-941](https://tools.hmcts.net/jira/browse/AMP-941) |

What follows is the part none of those can express: the decisions, the traps, and the prerequisites.

---

## 1. Why validation exists here at all

`JwtTokenParser` used to base64-decode the token payload and trust its `azp` claim with no signature
check. Because `azp` is the tenancy key in every repository query, forging it impersonated any
registered client.

The APIM `validate-jwt` policy blocked that from the internet, so it was never internet-exploitable.
It was open to anything reaching the pod directly — in-cluster callers, a port-forward, a misrouted
ingress — and to the Service Bus path, which never traverses the gateway. **That is what in-application
validation is for: the paths the gateway does not see.** It duplicates the gateway deliberately.

---

## 2. Decisions

### Nimbus, not Spring Security

`NimbusJwtDecoder` is a thin wrapper over Nimbus's `DefaultJWTProcessor`, and every hard requirement
here — JWKS caching, rate-limited refresh, outage tolerance, single-algorithm pinning — is a Nimbus
feature. Spring Security would add a framework to reach a library we can depend on directly, and its
filter chain registers at `spring.security.filter.order` (default `-100`), i.e. **after**
`TracingFilter`, `ClientIdResolutionFilter` and the audit filter. That reordering is not free in a
service whose filter order is load-bearing.

Revisit if per-path authorisation policy outgrows a simple map. Migration is cheap: the processor
becomes the `JwtDecoder`, so no validation logic is rewritten.

### One algorithm, pinned in code

`alg` appears in **three** places, and only one of them is trustworthy. Confusing them is the whole
vulnerability class, so to be explicit:

| Where | Present? | Trustworthy? |
|---|---|---|
| **The token's JOSE header** — `{"typ":"JWT","alg":"RS256","kid":"…"}` | Yes, always | **No.** It is part of the token the caller hands us, so the caller chooses it |
| **The JWKS key entries** — from the tenant's discovery endpoint | **No.** Entra's keys expose `kty, use, kid, n, e, x5c, x5t` and no `alg` | Would be, but it is absent |
| **Our configuration** — the key selector | Pinned to RS256 | Yes |

The token's `alg` is the *input to the attack*, not a defence: an attacker sets `alg: none` and strips
the signature, or sets `alg: HS256` and signs with the RSA public key from the JWKS as an HMAC secret —
that key is public, after all. A validator that reads `alg` from the header and verifies accordingly
accepts both.

Some libraries can constrain this from the key set instead: where a JWKS entry carries
`"alg": "RS256"`, a verifier can reject any token whose header disagrees without hardcoding anything.
**Entra does not publish that field**, so that route is closed and the algorithm must be pinned in
code:

```java
setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource))
```

The selector accepts exactly one algorithm, so a token declaring any other finds no candidate key and
fails before verification is attempted. That is why `alg: none`, algorithm confusion and
token-supplied key material (`jku`/`jwk`/`x5u`) are **structurally impossible** here rather than
separately defended — the selector consults only the configured JWKS. `rejectsUnsignedToken`,
`rejectsAlgorithmConfusionAttack` and `ignoresHeaderSuppliedKey` pin all three.

The keys do carry `"use": "sig"`, which separates signing from encryption but says nothing about
*which* signature algorithm, so it is not a substitute.

### App-only proven by `sub == oid`, never by `idtyp`

`idtyp: app` would be the direct check, but Entra omits that claim unless it is explicitly enabled as
an optional claim on the app registration. **Requiring it today would reject all legitimate traffic.**
App-only is therefore inferred from `sub == oid`, plus `roles` present, plus `scp` prohibited. There
is a regression test asserting a token *without* `idtyp` is accepted, so nobody "hardens" this into an
outage.

### No read/write role separation

`app.read` and `app.write` are the only roles Entra issues, and either satisfies any operation — the
same rule the deployed APIM policy applies. Separation is not implemented because `app.write` is not
yet assigned to the calling clients, so it could not be switched on, and a per-operation rule that
cannot be enabled is dead configuration whose enabled branch reaches production untested.

**Consequence: a read-only client can perform writes.** True of APIM before this change too, so not a
regression, but an open gap. When the assignment lands, the natural home is an operation-scoped APIM
policy — one place rather than one per service.

### `azp` is not resolved against the client registry

Requiring the caller to already exist would break onboarding: `POST /client-subscriptions` is how a
client first registers. Needs a per-endpoint rule before it can be added.

---

## 3. Endpoint coverage

Every endpoint requires a validated token, with two enumerated exemptions.

### 3.1 Exempt: the inbound notification endpoint

**`POST /notifications` is excluded from token validation.** It is called by Progression / Hearing
NOWs, not by a consuming client, and internal calls are not token-validated in this estate — a
decision taken by the API owner.

**Confirmed: those producers send no `Authorization` header at all.** There is therefore no token to
validate and no caller identity to record. This rules out the obvious alternative — an internal-only
application role that consumers are never granted — because requiring any role would stop the
notification pipeline, and with it the delivery of hearing-results documents. Authentication is not
available here at any price the producers can currently pay.

Consequences to be explicit about, since the service does not protect this path:

1. **It is protected by network and gateway controls, not by this service.** Anything that can reach
   the pod can post a notification event, and an injected event becomes a callback to a real
   subscriber.
2. **No attribution.** With no token there is nothing to log or audit about who posted an event,
   beyond the correlation id and source address.
3. Adding to the exemption list is a security change and must be reviewed as one. The list lives in
   `AuthorizationPolicy.INTERNAL_UNVALIDATED_PATHS` and is matched **exactly** — `/notifications/x`
   and `/notificationsx` remain protected, asserted by test.

### TODO — keep internal endpoints in CP APIM without requiring a token

The gap is at the gateway, not in the service. `createNotification` is currently published as an
operation on the **consumer-facing** APIM product, so any onboarded consumer holding `app.read` can
post an event (Finding 7 on AMP-941).

The fix is **gateway-side segregation, not authentication**: internal operations should be reachable
through CP APIM without a token — as the producers require — while not being reachable by consumers at
all. In practice that means moving them onto an internal-only API or product rather than exposing them
on the product consumers subscribe to.

Until that lands, "internal" describes intent rather than reachability. **This is an APIM change, not a code change.** Tracked on AMP-941.

### 3.2 Exempt: infrastructure paths

`/actuator/**`, `/` (`RootController`) and `/mock-callback**` (`MockCallbackController`). None carries
case data.

Two follow-ups, unchanged by the above:

- `/actuator/prometheus` should be non-public at the ingress. Being reachable without a token in the
  service does not mean it should be reachable from outside the cluster.
- **`MockCallbackController` MUST NOT be registered in production.** Assert via a profile condition
  and a test. Not done in this change.

### 3.3 Protected

Everything else, including the two endpoints the previous prefix-based filter skipped by accident:

| Endpoint | Previously | Now |
|---|---|---|
| `GET /event-types` | unauthenticated — outside the `/client-subscriptions` prefix | token required |
| `GET /client-subscriptions/{id}/documents/{id}` | token required, ownership checked | unchanged |
| `GET /client-subscriptions/{id}/hearing-events/{id}` | token required, ownership checked | unchanged |
| `POST` / `GET` / `PUT` / `DELETE /client-subscriptions**` | token required | unchanged |

Note the filter's old `/client-subscriptions/notifications` exemption was **dead code** — no such path
exists in the contract. The endpoint is `/notifications`, which was unprotected for a different
reason: it does not start with `/client-subscriptions` at all.

**Exemptions are enumerated, not implied.** A newly added endpoint is protected unless someone adds it
to a list, and a test enumerating the contract's paths fails until each is classified — which is what
the old prefix rule could not do.

---


---

## 4. Configuration

| Variable | Purpose | Dev value |
|---|---|---|
| `AUTH_MODE` | `OFF` / `OBSERVE` / `ENFORCE` | `ENFORCE` |
| `AUTH_TENANT_ID` | Expected `tid`; derives issuer and JWKS URI | `e2995d11-9947-4e78-9de6-d44e0603518e` |
| `AUTH_AUDIENCE` | **This API's** audience | `bff71afb-9651-445e-bec7-158796787815` |
| `AUTH_ISSUER`, `AUTH_JWKS_URI` | Overrides; derived from the tenant when blank | derived |
| `AUTH_CLOCK_SKEW_SECONDS` | Skew for `exp`/`nbf`; capped at 300 | `60` |
| `AUTH_JWKS_CACHE_TTL_SECONDS` | JWKS cache lifetime | `600` |

Two things that bite:

- **The audience and tenant default to the dev values.** They are non-blank, so the service starts in
  any environment — but with the wrong audience it rejects every token. Set them per environment. The
  failure is loud (`cp.auth.failure{reason=INVALID_CLAIMS}`), not silent.
- `d03af961-…`, which circulated during the spike, is **`pcr-results-api`'s** audience, not ours. A
  token minted with it is correctly rejected here.

`OFF` and `OBSERVE` take the client id from an **unverified** token and are rejected at startup in
SIT/PRP/PRD. `OBSERVE` validates, logs and counts what *would* have been rejected, and allows the
request through — a diagnostic for finding broken clients before enforcing, providing no protection.

---

## 5. Deliberately not checked

Recorded so they are not re-litigated. Each lacks a threat model in this context:

| Not checked | Why |
|---|---|
| TLS termination in-app | Terminates at the ingress; `isSecure()` is false behind a proxy, so this degrades to trusting `X-Forwarded-Proto` |
| Token size limits | The servlet container caps header size and rejects oversize requests first |
| Duplicate JSON keys | The signature covers the whole payload; a duplicate key cannot be injected without invalidating it |
| `iat` max age | `exp` already bounds validity, and Entra sets `iat == nbf` |
| `typ` header | Permitting the values Entra emits rejects nothing an attacker would send |
| Timing-uniform failures | Incompatible with the deliberately distinct 401/403/404 semantics |
| Replay / nonce tracking | Bearer tokens are replayable until `exp` by design. Mitigate via token lifetime. Entra emits `uti`, not `jti` |
| Delegated (user) tokens, `scp` handling | App-only only; delegated tokens are rejected |

Also out of scope: token revocation, continuous access evaluation, client-side token acquisition, and
outbound callback authentication (covered by HMAC signing).

---

## 6. Entra prerequisites

Code cannot compensate for claims a token does not carry. Full detail and status on AMP-941; the
essentials:

| Item | State |
|---|---|
| App registration exposing this API's audience | exists |
| `app.read`, `app.write` app roles | exist, and are named in the deployed APIM policy |
| **`app.write` assigned to calling clients + admin-consented** | **not confirmed** — dev tokens carry `app.read` alone. Blocks read/write separation |
| `requestedAccessTokenVersion` pinned to `2` | should be pinned; if it drifts to `1`, `aud` becomes the App ID URI and a GUID-based config rejects everything |
| `idtyp` optional claim | not enabled — see the decision above |
| Per-environment tenant and audience values | must be confirmed |

**A declared role is not an assigned one.** Roles declared without admin consent produce a token that
looks entirely correct but silently omits `roles` — verify by minting a token, not by reading the
portal.

### Client onboarding

Clients request `scope=<this API's audience>/.default` against
`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token` — **not** a Graph scope, and not another
CP API's audience. Either the GUID or `api://` form yields the bare GUID as `aud` on a v2.0 token.

---
## 7. Claim reference — every field, and what we do with it

Values shown are from the dev reference token (a dev token). "Reason" is the `TokenValidationException.Reason`
raised on failure, which maps to 401 for authentication and 403 for authorisation (§6).

### 7.1 JOSE header

| Field | Example | Meaning | What we do | Reason on failure |
|---|---|---|---|---|
| `alg` | `RS256` | Signing algorithm **as declared by the caller** | **Ignored as an input; RS256 is pinned in the key selector.** The header value is attacker-controlled, so it is never used to choose a verification algorithm — that is the alg-confusion attack. It cannot be constrained from the key set either, because Entra's JWKS entries carry no `alg` field. See §2 | `INVALID_SIGNATURE` |
| `kid` | `fEtqrhKT1bXAGafSdQoN1vXTRpI` | Which signing key was used | Selects the key from the cached JWKS. An unknown `kid` triggers one rate-limited refresh, then rejects — this is how Entra key rotation is absorbed without a redeploy | `INVALID_SIGNATURE` |
| `typ` | `JWT` | Token type | **Not validated** (§5). Accepting both `JWT` and `at+jwt` rejects nothing an attacker would send | — |
| `jku` `jwk` `x5u` `x5c` | — | Key material supplied *by the token* | **Ignored by construction.** The key selector consults only the configured JWKS, so a token cannot nominate its own verification key | `INVALID_SIGNATURE` |
| `crit` | — | Critical extensions | Not supported; Nimbus rejects unrecognised critical headers | `INVALID_SIGNATURE` |

### 7.2 Payload claims we validate

| Claim | Example | Meaning | What we do | Reason on failure |
|---|---|---|---|---|
| `iss` | `https://login.microsoftonline.com/e2995d11-…/v2.0` | Token issuer | **Exact string match** against the configured issuer. Never prefix or contains — those admit `…/v2.0.attacker.example` | `INVALID_CLAIMS` |
| `aud` | `bff71afb-…` | Intended recipient | Must equal **this API's** audience. v2.0 tokens carry the bare client-ID GUID, not `api://…`. **The single highest-value check**: it is the only thing rejecting a genuine, correctly signed token minted for a different resource — a Microsoft Graph token, or a sibling CP API's | `INVALID_CLAIMS` |
| `exp` | `1786567989` | Expiry | Required, and must be in the future within the configured skew (default 60 s, hard cap 300 s) | `INVALID_CLAIMS` |
| `nbf` | `1786538889` | Not valid before | Checked when present, same skew allowance | `INVALID_CLAIMS` |
| `tid` | `e2995d11-…` | Entra tenant | Exact match against the configured tenant. Strictly redundant while `iss` is exact-matched — the issuer contains the tenant — but one line of config, kept as defence in depth. **Not counted as independent coverage** | `INVALID_CLAIMS` |
| `ver` | `2.0` | Token version | Must be `2.0`. A v1.0 token is rejected by `iss` anyway (its issuer is `sts.windows.net`), so this is belt-and-braces that also documents the decision not to build the v1.0 path | `INVALID_CLAIMS` |
| `azp` | `a8791612-…` | **Calling application's client id** | The caller's identity and **the tenancy key for every repository query**. Must parse as a UUID. Note this is *not* `oid`/`sub` | `MISSING_CLIENT_ID`, `MALFORMED_CLIENT_ID` |
| `sub` | `2b5caaab-…` | Subject | For an app-only token Entra sets this to the service principal object id, so it **equals `oid`**. For a delegated token it identifies the user and the two differ — that inequality is how we detect a delegated token | `DELEGATED_TOKEN` |
| `oid` | `2b5caaab-…` | Service principal object id in this tenant | Compared with `sub` per above. **Must not be used as the client identity** — seeding a client registry with this instead of `azp` produces a signature-valid token that 403s, which is a confusing failure to debug | `DELEGATED_TOKEN` |
| `roles` | `["app.read"]` | Application roles | Must be present and non-empty, and must contain a role this API recognises (`app.read` or `app.write`). Either role satisfies any operation, matching the deployed APIM policy | `MISSING_ROLE`, `INSUFFICIENT_ROLE` |
| `scp` | *(absent)* | Delegated scopes | **Prohibited.** Its presence means a delegated (user) token. Enforcing app-only this way avoids depending on `idtyp`, which Entra omits by default | `INVALID_CLAIMS` |

### 7.3 Present, but deliberately not validated

| Claim | Example | Meaning | Why not |
|---|---|---|---|
| `iat` | `1786538889` | Issued at | `exp` already bounds the window and Entra sets `iat == nbf`. A separate max-age policy adds surprising rejections (§5) |
| `azpacr` | `1` | How the client authenticated: `0` public/none, `1` client secret, `2` certificate | Only `0` is worth rejecting, and the client-credentials flow should never produce it. **Recommended once clients hold certificates:** require `2`. Today the dev client uses a secret, so requiring it would reject real traffic |
| `uti` | `1y3wS3Mo7UKr4NkXIs6JAA` | Unique token identifier | Replay prevention is out of scope (§5) — bearer tokens are replayable until `exp` by design. If that is ever revisited, this is the key a replay cache would use. Entra emits `uti`, **not** `jti` |
| `aio`, `rh`, `xms_ftd` | opaque | Undocumented Entra internals | **Never validate, depend on, or log these.** They are unspecified and change without notice |

### 7.4 Recommended additions

| Item | Status | Value if added |
|---|---|---|
| `idtyp` optional claim | **Absent.** Not emitted unless enabled on the app registration | Turns the app-only inference (`sub == oid` + `roles` + no `scp`) into a single direct check. ⚠️ Until enabled, code MUST NOT require it — requiring it today rejects **all** legitimate traffic |
| `azpacr` = `2` | Blocked on client credentials | Proves the caller authenticated with a certificate rather than a shared secret. Pair with the self-serve onboarding model where consumers upload a public key |
| Shorter token lifetime | **Measured at 8 h 5 min**, not 1 hour. `exp - iat` is **exactly 29,100 s on two independent tokens** — one dev, one live SIT — so this is a configured value, not the randomised 60–90 min Entra default. The API owner's understanding is 1 hour, so something is setting a token-lifetime policy that they may not know about. Resolve the discrepancy before treating either figure as fact | The lifetime *is* the replay window, since replay tracking is out of scope (§5). Eight hours is long for a bearer token; shortening it is cheaper than any replay-tracking mechanism |

### 7.5 Never expected in an app-only token

If any of these appear, the token is a user (delegated) token and must not be accepted. `scp` prohibition
and the `sub == oid` check already reject it; these are listed so the shape is recognisable in a log or a
debugger.

| Claim | Meaning |
|---|---|
| `scp` | Delegated scopes (see 7.2) |
| `name`, `preferred_username`, `upn`, `email` | Human identity |
| `groups`, `wids` | Group and directory-role membership |
| `nonce` | OIDC replay guard, ID tokens only |
| `appid` | v1.0 equivalent of `azp`. Rejected via `ver`; would only appear on a v1.0 token |
