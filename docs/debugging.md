# Debugging

The command below will post to create a subscription and return a clientSubscriptionId which can then be queried with GET
```
export PORT=4550
export HOST="localhost:$PORT"
curl -XPOST http://$HOST/client-subscriptions \
    -H "Content-Type: application/json" \
    -d '{"eventTypes":["PRISON_COURT_REGISTER_GENERATED","CUSTODIAL_RESULT"],"notificationEndpoint":{"webhookUrl":"https://my-callback-url"}}'
```

... Creates and returns a subscription with a clientSubscriptionId 
ie.
```
{"clientSubscriptionId":"585fde62-44df-4b3d-ac38-5e1f4d7669e3",
    "createdAt":"2025-12-19T15:16:38.456506Z",
    "eventTypes":["CUSTODIAL_RESULT","PRISON_COURT_REGISTER_GENERATED"],
    "notificationEndpoint":{"webhookUrl":"https://my-callback-url"},"updatedAt":"2025-12-19T15:16:38.456772Z"}
```

Query
```
export SUBSCRIPTION_ID="585fde62-44df-4b3d-ac38-5e1f4d7669e3"
curl http://$HOST/client-subscriptions/$SUBSCRIPTION_ID
```