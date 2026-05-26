
### Nows/Results subscription Sequence diagram
```mermaid
%%{init: {"theme": "light"} }%%
sequenceDiagram
    autonumber

    participant HN as Hearing NOWs<br/>Service
    participant MS as Material<br/>Service
    participant SS as Subscription<br/>Service
    participant Q as Azure Service Bus
    participant C as Consumers<br/>(RaSS, YCS)

%% Subscriber discovery
    rect rgba(0, 128, 0, 0.1)
        C->>SS: GET NOWs events
        note right of SS: AMP-200

        SS-->>C: Returns event types
        note left of SS: AMP-200

        C->>SS: Subscribe to NOWs events
        note right of SS: AMP-197
    end

%% Hearing NOWs processing
    rect rgba(0, 0, 255, 0.1)
        HN->>MS: Upload PDF
        MS-->>HN: material.material-added

        HN->>HN: Feature toggle
        note left of HN: AMP-195

        HN->>HN: Build command
        note left of HN: AMP-196
    end

%% Notification trigger
    rect rgba(0, 0, 255, 0.1)

        HN->>SS: POST /notifications/now
        note right of SS: AMP-274

        rect rgba(233, 193, 208, 0.4)
            Note over SS,Q: PCR Notification Processing

            SS-->>+Q: Enqueue PCR notification
            note left of Q: AMP-TBC (producer publishes message)

            Q-->>+SS: Dequeue PCR notification
            note left of SS: AMP-TBC (consumer starts processing)

            SS-->>-MS: GET Query metadata
            note left of SS: AMP-TBC (enrich notification)

            MS-->>SS: Metadata response
        end

        SS->>SS: Find subscribers for documentType
        note left of SS: AMP-TBC (lookup subscriptions)

        loop For each subscriber
            SS->>Q: Enqueue webhook
            note left of Q: AMP-198

            Q-->>SS: Dequeue webhook
            note left of SS: AMP-TBC (webhook worker)

            SS->>C: Deliver webhook
            note right of C: AMP-271

            C-->>SS: 200 OK
        end
    end

%% Consumer fetch
    rect rgba(0, 128, 0, 0.1)
        C->>SS: Get PDF document
        SS->>MS: Get PDF document
        MS-->>SS: PDF stream
        SS-->>C: Return PDF document
    end


```