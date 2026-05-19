package uk.gov.hmcts.cp.subscription.converters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class EventPayloadConverterTest {

    private EventPayloadConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EventPayloadConverter(new JsonMapper());
    }

    @Test
    void convertToDatabaseColumn_should_serialise_eventPayload_to_json_string() {
        EventPayload payload = EventPayload.builder()
                .eventType("PRISON_COURT_REGISTER_GENERATED")
                .build();

        String result = converter.convertToDatabaseColumn(payload);

        assertThat(result).contains("PRISON_COURT_REGISTER_GENERATED");
    }

    @Test
    void convertToEntityAttribute_should_deserialise_json_string_to_eventPayload() {
        String json = "{\"eventType\":\"PRISON_COURT_REGISTER_GENERATED\"}";

        EventPayload result = converter.convertToEntityAttribute(json);

        assertThat(result.getEventType()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
    }
}
