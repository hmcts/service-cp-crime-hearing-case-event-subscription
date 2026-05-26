package uk.gov.hmcts.cp.subscription.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JsonMapper {

    private ObjectMapper objectMapper;

    public JsonMapper() {
        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
    }

    @SneakyThrows
    public String toJson(final Object object) {
        return objectMapper.writeValueAsString(object);
    }

    @SneakyThrows
    public <T> T fromJson(final String json, final Class<T> clazz) {
        return objectMapper.readValue(json, clazz);
    }

    @SneakyThrows
    public JsonNode toJsonNode(final String json) {
        return objectMapper.readTree(json);
    }

    @SneakyThrows
    public UUID getUUIDAtPath(final String json, final String jsonPointer) {
        final String uuid = toJsonNode(json).at(jsonPointer).textValue();
        return uuid == null ? null : UUID.fromString(uuid);
    }
}
