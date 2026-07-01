# APIM KQL queries

Queries for inspecting how the HRDS service is called through Azure API Management.

These run against **Application Insights `requests`** telemetry (APIM → App Insights
integration), because the gateway diagnostic logs (`ApiManagementGatewayLogs` /
`AzureDiagnostics`) are not currently populated in the workspace.

- The APIM **subscription** (name/id) and **product** are surfaced via `customDimensions`.
  A subscription maps 1:1 to a rate-limit tier, so it identifies which key/limit a caller uses.
- The raw subscription **key** is never logged (secret) — use Subscription Name/Id instead.
- Rate-limit rejections appear as HTTP **429**.

Adjust the `name contains "hrds"` filter and the time range to suit.

If APIM gateway diagnostic logging gets enabled later, prefer `ApiManagementGatewayLogs`
(resource-specific mode) or `AzureDiagnostics | where ResourceProvider == "MICROSOFT.APIMANAGEMENT"`
(legacy mode) — see `gateway-logs-fallback.kql`.
