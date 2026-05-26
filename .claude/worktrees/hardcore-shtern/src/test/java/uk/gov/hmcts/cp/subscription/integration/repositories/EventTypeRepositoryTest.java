package uk.gov.hmcts.cp.subscription.integration.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.subscription.entities.EventTypeEntity;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class EventTypeRepositoryTest extends IntegrationTestBase {

    @Transactional
    @Test
    void findAllEventTypes_should_return_document() {
        EventTypeEntity entity1 = EventTypeEntity.builder()
                .id(1L)
                .eventName("PRISON_COURT_REGISTER_GENERATED")
                .displayName("Prison court register")
                .category("REGISTER")
                .build();
        EventTypeEntity entity2 = EventTypeEntity.builder()
                .id(2L)
                .eventName("WEE_Layout5")
                .displayName("Warrant Supplement")
                .category("WARRANT")
                .build();

        List<EventTypeEntity> found = eventTypeRepository.findAll();
        assertThat(found.size()).isEqualTo(41);
        assertThat(found).contains(entity1, entity2);
    }

    @Transactional
    @Test
    void findByEventName_should_return_event_type() {
        Optional<EventTypeEntity> found = eventTypeRepository.findByEventName("PRISON_COURT_REGISTER_GENERATED");
        
        assertThat(found).isPresent();
        assertThat(found.get().getEventName()).isEqualTo("PRISON_COURT_REGISTER_GENERATED");
        assertThat(found.get().getDisplayName()).isEqualTo("Prison court register");
        assertThat(found.get().getCategory()).isEqualTo("REGISTER");
    }

    @Transactional
    @Test
    void findByEventName_should_return_empty_when_not_found() {
        Optional<EventTypeEntity> found = eventTypeRepository.findByEventName("NON_EXISTENT_EVENT");
        assertThat(found).isEmpty();
    }

    @Transactional
    @Test
    void existsByEventName_should_return_true_when_event_exists() {
        boolean exists = eventTypeRepository.existsByEventName("PRISON_COURT_REGISTER_GENERATED");
        assertThat(exists).isTrue();
    }
}
