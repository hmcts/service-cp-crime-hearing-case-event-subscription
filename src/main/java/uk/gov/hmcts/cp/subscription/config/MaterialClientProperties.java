package uk.gov.hmcts.cp.subscription.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Getter
@Service
public class MaterialClientProperties {

    private final String baseUrl;
    private final String cjscppuid;

    public MaterialClientProperties(
            @Value("${material-client.url}") final String baseUrl,
            @Value("${material-client.cjscppuid}") final String cjscppuid) {
        this.baseUrl = baseUrl;
        this.cjscppuid = cjscppuid;
    }
}
