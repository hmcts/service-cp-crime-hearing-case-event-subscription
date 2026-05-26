package uk.gov.hmcts.cp.subscription.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JsonMapperTest {

    @InjectMocks
    JsonMapper jsonMapper;

    @Test
    void mapper_should_convert_object_to_json_and_ignore_null_fields() {
        Example example = Example.builder()
                .build();

        String json = jsonMapper.toJson(example);
        assertThat(json).isEqualTo("{}");

        Example exampleAgain = jsonMapper.fromJson(json, Example.class);
        assertThat(exampleAgain).usingRecursiveComparison().isEqualTo(example);
    }

    @Test
    void mapper_should_convert_object_to_json_with_utc_time() {
        OffsetDateTime odt = OffsetDateTime.of(2026, 1, 31, 12, 30, 45, 500, ZoneOffset.UTC);
        Example example = Example.builder()
                .booleanField(true)
                .intField(24)
                .stringField("message")
                .dateField(odt)
                .build();

        String json = jsonMapper.toJson(example);
        assertThat(json).isEqualTo("{\"booleanField\":true,\"intField\":24,\"stringField\":\"message\",\"dateField\":\"2026-01-31T12:30:45.0000005Z\"}");

        Example exampleAgain = jsonMapper.fromJson(json, Example.class);
        assertThat(exampleAgain).usingRecursiveComparison().isEqualTo(example);
    }

    @Test
    void mapper_should_convert_object_to_json_with_list() {
        Example example = Example.builder()
                .listStringField(List.of("item1", "item2"))
                .build();

        String json = jsonMapper.toJson(example);
        assertThat(json).isEqualTo("{\"listStringField\":[\"item1\",\"item2\"]}");

        Example exampleAgain = jsonMapper.fromJson(json, Example.class);
        assertThat(exampleAgain).usingRecursiveComparison().isEqualTo(example);
    }

    @Test
    void mapper_should_convert_embedded_list() {
        OffsetDateTime odt = OffsetDateTime.of(2026, 1, 31, 12, 30, 45, 500, ZoneOffset.UTC);
        EmbeddedExample embeddedExample = EmbeddedExample.builder().stringField("some text").dateField(odt).build();
        Example example = Example.builder()
                .listEmbeddedExample(List.of(embeddedExample))
                .build();

        String json = jsonMapper.toJson(example);
        assertThat(json).isEqualTo("{\"listEmbeddedExample\":[{\"stringField\":\"some text\",\"dateField\":\"2026-01-31T12:30:45.0000005Z\"}]}");

        Example exampleAgain = jsonMapper.fromJson(json, Example.class);
        assertThat(exampleAgain).usingRecursiveComparison().isEqualTo(example);
    }

    @Test
    void json_node_pointer_should_get_field_value(){
        String json = "{\"listEmbeddedExample\":[{\"stringField\":\"some text\",\"dateField\":\"2026-01-31T12:30:45.0000005Z\"}]}";
        JsonNode jsonNode = jsonMapper.toJsonNode(json);
        assertThat(jsonNode.at("/listEmbeddedExample/0/stringField").textValue()).isEqualTo("some text");
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL) // Only show Non null i.e. omit null values
    static class Example {
        private Boolean booleanField;
        private Integer intField;
        private String stringField;
        private OffsetDateTime dateField;
        private List<String> listStringField;
        private List<EmbeddedExample> listEmbeddedExample;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    static class EmbeddedExample {
        private String stringField;
        private OffsetDateTime dateField;
    }
}