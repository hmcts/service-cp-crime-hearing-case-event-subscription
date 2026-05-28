# CP Service Coding Standards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce CP service coding standards (feature toggle placement, mapper-owned object construction) by fixing existing violations in the service and adding rules to the shared template and review checklist skill.

**Architecture:** Two independent workstreams — Workstream A fixes violations in the current service codebase (3 mapper extractions); Workstream B adds the rules to `service-shared.md` and the `review-checklist` skill so every CP service and every future PR review enforces them automatically.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Lombok, MapStruct, JUnit 5, Mockito, AssertJ

**Branch:** `dev/claude-review-checklist` (from `main`)

> **Note on PR #265 (`feature/AMP-504_2`):** Two violations from that branch are NOT in this plan — they must be fixed in the PR before merge:
> - Remove dead `hearingEventJsonEnabled` field from `NotificationController` (T5)
> - Extract inline `HearingEventPayloadEntity` / `HearingEventSubscriptionEntity` builders in `HearingEventPayloadService` to a new `HearingEventPayloadMapper` (M1)

---

## File Map

### Workstream A — Code fixes (service repo)

| Action | File |
|---|---|
| Create | `src/main/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapper.java` |
| Create | `src/test/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapperTest.java` |
| Modify | `src/main/java/uk/gov/hmcts/cp/subscription/services/DocumentService.java` |
| Modify | `src/test/java/uk/gov/hmcts/cp/subscription/services/DocumentServiceTest.java` |
| Create | `src/main/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapper.java` |
| Create | `src/test/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapperTest.java` |
| Modify | `src/main/java/uk/gov/hmcts/cp/subscription/services/SubscriptionService.java` |
| Modify | `src/test/java/uk/gov/hmcts/cp/subscription/unit/services/SubscriptionServiceTest.java` |
| Create | `src/main/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapper.java` |
| Create | `src/test/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapperTest.java` |
| Modify | `src/main/java/uk/gov/hmcts/cp/hmac/services/HmacKeyService.java` |
| Modify | `src/test/java/uk/gov/hmcts/cp/hmac/services/HmacKeyServiceTest.java` |

### Workstream B — Config enforcement

| Action | File |
|---|---|
| Modify | `../../apim-claude-template/templates/service-shared.md` |
| Modify | `~/.claude/plugins/marketplaces/agentic-plugins-marketplace/plugins/skills/review-checklist/skills/review-checklist/SKILL.md` |

---

## Workstream A — Code Fixes

---

### Task A1: Extract `DocumentContent` construction to `DocumentContentMapper`

`DocumentService.getDocumentContent()` builds a `DocumentContent` inline. Extract to a new mapper so the service test can mock construction without asserting on individual fields.

**Files:**
- Create: `src/main/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapper.java`
- Create: `src/test/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapperTest.java`
- Modify: `src/main/java/uk/gov/hmcts/cp/subscription/services/DocumentService.java`
- Modify: `src/test/java/uk/gov/hmcts/cp/subscription/services/DocumentServiceTest.java`

- [ ] **Step 1: Write the failing mapper test**

```java
// src/test/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapperTest.java
package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContentMapperTest {

    private final DocumentContentMapper mapper = new DocumentContentMapper();

    @Test
    void toDocumentContent_should_map_body_contentType_and_fileName() {
        final byte[] body = "pdf-bytes".getBytes();
        final DocumentContent result = mapper.toDocumentContent(body, MediaType.APPLICATION_PDF, "ruling.pdf");
        assertThat(result.getBody()).isEqualTo(body);
        assertThat(result.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(result.getFileName()).isEqualTo("ruling.pdf");
    }
}
```

- [ ] **Step 2: Run test — expect compile failure (class does not exist yet)**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.mappers.DocumentContentMapperTest' 2>&1 | tail -10
```

Expected: `error: cannot find symbol` for `DocumentContentMapper`.

- [ ] **Step 3: Create `DocumentContentMapper`**

```java
// src/main/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapper.java
package uk.gov.hmcts.cp.subscription.mappers;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.subscription.model.DocumentContent;

@Component
public class DocumentContentMapper {

