package uk.gov.hmcts.cp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.cp.entities.SubscriptionEntity;
import uk.gov.hmcts.cp.openapi.model.Result;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriptionMapper {

    @Mapping(source = "notifyUrl", target = "resultText")
    Result mapSubscriptionToResult(SubscriptionEntity subscription);

    List<Result> mapSubscriptionsToResults(List<SubscriptionEntity> subscriptions);

}
