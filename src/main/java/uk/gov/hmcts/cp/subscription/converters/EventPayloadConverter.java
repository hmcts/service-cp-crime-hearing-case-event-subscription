package uk.gov.hmcts.cp.subscription.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.openapi.model.EventPayload;
import uk.gov.hmcts.cp.subscription.services.JsonMapper;

@Converter
@Component
public class EventPayloadConverter implements AttributeConverter<EventPayload, String> {

    private static volatile EventPayloadConverter instance;

    private final JsonMapper jsonMapper;

    @Autowired
    public EventPayloadConverter(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        EventPayloadConverter.instance = this;
    }

    protected EventPayloadConverter() {
        this.jsonMapper = null;
    }

    private JsonMapper resolveMapper() {
        return jsonMapper != null ? jsonMapper : instance.jsonMapper;
    }

    @Override
    public String convertToDatabaseColumn(final EventPayload attribute) {
        return resolveMapper().toJson(attribute);
    }

    @Override
    public EventPayload convertToEntityAttribute(final String dbData) {
        return resolveMapper().fromJson(dbData, EventPayload.class);
    }
}
