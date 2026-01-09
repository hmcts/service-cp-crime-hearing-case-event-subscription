package uk.gov.hmcts.cp.subscription.services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@AllArgsConstructor
/**
 * We use a ClockService to expose the clock time in a simple method to allow mocking in Tests
 */
public class ClockService {

    private Clock clock;

    public Instant now() {
        return clock.instant();
    }

    public OffsetDateTime nowOffsetUTC() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }
}
