## Context

The service manages HMAC signing secrets for subscriber callbacks. When a subscription is created, `HmacManager.createAndStoreNewKey()` generates a 32-byte secret, stores it in Azure Key Vault keyed by a `kid-v1-<uuid>` identifier, and records the keyId in `ClientHmacEntity`. Callbacks are signed using this secret. Currently there is no way to rotate the secret.

Version 2.0.8 of the API spec library introduces the `POST /clientSubscription/{clientSubscriptionId}/secret/rotate` endpoint. This design covers the service implementation of that contract.

## Goals / Non-Goals

**Goals:**
- Bump the API spec library dependency to `2.0.8`
- Implement the rotate endpoint: authenticate the caller, validate ownership, replace the HMAC key, return the updated subscription with new keyId and secret
- Keep the rotate operation transactional — old key deleted and new key saved atomically in the database

**Non-Goals:**
- Invalidating or revoking the old Key Vault secret (Azure Key Vault versioning handles retention; the old keyId simply becomes unreferenced)
- Notifying subscribers that a rotation occurred
- Enforcing a minimum rotation interval

## Decisions

### Rotate the vault secret in-place, preserving the existing `keyId`

A subscription has exactly one active HMAC keyId at any time. On rotation, `HmacManager.rotateSecret(keyId)` generates new 32-byte secret material and overwrites the vault entry under the same `keyId` — the `ClientHmacEntity` row is unchanged. This avoids a delete-and-reinsert and keeps the `keyId` stable, which is important for clients that have the keyId cached.

### Return `HmacCredentials { keyId, secret }`

The 2.0.8 `SubscriptionApi` contract defines the rotate response as `HmacCredentials`, a lightweight model containing only `keyId` and base64-encoded `secret`. `SubscriptionService.rotateSubscriptionSecret` returns `HmacCredentials` directly, with the secret encoded via `EncodingService.encodeWithBase64`.

### `RotateSecretRequest.keyId` must match the stored keyId

The 2.0.8 contract passes a `RotateSecretRequest` body containing the caller's current `keyId`. The service SHALL verify this matches the `ClientHmacEntity.keyId` for the subscription before rotating. A mismatch throws `EntityNotFoundException`, returning 404.

### Reuse existing validation pattern

`SubscriptionValidationService.validateClientSubscriptionExists(clientId, subscriptionId)` already throws `EntityNotFoundException` (→ 404) when ownership fails. The rotate endpoint reuses this before proceeding, consistent with update and delete.

## Migration Plan

1. Bump `build.gradle` dependency to `2.0.8`.
2. Add `rotateSubscriptionSecret` to `SubscriptionService` (`@Transactional`: validate `RotateSecretRequest.keyId` matches stored keyId, rotate vault secret in-place via `HmacManager.rotateSecret`, return `HmacCredentials` with same keyId and new secret).
3. Implement the interface method in `SubscriptionController`, delegating to the service after ownership validation.
4. Add unit tests.
5. Deploy as a normal release — no schema migration required, no breaking change to existing subscribers.

Rollback: revert the dependency and remove the two new methods. No data migration needed.

## Open Questions

_(none)_
