package uk.gov.hmcts.cp.subscription.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.hmcts.cp.subscription.clients.model.MaterialResponse;

import java.util.UUID;

@FeignClient(
        name = "material-service",
        url = "${material-service.url}"
)
public interface MaterialService {

    @GetMapping("/material-query-api/query/api/rest/material/material/{materialId}/metadata")
    MaterialResponse getByMaterialId(@PathVariable UUID materialId);
}