    public DocumentContent toDocumentContent(final byte[] body, final MediaType contentType, final String fileName) {
        return DocumentContent.builder()
                .body(body)
                .contentType(contentType)
                .fileName(fileName)
                .build();
    }
}
```

- [ ] **Step 4: Run mapper test — expect PASS**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.mappers.DocumentContentMapperTest'
```

Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Update `DocumentService` to inject and use `DocumentContentMapper`**

In `DocumentService.java`, add `documentContentMapper` as a constructor-injected field and replace the inline builder in `getDocumentContent`:

```java
// add to field declarations (Lombok @RequiredArgsConstructor handles injection)
private final DocumentContentMapper documentContentMapper;

// replace the return statement in getDocumentContent():
// BEFORE:
//   return DocumentContent.builder()
//           .body(document.getBody())
//           .contentType(MediaType.APPLICATION_PDF)
//           .fileName(metadata.getFileName())
//           .build();
// AFTER:
return documentContentMapper.toDocumentContent(
        document.getBody(), MediaType.APPLICATION_PDF, metadata.getFileName());
```

Also remove the `import org.springframework.http.MediaType;` if it is no longer used elsewhere in `DocumentService` (it will be used in the mapper instead).

- [ ] **Step 6: Update `DocumentServiceTest` to mock `DocumentContentMapper`**

Remove the inline `DocumentContent` field assertion. Mock the mapper and verify the delegation:

```java
// add mock
@Mock
DocumentContentMapper documentContentMapper;

// in the getDocumentContent test, replace assertions on individual fields:
// BEFORE (remove these lines that assert on body/contentType/fileName directly):
//   assertThat(documentContent.getBody()).isEqualTo(body);
//   assertThat(documentContent.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
//   assertThat(documentContent.getFileName()).isEqualTo(metadata.getFileName());

// AFTER — stub the mapper and assert on the returned object:
final byte[] body = "bytes".getBytes();
final MaterialMetadata metadata = MaterialMetadata.builder().fileName("ruling.pdf").build();
final DocumentContent expected = DocumentContent.builder()
        .body(body).contentType(MediaType.APPLICATION_PDF).fileName("ruling.pdf").build();

when(materialClient.getMetadata(materialId)).thenReturn(metadata);
when(materialClient.getContentUrl(materialId)).thenReturn(materialUrl);
when(materialDocumentClient.getMaterialDocument(any())).thenReturn(ResponseEntity.ok(body));
when(documentContentMapper.toDocumentContent(body, MediaType.APPLICATION_PDF, "ruling.pdf"))
        .thenReturn(expected);

DocumentContent result = documentService.getDocumentContent(documentId);

assertThat(result).isEqualTo(expected);
verify(documentContentMapper).toDocumentContent(body, MediaType.APPLICATION_PDF, "ruling.pdf");
```

- [ ] **Step 7: Run all DocumentService tests — expect PASS**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.services.DocumentServiceTest'
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapper.java \
        src/test/java/uk/gov/hmcts/cp/subscription/mappers/DocumentContentMapperTest.java \
        src/main/java/uk/gov/hmcts/cp/subscription/services/DocumentService.java \
        src/test/java/uk/gov/hmcts/cp/subscription/services/DocumentServiceTest.java
git commit -m "$(cat <<'EOF'
refactor: extract DocumentContent construction to DocumentContentMapper (M1)

Service delegates object creation to mapper; mapper test covers field-by-field
construction; DocumentServiceTest mocks mapper, no ArgumentCaptor needed.
EOF
)"
```

---

### Task A2: Extract `HmacCredentials` construction to `HmacCredentialsMapper`

`SubscriptionService.rotateSubscriptionSecret()` builds `HmacCredentials` inline. Extract to a mapper.

**Files:**
- Create: `src/main/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapper.java`
- Create: `src/test/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapperTest.java`
- Modify: `src/main/java/uk/gov/hmcts/cp/subscription/services/SubscriptionService.java`
- Modify: `src/test/java/uk/gov/hmcts/cp/subscription/unit/services/SubscriptionServiceTest.java`

- [ ] **Step 1: Write the failing mapper test**

```java
// src/test/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapperTest.java
package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.HmacCredentials;

import static org.assertj.core.api.Assertions.assertThat;

class HmacCredentialsMapperTest {

    private final HmacCredentialsMapper mapper = new HmacCredentialsMapper();

