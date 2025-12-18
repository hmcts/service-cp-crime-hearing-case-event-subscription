package uk.gov.hmcts.cp.subscription.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@Slf4j
public class RootController {

    /**
     * Root GET endpoint. Temp solution. The deployment helm charts are set to perform health checks using /
     * Once we understand the helm better we will amend tne halth checks to use /actuator/info
     */
    @GetMapping("/")
    public ResponseEntity<String> root() {
        return ok("DEPRECATED root endpoint");
    }
}
