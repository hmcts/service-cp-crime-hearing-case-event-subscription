# Azure Service Bus Notes

We use Azure Service bus to process async messages

This currently includes the following operations

1) Inbound message from progression ( such as PCR event )
This is written to service bus queue so that we can reply quickly to progression. 
The processing of the queued item interacts with material service and may need to wait for the document to be ready

2) Outbound message from AMP to subscribers
We queue multiple outbound items with one queue item for each subscriber.
The processing of the queue items are independent of each other


# Queue Message Wrapper
We submit items to the ServiceBus queue using an azure object "ServiceBusMessage".
The ServiceBusMessage contains a string body that contains the details that the consumer needs to process the queue item.
To facilitate queue processing and queue retries, we use a wrapper around the message string.
The wrapper may contain additional information such as 
* CorrelationId
* Failure count
* Target url
etc.

# Queue Processing Retries


Note that we currently use a topic with a single subscription rather than a queue
We will probably switch this to using a service queue which has a one to one mapping rather than topic
