## 1. Dependency Upgrade

- [x] 1.1 In `build.gradle`, change `api-cp-crime-hearing-results-document-subscription` version from `2.0.7` to `2.0.8`
- [x] 1.2 Run `./gradlew dependencies` to confirm `2.0.8` resolves correctly

## 2. Service Implementation

- [x] 2.1 Add `rotateSecret(String keyId): String` to `HmacManager` — generates new secret bytes via `hmacKeyService.generateSecretBytes()`, stores under the existing `keyId` via `secretStoreService.setSecret`, returns the base64-encoded new secret
- [x] 2.2 In `SubscriptionService`, add `rotateSubscriptionSecret(UUID clientId, UUID subscriptionId, RotateSecretRequest request)` annotated `@Transactional`:
  - fetch `ClientEntity` via `fetchClient`
  - fetch `ClientHmacEntity` via `clientHmacRepository.findBySubscriptionId`
  - validate `request.getKeyId()` matches `clientHmacEntity.getKeyId()`, throw `EntityNotFoundException` (→ 404) if not
  - call `hmacManager.rotateSecret(request.getKeyId())` to get new encoded secret (keyId unchanged)
  - return `HmacCredentials.builder().keyId(request.getKeyId()).secret(newEncodedSecret).build()`

## 3. Controller Implementation

- [x] 3.1 In `SubscriptionController`, implement `rotateClientSubscriptionSecret(UUID clientSubscriptionId, RotateSecretRequest rotateSecretRequest, UUID xCorrelationId)`:
  - read `clientId` from MDC
  - log the operation
  - call `subscriptionValidationService.validateClientSubscriptionExists(clientId, clientSubscriptionId)`
  - delegate to `subscriptionService.rotateSubscriptionSecret(clientId, clientSubscriptionId, rotateSecretRequest)`
  - return `ResponseEntity.ok(result)`

## 4. Test Coverage

- [x] 4.1 In `SubscriptionServiceTest`, add a test that verifies `rotateSubscriptionSecret` calls `hmacManager.rotateSecret` with the existing keyId and returns `HmacCredentials` with the same keyId and new encoded secret
- [x] 4.2 In `SubscriptionServiceTest`, add a test that verifies `rotateSubscriptionSecret` throws `EntityNotFoundException` (→ 404) when `request.keyId` does not match the stored keyId
- [x] 4.3 In `SubscriptionControllerTest`, add a test for `rotateClientSubscriptionSecret` returning `200 OK` with `HmacCredentials` body
- [x] 4.4 In `SubscriptionControllerTest`, add a test for `rotateClientSubscriptionSecret` returning `404` when `validateClientSubscriptionExists` throws `EntityNotFoundException`

## 5. Verification

- [x] 5.1 Run `./gradlew test` and confirm all tests pass
