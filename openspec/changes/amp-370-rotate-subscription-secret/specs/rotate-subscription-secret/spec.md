## ADDED Requirements

### Requirement: Subscriber can rotate their HMAC signing secret
The system SHALL expose `POST /clientSubscription/{clientSubscriptionId}/secret/rotate`. When called by the authenticated owner of the subscription with a `RotateSecretRequest` body containing the current `keyId`, the system SHALL atomically replace the stored HMAC secret with a newly generated one (preserving the existing `keyId`) and return `HmacCredentials` containing the same `keyId` and the new base64-encoded `secret`.

#### Scenario: Successful rotation
- **WHEN** an authenticated client calls the rotate endpoint for a subscription they own with the correct current `keyId` in the request body
- **THEN** the system SHALL return `200 OK` with an `HmacCredentials` body containing the same `keyId` and a new `secret` different from the previous value

#### Scenario: Incorrect current keyId in request
- **WHEN** an authenticated client calls the rotate endpoint with a `keyId` that does not match the subscription's stored keyId
- **THEN** the system SHALL return `404 Not Found`

#### Scenario: Subscription not found or not owned by caller
- **WHEN** an authenticated client calls the rotate endpoint with a `clientSubscriptionId` that does not exist or belongs to a different client
- **THEN** the system SHALL return `404 Not Found`

### Requirement: API spec library version is 2.0.8
The project SHALL declare `uk.gov.hmcts.cp:api-cp-crime-hearing-results-document-subscription:2.0.8` as its implementation dependency in `build.gradle`.

#### Scenario: Dependency version matches 2.0.8
- **WHEN** the project is built
- **THEN** the resolved `api-cp-crime-hearing-results-document-subscription` jar version SHALL be `2.0.8`
