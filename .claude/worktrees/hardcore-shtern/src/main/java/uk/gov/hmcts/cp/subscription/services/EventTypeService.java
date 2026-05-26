package uk.gov.hmcts.cp.subscription.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.openapi.model.EventTypeResponse;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.mappers.EventTypeMapper;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventTypeService {

    private final EventTypeRepository eventTypeRepository;
    private final EventTypeMapper eventTypeMapper;

    public EventTypeResponse getAllEventTypes() {
        final List<EventTypeEntity> eventTypeEntityList = eventTypeRepository.findAll();
        return eventTypeMapper.mapToEventTypes(eventTypeEntityList);
    }

    public boolean eventExists(final String eventName) {
        return eventTypeRepository.existsByEventName(eventName);
    }
}