    @Test
    void toCredentials_should_map_keyId_and_secret() {
        HmacCredentials result = mapper.toCredentials("kid-v1-abc", "encoded-secret");
        assertThat(result.getKeyId()).isEqualTo("kid-v1-abc");
        assertThat(result.getSecret()).isEqualTo("encoded-secret");
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.mappers.HmacCredentialsMapperTest' 2>&1 | tail -10
```

Expected: `error: cannot find symbol` for `HmacCredentialsMapper`.

- [ ] **Step 3: Create `HmacCredentialsMapper`**

```java
// src/main/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapper.java
package uk.gov.hmcts.cp.subscription.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.HmacCredentials;

@Component
public class HmacCredentialsMapper {

    public HmacCredentials toCredentials(final String keyId, final String secret) {
        return HmacCredentials.builder()
                .keyId(keyId)
                .secret(secret)
                .build();
    }
}
```

- [ ] **Step 4: Run mapper test — expect PASS**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.mappers.HmacCredentialsMapperTest'
```

Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Update `SubscriptionService`**

Add `hmacCredentialsMapper` field and replace the inline builder in `rotateSubscriptionSecret`:

```java
// add to field declarations
private final HmacCredentialsMapper hmacCredentialsMapper;

// replace in rotateSubscriptionSecret():
// BEFORE:
//   return HmacCredentials.builder()
//           .keyId(request.getKeyId())
//           .secret(newEncodedSecret)
//           .build();
// AFTER:
return hmacCredentialsMapper.toCredentials(request.getKeyId(), newEncodedSecret);
```

- [ ] **Step 6: Update `SubscriptionServiceTest` — mock mapper, remove field assertions**

```java
// add mock
@Mock
HmacCredentialsMapper hmacCredentialsMapper;

// in rotate_should_return_credentials test, replace:
// BEFORE (remove):
//   assertThat(result.getKeyId()).isEqualTo(existingKeyId);
//   assertThat(result.getSecret()).isEqualTo(newEncodedSecret);

// AFTER — stub and verify:
final HmacCredentials expected = HmacCredentials.builder()
        .keyId(existingKeyId).secret(newEncodedSecret).build();
when(hmacCredentialsMapper.toCredentials(existingKeyId, newEncodedSecret)).thenReturn(expected);

HmacCredentials result = subscriptionService.rotateSubscriptionSecret(clientId, subscriptionId, request);

assertThat(result).isEqualTo(expected);
verify(hmacCredentialsMapper).toCredentials(existingKeyId, newEncodedSecret);
```

- [ ] **Step 7: Run SubscriptionService tests — expect PASS**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.subscription.unit.services.SubscriptionServiceTest'
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapper.java \
        src/test/java/uk/gov/hmcts/cp/subscription/mappers/HmacCredentialsMapperTest.java \
        src/main/java/uk/gov/hmcts/cp/subscription/services/SubscriptionService.java \
        src/test/java/uk/gov/hmcts/cp/subscription/unit/services/SubscriptionServiceTest.java
git commit -m "$(cat <<'EOF'
refactor: extract HmacCredentials construction to HmacCredentialsMapper (M1)

EOF
)"
```

---

### Task A3: Extract `KeyPair` construction to `HmacKeyMapper`

`HmacKeyService.generateKey()` builds a `KeyPair` inline. Extract to a mapper in the `hmac` package.

**Files:**
- Create: `src/main/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapper.java`
- Create: `src/test/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapperTest.java`
- Modify: `src/main/java/uk/gov/hmcts/cp/hmac/services/HmacKeyService.java`
- Modify: `src/test/java/uk/gov/hmcts/cp/hmac/services/HmacKeyServiceTest.java`

- [ ] **Step 1: Write the failing mapper test**

```java
// src/test/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapperTest.java
package uk.gov.hmcts.cp.hmac.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.hmac.model.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

class HmacKeyMapperTest {

    private final HmacKeyMapper mapper = new HmacKeyMapper();

