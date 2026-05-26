package uk.gov.hmcts.cp.subscription.mappers;

import org.mapstruct.Mapper;
import uk.gov.hmcts.cp.openapi.model.EventTypeResponse;
import uk.gov.hmcts.cp.openapi.model.EventTypePayload;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventTypeMapper {

    List<EventTypePayload> map(List<EventTypeEntity> eventTypeEntities);

    default EventTypeResponse mapToEventTypes(final List<EventTypeEntity> eventTypeEntities) {
        return EventTypeResponse.builder()
                .events(map(eventTypeEntities))
                .build();
    }
}
