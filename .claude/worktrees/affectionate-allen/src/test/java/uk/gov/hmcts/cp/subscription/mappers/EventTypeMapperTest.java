package uk.gov.hmcts.cp.subscription.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.openapi.model.EventTypePayload;
import uk.gov.hmcts.cp.openapi.model.EventTypeResponse;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class EventTypeMapperTest {

    EventTypeMapper eventTypeMapper = new EventTypeMapperImpl();

    @Test
    void entity_should_map_to_payload() {
        EventTypeEntity entity = EventTypeEntity.builder()
                .id(1L)
                .eventName("PRISON_COURT_REGISTER_GENERATED")
                .displayName("Prison court register")
                .category("REGISTER")
                .build();

        EventTypeResponse eventTypes = eventTypeMapper.mapToEventTypes(List.of(entity));
        assertThat(eventTypes.getEvents().size()).isEqualTo(1);

        EventTypePayload eventType = eventTypes.getEvents().getFirst();
        assertThat(eventType.getEventName()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(eventType.getDisplayName()).isEqualTo("Prison court register");
        assertThat(eventType.getCategory()).isEqualTo("REGISTER");
    }


}