    @Test
    void toKeyPair_should_map_keyId_and_secret() {
        final byte[] secret = new byte[]{1, 2, 3};
        final KeyPair result = mapper.toKeyPair("kid-v1-abc", secret);
        assertThat(result.getKeyId()).isEqualTo("kid-v1-abc");
        assertThat(result.getSecret()).isEqualTo(secret);
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.hmac.mappers.HmacKeyMapperTest' 2>&1 | tail -10
```

Expected: `error: cannot find symbol`.

- [ ] **Step 3: Create `HmacKeyMapper`**

```java
// src/main/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapper.java
package uk.gov.hmcts.cp.hmac.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.hmac.model.KeyPair;

@Component
public class HmacKeyMapper {

    public KeyPair toKeyPair(final String keyId, final byte[] secret) {
        return KeyPair.builder()
                .keyId(keyId)
                .secret(secret)
                .build();
    }
}
```

- [ ] **Step 4: Run mapper test — expect PASS**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.hmac.mappers.HmacKeyMapperTest'
```

Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 5: Update `HmacKeyService`**

Change `@AllArgsConstructor` to `@RequiredArgsConstructor` and add `hmacKeyMapper` field. Replace the inline builder in `generateKey()`:

```java
// Full updated class:
package uk.gov.hmcts.cp.hmac.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.hmac.mappers.HmacKeyMapper;
import uk.gov.hmcts.cp.hmac.model.KeyPair;
import uk.gov.hmcts.cp.vault.VaultServiceProperties;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HmacKeyService {

    private static final int SECRET_BYTES_LENGTH = 32;

    private final HmacKeyMapper hmacKeyMapper;
    private final VaultServiceProperties vaultServiceProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public KeyPair generateKey() {
        final String keyId = "kid-v1-" + UUID.randomUUID();
        log.info("Generating new keyPair for keyId:{}", keyId);
        final byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(secretBytes);
        return hmacKeyMapper.toKeyPair(keyId, secretBytes);
    }

    public byte[] generateSecretBytes() {
        log.info("Generating secrets bytes.");
        final byte[] secretBytes = new byte[SECRET_BYTES_LENGTH];
        secureRandom.nextBytes(secretBytes);
        return secretBytes;
    }
}
```

- [ ] **Step 6: Update `HmacKeyServiceTest` — add mapper mock, replace output assertions with delegation verification**

```java
// replace the generateKey test:
@Mock
private HmacKeyMapper hmacKeyMapper;

@Mock
private VaultServiceProperties vaultServiceProperties;

@InjectMocks
private HmacKeyService service;

@Test
void generateKey_should_delegate_to_mapper_with_generated_keyId_and_secret() {
    final KeyPair expected = KeyPair.builder().keyId("kid-v1-test").secret(new byte[32]).build();
    when(hmacKeyMapper.toKeyPair(anyString(), any(byte[].class))).thenReturn(expected);

    final KeyPair result = service.generateKey();

    verify(hmacKeyMapper).toKeyPair(matches("^kid-v1-[a-f0-9\\-]{36}$"), any(byte[].class));
    assertThat(result).isEqualTo(expected);
}

@Test
void generateSecretBytes_should_return_32_bytes() {
    assertThat(service.generateSecretBytes()).hasSize(32);
}
```

> Note: the existing `generateKey_should_return_distinct_when_vault_enabled` test asserted on uniqueness of real outputs — move that assertion to `HmacKeyMapperTest` or delete it (the mapper test proves construction; uniqueness comes from `UUID.randomUUID()` which is not under test here).

- [ ] **Step 7: Run HmacKeyService tests — expect PASS**

```bash
./gradlew test --tests 'uk.gov.hmcts.cp.hmac.services.HmacKeyServiceTest'
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Run full test suite to confirm no regressions**

```bash
./gradlew test -x apiTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapper.java \
        src/test/java/uk/gov/hmcts/cp/hmac/mappers/HmacKeyMapperTest.java \
        src/main/java/uk/gov/hmcts/cp/hmac/services/HmacKeyService.java \
        src/test/java/uk/gov/hmcts/cp/hmac/services/HmacKeyServiceTest.java
git commit -m "$(cat <<'EOF'
refactor: extract KeyPair construction to HmacKeyMapper (M1)

EOF
)"
```

---

## Workstream B — Config Enforcement

---

### Task B1: Update `service-shared.md` with layer architecture and toggle rules

This file is `@`-included in every `service-cp-*` repo's `.claude/CLAUDE.md`. Adding rules here means every future Claude session in any CP service repo will enforce them automatically.

**Files:**
- Modify: `../../apim-claude-template/templates/service-shared.md`

- [ ] **Step 1: Add the Layer Architecture section**

Open `apim-claude-template/templates/service-shared.md`. Find the `## Architecture Rules` section. Insert the following block **before** the existing bullet points:

```markdown
### Layer Architecture

Each layer has one responsibility. Layers communicate only with the layer directly below them.

| Layer | Responsibility | Constraint |
|---|---|---|
| **Controller** | Receive HTTP; validate thoroughly; delegate to Manager or Service | No business logic; no object construction |
| **Manager** | Orchestrate multiple services; prevent bi-directional service dependencies | No direct repository calls |
| **Service** | Business logic; call clients and repositories via mappers | Never construct objects inline — delegate all construction to a mapper |
| **Mapper** | Convert objects between layers; create any new objects | Owns all `builder()` calls; has its own unit test covering field-by-field construction |
| **Repository** | JPA entity interactions | Must have a `@DataJpaTest` test proving Flyway schema matches JPA entity |
| **Client** | External HTTP calls | No business logic |

**Mapper-creates-objects rule:** Mappers do not only convert — they also create new objects. A service method must never call `.builder()` directly. This ensures:
- Service unit tests mock the mapper and verify the call — no `ArgumentCaptor` needed
- All construction logic is tested in one focused mapper test
```

- [ ] **Step 2: Add the Feature Toggle Placement section**

After the Layer Architecture block, add:

```markdown
### Feature Toggle Placement

Feature toggles (`@Value`-injected booleans) are decision-layer concerns. Five rules apply:

**T1 — `@Value` toggle fields live only in orchestrating services.**
Persist/domain services and controllers must not declare `@Value` toggle fields. Grepping the property key (e.g. `hearing-event.json.enabled`) must find every declaration and every check in one pass.

**T2 — Toggle check is explicit and at call-site.**
Reference the boolean field directly before calling the downstream service — never delegate to a private method that returns a sentinel value.
```java
// CORRECT
if (hearingEventJsonEnabled) {
    hearingEventPayloadService.saveIfAbsent(eventPayload);
}

// WRONG — toggle delegated to private method returning null on toggle-off
private UUID persistPayload(EventPayload p) {
    if (hearingEventJsonEnabled) { return svc.save(p); }
    return null;
}
```

**T3 — Switch state must not be inferred from data state.**
Do not set a return value to `null` to signal toggle-off, then null-check it downstream to infer toggle state. When the toggle is removed, a developer greps for its name — null checks scattered in data flow will not appear in that grep and will survive as dead code.
```java
// WRONG — null encodes "toggle was off"; null check survives toggle removal
final UUID id = hearingEventJsonEnabled ? svc.save(p) : null;
if (id != null) { subscriptionSvc.save(subscriptionId, id); }

// CORRECT — both branches are findable on removal
if (hearingEventJsonEnabled) {
    final UUID id = svc.save(p);
    subscriptionSvc.save(subscriptionId, id);
}
```

**T4 — Persist/domain services are toggle-blind.**
Any class that owns a `Repository` must not declare any `@Value` toggle field. It does exactly what its method name says, unconditionally.

**T5 — No dead toggle fields.**
If a `@Value` toggle field is declared but never read in that class, remove it.
```

- [ ] **Step 3: Add the remaining coding patterns section**

After the Feature Toggle Placement block, add:

```markdown
### Coding Patterns

**Validation at entry point:** Input validation (unknown event type, null required field) at the earliest boundary — controller for HTTP flows, message handler (`ServiceBusHandlers`) for Service Bus flows. Domain services must not throw `IllegalArgumentException` for bad input that should have been rejected upstream.

**Explicit idempotency:** When a persist method skips a duplicate (`existsBy…` → return), it must log at INFO. Silent returns with no trace are not permitted.

**Test naming:** All test methods follow `subject_should_doOutcome` or `subject_should_doOutcome_whenCondition`. Mixed styles in one class are not permitted.
```

- [ ] **Step 4: Verify the change renders correctly**

```bash
cat ../../apim-claude-template/templates/service-shared.md | grep -A3 "Layer Architecture\|Feature Toggle\|Coding Patterns"
```

Expected: the three new section headings appear.

- [ ] **Step 5: Commit from the template repo**

```bash
cd ../../apim-claude-template
git add templates/service-shared.md
git commit -m "$(cat <<'EOF'
docs: add layer architecture, feature toggle placement (T1-T5), and coding patterns to service-shared.md

Enforces CP service standards in every service-cp-* Claude session via @-include.
EOF
)"
cd -
```

---

### Task B2: Add CP Service Patterns block to `review-checklist` skill

Every time the `review-checklist` skill is invoked on a CP service PR, it will now surface violations of T1–T5, M1–M3, V1, I1, N1 explicitly.

**Files:**
- Modify: `~/.claude/plugins/marketplaces/agentic-plugins-marketplace/plugins/skills/review-checklist/skills/review-checklist/SKILL.md`

- [ ] **Step 1: Append the CP Service Patterns section to the checklist**

Open the file. At the end of the `## Checklist` section (before `## Scoring`), insert:

```markdown
### CP Service Patterns
_Apply only when the diff touches a `service-cp-*` repo. Skip for `api-cp-*`, `cpp-context-*`, or `cpp-apitests`._

| # | Check | Pass / Fail |
|---|-------|-------------|
| T1 | `@Value` toggle fields declared only in orchestrating services — not in controllers, persist services, or domain services | |
| T2 | Toggle check references the `@Value` boolean field directly at call-site — not delegated to a private method returning a sentinel | |
| T3 | Switch state not inferred from data state — no null/sentinel return used to encode toggle-off; each guarded branch references the boolean field explicitly | |
| T4 | Persist/domain services are toggle-blind — no `@Value` toggle field in any class that owns a `Repository` | |
| T5 | No `@Value` toggle field declared in a class that never reads it (dead toggle field) | |
| M1 | Mapper owns ALL object construction between layers — no inline `.builder()` calls in service methods | |
| M2 | Each mapper has its own unit test covering field-by-field construction; service tests mock the mapper (no `ArgumentCaptor`) | |
| M3 | Each `Repository` has a `@DataJpaTest` test proving Flyway schema matches JPA entity | |
| V1 | Input validation at entry point (controller or `ServiceBusHandlers`) — not in downstream services | |
| I1 | Idempotency skips (`existsBy…` → return) have an INFO log at the skip site | |
| N1 | All test methods follow `subject_should_doOutcome[_whenCondition]` naming — no mixed styles in a class | |
```

- [ ] **Step 2: Update the Scoring section to include CP patterns**

Find the Scoring block and add one line:

```markdown
- Any FAIL in **T3** or **T4** in a CP service → **block merge** (toggle removal safety and domain purity)
```

- [ ] **Step 3: Verify the file renders correctly**

```bash
grep -A 15 "CP Service Patterns" ~/.claude/plugins/marketplaces/agentic-plugins-marketplace/plugins/skills/review-checklist/skills/review-checklist/SKILL.md
```

Expected: the table header and first few rows appear.

- [ ] **Step 4: Commit**

```bash
cd ~/.claude/plugins/marketplaces/agentic-plugins-marketplace
git add plugins/skills/review-checklist/skills/review-checklist/SKILL.md
git commit -m "$(cat <<'EOF'
feat: add CP Service Patterns block to review-checklist skill (T1-T5, M1-M3, V1, I1, N1)

T3/T4 failures block merge for toggle removal safety and domain purity.
EOF
)"
cd -
```

- [ ] **Step 5: Reload plugins so the updated skill takes effect immediately**

In the Claude Code CLI, run:
```
/reload-plugins
```

Then verify by running the review checklist:
```
/review-checklist
```

Expected: the CP Service Patterns table appears in the checklist output for this service repo.

---

## How the Enforcement Works End-to-End

Once both workstreams are complete, the standards are enforced at two points:

```
Developer writes new feature-toggle code
        ↓
Claude reads service-shared.md (via @-include in .claude/CLAUDE.md)
        ↓
Claude applies T1–T5 rules during implementation — correct pattern from the start
        ↓
PR raised → /review-checklist invoked
        ↓
CP Service Patterns block checks T1–T5, M1–M3, V1, I1, N1
        ↓
Any FAIL surfaced before merge — T3/T4 block merge entirely
```

**Retroactive enforcement:** Existing violations (e.g. PR #265 `NotificationController` dead field, `HearingEventPayloadService` inline builders) are caught the first time the review checklist runs on those files. The three violations fixed in Workstream A (`DocumentService`, `SubscriptionService`, `HmacKeyService`) are already resolved on `dev/claude-review-checklist`.