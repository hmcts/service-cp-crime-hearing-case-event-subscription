package uk.gov.hmcts.cp.subscription.services;

import java.util.Map;

public final class EventPayloadValidator {

    private EventPayloadValidator() {
    }

    public static boolean isJsonNodeIntrospection(final Map<String, Object> payload) {
        return payload != null
                && payload.containsKey("nodeType")
                && payload.containsKey("containerNode")
                && payload.containsKey("object");
    }
}