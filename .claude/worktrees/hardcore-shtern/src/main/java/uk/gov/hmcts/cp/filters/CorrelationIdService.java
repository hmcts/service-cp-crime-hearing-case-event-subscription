package uk.gov.hmcts.cp.filters;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CorrelationIdService {

    public UUID random() {
        return UUID.randomUUID();
    }

    public String randomString() {
        return UUID.randomUUID().toString();
    }
}
