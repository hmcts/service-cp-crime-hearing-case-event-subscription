package uk.gov.hmcts.cp.subscription.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.openapi.model.EventTypeResponse;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.mappers.EventTypeMapper;
import uk.gov.hmcts.cp.subscription.repositories.EventTypeRepository;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventTypeServiceTest {

    @Mock
    private EventTypeRepository eventTypeRepository;

    @Mock
    private EventTypeMapper eventTypeMapper;

    @InjectMocks
    private EventTypeService eventTypeService;

    @Test
    void all_event_types_should_be_returned() {
        EventTypeEntity entity = Mockito.mock(EventTypeEntity.class);
        List<EventTypeEntity> entityList = List.of(entity);

        EventTypeResponse eventTypes = Mockito.mock(EventTypeResponse.class);

        when(eventTypeRepository.findAll()).thenReturn(entityList);
        when(eventTypeMapper.mapToEventTypes(entityList)).thenReturn(eventTypes);

        eventTypeService.getAllEventTypes();

        verify(eventTypeRepository).findAll();
        verify(eventTypeMapper).mapToEventTypes(entityList);

    }

    @Test
    void eventExists_should_return_true_when_event_exists() {
        String eventName = "PRISON_COURT_REGISTER_GENERATED";
        when(eventTypeRepository.existsByEventName(eventName)).thenReturn(true);

        boolean result = eventTypeService.eventExists(eventName);
        assertThat(result).isTrue();
        verify(eventTypeRepository).existsByEventName(eventName);
    }

    @Test
    void eventExists_should_return_false_when_event_not_exists() {
        String eventName = "NON_EXISTENT_EVENT";
        when(eventTypeRepository.existsByEventName(eventName)).thenReturn(false);

        boolean result = eventTypeService.eventExists(eventName);
        assertThat(result).isFalse();
        verify(eventTypeRepository).existsByEventName(eventName);
    }
}