package uk.gov.hmcts.cp.subscription.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.hmcts.cp.subscription.entities.ClientEventEntity;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ClientEventEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "subscriptionId", source = "subscriptionId")
    @Mapping(target = "eventTypeId", source = "eventTypeId")
    ClientEventEntity toEntity(UUID subscriptionId, Long eventTypeId);

}
