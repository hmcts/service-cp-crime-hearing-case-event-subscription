package uk.gov.hmcts.cp.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: verifies that log message fragments used in KQL queries (support/logs-kql/ and
 * support/alerts-kql/) still exist in the expected Java source files, the correct number of times.
 *
 * <p>If a log message is renamed or removed, this test fails — reminding you to consider the KQL
 * queries too.
 */
class KqlLogMessageContractTest {

    private static final Map<String, String> RUNTIME_VALUE_SUBSTITUTIONS = Map.of(
            "failureCount:6", "failureCount:"
    );

    private static final Set<String> PLACEHOLDER_FRAGMENTS = Set.of(
            "REPLACE_ME",          // trace query placeholder
            "notifications.inbound",   // queue name embedded at runtime, not a literal log string
            "notifications.outbound",  // queue name embedded at runtime, not a literal log string
            "NotificationService",     // class name used as log filter, not a literal log message
            "CallbackClient",          // class name used as log filter, not a literal log message
            "CallbackDeliveryService", // class name used as log filter, not a literal log message
            "correlationId"            // variable substituted at query time
    );

    private static final Pattern KQL_CONTAINS_PATTERN =
            Pattern.compile("(?:\\| where|or) LogMessage contains ['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    static Stream<Arguments> kqlLogMessageContracts() {
        return Stream.of(
                kqlContract("Received notification request from Progression/HearingNows", "NotificationController", 1),
                kqlContract("Received notification request", "NotificationController", 1),
                kqlContract("handleMessage", "ServiceBusProcessorService", 5),
                kqlContract("processInboundEvent", "NotificationService", 2),
                kqlContract("handleMessage FAILED FINALLY", "ServiceBusProcessorService", 2),
                kqlContract("handleError unexpected error on", "ServiceBusProcessorService", 1),
                kqlContract("Sending notification", "CallbackClient", 1),
                kqlContract("failureCount:", "ServiceBusProcessorService", 2),
                kqlContract("duplicate subscription request", "SubscriptionValidationService", 1)
        );
    }

    private static Arguments kqlContract(final String fragment, final String className, final int expectedCount) {
        return Arguments.of(fragment, className, expectedCount);
    }

    @Test
    void all_kql_log_message_fragments_should_be_covered_by_contracts() throws IOException {
        final Set<String> kqlFragments = extractKqlFragments();
        final Set<String> contractFragments = kqlLogMessageContracts()
                .map(args -> (String) args.get()[0])
                .collect(Collectors.toSet());

        assertThat(kqlFragments)
                .as("KQL fragments not covered by contract test — add entries to kqlLogMessageContracts()")
                .containsExactlyInAnyOrderElementsOf(contractFragments);
    }

    @ParameterizedTest(name = "\"{0}\" should appear {2} time(s) in {1}")
    @MethodSource("kqlLogMessageContracts")
    void kql_log_message_fragment_should_exist_in_java_source(
            final String fragment, final String className, final int expectedCount) throws IOException {
        final Path sourceFile = findJavaFile(className);
        assertThat(sourceFile).as("Java source file not found for class: " + className).isNotNull();

        final String source = Files.readString(sourceFile);
        final int actualCount = countOccurrences(source, fragment);

        assertThat(actualCount)
                .as("Log message fragment \"%s\" expected %d time(s) in %s but found %d — "
                                + "be careful changing this log message it may affect KQL alert queries",
                        fragment, expectedCount, className, actualCount)
                .isEqualTo(expectedCount);
    }

    /**
     * The inbound/outbound failure-count alerts (amp-hrds-inbound-failure-count-6.kql /
     * amp-hrds-outbound-failure-count-6.kql) filter a single LogMessage on BOTH "failureCount:" AND
     * the queue name ("notifications.inbound" / "notifications.outbound"). That only works if the
     * failureCount log statement also logs the queueName — otherwise the two fragments never co-occur
     * in one message and the alerts silently never fire.
     *
     * <p>This guards against dropping queueName from those log statements (the original bug that
     * slipped past the fragment-existence contracts above).
     */
    @Test
    void failure_count_log_statements_should_include_queue_name_so_inbound_outbound_alerts_can_match()
            throws IOException {
        final Path sourceFile = findJavaFile("ServiceBusProcessorService");
        assertThat(sourceFile).as("ServiceBusProcessorService.java not found").isNotNull();

        final String source = Files.readString(sourceFile);
        final Pattern failureCountStatement =
                Pattern.compile("log\\.[a-z]+\\([^;]*failureCount:[^;]*\\);");
        final Matcher matcher = failureCountStatement.matcher(source);

        int statementsChecked = 0;
        while (matcher.find()) {
            final String statement = matcher.group();
            assertThat(statement)
                    .as("failureCount log statement must also log queueName, otherwise the inbound/outbound "
                            + "failure-count alerts (which filter on the queue name) can never match:%n%s", statement)
                    .contains("queueName");
            statementsChecked++;
        }

        assertThat(statementsChecked)
                .as("expected to find failureCount log statements in ServiceBusProcessorService")
                .isEqualTo(2);
    }

    private Set<String> extractKqlFragments() throws IOException {
        final Set<String> fragments = new HashSet<>();
        for (final String folder : new String[]{"support/logs-kql", "support/alerts-kql"}) {
            final Path kqlPath = Path.of(folder);
            if (!Files.exists(kqlPath)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(kqlPath)) {
                files.filter(p -> p.toString().endsWith(".kql")).forEach(kqlFile -> {
                    try {
                        final Matcher matcher = KQL_CONTAINS_PATTERN.matcher(Files.readString(kqlFile));
                        while (matcher.find()) {
                            final String rawFragment = matcher.group(1);
                            if (!PLACEHOLDER_FRAGMENTS.contains(rawFragment)) {
                                fragments.add(RUNTIME_VALUE_SUBSTITUTIONS.getOrDefault(rawFragment, rawFragment));
                            }
                        }
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        return fragments;
    }

    private Path findJavaFile(final String className) throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files
                    .filter(p -> p.getFileName().toString().equals(className + ".java"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private int countOccurrences(final String source, final String fragment) {
        final Pattern logPattern = Pattern.compile("log\\.[^\n]*" + Pattern.quote(fragment));
        return (int) logPattern.matcher(source).results().count();
    }
}
