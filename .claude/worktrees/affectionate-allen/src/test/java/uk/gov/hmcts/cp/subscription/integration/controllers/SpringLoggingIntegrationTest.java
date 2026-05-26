package uk.gov.hmcts.cp.subscription.integration.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.gov.hmcts.cp.subscription.integration.IntegrationTestBase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.cp.filters.TracingFilter.CORRELATION_ID_KEY;

@Slf4j
class SpringLoggingIntegrationTest extends IntegrationTestBase {

    private final PrintStream originalStdOut = System.out;

    @AfterEach
    void afterEach() {
        System.setOut(originalStdOut);
    }

    UUID correlationId = UUID.randomUUID();

    @Test
    void springboot_test_should_log_correct_fields() throws IOException {
        MDC.put(CORRELATION_ID_KEY, correlationId.toString());
        final ByteArrayOutputStream capturedStdOut = captureStdOut();
        log.info("spring boot test message", new RuntimeException("TestException"));

        final String logMessage = capturedStdOut.toString(StandardCharsets.UTF_8);
        assertThat(logMessage).isNotEmpty();

        final Map<String, Object> capturedFields =
                new ObjectMapper().readValue(logMessage, new TypeReference<>() {
                });

        assertThat(capturedFields.get(CORRELATION_ID_KEY)).isEqualTo(correlationId.toString());
        assertThat(capturedFields.get("timestamp")).isNotNull();
        assertThat(capturedFields.get("message").toString()).contains("spring boot test message");
        assertThat(capturedFields.get("logger_name"))
                .isEqualTo("uk.gov.hmcts.cp.subscription.integration.controllers.SpringLoggingIntegrationTest");
        assertThat(capturedFields.get("thread_name")).isEqualTo("Test worker");
        assertThat(capturedFields.get("level")).isEqualTo("INFO");
        assertThat(capturedFields.get("exception").toString())
                .contains("java.lang.RuntimeException: TestException")
                .contains("uk.gov.hmcts.cp.subscription.integration.controllers.SpringLoggingIntegrationTest");
    }

    @Test
    void log_exception_should_be_just_about_visible_in_idea() {
        log.info("An info message");
        log.error("An error message");
        // This error is very difficult to see in idea.
        // We get a grey / non obvious "<1 internal line>" link on the end of previous line
        log.error("An error message with exception", new RuntimeException("Json parse error"));
        log.info("Another info message");
        log.error("Another error message");
    }

    private ByteArrayOutputStream captureStdOut() {
        final ByteArrayOutputStream capturedStdOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedStdOut, true, StandardCharsets.UTF_8));
        return capturedStdOut;
    }
}
