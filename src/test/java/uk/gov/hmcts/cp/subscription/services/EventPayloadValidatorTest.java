package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventPayloadValidatorTest {

    @Test
    void detects_jsonnode_introspection_payload() {
        final Map<String, Object> introspection = new LinkedHashMap<>();
        introspection.put("int", false);
        introspection.put("long", false);
        introspection.put("object", true);
        introspection.put("nodeType", "OBJECT");
        introspection.put("textual", false);
        introspection.put("containerNode", true);

        assertThat(EventPayloadValidator.isJsonNodeIntrospection(introspection)).isTrue();
    }

    @Test
    void accepts_real_pcr_document_payload() {
        final Map<String, Object> realPayload = new LinkedHashMap<>();
        realPayload.put("registerDate", "2026-05-29");
        realPayload.put("cases", java.util.List.of(Map.of("caseId", "11111111-1111-1111-1111-111111111111")));
        realPayload.put("orderingCourt", "Some Court");

        assertThat(EventPayloadValidator.isJsonNodeIntrospection(realPayload)).isFalse();
    }

    @Test
    void accepts_real_now_document_payload() {
        final Map<String, Object> realPayload = new LinkedHashMap<>();
        realPayload.put("prosecutionCase", Map.of("defendant", Map.of("name", "A B")));
        realPayload.put("orderDate", "2026-05-29");

        assertThat(EventPayloadValidator.isJsonNodeIntrospection(realPayload)).isFalse();
    }

    @Test
    void treats_null_and_empty_payload_as_valid() {
        assertThat(EventPayloadValidator.isJsonNodeIntrospection(null)).isFalse();
        assertThat(EventPayloadValidator.isJsonNodeIntrospection(Map.of())).isFalse();
    }
}