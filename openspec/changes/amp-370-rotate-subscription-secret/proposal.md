## Why

Subscribers currently have no way to rotate their HMAC signing secret. A dedicated rotate endpoint lets subscribers refresh their secret on demand without disruption.

## What Changes

- Bump dependency `uk.gov.hmcts.cp:api-cp-crime-hearing-results-document-subscription` from `2.0.7` to `2.0.8` in `build.gradle` (the 2.0.8 spec library introduces the rotate endpoint contract)
- Implement `POST /clientSubscription/{clientSubscriptionId}/secret/rotate` in `SubscriptionController`
- Add `rotateSubscriptionSecret` to `SubscriptionService` — validates the caller's current keyId, rotates the vault secret in-place via `HmacManager`, returns `HmacCredentials` with the same keyId and new secret
- Add validation in `SubscriptionValidationService` reusing the existing ownership check

## Capabilities

### New Capabilities

- `rotate-subscription-secret`: Subscribers can rotate their HMAC signing secret via a POST endpoint, receiving the same keyId with a new encoded secret in response without altering their subscription configuration

### Modified Capabilities

_(none)_

## Impact

- `build.gradle`: dependency version bump `2.0.7` → `2.0.8`
- `SubscriptionController`: new `rotateSubscriptionSecret` method implementing the generated `SubscriptionApi` interface
- `SubscriptionService`: new `rotateSubscriptionSecret` method — validates keyId match, rotates vault secret in-place via `HmacManager.rotateSecret`, returns `HmacCredentials`
- `SubscriptionValidationService`: existing `validateClientSubscriptionExists` reused for ownership check
- New unit tests in `SubscriptionServiceTest` and `SubscriptionControllerTest`
